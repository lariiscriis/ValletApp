package br.edu.fatecpg.valletprojeto.fragments

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognizerIntent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import br.edu.fatecpg.valletprojeto.VagaActivity
import br.edu.fatecpg.valletprojeto.adapter.FavoriteParkingAdapter
import br.edu.fatecpg.valletprojeto.adapter.EstacionamentosAdapter
import br.edu.fatecpg.valletprojeto.model.Estacionamento
import br.edu.fatecpg.valletprojeto.R
import br.edu.fatecpg.valletprojeto.databinding.FragmentEstacionamentosBinding
import br.edu.fatecpg.valletprojeto.viewmodel.EstacionamentosViewModel
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Locale
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class EstacionamentosFragment : Fragment() {

    private var _binding: FragmentEstacionamentosBinding? = null
    private val binding get() = _binding!!

    private val viewModel: EstacionamentosViewModel by viewModels()

    private lateinit var simpleAdapter: EstacionamentosAdapter
    private lateinit var favoriteAdapter: FavoriteParkingAdapter

    private val handler = Handler(Looper.getMainLooper())
    private var currentLoadingStep = ""

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                updateLoadingStep("Permissão concedida - Buscando localização...")
                viewModel.loadData()
            } else {
                updateLoadingStep("Permissão negada - Carregando sem localização...")
                Toast.makeText(requireContext(), "Permissão de localização negada.", Toast.LENGTH_SHORT).show()
                viewModel.loadData(useLocation = false)
            }
        }

    private val speechResultLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val data = result.data
            val results = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val spokenText = results?.getOrNull(0)
            if (!spokenText.isNullOrBlank()) {
                handleVoiceCommand(spokenText)
            }
        }
    }

    private fun handleVoiceCommand(spokenText: String) {
        val command = spokenText.lowercase(Locale.getDefault())

        when {
            command.contains("cancel") -> {
                cancelarReservaAtivaPorVoz()
            }
            command.contains("reserv") -> {
                val parkingNameQuery = extractParkingNameFromCommand(command)
                val currentParkings = viewModel.parkings.value ?: emptyList()

                val targetParking = if (parkingNameQuery.isNotBlank()) {
                    currentParkings.firstOrNull { it.nome.contains(parkingNameQuery, ignoreCase = true) }
                        ?: currentParkings.firstOrNull()
                } else {
                    currentParkings.firstOrNull()
                }

                if (targetParking != null) {
                    Toast.makeText(requireContext(), "Abrindo vagas para ${targetParking.nome}", Toast.LENGTH_SHORT).show()
                    val intent = Intent(requireContext(), VagaActivity::class.java)
                    intent.putExtra("estacionamentoId", targetParking.id)
                    intent.putExtra("estacionamentoNome", targetParking.nome)
                    startActivity(intent)
                } else {
                    Toast.makeText(requireContext(), "Nenhum estacionamento encontrado para reserva", Toast.LENGTH_SHORT).show()
                    binding.svSearch.setQuery(spokenText, true)
                }
            }
            command.contains("favorit") -> {
                Toast.makeText(requireContext(), "Filtrando favoritos", Toast.LENGTH_SHORT).show()
                binding.svSearch.setQuery("favorito", false)
            }
            else -> {
                binding.svSearch.setQuery(spokenText, true)
            }
        }
    }

    private fun extractParkingNameFromCommand(command: String): String {
        return command
            .replace("reserve", "")
            .replace("reservar", "")
            .replace("reserva", "")
            .replace("uma", "")
            .replace("vaga", "")
            .replace("no", "")
            .replace("na", "")
            .replace("estacionamento", "")
            .replace("por favor", "")
            .trim()
    }

    private fun cancelarReservaAtivaPorVoz() {
        val contexto = requireContext().applicationContext
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                br.edu.fatecpg.valletprojeto.service.CancelamentoReserva.cancelarAtiva(contexto)
                Toast.makeText(contexto, "Reserva cancelada com sucesso!", Toast.LENGTH_LONG).show()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Toast.makeText(contexto, e.message ?: "Não foi possível cancelar.", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentEstacionamentosBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        showLoadingState()
        updateLoadingStep("Preparando ambiente...")

        setupRecyclerViews()
        setupSearchView()
        observeViewModel()

        verificarPermissaoLocalizacao()
    }

    private fun showLoadingState() {
        binding.loadingState.visibility = View.VISIBLE
        binding.contentState.visibility = View.GONE

        binding.loadingFavorites.visibility = View.VISIBLE
        binding.loadingOtherParkings.visibility = View.VISIBLE

        binding.rvFavorites.visibility = View.GONE
        binding.txvFavoritesTitle.visibility = View.GONE
        binding.rvOtherParkings.visibility = View.GONE
        binding.txvOtherParkingsTitle.visibility = View.GONE
    }

    private fun hideLoadingState() {
        binding.loadingState.visibility = View.GONE
        binding.contentState.visibility = View.VISIBLE
    }

    private fun showContentGradually() {
        if (_binding == null || !isAdded) return

        val favorites = viewModel.favoriteParkings.value.orEmpty()
        val parkings = viewModel.parkings.value.orEmpty()

        binding.loadingState.visibility = View.GONE
        binding.contentState.visibility = View.VISIBLE

        binding.loadingFavorites.visibility = View.GONE
        binding.loadingOtherParkings.visibility = View.GONE

        binding.txvFavoritesTitle.visibility =
            if (favorites.isNotEmpty()) View.VISIBLE else View.GONE

        binding.rvFavorites.visibility =
            if (favorites.isNotEmpty()) View.VISIBLE else View.GONE

        binding.txvOtherParkingsTitle.visibility =
            if (parkings.isNotEmpty()) View.VISIBLE else View.GONE

        binding.rvOtherParkings.visibility =
            if (parkings.isNotEmpty()) View.VISIBLE else View.GONE
    }

    private fun updateLoadingStep(step: String) {
        currentLoadingStep = step
        if (isAdded) {
            binding.txvLoadingStep.text = step
        }
    }

    private fun setupRecyclerViews() {
        simpleAdapter = EstacionamentosAdapter(
            onFavoriteClicked = { estacionamento ->
                viewModel.toggleFavoriteStatus(estacionamento)
            },
            onItemClicked = { estacionamento ->
                viewModel.onEstacionamentoClicked(estacionamento)
            },
            onQrCodeClicked = { estacionamento ->
                mostrarQrCodeAcesso(estacionamento)
            }
        )
        binding.rvOtherParkings.layoutManager = object : LinearLayoutManager(requireContext()) {
            override fun canScrollVertically(): Boolean = false
        }
        binding.rvOtherParkings.isNestedScrollingEnabled = false
        binding.rvOtherParkings.setHasFixedSize(false)
        binding.rvOtherParkings.adapter = simpleAdapter

        favoriteAdapter = FavoriteParkingAdapter(
            onFavoriteClicked = { estacionamento ->
                viewModel.toggleFavoriteStatus(estacionamento)
            },
            onItemClicked = { estacionamento ->
                viewModel.onEstacionamentoClicked(estacionamento)
            },
            onQrCodeClicked = { estacionamento ->
                mostrarQrCodeAcesso(estacionamento)
            }
        )
        binding.rvFavorites.layoutManager = object : LinearLayoutManager(requireContext()) {
            override fun canScrollVertically(): Boolean = false
        }
        binding.rvFavorites.isNestedScrollingEnabled = false
        binding.rvFavorites.setHasFixedSize(false)
        binding.rvFavorites.adapter = favoriteAdapter
    }

    private fun mostrarQrCodeAcesso(estacionamento: Estacionamento) {
        val context = requireContext()
        val imageView = android.widget.ImageView(context).apply {
            layoutParams = android.widget.FrameLayout.LayoutParams(650, 650).apply {
                gravity = android.view.Gravity.CENTER
            }
            scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
            setPadding(16, 16, 16, 16)
        }

        val qrContent = "VALLET-ACESSO-${estacionamento.id}-${System.currentTimeMillis()}"
        val bitmap = gerarBitmapQrCode(qrContent)
        imageView.setImageBitmap(bitmap)

        val cardView = com.google.android.material.card.MaterialCardView(context).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = android.view.Gravity.CENTER
                setMargins(0, 16, 0, 16)
            }
            radius = 24f * context.resources.displayMetrics.density
            cardElevation = 4f
            setCardBackgroundColor(ContextCompat.getColor(context, R.color.white))
            addView(imageView)
        }

        val btnFechar = com.google.android.material.button.MaterialButton(context).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                140
            ).apply {
                setMargins(0, 24, 0, 0)
            }
            text = "Fechar"
            isAllCaps = false
            textSize = 15f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(ContextCompat.getColor(context, android.R.color.white))
            backgroundTintList = ContextCompat.getColorStateList(context, R.color.vallet_green_dark)
            cornerRadius = (12f * context.resources.displayMetrics.density).toInt()
        }

        val container = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
            gravity = android.view.Gravity.CENTER
            setBackgroundResource(R.drawable.dialog_background)

            addView(android.widget.TextView(context).apply {
                text = "QR Code de Acesso"
                textSize = 18f
                textAlignment = android.view.View.TEXT_ALIGNMENT_CENTER
                setTypeface(null, android.graphics.Typeface.BOLD)
                setPadding(0, 0, 0, 8)
                setTextColor(ContextCompat.getColor(context, R.color.vallet_green_dark))
            })
            addView(android.widget.TextView(context).apply {
                text = estacionamento.nome
                textSize = 14f
                textAlignment = android.view.View.TEXT_ALIGNMENT_CENTER
                setPadding(0, 0, 0, 24)
                setTextColor(ContextCompat.getColor(context, R.color.vallet_text))
            })
            addView(cardView)
            addView(android.widget.TextView(context).apply {
                text = "Apresente este QR Code na entrada do estacionamento."
                textSize = 13f
                textAlignment = android.view.View.TEXT_ALIGNMENT_CENTER
                setPadding(0, 16, 0, 0)
                setTextColor(ContextCompat.getColor(context, R.color.vallet_green_dark))
            })
            addView(btnFechar)
        }

        val dialog = androidx.appcompat.app.AlertDialog.Builder(context)
            .setView(container)
            .create()

        btnFechar.setOnClickListener {
            dialog.dismiss()
        }

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }

    private fun gerarBitmapQrCode(conteudo: String): android.graphics.Bitmap {
        val tamanho = 512
        val bitMatrix = com.google.zxing.qrcode.QRCodeWriter().encode(conteudo, com.google.zxing.BarcodeFormat.QR_CODE, tamanho, tamanho)
        val bitmap = android.graphics.Bitmap.createBitmap(tamanho, tamanho, android.graphics.Bitmap.Config.RGB_565)
        for (x in 0 until tamanho) {
            for (y in 0 until tamanho) {
                bitmap.setPixel(x, y, if (bitMatrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
            }
        }
        return bitmap
    }

    private fun setupSearchView() {
        binding.svSearch.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean = false
            override fun onQueryTextChange(newText: String?): Boolean {
                viewModel.searchParkings(newText.orEmpty())
                return true
            }
        })

        binding.btnVoiceSearch.setOnClickListener {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Fale o nome ou endereço do estacionamento")
            }
            try {
                speechResultLauncher.launch(intent)
            } catch (_: Exception) {
                Toast.makeText(requireContext(), "Reconhecimento de voz não disponível", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnFilterRadius.setOnClickListener {
            mostrarDialogoRaio()
        }
    }

    private fun mostrarDialogoRaio() {
        val raios = arrayOf("1 km", "2 km", "3 km", "4 km", "5 km")
        val valores = doubleArrayOf(1.0, 2.0, 3.0, 4.0, 5.0)

        val currentIndex = valores.indexOfFirst { it == viewModel.currentRadiusKm }.coerceAtLeast(4)

        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Escolher raio de distância")
            .setSingleChoiceItems(raios, currentIndex) { dialog, which ->
                val selectedRadius = valores[which]
                viewModel.setRadius(selectedRadius)
                Toast.makeText(requireContext(), "Raio alterado para ${raios[which]}", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun observeViewModel() {

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            if (isLoading == false && isAdded && _binding != null) {
                hideLoadingState()
                showContentGradually()
            }
        }

        viewModel.navigateToVagas.observe(viewLifecycleOwner, Observer { event ->
            event.getContentIfNotHandled()?.let { estacionamentoId ->
                val estacionamentoNome = viewModel.parkings.value?.find { it.id == estacionamentoId }?.nome
                    ?: viewModel.favoriteParkings.value?.find { it.id == estacionamentoId }?.nome
                    ?: "Estacionamento"

                val intent = Intent(requireContext(), VagaActivity::class.java)
                intent.putExtra("estacionamentoId", estacionamentoId)
                intent.putExtra("estacionamentoNome", estacionamentoNome)
                startActivity(intent)
            }


        })

        viewModel.parkings.observe(viewLifecycleOwner, Observer { parkings ->
            simpleAdapter.submitList(parkings)
            updateLoadingStep("${parkings.size} estacionamentos carregados")
            if (viewModel.isLoading.value != true) {
                showContentGradually()
            }
        })

        viewModel.favoriteParkings.observe(viewLifecycleOwner, Observer { favorites ->
            favoriteAdapter.submitList(favorites)
            updateLoadingStep("${favorites.size} favoritos carregados")
            if (viewModel.isLoading.value != true) {
                showContentGradually()
            }
        })

        viewModel.error.observe(viewLifecycleOwner, Observer { errorMessage ->
            errorMessage?.let {
                if (isAdded) {
                    hideLoadingState()
                    showContentGradually()
                    Toast.makeText(requireContext(), it, Toast.LENGTH_LONG).show()
                }
            }
        })

        viewModel.toastMessage.observe(viewLifecycleOwner, Observer { event ->
            event.getContentIfNotHandled()?.let { message ->
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
            }
        })

    }

    private fun verificarPermissaoLocalizacao() {
        updateLoadingStep("Verificando permissões...")

        when {
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED -> {
                updateLoadingStep("Permissão concedida - Buscando localização...")
                viewModel.loadData()
            }
            else -> {
                updateLoadingStep("Solicitando permissão de localização...")
                requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        handler.removeCallbacksAndMessages(null)
        _binding = null
    }
}

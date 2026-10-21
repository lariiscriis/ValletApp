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
import br.edu.fatecpg.valletprojeto.databinding.FragmentEstacionamentosBinding
import br.edu.fatecpg.valletprojeto.viewmodel.EstacionamentosViewModel
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Locale

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
        val userId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
        if (userId == null) {
            Toast.makeText(requireContext(), "Usuário não autenticado", Toast.LENGTH_SHORT).show()
            return
        }

        Toast.makeText(requireContext(), "Buscando reserva ativa para cancelar...", Toast.LENGTH_SHORT).show()

        val db = FirebaseFirestore.getInstance()
        db.collection("reserva")
            .whereEqualTo("usuarioId", userId)
            .whereEqualTo("status", "ativa")
            .limit(1)
            .get()
            .addOnSuccessListener { snapshot ->
                val doc = snapshot.documents.firstOrNull()
                if (doc != null) {
                    val reservaId = doc.id
                    val vagaId = doc.getString("vagaId")

                    val batch = db.batch()
                    batch.update(doc.reference, "status", "cancelada")
                    if (!vagaId.isNullOrEmpty()) {
                        val vagaRef = db.collection("vaga").document(vagaId)
                        batch.update(vagaRef, "disponivel", true)
                    }

                    batch.commit().addOnSuccessListener {
                        Toast.makeText(requireContext(), "Reserva cancelada com sucesso!", Toast.LENGTH_LONG).show()
                        try {
                            androidx.work.WorkManager.getInstance(requireContext()).cancelAllWorkByTag("reserva_$reservaId")
                        } catch (_: Exception) {}
                    }.addOnFailureListener { e ->
                        Toast.makeText(requireContext(), "Erro ao cancelar reserva: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(requireContext(), "Nenhuma reserva ativa encontrada para cancelar", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Erro ao buscar reserva: ${e.message}", Toast.LENGTH_SHORT).show()
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
            }
        )
        binding.rvOtherParkings.layoutManager = LinearLayoutManager(requireContext())
        binding.rvOtherParkings.adapter = simpleAdapter

        favoriteAdapter = FavoriteParkingAdapter(
            onFavoriteClicked = { estacionamento ->
                viewModel.toggleFavoriteStatus(estacionamento)
            },
            onItemClicked = { estacionamento ->
                viewModel.onEstacionamentoClicked(estacionamento)
            }
        )
        binding.rvFavorites.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.rvFavorites.adapter = favoriteAdapter
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

            if (parkings.isEmpty() && viewModel.isLoading.value != true) {
                binding.loadingOtherParkings.visibility = View.GONE
                binding.txvOtherParkingsTitle.visibility = View.VISIBLE
                binding.rvOtherParkings.visibility = View.VISIBLE
            }
        })

        viewModel.favoriteParkings.observe(viewLifecycleOwner, Observer { favorites ->
            favoriteAdapter.submitList(favorites)
            updateLoadingStep("${favorites.size} favoritos carregados")

            if (favorites.isEmpty() && viewModel.isLoading.value != true) {
                binding.loadingFavorites.visibility = View.GONE
                binding.txvFavoritesTitle.visibility = View.GONE
                binding.rvFavorites.visibility = View.GONE
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

        viewModel.navigateToVagas.observe(viewLifecycleOwner, Observer { event ->
            event.getContentIfNotHandled()?.let { estacionamentoId ->
                val intent = Intent(requireContext(), VagaActivity::class.java)
                intent.putExtra("estacionamentoId", estacionamentoId)
                startActivity(intent)
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

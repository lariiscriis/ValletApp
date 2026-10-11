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
import br.edu.fatecpg.valletprojeto.adapter.SimpleParkingAdapter
import br.edu.fatecpg.valletprojeto.databinding.FragmentSpotsBinding
import br.edu.fatecpg.valletprojeto.viewmodel.SpotsViewModel

class SpotsFragment : Fragment() {

    private var _binding: FragmentSpotsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SpotsViewModel by viewModels()

    private lateinit var simpleAdapter: SimpleParkingAdapter
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
                binding.svSearch.setQuery(spokenText, true)
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSpotsBinding.inflate(inflater, container, false)
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
        handler.postDelayed({
            if (isAdded) {
                binding.loadingFavorites.visibility = View.GONE
                binding.txvFavoritesTitle.visibility = View.VISIBLE
                binding.rvFavorites.visibility = View.VISIBLE
            }
        }, 300)

        handler.postDelayed({
            if (isAdded) {
                binding.loadingOtherParkings.visibility = View.GONE
                binding.txvOtherParkingsTitle.visibility = View.VISIBLE
                binding.rvOtherParkings.visibility = View.VISIBLE
            }
        }, 600)
    }

    private fun updateLoadingStep(step: String) {
        currentLoadingStep = step
        if (isAdded) {
            binding.txvLoadingStep.text = step
        }
    }

    private fun setupRecyclerViews() {
        simpleAdapter = SimpleParkingAdapter(
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
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, java.util.Locale.getDefault())
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
        viewModel.isLoading.observe(viewLifecycleOwner, Observer { isLoading ->
            if (isLoading) {
            } else {
                handler.postDelayed({
                    if (isAdded) {
                        hideLoadingState()
                        showContentGradually()
                    }
                }, 500)
            }
        })

        viewModel.parkings.observe(viewLifecycleOwner, Observer { parkings ->
            simpleAdapter.submitList(parkings)
            updateLoadingStep("${parkings.size} estacionamentos carregados")

            if (parkings.isEmpty() && !viewModel.isLoading.value!!) {
                binding.loadingOtherParkings.visibility = View.GONE
                binding.txvOtherParkingsTitle.visibility = View.VISIBLE
                binding.rvOtherParkings.visibility = View.VISIBLE
            }
        })

        viewModel.favoriteParkings.observe(viewLifecycleOwner, Observer { favorites ->
            favoriteAdapter.submitList(favorites)
            updateLoadingStep("${favorites.size} favoritos carregados")

            if (favorites.isEmpty() && !viewModel.isLoading.value!!) {
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

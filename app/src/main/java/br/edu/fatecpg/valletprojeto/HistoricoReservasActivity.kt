package br.edu.fatecpg.valletprojeto

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import br.edu.fatecpg.valletprojeto.adapter.ReservaAdapter
import br.edu.fatecpg.valletprojeto.databinding.ActivityHistoricoReservasBinding
import br.edu.fatecpg.valletprojeto.ui.prepararVoltar
import br.edu.fatecpg.valletprojeto.viewmodel.HistoricoReservasViewModel

class HistoricoReservasActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHistoricoReservasBinding
    private lateinit var viewModel: HistoricoReservasViewModel
    private lateinit var reservaAdapter: ReservaAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityHistoricoReservasBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.btnVoltar.prepararVoltar()
        br.edu.fatecpg.valletprojeto.ui.FormularioInsets.instalar(binding.root)

        binding.btnVoltar.setOnClickListener {
            finish()
        }

        viewModel = ViewModelProvider(this)[HistoricoReservasViewModel::class.java]

        setupRecyclerView()
        setupObservers()

        viewModel.carregarHistoricoCompleto()
    }

    private fun setupRecyclerView() {
        reservaAdapter = ReservaAdapter()
        binding.rvHistoricoReservas.apply {
            layoutManager = LinearLayoutManager(this@HistoricoReservasActivity)
            adapter = reservaAdapter
        }
    }

    private fun setupObservers() {
        viewModel.uiState.observe(this) { state ->
            binding.progressBar.visibility = if (state.isLoading) View.VISIBLE else View.GONE
            binding.rvHistoricoReservas.visibility = if (state.isLoading || state.errorMessage != null) View.GONE else View.VISIBLE
            binding.tvMensagemVazia.visibility = if (!state.isLoading && state.reservas.isEmpty()) View.VISIBLE else View.GONE

            if (state.errorMessage != null) {
                Toast.makeText(this, state.errorMessage, Toast.LENGTH_LONG).show()
            }

            reservaAdapter.submitList(state.reservas)
        }
    }
}

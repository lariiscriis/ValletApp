package br.edu.fatecpg.valletprojeto

import android.content.Intent
import android.graphics.Color
import android.graphics.Rect
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import br.edu.fatecpg.valletprojeto.adapter.VagasAdapter
import br.edu.fatecpg.valletprojeto.databinding.ActivityVagaBinding
import br.edu.fatecpg.valletprojeto.model.Vaga
import br.edu.fatecpg.valletprojeto.viewmodel.ReservaUIState
import br.edu.fatecpg.valletprojeto.viewmodel.ReservaViewModel
import br.edu.fatecpg.valletprojeto.viewmodel.VagaViewModel
import com.google.android.material.chip.Chip

class VagaActivity : AppCompatActivity() {

    private lateinit var viewModel: VagaViewModel
    private lateinit var reservaViewModel: ReservaViewModel
    private lateinit var binding: ActivityVagaBinding
    private lateinit var vagasAdapter: VagasAdapter
    private var estacionamentoId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVagaBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this)[VagaViewModel::class.java]
        reservaViewModel = ViewModelProvider(this)[ReservaViewModel::class.java]

        estacionamentoId = intent.getStringExtra("estacionamentoId")
        val estacionamentoNome = intent.getStringExtra("estacionamentoNome")

        if (estacionamentoId == null) {
            Toast.makeText(this, "Erro: ID do estacionamento não fornecido.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        binding.tvParkingName.text = estacionamentoNome ?: "Estacionamento"

        setupUI()
    }

    private fun setupReservaObserver() {
        reservaViewModel.temReservaAtiva.observe(this) { temReserva ->
            if (temReserva) {
                binding.recyclerView.visibility = View.GONE
                binding.chipGroupTipoVaga.visibility = View.GONE

                mostrarTelaBloqueio()
            } else {
                binding.recyclerView.visibility = View.VISIBLE
                binding.chipGroupTipoVaga.visibility = View.VISIBLE
                esconderTelaBloqueio()

                viewModel.fetchVagasComFiltro(estacionamentoId!!)
            }
        }
    }

    private fun mostrarTelaBloqueio() {
        val existingBloqueio = findViewById<View>(R.id.layoutBloqueio)
        if (existingBloqueio != null) {
            binding.root.removeView(existingBloqueio)
        }

        val layoutBloqueio = View.inflate(this, R.layout.layout_bloqueio_reserva, null)
        layoutBloqueio.id = R.id.layoutBloqueio
        binding.root.addView(layoutBloqueio)

        val btnVerReserva = layoutBloqueio.findViewById<Button>(R.id.btnVerReserva)
        val textBloqueio = layoutBloqueio.findViewById<TextView>(R.id.textBloqueio)

        reservaViewModel.uiState.observe(this) { uiState ->
            when (uiState) {
                is ReservaUIState.Active -> {
                    val mensagem = "Você já tem uma reserva ativa na vaga ${uiState.vaga.numero}.\n\nFinalize sua reserva atual antes de reservar outra vaga."
                    textBloqueio.text = mensagem

                    btnVerReserva.setOnClickListener {
                        val intent = Intent(this, ReservaActivity::class.java).apply {
                            putExtra("vagaId", uiState.reserva.vagaId)
                            putExtra("estacionamentoId", uiState.reserva.estacionamentoId)
                        }
                        startActivity(intent)
                    }
                }
                else -> {
                    textBloqueio.text = "Você já tem uma reserva ativa em andamento.\n\nFinalize sua reserva atual antes de reservar outra vaga."

                    btnVerReserva.setOnClickListener {
                        reservaViewModel.verificarReservaAtiva()
                        Toast.makeText(this, "Buscando reserva ativa...", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun esconderTelaBloqueio() {
        val layoutBloqueio = findViewById<View>(R.id.layoutBloqueio)
        layoutBloqueio?.let {
            binding.root.removeView(it)
        }
    }

    private fun setupUI() {
        binding.btnBack.setOnClickListener { finish() }

        setupRecyclerView()
        setupObservers()
        setupFilterListeners()
        setupReservaObserver()
        viewModel.fetchVagasComFiltro(estacionamentoId!!)

        reservaViewModel.verificarReservaAtiva()

        binding.btnReservar.isEnabled = false
        binding.btnReservar.alpha = 0.5f

        atualizarCoresDosFiltros(binding.chipGroupTipoVaga.checkedChipId)
    }

    private fun setupRecyclerView() {
        vagasAdapter = VagasAdapter(
            onVagaSelecionada = { vaga ->
                if (reservaViewModel.temReservaAtiva.value == true) {
                    Toast.makeText(this, "Você já tem uma reserva ativa. Finalize-a antes de reservar outra vaga.", Toast.LENGTH_LONG).show()
                    return@VagasAdapter
                }
                atualizarPainelInferior(vaga)
            }
        )
        binding.recyclerView.layoutManager = GridLayoutManager(this, 2)
        binding.recyclerView.adapter = vagasAdapter

        binding.recyclerView.addItemDecoration(
            GridSpacingItemDecoration(2, 48)
        )
    }

    private fun atualizarPainelInferior(vaga: Vaga) {
        binding.tvSelectedSpotName.text = "Vaga ${vaga.numero}"
        val tipoVaga = if (vaga.preferencial) "Preferencial" else vaga.tipo.replaceFirstChar { it.uppercase() }
        binding.tvSelectedSpotDetails.text = "$tipoVaga · Livre"
        binding.tvPrecoReserva.text = "R$ %.2f".format(vaga.preco)

        binding.btnReservar.isEnabled = true
        binding.btnReservar.alpha = 1.0f

        binding.btnReservar.setOnClickListener {
            val intent = Intent(this, ReservaActivity::class.java).apply {
                putExtra("vagaId", vaga.id)
                putExtra("estacionamentoId", estacionamentoId)
                putExtra("numero", vaga.numero)
                putExtra("preco", vaga.preco)
                putExtra("tipo", vaga.tipo)
            }
            startActivity(intent)
        }
    }

    private fun setupObservers() {
        viewModel.vagas.observe(this) { vagas ->
            if (reservaViewModel.temReservaAtiva.value != true) {
                vagasAdapter.submitList(vagas)
            }
        }

        viewModel.errorMessage.observe(this) { message ->
            message?.let { Toast.makeText(this, it, Toast.LENGTH_SHORT).show() }
        }

        viewModel.veiculoPadraoTipo.observe(this) { tipo ->
            if (binding.chipGroupTipoVaga.checkedChipId == View.NO_ID) {
                when (tipo?.lowercase()) {
                    "carro" -> binding.chipCarro.isChecked = true
                    "moto" -> binding.chipMoto.isChecked = true
                    else -> binding.chipTodos.isChecked = true
                }
                atualizarCoresDosFiltros(binding.chipGroupTipoVaga.checkedChipId)
            }
            atualizarIconeEntrada(tipo ?: "carro")
        }
    }

    private fun setupFilterListeners() {
        binding.chipGroupTipoVaga.setOnCheckedChangeListener { group, checkedId ->
            if (checkedId == View.NO_ID) return@setOnCheckedChangeListener

            if (reservaViewModel.temReservaAtiva.value == true) return@setOnCheckedChangeListener

            atualizarCoresDosFiltros(checkedId)

            val chip = group.findViewById<Chip>(checkedId)
            val filtroTexto = chip.text.toString()

            atualizarIconeEntrada(filtroTexto)

            viewModel.fetchVagasComFiltro(estacionamentoId!!, tipoFiltro = filtroTexto)
        }
    }

    private fun atualizarIconeEntrada(tipoSelecionado: String) {
        val isMoto = tipoSelecionado.lowercase() == "moto" ||
                (tipoSelecionado.lowercase() !in listOf("carro", "moto") && viewModel.veiculoPadraoTipo.value?.lowercase() == "moto")

        if (isMoto) {
            binding.ivCarroEntrada.setImageResource(R.drawable.moto_vagaocupada)
        } else {
            binding.ivCarroEntrada.setImageResource(R.drawable.carro_vagaocupada)
        }
    }

    private fun atualizarCoresDosFiltros(checkedId: Int) {
        val chips = listOf(binding.chipTodos, binding.chipCarro, binding.chipMoto, binding.chipPreferencial)

        for (chip in chips) {
            if (chip.id == checkedId) {
                chip.setChipBackgroundColorResource(R.color.vallet_green_dark)
                chip.setTextColor(Color.WHITE)
            } else {
                chip.chipBackgroundColor = android.content.res.ColorStateList.valueOf(Color.WHITE)
                chip.setTextColor(Color.parseColor("#3B5942"))
            }
        }
    }

    override fun onResume() {
        super.onResume()
        reservaViewModel.verificarReservaAtiva()
    }
}

class GridSpacingItemDecoration(
    private val spanCount: Int,
    private val spacingDp: Int
) : RecyclerView.ItemDecoration() {

    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State
    ) {
        val position = parent.getChildAdapterPosition(view)
        if (position == RecyclerView.NO_POSITION) return

        val density = view.resources.displayMetrics.density
        val spacing = (spacingDp * density).toInt()

        val column = position % spanCount

        if (column == 0) {
            outRect.left = (8 * density).toInt()
            outRect.right = spacing / 2
        } else {
            outRect.left = spacing / 2
            outRect.right = (8 * density).toInt()
        }
    }
}
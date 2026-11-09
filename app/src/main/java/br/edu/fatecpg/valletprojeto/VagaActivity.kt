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
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import br.edu.fatecpg.valletprojeto.adapter.VagasAdapter
import br.edu.fatecpg.valletprojeto.databinding.ActivityVagaBinding
import br.edu.fatecpg.valletprojeto.model.Vaga
import br.edu.fatecpg.valletprojeto.viewmodel.ReservaUIState
import br.edu.fatecpg.valletprojeto.viewmodel.ReservaViewModel
import br.edu.fatecpg.valletprojeto.viewmodel.VagaViewModel

class VagaActivity : AppCompatActivity() {

    private lateinit var viewModel: VagaViewModel
    private lateinit var reservaViewModel: ReservaViewModel
    private lateinit var binding: ActivityVagaBinding
    private lateinit var vagasAdapter: VagasAdapter

    private var estacionamentoId: String? = null
    private var layoutBloqueio: View? = null
    private var reservaAtivaAtual: ReservaUIState.Active? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityVagaBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this)[VagaViewModel::class.java]
        reservaViewModel =
            ViewModelProvider(this)[ReservaViewModel::class.java]

        estacionamentoId = intent.getStringExtra("estacionamentoId")
        val estacionamentoNome =
            intent.getStringExtra("estacionamentoNome")

        if (estacionamentoId == null) {
            Toast.makeText(
                this,
                "Erro: ID do estacionamento não fornecido.",
                Toast.LENGTH_LONG
            ).show()

            finish()
            return
        }

        binding.tvParkingName.text =
            estacionamentoNome ?: "Estacionamento"

        setupUI()
    }

    private fun setupUI() {
        binding.btnBack.setOnClickListener {
            finish()
        }

        setupRecyclerView()
        setupObservers()
        setupFilterListeners()
        setupReservaObserver()

        viewModel.fetchVagasComFiltro(estacionamentoId!!, tipoFiltro = filtroSelecionado())

        binding.btnReservar.isEnabled = false
        binding.btnReservar.alpha = 0.5f

        atualizarCoresDosFiltros(
            binding.chipGroupTipoVaga.checkedChipId
        )
    }

    private fun setupReservaObserver() {
        // Registrado uma única vez durante a criação da tela.
        reservaViewModel.uiState.observe(this) { uiState ->
            reservaAtivaAtual = uiState as? ReservaUIState.Active
            atualizarTelaBloqueio()
        }

        reservaViewModel.temReservaAtiva.observe(this) { temReserva ->
            if (temReserva) {
                // Oculta o cabeçalho.
                binding.btnBack.visibility = View.GONE
                binding.tvParkingName.visibility = View.GONE
                binding.tvSubtitle.visibility = View.GONE

                // Oculta toda a seleção de vagas.
                binding.recyclerView.visibility = View.GONE
                binding.chipGroupTipoVaga.visibility = View.GONE
                binding.scrollChips.visibility = View.GONE
                binding.plantContainer.visibility = View.GONE
                binding.bottomPanel.visibility = View.GONE

                binding.btnReservar.isEnabled = false
                binding.btnReservar.setOnClickListener(null)

                mostrarTelaBloqueio()
            } else {
                esconderTelaBloqueio()

                // Restaura o cabeçalho.
                binding.btnBack.visibility = View.VISIBLE
                binding.tvParkingName.visibility = View.VISIBLE
                binding.tvSubtitle.visibility = View.VISIBLE

                // Restaura a seleção de vagas.
                binding.recyclerView.visibility = View.VISIBLE
                binding.chipGroupTipoVaga.visibility = View.VISIBLE
                binding.scrollChips.visibility = View.VISIBLE
                binding.plantContainer.visibility = View.VISIBLE
                binding.bottomPanel.visibility = View.VISIBLE

                binding.tvSubtitle.text =
                    "Escolha sua vaga e garanta seu espaço."

                binding.tvSelectedSpotName.text = "Selecione uma vaga"
                binding.tvSelectedSpotDetails.text = "Planta interativa"
                binding.tvPrecoReserva.text = "R$ --"

                binding.btnReservar.isEnabled = false
                binding.btnReservar.alpha = 0.5f
                binding.btnReservar.setOnClickListener(null)

                viewModel.fetchVagasComFiltro(estacionamentoId!!)
            }
        }
    }

    private fun mostrarTelaBloqueio() {
        if (layoutBloqueio == null) {
            val novoLayout = layoutInflater.inflate(
                R.layout.layout_bloqueio_reserva,
                binding.root,
                false
            )

            // O aviso ocupa toda a área disponível da tela.
            val parametros = ConstraintLayout.LayoutParams(0, 0).apply {
                topToTop = ConstraintLayout.LayoutParams.PARENT_ID
                bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
                startToStart = ConstraintLayout.LayoutParams.PARENT_ID
                endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
            }

            binding.root.addView(novoLayout, parametros)
            layoutBloqueio = novoLayout
        }

        atualizarTelaBloqueio()
    }

    private fun atualizarTelaBloqueio() {
        val tela = layoutBloqueio ?: return

        val botao = tela.findViewById<Button>(R.id.btnVerReserva)
        val mensagem = tela.findViewById<TextView>(R.id.textBloqueio)
        val ativa = reservaAtivaAtual

        if (ativa != null) {
            val numero = ativa.vaga.numero.ifBlank {
                "selecionada"
            }

            val estacionamento = ativa.reserva.estacionamentoNome

            mensagem.text = if (estacionamento.isBlank()) {
                "Sua reserva atual está na vaga $numero."
            } else {
                "Vaga $numero • $estacionamento"
            }

            botao.text = "Ver minha reserva"

            botao.setOnClickListener {
                val atual = reservaAtivaAtual
                    ?: return@setOnClickListener

                if (reservaViewModel.temReservaAtiva.value != true) {
                    return@setOnClickListener
                }

                val intent = Intent(
                    this,
                    ReservaActivity::class.java
                ).apply {
                    putExtra("vagaId", atual.reserva.vagaId)
                    putExtra(
                        "estacionamentoId",
                        atual.reserva.estacionamentoId
                    )
                }

                startActivity(intent)
                finish()
            }
        } else {
            mensagem.text = "Você tem uma reserva em andamento."
            botao.text = "Buscar minha reserva"

            botao.setOnClickListener {
                reservaViewModel.verificarReservaAtiva()

                Toast.makeText(
                    this,
                    "Buscando reserva ativa...",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun esconderTelaBloqueio() {
        layoutBloqueio?.let {
            binding.root.removeView(it)
        }

        layoutBloqueio = null
    }

    private fun setupRecyclerView() {
        vagasAdapter = VagasAdapter(
            onVagaSelecionada = { vaga ->
                if (reservaViewModel.temReservaAtiva.value == true) {
                    Toast.makeText(
                        this,
                        "Você já tem uma reserva ativa. " +
                                "Finalize-a antes de reservar outra vaga.",
                        Toast.LENGTH_LONG
                    ).show()

                    return@VagasAdapter
                }

                atualizarPainelInferior(vaga)
            }
        )

        binding.recyclerView.layoutManager =
            GridLayoutManager(this, 2)

        binding.recyclerView.adapter = vagasAdapter

        binding.recyclerView.addItemDecoration(
            GridSpacingItemDecoration(2, 48)
        )
    }

    private fun atualizarPainelInferior(vaga: Vaga) {
        binding.tvSelectedSpotName.text = "Vaga ${vaga.numero}"

        val tipoVaga = if (vaga.preferencial) {
            "Preferencial"
        } else {
            vaga.tipo.replaceFirstChar {
                it.uppercase()
            }
        }

        binding.tvSelectedSpotDetails.text = "$tipoVaga · Livre"
        binding.tvPrecoReserva.text = "R$ %.2f".format(vaga.preco)

        binding.btnReservar.isEnabled = true
        binding.btnReservar.alpha = 1.0f

        binding.btnReservar.setOnClickListener {
            if (reservaViewModel.temReservaAtiva.value == true) {
                Toast.makeText(
                    this,
                    "Você já tem uma reserva ativa. " +
                            "Acesse sua reserva para continuar.",
                    Toast.LENGTH_LONG
                ).show()

                return@setOnClickListener
            }

            val intent = Intent(
                this,
                ReservaActivity::class.java
            ).apply {
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
            message?.let {
                Toast.makeText(
                    this,
                    it,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        viewModel.veiculoPadraoTipo.observe(this) {
            atualizarIconeEntrada(filtroSelecionado())
        }
    }

    private fun setupFilterListeners() {
        binding.chipGroupTipoVaga.setOnCheckedStateChangeListener {
                _, checkedIds ->

            val checkedId = checkedIds.firstOrNull()
                ?: return@setOnCheckedStateChangeListener

            if (reservaViewModel.temReservaAtiva.value == true) {
                return@setOnCheckedStateChangeListener
            }

            atualizarCoresDosFiltros(checkedId)

            val filtroTexto = filtroSelecionado(checkedId)

            atualizarIconeEntrada(filtroTexto)

            viewModel.fetchVagasComFiltro(
                estacionamentoId!!,
                tipoFiltro = filtroTexto
            )
        }
    }

    private fun filtroSelecionado(
        checkedId: Int = binding.chipGroupTipoVaga.checkedChipId
    ): String = when (checkedId) {
        R.id.chip_carro -> "carro"
        R.id.chip_moto -> "moto"
        R.id.chip_preferencial -> "preferencial"
        else -> "todos"
    }

    private fun atualizarIconeEntrada(tipoSelecionado: String) {
        val tipo = tipoSelecionado.trim().lowercase()

        val isMoto = tipo == "moto" ||
            (tipo !in listOf("carro", "moto") && viewModel.veiculoPadraoTipo.value?.trim()?.lowercase() == "moto")

        if (isMoto) {
            binding.ivCarroEntrada.setImageResource(
                R.drawable.moto_vagaocupada
            )
        } else {
            binding.ivCarroEntrada.setImageResource(
                R.drawable.carro_vagaocupada
            )
        }
    }

    private fun atualizarCoresDosFiltros(checkedId: Int) {
        val chips = listOf(
            binding.chipTodos,
            binding.chipCarro,
            binding.chipMoto,
            binding.chipPreferencial
        )

        for (chip in chips) {
            if (chip.id == checkedId) {
                chip.setChipBackgroundColorResource(
                    R.color.vallet_green_dark
                )

                chip.setTextColor(Color.WHITE)
            } else {
                chip.chipBackgroundColor =
                    android.content.res.ColorStateList.valueOf(
                        Color.WHITE
                    )

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

        if (position == RecyclerView.NO_POSITION) {
            return
        }

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
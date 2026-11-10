package br.edu.fatecpg.valletprojeto

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import br.edu.fatecpg.valletprojeto.databinding.ActivityReservaBinding
import br.edu.fatecpg.valletprojeto.viewmodel.ReservaUIState
import br.edu.fatecpg.valletprojeto.viewmodel.ReservaViewModel
import com.bumptech.glide.Glide
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*
import br.edu.fatecpg.valletprojeto.model.Vaga
import androidx.core.net.toUri

class ReservaActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReservaBinding
    private lateinit var viewModel: ReservaViewModel
    private val db = FirebaseFirestore.getInstance()
    private var vagaId: String? = null
    private var estacionamentoId: String? = null

    private var vagaAtivaParaPagamento: Vaga? = null

    private var estacionamentoLat: Double? = null
    private var estacionamentoLon: Double? = null
    private var estacionamentoNome: String? = null

    // Capturando o valor real da hora do BD
    private var estacionamentoValorHora: Double = 10.0

    private val pagamentoLauncher =
        registerForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == RESULT_OK) {
                vagaAtivaParaPagamento?.let { vaga ->

                    // O ViewModel finaliza e libera a coleção "vaga" no mesmo lote.
                    viewModel.finalizarReservaAposPagamento(vaga) {
                        val intent = Intent(this, DashboardActivity::class.java)
                        startActivity(intent)
                        finish()
                    }
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReservaBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this)[ReservaViewModel::class.java]

        vagaId = intent.getStringExtra("vagaId")
        estacionamentoId = intent.getStringExtra("estacionamentoId")

        if (vagaId == null || estacionamentoId == null) {
            Toast.makeText(this, "Dados da vaga incompletos.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        buscarDadosEstacionamento(estacionamentoId!!)

        binding.btnVerificarRota.setOnClickListener {
            if (estacionamentoLat != null && estacionamentoLon != null && estacionamentoNome != null) {
                abrirRotaParaEstacionamento(
                    this,
                    estacionamentoLat!!,
                    estacionamentoLon!!,
                    estacionamentoNome!!
                )
            } else {
                Toast.makeText(this, "Carregando dados, tente novamente.", Toast.LENGTH_SHORT).show()
            }
        }

        setupGifAnimation()
        setupListeners()
        setupObservers()

        viewModel.carregarDadosIniciais(vagaId!!)
    }

    private fun buscarDadosEstacionamento(id: String) {
        db.collection("estacionamento").document(id).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    estacionamentoLat = document.getDouble("latitude")
                    estacionamentoLon = document.getDouble("longitude")
                    estacionamentoNome = document.getString("nome")

                    val valorBd = document.getDouble("valorHora") ?: document.getDouble("valor_hora")
                    if (valorBd != null) {
                        estacionamentoValorHora = valorBd
                    }

                    Log.d("ReservaActivity", "Estacionamento: $estacionamentoNome | Valor: R$ $estacionamentoValorHora")
                } else {
                    Toast.makeText(this, "Estacionamento não encontrado.", Toast.LENGTH_LONG).show()
                }
            }
            .addOnFailureListener { e ->
                Log.e("ReservaActivity", "Erro ao buscar dados do estacionamento", e)
            }
    }

    @SuppressLint("QueryPermissionsNeeded")
    fun abrirRotaParaEstacionamento(
        context: Context,
        destinoLatitude: Double,
        destinoLongitude: Double,
        nomeDestino: String
    ) {
        val gmmIntentUri = "google.navigation:q=$destinoLatitude,$destinoLongitude".toUri()
        val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
        mapIntent.setPackage("com.google.android.apps.maps")
        if (mapIntent.resolveActivity(context.packageManager) != null) {
            context.startActivity(mapIntent)
        } else {
            val webIntent = Intent(
                Intent.ACTION_VIEW,
                "https://www.google.com/maps/dir/?api=1&destination=$destinoLatitude,$destinoLongitude&destination_place_id=$nomeDestino".toUri()
            )
            if (webIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(webIntent)
            } else {
                Toast.makeText(context, "Nenhum aplicativo de mapas encontrado.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun carregarImagemVeiculo(tipo: String) {
        val imagemResId = if (tipo.equals("Moto", ignoreCase = true) || tipo.equals("Motocicleta", ignoreCase = true)) {
            R.drawable.moto_vagaocupada
        } else {
            R.drawable.carro_vagaocupada
        }

        Glide.with(this)
            .load(imagemResId)
            .into(binding.gifCarro)
    }

    private fun setupGifAnimation() {
        val pulseAnimation = android.view.animation.AnimationUtils.loadAnimation(this, R.anim.pulse_animation)
        binding.circuloFundoAnimado.startAnimation(pulseAnimation)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        atualizarDuracaoSelecionada()
    }

    private fun atualizarDuracaoSelecionada() {
        val horas = binding.sliderTempo.value.toInt()
        binding.tvTempoSelecionado.text =
            resources.getQuantityString(R.plurals.reserva_duracao_horas, horas, horas)
    }

    private fun setupListeners() {
        binding.btnVoltar.setOnClickListener {
            finish()
        }

        atualizarDuracaoSelecionada()
        binding.sliderTempo.addOnChangeListener { _, _, _ ->
            atualizarDuracaoSelecionada()
        }

        binding.btnReservar.setOnClickListener {
            val horasSelecionadas = binding.sliderTempo.value.toInt()
            if (estacionamentoNome != null) {
                viewModel.criarReserva(vagaId!!, estacionamentoId!!,
                    estacionamentoNome!!, horasSelecionadas)
            } else {
                Toast.makeText(this, "Nome não carregado. Tente novamente.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun setupObservers() {
        viewModel.uiState.observe(this) { state ->
            binding.progressBar.visibility = View.GONE
            binding.layoutConfigReserva.visibility = View.GONE
            binding.layoutTimerReserva.visibility = View.GONE
            binding.btnReservar.visibility = View.GONE
            binding.btnRenovar.visibility = View.GONE
            binding.btnFinalizar.visibility = View.GONE
            binding.btnCancelar.visibility = View.GONE

            when (state) {
                is ReservaUIState.Initial, is ReservaUIState.Loading -> {
                    binding.progressBar.visibility = View.VISIBLE
                }

                is ReservaUIState.Idle -> {
                    binding.layoutConfigReserva.visibility = View.VISIBLE
                    binding.btnReservar.visibility = View.VISIBLE

                    binding.tvVagaNumero.text = state.vaga.numero
                    binding.tvVagaDetalhes.text = "${state.vaga.tipo}\n${state.vaga.localizacao}"
                    binding.tvVeiculoInfo.text = "${state.veiculo.modelo} (${state.veiculo.placa})"

                    carregarImagemVeiculo(state.vaga.tipo)
                }

                is ReservaUIState.Active -> {
                    vagaAtivaParaPagamento = state.vaga

                    binding.layoutTimerReserva.visibility = View.VISIBLE
                    binding.btnRenovar.visibility = View.VISIBLE
                    binding.btnFinalizar.visibility = View.VISIBLE
                    binding.btnCancelar.visibility = View.VISIBLE

                    binding.tvVagaNumero.text = state.vaga.numero
                    binding.tvVagaDetalhes.text = "${state.vaga.tipo}\n${state.vaga.localizacao}"
                    binding.tvVeiculoInfo.text = "${state.veiculo.modelo} (${state.veiculo.placa})"

                    carregarImagemVeiculo(state.vaga.tipo)

                    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
                    val inicioStr = sdf.format(state.reserva.inicioReserva!!.toDate())
                    val fimStr = sdf.format(state.reserva.fimReserva!!.toDate())
                    binding.tvHorarioReserva.text = "$inicioStr às $fimStr"

                    binding.btnRenovar.setOnClickListener { viewModel.renovarReserva(state.reserva) }
                    binding.btnCancelar.setOnClickListener { viewModel.cancelarReserva(state.vaga) }

                    binding.btnFinalizar.setOnClickListener {
                        val intent = Intent(this, PagamentoActivity::class.java).apply {
                            putExtra("vagaId", state.vaga.id)
                            putExtra("vagaNumero", state.vaga.numero)
                            putExtra("vagaTipo", state.vaga.tipo)
                            putExtra("vagaLocalizacao", state.vaga.localizacao)
                            putExtra("estacionamentoNome", estacionamentoNome ?: "Vallet")
                            putExtra("veiculoModelo", state.veiculo.modelo)
                            putExtra("veiculoPlaca", state.veiculo.placa)
                            putExtra("horarioInicioTexto", inicioStr)
                            putExtra("horarioFimTexto", fimStr)
                            putExtra("horaInicioMillis", state.reserva.inicioReserva.toDate().time)

                            // ENVIANDO O PREÇO PARA A TELA DE PAGAMENTO!
                            putExtra("valorHora", estacionamentoValorHora)

                            putExtra("reservaId", state.reserva.id)
                            putExtra("usuarioId", com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid)
                        }

                        pagamentoLauncher.launch(intent)
                    }
                }

                is ReservaUIState.Finished -> {
                    Toast.makeText(this, state.message, Toast.LENGTH_LONG).show()
                    finish()
                }

                is ReservaUIState.Error -> {
                    Toast.makeText(this, state.message, Toast.LENGTH_LONG).show()
                    binding.progressBar.visibility = View.GONE
                }
            }
        }

        viewModel.tempoRestante.observe(this) { tempo ->
            binding.tvTimer.text = tempo
        }
    }
}
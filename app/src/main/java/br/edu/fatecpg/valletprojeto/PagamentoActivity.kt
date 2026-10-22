package br.edu.fatecpg.valletprojeto

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import br.edu.fatecpg.valletprojeto.databinding.ActivityPagamentoBinding
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlin.math.ceil
import kotlin.math.max
import android.graphics.BitmapFactory
import android.util.Base64
import android.widget.LinearLayout
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import br.edu.fatecpg.valletprojeto.service.RequisicaoPagamento
import br.edu.fatecpg.valletprojeto.service.RespostaPix
import br.edu.fatecpg.valletprojeto.service.RetrofitClient

class PagamentoActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPagamentoBinding
    private enum class FormaPagamento { PIX, CREDITO, DEBITO }
    private var formaSelecionada: FormaPagamento? = null

    private var vagaNumero: String = "A-01"
    private var vagaTipo: String = "Carro"
    private var vagaLocalizacao: String = "Térreo"
    private var estacionamentoNome: String = "Vallet Center"
    private var veiculoModelo: String = "Fiat Mobi"
    private var veiculoPlaca: String = "ABC-1234"
    private var horarioInicioTexto: String = "14:00"
    private var horarioFimTexto: String = "15:00"
    private var horaInicioMillis: Long = 0L

    // Agora o valor base vem da Intent
    private var valorPorHora = 10.0
    private var horasCobradas = 1
    private var valorTotal = 10.0

    private var reservaIdExtra: String = ""
    private var usuarioIdExtra: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPagamentoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        lerExtrasDaIntent()
        calcularValorDinâmico()
        preencherResumo()
        setupSelecaoFormaPagamento()
        setupBotaoConfirmar()
        setupTelaSucesso()

        binding.btnVoltar.setOnClickListener { finish() }
    }

    private fun lerExtrasDaIntent() {
        intent.getStringExtra("vagaNumero")?.let { vagaNumero = it }
        intent.getStringExtra("vagaTipo")?.let { vagaTipo = it }
        intent.getStringExtra("vagaLocalizacao")?.let { vagaLocalizacao = it }
        intent.getStringExtra("estacionamentoNome")?.let { estacionamentoNome = it }
        intent.getStringExtra("veiculoModelo")?.let { veiculoModelo = it }
        intent.getStringExtra("veiculoPlaca")?.let { veiculoPlaca = it }
        intent.getStringExtra("horarioInicioTexto")?.let { horarioInicioTexto = it }
        intent.getStringExtra("horarioFimTexto")?.let { horarioFimTexto = it }
        horaInicioMillis = intent.getLongExtra("horaInicioMillis", System.currentTimeMillis() - 3_600_000)

        // CAPTURA DO VALOR REAL DA HORA:
        valorPorHora = intent.getDoubleExtra("valorHora", 10.0)

        intent.getStringExtra("reservaId")?.let { reservaIdExtra = it }
        intent.getStringExtra("usuarioId")?.let { usuarioIdExtra = it }
    }

    private fun calcularValorDinâmico() {
        val tempoUtilizadoMinutos = max(1L, (System.currentTimeMillis() - horaInicioMillis) / 60_000)
        horasCobradas = ceil(tempoUtilizadoMinutos / 60.0).toInt().coerceAtLeast(1)
        valorTotal = horasCobradas * valorPorHora

        val horas = tempoUtilizadoMinutos / 60
        val minutos = tempoUtilizadoMinutos % 60
        val tempoTexto = if (horas > 0) "${horas}h ${minutos}min" else "${minutos}min"
        binding.tvResumoTempoUtilizado.text = tempoTexto
    }

    private fun preencherResumo() {
        binding.tvResumoEstacionamento.text = estacionamentoNome
        binding.tvResumoVaga.text = vagaNumero
        binding.tvResumoVeiculo.text = "$veiculoModelo • $veiculoPlaca"
        binding.tvResumoHorario.text = "$horarioInicioTexto - $horarioFimTexto"
        binding.tvValorTotal.text = formatarValor(valorTotal)
    }

    private fun formatarValor(valor: Double): String = "R$ %.2f".format(valor).replace(".", ",")

    private fun setupSelecaoFormaPagamento() {
        binding.cardPix.setOnClickListener { selecionarForma(FormaPagamento.PIX) }
        binding.cardCredito.setOnClickListener { selecionarForma(FormaPagamento.CREDITO) }
        binding.cardDebito.setOnClickListener { selecionarForma(FormaPagamento.DEBITO) }
    }

    private fun selecionarForma(forma: FormaPagamento) {
        formaSelecionada = forma
        atualizarEstadoCard(binding.cardPix, binding.ivCheckPix, forma == FormaPagamento.PIX)
        atualizarEstadoCard(binding.cardCredito, binding.ivCheckCredito, forma == FormaPagamento.CREDITO)
        atualizarEstadoCard(binding.cardDebito, binding.ivCheckDebito, forma == FormaPagamento.DEBITO)

        binding.tvExplicacaoPix.visibility = if (forma == FormaPagamento.PIX) View.VISIBLE else View.GONE
        binding.layoutCamposCartao.visibility =
            if (forma == FormaPagamento.CREDITO || forma == FormaPagamento.DEBITO) View.VISIBLE else View.GONE
    }

    private fun atualizarEstadoCard(card: LinearLayout, check: android.widget.ImageView, selecionado: Boolean) {
        if (selecionado) {
            card.setBackgroundResource(R.drawable.bg_card_opcao_selecionada)
            check.visibility = View.VISIBLE
        } else {
            card.setBackgroundResource(R.drawable.bg_card_opcao_padrao)
            check.visibility = View.INVISIBLE
        }
    }

    private fun setupBotaoConfirmar() {
        binding.btnConfirmarPagamento.setOnClickListener {
            if (!validarSelecaoEDados()) return@setOnClickListener

            if (formaSelecionada == FormaPagamento.PIX) {
                solicitarPixNaAPI()
            } else {
                Toast.makeText(this, "Apenas PIX está implementado.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun validarSelecaoEDados(): Boolean {
        if (formaSelecionada == null) {
            Toast.makeText(this, "Selecione uma forma de pagamento.", Toast.LENGTH_SHORT).show()
            return false
        }

        if (formaSelecionada == FormaPagamento.CREDITO || formaSelecionada == FormaPagamento.DEBITO) {
            val numero = binding.etNumeroCartao.text?.toString().orEmpty().trim()
            val nome = binding.etNomeCartao.text?.toString().orEmpty().trim()
            val validade = binding.etValidadeCartao.text?.toString().orEmpty().trim()
            val cvv = binding.etCvvCartao.text?.toString().orEmpty().trim()

            if (numero.isEmpty() || nome.isEmpty() || validade.isEmpty() || cvv.isEmpty()) {
                Toast.makeText(this, "Preencha os dados do cartão.", Toast.LENGTH_SHORT).show()
                return false
            }
        }
        return true
    }

    private fun solicitarPixNaAPI() {
        binding.btnConfirmarPagamento.isEnabled = false
        binding.progressBarPagamento.visibility = View.VISIBLE

        val usuarioId = if (usuarioIdExtra.isNotEmpty()) usuarioIdExtra else "teste"
        val reservaId = if (reservaIdExtra.isNotEmpty()) reservaIdExtra else "teste"

        val requisicao = RequisicaoPagamento(valorTotal, "pix", usuarioId, reservaId)

        RetrofitClient.valletApiService.criarPagamentoPix(requisicao).enqueue(object : Callback<RespostaPix> {
            override fun onResponse(call: Call<RespostaPix>, response: Response<RespostaPix>) {
                binding.progressBarPagamento.visibility = View.GONE
                if (response.isSuccessful && response.body() != null) {
                    val resposta = response.body()!!
                    exibirQrCode(resposta.qr_code, resposta.qr_code_base64)
                } else {
                    Toast.makeText(this@PagamentoActivity, "Erro ao gerar PIX", Toast.LENGTH_SHORT).show()
                    binding.btnConfirmarPagamento.isEnabled = true
                }
            }
            override fun onFailure(call: Call<RespostaPix>, t: Throwable) {
                binding.progressBarPagamento.visibility = View.GONE
                Toast.makeText(this@PagamentoActivity, "Falha: ${t.message}", Toast.LENGTH_LONG).show()
                binding.btnConfirmarPagamento.isEnabled = true
            }
        })
    }

    private fun exibirQrCode(codigoCopiaCola: String, qrCodeBase64: String) {
        binding.layoutEtapa1Pagamento.visibility = View.GONE
        binding.layoutEtapa2Pix.visibility = View.VISIBLE

        binding.tvCodigoPixCopiaCola.text = codigoCopiaCola

        try {
            val imageBytes = Base64.decode(qrCodeBase64, Base64.DEFAULT)
            val decodedImage = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
            binding.ivQrCodePagamento.setImageBitmap(decodedImage)
        } catch (e: Exception) {
            Toast.makeText(this, "Erro ao decodificar QR Code", Toast.LENGTH_SHORT).show()
        }

        binding.btnVoltarPix.setOnClickListener {
            binding.layoutEtapa2Pix.visibility = View.GONE
            binding.layoutEtapa1Pagamento.visibility = View.VISIBLE
        }

        binding.btnSimularConfirmacaoWebhook.setOnClickListener {
            mostrarPagamentoAprovado()
        }

        binding.btnCopiarPix.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Código Pix", codigoCopiaCola)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "Código Pix copiado!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun mostrarPagamentoAprovado() {
        binding.layoutEtapa2Pix.visibility = View.GONE
        binding.layoutEtapa1Pagamento.visibility = View.GONE
        binding.layoutEtapa3Sucesso.visibility = View.VISIBLE

        gerarQrCode("VALLET-SAIDA-${System.currentTimeMillis()}")
        animarEntradaSucesso()
    }

    private fun animarEntradaSucesso() {
        val icone = binding.layoutSucesso.getChildAt(0)
        icone.scaleX = 0f
        icone.scaleY = 0f
        icone.animate()
            .scaleX(1f).scaleY(1f)
            .setDuration(400)
            .setInterpolator(android.view.animation.OvershootInterpolator())
            .start()
    }

    private fun gerarQrCode(conteudo: String) {
        try {
            val tamanho = 512
            val bitMatrix = QRCodeWriter().encode(conteudo, BarcodeFormat.QR_CODE, tamanho, tamanho)
            val bitmap = Bitmap.createBitmap(tamanho, tamanho, Bitmap.Config.RGB_565)
            for (x in 0 until tamanho) {
                for (y in 0 until tamanho) {
                    bitmap.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
                }
            }
            binding.ivQrCode.setImageBitmap(bitmap)
        } catch (e: Exception) {
            binding.ivQrCode.setImageResource(R.drawable.ic_payments)
        }
    }

    private fun setupTelaSucesso() {
        binding.btnSimularLeitura.setOnClickListener {
            binding.layoutSucesso.visibility = View.GONE
            binding.layoutSaidaLiberada.visibility = View.VISIBLE
        }

        binding.btnConcluir.setOnClickListener {
            setResult(RESULT_OK)
            finish()
        }
    }
}
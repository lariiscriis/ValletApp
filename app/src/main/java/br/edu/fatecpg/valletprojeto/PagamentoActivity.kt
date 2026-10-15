package br.edu.fatecpg.valletprojeto

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import br.edu.fatecpg.valletprojeto.databinding.ActivityPagamentoBinding
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlin.math.ceil
import kotlin.math.max

/**
 * Tela de pagamento (mock/simulação).
 *
 * Não existe nenhuma integração real com gateway de pagamento ou banco.
 * O objetivo aqui é apenas demonstrar o fluxo completo:
 * resumo da reserva -> escolha da forma de pagamento -> "processamento"
 * simulado -> tela de sucesso -> QR Code de saída -> simulação de leitura.
 *
 * Todos os dados de reserva chegam via Intent extras vindos da ReservaActivity.
 * Caso algum extra não tenha sido enviado, um valor mock é usado no lugar,
 * para que a tela sempre funcione mesmo em testes isolados desta Activity.
 */
class PagamentoActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPagamentoBinding

    // Forma de pagamento selecionada. Nula = nenhuma selecionada ainda.
    private enum class FormaPagamento { PIX, CREDITO, DEBITO }
    private var formaSelecionada: FormaPagamento? = null

    // ---------- Dados recebidos da ReservaActivity (com fallback mock) ----------
    private var vagaNumero: String = "A-01"
    private var vagaTipo: String = "Carro"
    private var vagaLocalizacao: String = "Térreo"
    private var estacionamentoNome: String = "Vallet Center"
    private var veiculoModelo: String = "Fiat Mobi"
    private var veiculoPlaca: String = "ABC-1234"
    private var horarioInicioTexto: String = "14:00"
    private var horarioFimTexto: String = "15:00"
    private var horaInicioMillis: Long = 0L

    // Regra de cobrança MOCK: valor fixo por hora.
    // TODO: substituir pela regra real de precificação do Vallet quando existir.
    private val valorPorHora = 10.0
    private var horasCobradas = 1
    private var valorTotal = 10.0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPagamentoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        lerExtrasDaIntent()
        calcularValorMock()
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
    }

    /**
     * Cálculo simples do valor a pagar.
     * tempo utilizado = agora - início da reserva
     * horas cobradas = arredondado pra cima (qualquer fração de hora conta como 1h)
     * valor total = horas cobradas * valor por hora
     *
     * Isso é só um mock para o protótipo. A regra financeira real (tolerância,
     * cobrança por minuto, taxas etc.) deve substituir esse trecho depois.
     */
    private fun calcularValorMock() {
        val tempoUtilizadoMinutos = max(1L, (System.currentTimeMillis() - horaInicioMillis) / 60_000)
        horasCobradas = ceil(tempoUtilizadoMinutos / 60.0).toInt().coerceAtLeast(1)
        valorTotal = horasCobradas * valorPorHora

        val horas = tempoUtilizadoMinutos / 60
        val minutos = tempoUtilizadoMinutos % 60
        val tempoTexto = if (horas > 0) "${horas}h ${minutos}min" else "${minutos}min"
        binding.tvResumoTempoUtilizado.text = "Tempo utilizado: $tempoTexto"
    }

    private fun preencherResumo() {
        binding.tvResumoEstacionamento.text = "Estacionamento: $estacionamentoNome"
        binding.tvResumoVaga.text = "Vaga: $vagaNumero"
        binding.tvResumoVeiculo.text = "Veículo: $veiculoModelo • $veiculoPlaca"
        binding.tvResumoHorario.text = "Horário: $horarioInicioTexto - $horarioFimTexto"
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

        // Reseta visual dos 3 cards
        atualizarEstadoCard(binding.cardPix, binding.ivCheckPix, forma == FormaPagamento.PIX)
        atualizarEstadoCard(binding.cardCredito, binding.ivCheckCredito, forma == FormaPagamento.CREDITO)
        atualizarEstadoCard(binding.cardDebito, binding.ivCheckDebito, forma == FormaPagamento.DEBITO)

        // Mostra/esconde os blocos condicionais
        binding.tvExplicacaoPix.visibility = if (forma == FormaPagamento.PIX) View.VISIBLE else View.GONE
        binding.layoutCamposCartao.visibility =
            if (forma == FormaPagamento.CREDITO || forma == FormaPagamento.DEBITO) View.VISIBLE else View.GONE
    }

    private fun atualizarEstadoCard(
        card: com.google.android.material.card.MaterialCardView,
        check: android.widget.ImageView,
        selecionado: Boolean
    ) {
        card.strokeColor = if (selecionado)
            getColor(R.color.verdeescuro) else getColor(R.color.cinza)
        card.strokeWidth = if (selecionado) 4 else 2
        check.visibility = if (selecionado) View.VISIBLE else View.INVISIBLE
    }

    private fun setupBotaoConfirmar() {
        binding.btnConfirmarPagamento.setOnClickListener {
            if (!validarSelecaoEDados()) return@setOnClickListener
            processarPagamentoSimulado()
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
                Toast.makeText(this, "Preencha todos os dados do cartão.", Toast.LENGTH_SHORT).show()
                return false
            }
        }
        return true
    }

    private fun processarPagamentoSimulado() {
        binding.btnConfirmarPagamento.isEnabled = false
        binding.progressBarPagamento.visibility = View.VISIBLE

        // Simula um pequeno tempo de processamento (sem coroutine/rede real).
        Handler(Looper.getMainLooper()).postDelayed({
            binding.progressBarPagamento.visibility = View.GONE
            mostrarPagamentoAprovado()
        }, 1800)
    }

    private fun mostrarPagamentoAprovado() {
        binding.layoutConteudoPagamento.visibility = View.GONE
        binding.layoutSucesso.visibility = View.VISIBLE

        binding.tvSucessoEstacionamento.text = "Estacionamento: $estacionamentoNome"
        binding.tvSucessoVaga.text = "Vaga: $vagaNumero"
        binding.tvSucessoVeiculo.text = "Veículo: $veiculoModelo • $veiculoPlaca"
        binding.tvSucessoValor.text = "Valor pago: ${formatarValor(valorTotal)}"

        val conteudoQr = "VALLET-SAIDA-${System.currentTimeMillis()}"
        gerarQrCode(conteudoQr)

        animarEntradaSucesso()
    }

    private fun animarEntradaSucesso() {
        val icone = binding.layoutSucesso.getChildAt(0) // ImageView do check
        icone.scaleX = 0f
        icone.scaleY = 0f
        icone.animate()
            .scaleX(1f).scaleY(1f)
            .setDuration(400)
            .setInterpolator(android.view.animation.OvershootInterpolator())
            .start()
    }

    /**
     * Gera o QR Code usando a biblioteca com.google.zxing:core (dependência mínima*/
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
            preencherResumoSaida()
            animarSaidaLiberada()
        }

        binding.btnConcluir.setOnClickListener {
            setResult(RESULT_OK)
            finish()
        }
    }

    private fun preencherResumoSaida() {
        binding.tvSaidaTempoTotal.text = "${horasCobradas}h"
        binding.tvSaidaValorPago.text = formatarValor(valorTotal)
    }


    private fun animarSaidaLiberada() {
        // Ícone: pop
        binding.ivExitIcon.scaleX = 0f
        binding.ivExitIcon.scaleY = 0f
        binding.ivExitIcon.animate()
            .scaleX(1f).scaleY(1f)
            .setStartDelay(100)
            .setDuration(400)
            .setInterpolator(android.view.animation.OvershootInterpolator())
            .start()

        // Anel de pulso
        binding.viewPulso.scaleX = 0.6f
        binding.viewPulso.scaleY = 0.6f
        binding.viewPulso.alpha = 0.8f
        binding.viewPulso.animate()
            .scaleX(1.6f).scaleY(1.6f)
            .alpha(0f)
            .setStartDelay(150)
            .setDuration(900)
            .withEndAction {
                // reseta e pulsa mais uma vez, pra reforçar o efeito sem ficar repetitivo
                binding.viewPulso.scaleX = 0.6f
                binding.viewPulso.scaleY = 0.6f
                binding.viewPulso.alpha = 0.8f
                binding.viewPulso.animate()
                    .scaleX(1.6f).scaleY(1.6f)
                    .alpha(0f)
                    .setDuration(900)
                    .start()
            }
            .start()

        // Textos e resumo: fade + sobe de baixo pra cima, em sequência
        val elementos = listOf(
            binding.tvSaidaTitulo,
            binding.tvSaidaSubtitulo,
            binding.layoutSaidaResumo,
            binding.btnConcluir
        )
        elementos.forEachIndexed { index, view ->
            view.alpha = 0f
            view.translationY = 40f
            view.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(250L + index * 120L)
                .setDuration(350)
                .start()
        }
    }
}
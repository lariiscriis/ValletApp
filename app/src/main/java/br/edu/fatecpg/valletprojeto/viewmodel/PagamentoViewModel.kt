package br.edu.fatecpg.valletprojeto.viewmodel

import android.app.Application
import android.content.pm.ApplicationInfo
import android.os.Bundle
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import br.edu.fatecpg.valletprojeto.R
import br.edu.fatecpg.valletprojeto.service.*
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.util.UUID
import kotlin.math.ceil
import kotlin.math.max

object CalculoPagamento {
    fun minutos(inicio: Long, agora: Long) = max(1L, (agora - inicio) / 60_000)
    fun horas(minutos: Long) = ceil(minutos / 60.0).toInt().coerceAtLeast(1)
}

class PagamentoViewModel(app: Application, private val state: SavedStateHandle) : AndroidViewModel(app) {
    enum class Etapa { RESUMO, PIX, SAIDA }
    val alteracoes = MutableLiveData(0)
    val desenvolvimento = app.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    private var chamada: Call<RespostaPix>? = null
    // A imagem da API permanece em memória; após morte do processo, o QR é reconstruído do conteúdo.
    var qrCodeBase64: String = ""
        private set
    val dados: Bundle get() = state.get<Bundle>("dados") ?: Bundle()
    val etapa get() = Etapa.valueOf(state.get<String>("etapa") ?: Etapa.RESUMO.name)
    val ocupado get() = state.get<Boolean>("ocupado") == true
    val simulado get() = desenvolvimento && state.get<Boolean>("simulado") == true
    val leitura get() = state.get<Boolean>("leitura") == true
    val codigo get() = state.get<String>("codigo").orEmpty()
    val saida get() = state.get<String>("saida").orEmpty()
    val status get() = state.get<String>("status").orEmpty()
    val transacao get() = state.get<String>("transacao").orEmpty()
    val erro get() = state.get<Int>("erro") ?: 0
    val minutos get() = dados.getLong("minutos")
    val valor get() = CalculoPagamento.horas(minutos) * dados.getDouble("valorHora")
    val confirmado get() = status.lowercase(java.util.Locale.ROOT) in setOf("approved", "aprovado", "paid", "pago")
    val valido get() = dados.getLong("horaInicioMillis") > 0 &&
        dados.containsKey("valorHora") && dados.getDouble("valorHora").isFinite() &&
        dados.getDouble("valorHora") >= 0 &&
        !dados.getString("usuarioId").isNullOrBlank() && !dados.getString("reservaId").isNullOrBlank()

    init {
        // Um processo restaurado nunca repete uma solicitação que pode ter chegado ao servidor.
        if (ocupado) { state["ocupado"] = false; state["erro"] = R.string.pag_interrompido }
        if (!desenvolvimento && state.get<Boolean>("simulado") == true) {
            state["etapa"] = Etapa.RESUMO.name
            state["codigo"] = ""
            state["saida"] = ""
            state["simulado"] = false
        }
    }
    fun inicializar(extras: Bundle?, agora: Long = System.currentTimeMillis()) {
        if (state.contains("dados")) return
        val bundle = Bundle(extras ?: Bundle())
        bundle.putLong("minutos", CalculoPagamento.minutos(bundle.getLong("horaInicioMillis", agora), agora))
        state["dados"] = bundle
        if (!valido) state["erro"] = R.string.pag_dados_invalidos
    }
    private fun atualizar() { alteracoes.value = (alteracoes.value ?: 0) + 1 }
    fun voltarAoResumo() { if (!ocupado && etapa != Etapa.SAIDA) { state["etapa"] = Etapa.RESUMO.name; atualizar() } }
    fun pagar() {
        if (ocupado || !valido || etapa == Etapa.SAIDA) return
        if (codigo.isNotBlank()) { state["etapa"] = Etapa.PIX.name; atualizar(); return }
        state["ocupado"] = true
        state["erro"] = 0
        atualizar()
        val request = RequisicaoPagamento(valor, "pix", dados.getString("usuarioId")!!, dados.getString("reservaId")!!)
        chamada = RetrofitClient.valletApiService.criarPagamentoPix(request)
        chamada!!.enqueue(object : Callback<RespostaPix> {
            override fun onResponse(call: Call<RespostaPix>, response: Response<RespostaPix>) {
                state["ocupado"] = false
                val r = response.body()
                if (response.isSuccessful && r != null) {
                    state["codigo"] = r.qrCode.orEmpty()
                    state["status"] = r.status.orEmpty()
                    state["transacao"] = r.idTransacao.orEmpty()
                    state["mensagem"] = r.mensagem.orEmpty()
                    qrCodeBase64 = r.qrCodeBase64.orEmpty()
                    state["simulado"] = false
                    when {
                        confirmado -> state["etapa"] = Etapa.SAIDA.name
                        codigo.isNotBlank() -> state["etapa"] = Etapa.PIX.name
                        else -> state["erro"] = R.string.pag_erro
                    }
                } else {
                    android.util.Log.w("PagamentoPix", "Falha ao criar Pix: HTTP ${response.code()}")
                    state["erro"] = R.string.pag_erro
                }
                atualizar()
            }
            override fun onFailure(call: Call<RespostaPix>, t: Throwable) {
                android.util.Log.w("PagamentoPix", "Falha de comunicação Pix: ${t.javaClass.simpleName}")
                state["ocupado"] = false
                state["erro"] = R.string.pag_erro
                atualizar()
            }
        })
    }
    fun demonstrar() {
        if (!desenvolvimento || ocupado || codigo.isNotBlank() || etapa == Etapa.SAIDA) return
        state["simulado"] = true
        state["codigo"] = "VALLET-DEMO-PIX-${UUID.randomUUID()}"
        state["status"] = "demo"
        state["erro"] = 0
        state["etapa"] = Etapa.PIX.name
        atualizar()
    }
    fun simularAprovacao() {
        if (!simulado || etapa != Etapa.PIX) return
        state["saida"] = "VALLET-SAIDA-DEMO-${UUID.randomUUID()}"
        state["etapa"] = Etapa.SAIDA.name
        atualizar()
    }
    fun simularLeitura() {
        if (!simulado || etapa != Etapa.SAIDA) return
        state["leitura"] = true
        atualizar()
    }
    override fun onCleared() { chamada?.cancel() }
}

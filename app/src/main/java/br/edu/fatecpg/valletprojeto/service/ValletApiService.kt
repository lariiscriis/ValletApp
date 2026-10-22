package br.edu.fatecpg.valletprojeto.service

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST

// 1. Dados que o App envia para a API
data class RequisicaoPagamento(
    val valor: Double,
    val metodo: String,
    val usuario_id: String,
    val reserva_id: String
)

// 2. Dados que a API devolve para o App
data class RespostaPix(
    val id_transacao: String,
    val status: String,
    val qr_code: String,
    val qr_code_base64: String,
    val mensagem: String
)

// 3. A rota da sua API
interface ValletApiService {
    @POST("pagamento/pix") // Ajuste o caminho se a sua rota no FastAPI for diferente
    fun criarPagamentoPix(@Body request: RequisicaoPagamento): Call<RespostaPix>
}
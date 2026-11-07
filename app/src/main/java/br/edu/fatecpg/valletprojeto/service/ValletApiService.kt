package br.edu.fatecpg.valletprojeto.service

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Header
import retrofit2.Response
import okhttp3.ResponseBody

// 1. Dados que o App envia para a API
data class RequisicaoPagamento(
    val valor: Double,
    val metodo: String,
    val usuarioId: String,
    val reservaId: String
)

// 2. Dados que a API devolve para o App
data class RespostaPix(
    val idTransacao: String,
    val status: String,
    val qrCode: String,
    val qrCodeBase64: String,
    val mensagem: String
)

// 3. A rota da sua API
interface ValletApiService {
    @POST("reserva/{reservaId}/notificar-inicio")
    suspend fun notificarInicioReserva(
        @Path("reservaId") reservaId: String,
        @Header("Authorization") authorization: String
    ): Response<ResponseBody>

    @POST("pagamento/pix") // Ajuste o caminho se a sua rota no FastAPI for diferente
    fun criarPagamentoPix(@Body request: RequisicaoPagamento): Call<RespostaPix>
}

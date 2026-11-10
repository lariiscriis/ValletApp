package br.edu.fatecpg.valletprojeto

import br.edu.fatecpg.valletprojeto.service.RequisicaoPagamento
import br.edu.fatecpg.valletprojeto.service.ValletApiService
import com.google.gson.Gson
import com.google.gson.JsonParser
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class PagamentoContratoTest {
    private val dados = RequisicaoPagamento(12.50, "pix", "usuario-teste", "reserva-teste")

    @Test fun identificadoresUsamNomesExigidosPelaApi() {
        val json = JsonParser.parseString(Gson().toJson(dados)).asJsonObject
        assertEquals("usuario-teste", json["usuario_id"].asString)
        assertEquals("reserva-teste", json["reserva_id"].asString)
        assertFalse(json.has("usuarioId"))
        assertFalse(json.has("reservaId"))
        assertEquals("pix", json["metodo"].asString)
        assertEquals(12.5, json["valor"].asDouble, 0.0)
    }

    @Test fun retrofitMontaPostPixSemExecutarCobranca() {
        val api = Retrofit.Builder().baseUrl("https://example.invalid/")
            .addConverterFactory(GsonConverterFactory.create()).build()
            .create(ValletApiService::class.java)
        // Apenas constrói a requisição; nunca chama execute() ou enqueue().
        val request = api.criarPagamentoPix(dados).request()
        assertEquals("POST", request.method)
        assertEquals("/pagamento/pix", request.url.encodedPath)
        val buffer = Buffer()
        request.body!!.writeTo(buffer)
        val json = JsonParser.parseString(buffer.readUtf8()).asJsonObject
        assertEquals("reserva-teste", json["reserva_id"].asString)
        assertEquals("usuario-teste", json["usuario_id"].asString)
    }
}

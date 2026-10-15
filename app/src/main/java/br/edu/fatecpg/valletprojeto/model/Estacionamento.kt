package br.edu.fatecpg.valletprojeto.model

import android.location.Location
import java.util.Calendar
import java.util.concurrent.TimeUnit

data class Estacionamento(
    var id: String = "",
    val nome: String = "",
    val cnpj: String = "",
    val telefone: String = "",
    val adminUid: String? = null,
    val adminEmail: String = "",
    val dataCadastro: Any? = null,
    val endereco: Map<String, Any>? = null,
    val cep: String = "",
    val tiposVagasComum: Boolean = false,
    val tiposVagasIdosoPcd: Boolean = false,
    val quantidadeVagasComum: Int? = null,
    val quantidadeVagasIdosoPcd: Int? = null,
    val possuiCobertura: Boolean = false,
    val numeroPavimentos: Int? = null,
    val valorDiario: Double = 0.0,
    val tempoMaxReservaHoras: Int = 2,
    val toleranciaReservaMinutos: Int = 10,
    val fotoEstacionamentoUri: String? = null,
    var vagasDisponiveis: Int? = null,
    var distanciaMetros: Int? = null,
    var latitude: Double = 0.0,
    var longitude: Double = 0.0,
    val quantidadeVagasTotal: Int? = null,
    val horarioAbertura: String = "8:00",
    val horarioFechamento: String = "7:00",
    val valorHora: Double? = null,
    val geohash: String? = null,
    val isFavorite: Boolean = false

)
{

    val enderecoCompleto: String
        get() {
            if (endereco == null) return ""
            val logradouro = endereco["logradouro"] ?: ""
            val numero = endereco["numero"] ?: ""
            val bairro = endereco["bairro"] ?: ""
            val cidade = endereco["cidade"] ?: ""
            val uf = endereco["uf"] ?: ""
            return "$logradouro, $numero - $bairro, $cidade - $uf".trim().trim(',', '-', ' ')
        }
    fun calcularDistancia(userLocation: Location?): Int? {
        if (userLocation == null || (latitude == 0.0 && longitude == 0.0)) {
            return null
        }

        val estacionamentoLocation = Location("Estacionamento")
        estacionamentoLocation.latitude = latitude
        estacionamentoLocation.longitude = longitude

        return userLocation.distanceTo(estacionamentoLocation).toInt()
    }

    fun estaAberto(): Boolean {
        return try {
            val now = Calendar.getInstance()
            var currentMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)

            val (openHour, openMinute) = horarioAbertura.split(":").map { it.toInt() }
            val openMinutes = openHour * 60 + openMinute

            val (closeHour, closeMinute) = horarioFechamento.split(":").map { it.toInt() }
            var closeMinutes = closeHour * 60 + closeMinute

            if (closeMinutes < openMinutes) {
                closeMinutes += 24 * 60
                if (currentMinutes < openMinutes) {
                    currentMinutes += 24 * 60
                }
            }

            currentMinutes in openMinutes until closeMinutes
        } catch (e: Exception) {
            true
        }
    }
}
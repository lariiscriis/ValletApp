package br.edu.fatecpg.valletprojeto.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import br.edu.fatecpg.valletprojeto.model.Reserva
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

data class PerfilUIState(
    val nome: String = "",
    val email: String = "",
    val telefone: String = "",
    val cnh: String = "",
    val tipoUser: String = "",
    val fotoPerfilUrl: String? = null,
    val totalReservas: Long = 0,
    val tempoTotalUso: String = "0h",
    val locaisMaisFrequentados: String = "Nenhum",
    val ultimasReservas: List<Reserva> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null
)

class PerfilMotoristaViewModel(application: Application) : AndroidViewModel(application) {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val _uiState = MutableLiveData(PerfilUIState())
    val uiState: LiveData<PerfilUIState> = _uiState

    init {
        carregarDadosPerfil()
    }

    fun carregarDadosPerfil() {
        viewModelScope.launch {
            _uiState.value = _uiState.value?.copy(isLoading = true, errorMessage = null)
            val userId = auth.currentUser?.uid
            val email = auth.currentUser?.email

            if (userId == null || email == null) {
                _uiState.value = _uiState.value?.copy(
                    isLoading = false,
                    errorMessage = "Usuário não autenticado."
                )
                return@launch
            }

            try {
                val userSnapshot = db.collection("usuario")
                    .whereEqualTo("email", email)
                    .get().await()

                val userDoc = userSnapshot.documents.firstOrNull()
                val currentState = _uiState.value ?: PerfilUIState()

                if (userDoc != null) {
                    _uiState.value = currentState.copy(
                        nome = userDoc.getString("nome") ?: "",
                        email = email,
                        telefone = userDoc.getString("telefone") ?: "",
                        cnh = userDoc.getString("cnh") ?: "",
                        tipoUser = userDoc.getString("tipo_user") ?: "",
                        fotoPerfilUrl = userDoc.getString("fotoPerfil")
                    )
                } else {
                    _uiState.value = currentState.copy(errorMessage = "Dados do usuário não encontrados.")
                    return@launch
                }

                val reservasSnapshot = db.collection("reserva")
                    .whereEqualTo("usuarioId", userId)
                    .orderBy("fimReserva", Query.Direction.DESCENDING) // Ordenar pela mais recente
                    .limit(3)
                    .get().await()

                val ultimasReservas = reservasSnapshot.documents.mapNotNull { document ->
                    document.toObject(Reserva::class.java)?.apply {
                        id = document.id
                    }
                }

                val todasReservasSnapshot = db.collection("reserva")
                    .whereEqualTo("usuarioId", userId)
                    .get().await()

                val todasReservas = todasReservasSnapshot.toObjects(Reserva::class.java)

                val totalReservas = todasReservas.size.toLong()
                val (tempoTotalUso, locaisMaisFrequentados) = calcularEstatisticas(todasReservas)

                _uiState.value = _uiState.value?.copy(
                    ultimasReservas = ultimasReservas,
                    totalReservas = totalReservas,
                    tempoTotalUso = tempoTotalUso,
                    locaisMaisFrequentados = locaisMaisFrequentados,
                    isLoading = false
                )

            } catch (e: Exception) {
                _uiState.value = _uiState.value?.copy(
                    isLoading = false,
                    errorMessage = "Erro ao carregar perfil: ${e.message}"
                )
            }
        }
    }

    private fun calcularEstatisticas(reservas: List<Reserva>): Pair<String, String> {
        var tempoTotalMillis = 0L
        val frequenciaLocais = mutableMapOf<String, Int>()

        reservas.forEach { reserva ->
            val inicio = reserva.inicioReserva?.toDate()?.time
            val fim = reserva.fimReserva?.toDate()?.time

            if (inicio != null && fim != null && fim > inicio) {
                tempoTotalMillis += (fim - inicio)
            }

            val estacionamento = reserva.estacionamentoNome.ifEmpty { "Desconhecido" }
            frequenciaLocais[estacionamento] = (frequenciaLocais[estacionamento] ?: 0) + 1
        }

        val tempoTotalHoras = TimeUnit.MILLISECONDS.toHours(tempoTotalMillis)
        val tempoTotalFormatado = "${tempoTotalHoras}h"

        val locaisMaisFrequentados = frequenciaLocais.entries
            .sortedByDescending { it.value }
            .take(3)
            .joinToString(", ") { "${it.key} (${it.value})" }

        return Pair(tempoTotalFormatado, locaisMaisFrequentados)
    }

    fun formatarReserva(reserva: Reserva): String {
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        val inicioStr = reserva.inicioReserva?.toDate()?.let { sdf.format(it) } ?: "N/A"
        val fimStr = reserva.fimReserva?.toDate()?.let { sdf.format(it) } ?: "N/A"
        val status = reserva.status.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }

        return "Estacionamento: ${reserva.estacionamentoNome}\n" +
                "Início: $inicioStr\n" +
                "Fim: $fimStr\n" +
                "Status: $status"
    }
}
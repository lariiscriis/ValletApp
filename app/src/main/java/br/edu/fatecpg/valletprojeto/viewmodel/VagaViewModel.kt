package br.edu.fatecpg.valletprojeto.viewmodel

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.edu.fatecpg.valletprojeto.model.Vaga
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class VagaViewModel : ViewModel() {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val vagasCollection = db.collection("vaga")

    val vagas = MutableLiveData<List<Vaga>>()
    val errorMessage = MutableLiveData<String>()
    val veiculoPadraoTipo = MutableLiveData<String?>()

    private var listenerRegistration: ListenerRegistration? = null
    private var estacionamentoAtual: String? = null
    private var geracaoListener = 0
    private val filtroVagas = FiltroVagas()

    init {
        // O veículo padrão só define o ícone de entrada, nunca o filtro de vagas.
        viewModelScope.launch {
            veiculoPadraoTipo.value = buscarVeiculoPadraoTipo()
        }
    }

    fun fetchVagasComFiltro(estacionamentoId: String, tipoFiltro: String? = null, precoMax: Double? = null) {
        if (estacionamentoAtual != estacionamentoId) {
            filtroVagas.atualizarVagas(emptyList())
        }

        vagas.value = filtroVagas.selecionarFiltro(tipoFiltro, precoMax)

        // Trocar o chip não precisa aguardar outra consulta ao Firestore.
        if (estacionamentoAtual == estacionamentoId && listenerRegistration != null) {
            return
        }

        observarVagas(estacionamentoId)
    }

    private suspend fun buscarVeiculoPadraoTipo(): String? = withContext(Dispatchers.IO) {
        val userId = auth.currentUser?.uid ?: return@withContext null
        try {
            val veiculoSnapshot = db.collection("veiculo")
                .whereEqualTo("usuarioId", userId)
                .whereEqualTo("padrao", true)
                .limit(1)
                .get()
                .await()

            veiculoSnapshot.documents.firstOrNull()?.getString("tipo")?.trim()
        } catch (e: Exception) {
            null
        }
    }

    private fun observarVagas(estacionamentoId: String) {
        listenerRegistration?.remove()
        estacionamentoAtual = estacionamentoId
        val geracaoAtual = ++geracaoListener

        // A fonte sempre contém todas as vagas, livres e ocupadas.
        listenerRegistration = vagasCollection
            .whereEqualTo("estacionamentoId", estacionamentoId)
            .addSnapshotListener { snapshot, error ->
                if (geracaoAtual != geracaoListener) return@addSnapshotListener

                if (error != null) {
                    errorMessage.value = "Erro ao buscar vagas: ${error.message}"
                    return@addSnapshotListener
                }

                val lista = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(Vaga::class.java)?.apply { id = doc.id }
                } ?: emptyList()

                vagas.value = filtroVagas.atualizarVagas(lista)
            }
    }

    fun verificarSeTemVagas(estacionamentoId: String, onResult: (Boolean) -> Unit) {
        if (estacionamentoId.isBlank()) {
            onResult(false)
            return
        }
        vagasCollection
            .whereEqualTo("estacionamentoId", estacionamentoId)
            .whereEqualTo("disponivel", true)
            .limit(1)
            .get()
            .addOnSuccessListener { snapshot -> onResult(!snapshot.isEmpty) }
            .addOnFailureListener { onResult(false) }
    }

    override fun onCleared() {
        super.onCleared()
        listenerRegistration?.remove()
    }
}

// Preserva a seleção do filtro enquanto os dados são carregados ou atualizados.
internal class FiltroVagas {
    private var todasAsVagas: List<Vaga> = emptyList()
    private var tipoFiltro = "todos"
    private var precoMax: Double? = null

    fun atualizarVagas(vagas: List<Vaga>): List<Vaga> {
        todasAsVagas = vagas.toList()
        return filtrar()
    }

    fun selecionarFiltro(tipo: String? = null, precoMax: Double? = null): List<Vaga> {
        if (tipo != null) tipoFiltro = tipo.trim().lowercase()
        this.precoMax = precoMax
        return filtrar()
    }

    private fun filtrar(): List<Vaga> = todasAsVagas.filter { vaga ->
        val correspondeAoTipo = when (tipoFiltro) {
            "todos" -> true
            "preferencial" -> vaga.preferencial
            else -> vaga.tipo.trim().equals(tipoFiltro, ignoreCase = true)
        }
        val limite = precoMax
        correspondeAoTipo && (limite == null || limite <= 0 || vaga.preco <= limite)
    }
}

package br.edu.fatecpg.valletprojeto.viewmodel

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.edu.fatecpg.valletprojeto.model.Vaga
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
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

    fun fetchVagasComFiltro(estacionamentoId: String, tipoFiltro: String? = null, precoMax: Double? = null) {
        viewModelScope.launch {
            val tipoVaga = tipoFiltro ?: buscarVeiculoPadraoTipo()
            val tipoFinal = if (tipoVaga.equals("Todos", ignoreCase = true)) null else tipoVaga

            aplicarListenerComFiltros(estacionamentoId, tipoFinal, precoMax)
        }
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

            val tipo = veiculoSnapshot.documents.firstOrNull()?.getString("tipo")

            withContext(Dispatchers.Main) {
                veiculoPadraoTipo.value = tipo
            }
            tipo
        } catch (e: Exception) {
            null
        }
    }

    private fun aplicarListenerComFiltros(estacionamentoId: String, tipo: String?, precoMax: Double?) {
        listenerRegistration?.remove()

        var query: Query = vagasCollection
            .whereEqualTo("estacionamentoId", estacionamentoId)

        if (tipo != null) {
            query = if (tipo.equals("Preferencial", ignoreCase = true)) {
                query.whereEqualTo("preferencial", true)
            } else {
                query.whereEqualTo("tipo", tipo.lowercase())
            }
        }

        if (precoMax != null && precoMax > 0) {
            query = query.whereLessThanOrEqualTo("preco", precoMax)
        }

        listenerRegistration = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                errorMessage.value = "Erro ao buscar vagas: ${error.message}"
                return@addSnapshotListener
            }

            val lista = snapshot?.documents?.mapNotNull { doc ->
                doc.toObject(Vaga::class.java)?.apply { id = doc.id }
            } ?: emptyList()

            vagas.value = lista
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

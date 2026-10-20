package br.edu.fatecpg.valletprojeto.dao

import br.edu.fatecpg.valletprojeto.model.Vaga
import com.google.firebase.firestore.FirebaseFirestore

class ListagemVagasDao {

    private val db = FirebaseFirestore.getInstance()

    fun listarTodasAsVagas(
        onSuccess: (List<Vaga>) -> Unit,
        onError: (String) -> Unit
    ) {
        db.collection("vaga")
            .get()
            .addOnSuccessListener { snapshot ->
                val lista = snapshot.documents.mapNotNull { doc ->
                    try {
                        val numero = doc.getString("numero") ?: ""
                        val localizacao = doc.getString("localizacao") ?: ""
                        val preco = doc.getDouble("preco") ?: 0.0
                        val tipo = doc.getString("tipo") ?: ""
                        val disponivel = doc.getBoolean("disponivel") ?: true
                        Vaga(
                            id = doc.id,
                            numero = numero,
                            localizacao = localizacao,
                            preco = preco,
                            tipo = tipo,
                            disponivel = disponivel,
                            estacionamentoId = doc.getString("estacionamentoId") ?: ""
                        )
                    } catch (e: Exception) {
                        null
                    }
                }
                onSuccess(lista)
            }
            .addOnFailureListener { exception ->
                onError(exception.message ?: "Erro desconhecido")
            }
    }

}

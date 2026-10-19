package br.edu.fatecpg.valletprojeto.dao

import android.util.Log
import br.edu.fatecpg.valletprojeto.model.Veiculo
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

object VeiculoDao {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    fun cadastrarVeiculo(
        veiculo: Veiculo,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        val emailUsuario = auth.currentUser?.email ?: return onFailure("Usuário não autenticado")
        val userId = auth.currentUser?.uid ?: return onFailure("Usuário não autenticado")

        val veiculoCadastrado = hashMapOf(
            "placa" to veiculo.placa,
            "marca" to veiculo.marca,
            "modelo" to veiculo.modelo,
            "ano" to veiculo.ano,
            "cor" to veiculo.cor.trim(),
            "tipo" to veiculo.tipo,
            "usuarioEmail" to emailUsuario,
            "usuarioId" to userId,
            "padrao" to veiculo.padrao,
            "data_cadastro" to FieldValue.serverTimestamp()
        )

        db.collection("veiculo")
            .document(veiculo.placa)
            .set(veiculoCadastrado)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onFailure(it.message ?: "Erro desconhecido") }
    }

    fun definirVeiculoPadrao(
        veiculoId: String,
        usuarioId: String,
        onComplete: (Boolean) -> Unit
    ) {
        Log.d("VeiculoDebug", "DAO: Iniciando transação para definir $veiculoId como padrão.")

        db.collection("veiculo")
            .whereEqualTo("usuarioId", usuarioId)
            .get()
            .addOnSuccessListener { snapshot ->
                Log.d("VeiculoDebug", "DAO: Busca de veículos do usuário retornou ${snapshot.size()} documentos.")
                val batch = db.batch()

                var desmarcouAlgum = false
                for (document in snapshot.documents) {
                    if (document.id != veiculoId && document.getBoolean("padrao") == true) {
                        Log.d("VeiculoDebug", "DAO: Adicionando ao batch -> desmarcar ${document.id}")
                        batch.update(document.reference, "padrao", false)
                        desmarcouAlgum = true
                    }
                }
                if (!desmarcouAlgum) {
                    Log.d("VeiculoDebug", "DAO: Nenhum veículo antigo para desmarcar.")
                }

                val novoPadraoRef = db.collection("veiculo").document(veiculoId)
                Log.d("VeiculoDebug", "DAO: Adicionando ao batch -> marcar $veiculoId")
                batch.update(novoPadraoRef, "padrao", true)

                batch.commit()
                    .addOnSuccessListener {
                        Log.d("VeiculoDebug", "DAO: Batch commit SUCESSO.")
                        onComplete(true)
                    }
                    .addOnFailureListener { e ->
                        Log.e("VeiculoDebug", "DAO: Batch commit FALHOU.", e)
                        onComplete(false)
                    }
            }
            .addOnFailureListener { e ->
                Log.e("VeiculoDebug", "DAO: Busca inicial de veículos FALHOU.", e)
                onComplete(false)
            }
    }

    fun listarVeiculosDoUsuario(
        onSuccess: (List<Veiculo>) -> Unit,
        onFailure: (String) -> Unit
    ) {
        val userId = auth.currentUser?.uid ?: return onFailure("Usuário não autenticado")

        db.collection("veiculo")
            .whereEqualTo("usuarioId", userId)
            .get()
            .addOnSuccessListener { result ->
                val lista = result.toObjects(Veiculo::class.java)
                lista.forEachIndexed { index, veiculo ->
                    veiculo.id = result.documents[index].id
                }
                onSuccess(lista)
            }
            .addOnFailureListener {
                onFailure(it.message ?: "Erro desconhecido")
            }
    }
}

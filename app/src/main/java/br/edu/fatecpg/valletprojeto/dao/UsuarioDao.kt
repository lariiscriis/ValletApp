package br.edu.fatecpg.valletprojeto.dao

import br.edu.fatecpg.valletprojeto.model.Usuario
import br.edu.fatecpg.valletprojeto.onboarding.ValidacaoCadastro
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

class UsuarioDao {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    fun cadastrarUsuario(usuario: Usuario, onSuccess: () -> Unit, onError: (String) -> Unit) {
        if (!ValidacaoCadastro.cnhValida(usuario.cnh.orEmpty())) {
            onError("CNH inválida. Confira os 11 dígitos do número de registro.")
            return
        }
        val atual = auth.currentUser
        if (atual != null) {
            salvarPerfil(atual.uid, atual.email.orEmpty(), usuario, onSuccess, onError)
            return
        }
        auth.createUserWithEmailAndPassword(usuario.email, usuario.senha)
            .addOnSuccessListener { result ->
                val user = result.user
                if (user == null) onError("Erro ao obter a sessão do usuário")
                else salvarPerfil(user.uid, user.email.orEmpty(), usuario, onSuccess, onError)
            }
            .addOnFailureListener { onError(it.message ?: "Erro ao criar usuário") }
    }

    private fun salvarPerfil(
        uid: String, email: String, usuario: Usuario,
        onSuccess: () -> Unit, onError: (String) -> Unit
    ) {
        val ref = db.collection("usuario").document(uid)
        db.runTransaction { transaction ->
            check(auth.currentUser?.uid == uid) { "A sessão foi alterada" }
            val anterior = transaction.get(ref)
            // Uma conta de outro tipo jamais pode virar motorista por este formulário.
            check(!anterior.exists() ||
                anterior.getString("tipo_user")?.trim().equals("motorista", ignoreCase = true)) {
                "Este aplicativo é exclusivo para motoristas"
            }
            val dados = hashMapOf<String, Any>(
                "email" to email, "nome" to usuario.nome,
                "cnh" to usuario.cnh.orEmpty(), "tipo_user" to "motorista",
                "telefone" to usuario.telefone
            )
            if (!anterior.exists()) {
                dados["data_criacao"] = FieldValue.serverTimestamp()
                dados["cadastroVersao"] = 2
                dados["fotoCadastroConcluida"] = false
            }
            transaction.set(ref, dados, SetOptions.merge())
        }.addOnSuccessListener {
            if (auth.currentUser?.uid == uid) onSuccess()
            else onError("A sessão foi alterada")
        }.addOnFailureListener {
            // A conta já pode existir no Auth. Manter a sessão permite completar o perfil.
            onError(it.message ?: "Erro ao salvar perfil. Tente novamente.")
        }
    }
}

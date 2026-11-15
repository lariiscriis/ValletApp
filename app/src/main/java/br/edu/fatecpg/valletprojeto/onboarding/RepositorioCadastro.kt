package br.edu.fatecpg.valletprojeto.onboarding

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.tasks.await

class RepositorioCadastro {
    suspend fun destino(): DestinoCadastro {
        val auth = FirebaseAuth.getInstance()
        val user = auth.currentUser ?: return DestinoCadastro.INICIO
        val db = FirebaseFirestore.getInstance()
        val perfil = db.collection("usuario").document(user.uid).get(Source.SERVER).await()
        check(!perfil.metadata.hasPendingWrites()) { "O perfil ainda aguarda confirmação do servidor." }
        check(auth.currentUser?.uid == user.uid) { "A sessão foi alterada. Tente novamente." }
        PoliticaCadastro.perfil(
            perfil.exists(), perfil.getString("tipo_user"),
            perfil.getString("nome"), perfil.getString("cnh")
        )?.let { return it }

        if ((perfil.getLong("cadastroVersao") ?: 0) >= 2) {
            if (!ValidacaoCadastro.telefoneValido(perfil.getString("telefone").orEmpty())) return DestinoCadastro.CONTA
            if (perfil.getBoolean("fotoCadastroConcluida") != true) return DestinoCadastro.FOTO
        }

        // Nunca usar cache ou transformar uma falha de rede em lista vazia.
        val veiculos = db.collection("veiculo").whereEqualTo("usuarioId", user.uid)
            .limit(1).get(Source.SERVER).await()
        check(!veiculos.metadata.hasPendingWrites()) { "O veículo ainda aguarda confirmação do servidor." }
        check(auth.currentUser?.uid == user.uid) { "A sessão foi alterada. Tente novamente." }
        return PoliticaCadastro.veiculos(!veiculos.isEmpty)
    }
}

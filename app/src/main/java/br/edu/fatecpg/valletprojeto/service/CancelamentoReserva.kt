package br.edu.fatecpg.valletprojeto.service

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.work.WorkManager
import br.edu.fatecpg.valletprojeto.receiver.ReservaExpiradaReceiver
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.tasks.await
import org.json.JSONObject

/** Único caminho de cancelamento, inclusive comandos de voz. Sem fallback de escrita direta. */
object CancelamentoReserva {
    private val mutex = Mutex()

    suspend fun cancelarAtiva(context: Context) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: error("Usuário não autenticado.")
        val docs = com.google.firebase.firestore.FirebaseFirestore.getInstance()
            .collection("reserva").whereEqualTo("usuarioId", uid)
            .whereEqualTo("status", "ativa").limit(2)
            .get(com.google.firebase.firestore.Source.SERVER).await().documents
        check(docs.size == 1) { "Não foi encontrada uma única reserva ativa para cancelar." }
        cancelar(context, docs.single().id, docs.single().getString("vagaId") ?: error("Vaga inválida."))
    }

    suspend fun cancelar(context: Context, reservaId: String, vagaIdEsperada: String) {
        check(mutex.tryLock()) { "Já existe um cancelamento em andamento." }
        try {
            val usuario = FirebaseAuth.getInstance().currentUser
                ?: error("Usuário não autenticado.")
            val token = usuario.getIdToken(false).await().token
                ?: error("Não foi possível autenticar o cancelamento.")
            val resposta = RetrofitClient.valletApiService.cancelarReserva(reservaId, "Bearer $token", vagaIdEsperada)
            if (!resposta.isSuccessful) {
                val detalhe = resposta.errorBody()?.string()
                val mensagem = runCatching { JSONObject(detalhe.orEmpty()).getString("detail") }
                    .getOrDefault("Não foi possível cancelar. Tente novamente.")
                // 404 do roteador é diferente de "Reserva não encontrada" da operação.
                if (resposta.code() == 404 && mensagem.equals("Not Found", ignoreCase = true)) {
                    error(context.getString(br.edu.fatecpg.valletprojeto.R.string.cancelamento_indisponivel))
                }
                error(mensagem)
            }
            val dados = JSONObject(resposta.body()?.string() ?: error("Resposta de cancelamento inválida."))
            check(dados.getString("reservaId") == reservaId && dados.getString("status") == "cancelada")
            val vagaId = dados.getString("vagaId")
            // Somente após resposta confirmada da gravação.
            runCatching {
                val app = context.applicationContext
                WorkManager.getInstance(app).cancelAllWorkByTag("reserva_$reservaId")
                val intent = Intent(app, ReservaExpiradaReceiver::class.java)
                val pending = PendingIntent.getBroadcast(app, reservaId.hashCode(), intent,
                    PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
                if (pending != null) {
                    (app.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(pending)
                    pending.cancel()
                }
                val manager = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                manager.cancel(vagaId.hashCode())
                manager.cancel(vagaId.hashCode() + 1)
                manager.cancel(vagaId.hashCode() + 2)
            }
        } finally {
            mutex.unlock()
        }
    }
}

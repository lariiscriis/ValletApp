package br.edu.fatecpg.valletprojeto.worker

import android.app.NotificationManager
import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import br.edu.fatecpg.valletprojeto.service.RetrofitClient
import kotlinx.coroutines.CancellationException
import java.io.IOException
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import br.edu.fatecpg.valletprojeto.R
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import android.app.PendingIntent
import android.content.Intent
import br.edu.fatecpg.valletprojeto.ReservaActivity

class ReservaNotificationWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val db = FirebaseFirestore.getInstance()

    override suspend fun doWork(): Result {
        val tipoNotificacao = inputData.getString("tipo")
        val vagaId = inputData.getString("vagaId")
        val reservaId = inputData.getString("reservaId")
        val estacionamentoId = inputData.getString("estacionamentoId")

        Log.d("ReservaNotificationWorker", "Executando worker: tipo=$tipoNotificacao, reservaId=$reservaId, vagaId=$vagaId")

        // O início é um evento histórico. Deve ser enviado mesmo se a reserva
        // já foi finalizada durante uma falha de rede.
        if (tipoNotificacao == "inicio_gestor") {
            return enviarInicioAoGestor(reservaId)
        }

        // VERIFICAR SE A RESERVA AINDA EXISTE E ESTÁ ATIVA
        if (reservaId != null) {
            try {
                val reservaDoc = db.collection("reserva").document(reservaId).get().await()
                if (!reservaDoc.exists() || reservaDoc.getString("status") != "ativa") {
                    Log.d("ReservaNotificationWorker", "Reserva não existe ou não está mais ativa - Cancelando notificação")
                    return Result.success()
                }
            } catch (e: Exception) {
                Log.e("ReservaNotificationWorker", "Erro ao verificar reserva", e)
            }
        }

        return when (tipoNotificacao) {
            "aviso" -> enviarNotificacaoAviso(vagaId, estacionamentoId)
            "expirada" -> enviarNotificacaoExpirada(vagaId)
            else -> Result.failure()
        }
    }

    private suspend fun enviarInicioAoGestor(reservaId: String?): Result {
        if (reservaId.isNullOrBlank()) return Result.failure()
        if (runAttemptCount >= 8) {
            Log.e("ReservaNotificationWorker", "Limite de tentativas para notificar início: $reservaId")
            return Result.failure()
        }
        val usuario = FirebaseAuth.getInstance().currentUser ?: return Result.retry()
        if (usuario.uid != inputData.getString("usuarioId")) {
            Log.w("ReservaNotificationWorker", "Conta alterada; evento não será enviado por outro usuário")
            return Result.failure()
        }
        return try {
            val token = usuario.getIdToken(runAttemptCount > 0).await().token
                ?: return Result.retry()
            val resposta = RetrofitClient.valletApiService.notificarInicioReserva(
                reservaId, "Bearer $token"
            )
            try {
                when {
                    resposta.isSuccessful -> {
                        Log.i("ReservaNotificationWorker", "Início registrado na central do gestor: $reservaId")
                        Result.success()
                    }
                    resposta.code() in listOf(401, 408, 429) || resposta.code() >= 500 -> {
                        Log.w("ReservaNotificationWorker", "Falha temporária HTTP ${resposta.code()} para início $reservaId")
                        Result.retry()
                    }
                    else -> {
                        Log.e("ReservaNotificationWorker", "Início não registrado: HTTP ${resposta.code()}. Confira API, vínculo e permissão")
                        Result.failure()
                    }
                }
            } finally {
                resposta.body()?.close()
                resposta.errorBody()?.close()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            Log.w("ReservaNotificationWorker", "API indisponível; nova tentativa de notificar início", e)
            Result.retry()
        } catch (e: Exception) {
            Log.e("ReservaNotificationWorker", "Falha na autenticação ou configuração da API para notificar início", e)
            Result.retry()
        }
    }

    private fun enviarNotificacaoAviso(
        vagaId: String?,
        estacionamentoId: String?
    ): Result {
        if (vagaId.isNullOrBlank() || estacionamentoId.isNullOrBlank()) {
            return Result.failure()
        }

        NotificationUtils.createNotificationChannel(applicationContext)

        val intent = Intent(applicationContext, ReservaActivity::class.java).apply {
            putExtra("vagaId", vagaId)
            putExtra("estacionamentoId", estacionamentoId)
        }

        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            vagaId.hashCode() + 1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificationManager = applicationContext
            .getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val notification = NotificationCompat.Builder(
            applicationContext,
            NotificationConstants.RESERVA_CHANNEL_ID
        )
            .setSmallIcon(R.drawable.ic_parking)
            .setContentTitle("Sua reserva está acabando!")
            .setContentText("Faltam 10 minutos. Toque para abrir a reserva e renovar.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(vagaId.hashCode() + 1, notification)

        Log.d(
            "ReservaNotificationWorker",
            "Notificação de aviso enviada para vaga: $vagaId"
        )

        return Result.success()
    }

    private suspend fun enviarNotificacaoExpirada(vagaId: String?): Result {
        if (vagaId == null) return Result.failure()

        // Verifica no Firebase se a reserva realmente expirou
        val reservas = db.collection("reserva")
            .whereEqualTo("vagaId", vagaId)
            .whereEqualTo("status", "ativa")
            .get()
            .await()

        if (!reservas.isEmpty) {
            val reserva = reservas.documents.first()
            val fimReserva = reserva.getTimestamp("fimReserva")?.toDate()

            if (fimReserva != null && fimReserva.time <= System.currentTimeMillis()) {
                NotificationUtils.createNotificationChannel(applicationContext)
                val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

                val notification = NotificationCompat.Builder(applicationContext, NotificationConstants.RESERVA_CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_parking)
                    .setContentTitle("Reserva Expirada!")
                    .setContentText("Sua reserva na vaga $vagaId expirou.")
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setAutoCancel(true)
                    .build()

                notificationManager.notify(vagaId.hashCode() + 2, notification)
                Log.d("ReservaNotificationWorker", "Notificação de expiração enviada para vaga: $vagaId")
            }
        }

        return Result.success()
    }
}


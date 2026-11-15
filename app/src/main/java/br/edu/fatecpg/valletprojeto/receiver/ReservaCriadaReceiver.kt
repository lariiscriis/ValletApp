package br.edu.fatecpg.valletprojeto.receiver

import android.app.NotificationManager
import android.Manifest
import android.os.Build
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationManagerCompat
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import br.edu.fatecpg.valletprojeto.R
import br.edu.fatecpg.valletprojeto.ReservaActivity
import br.edu.fatecpg.valletprojeto.worker.NotificationConstants
import br.edu.fatecpg.valletprojeto.worker.NotificationUtils

class ReservaCriadaReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED) {
            Log.w("ReservaCriadaReceiver", "Confirmação não exibida: permita notificações nas configurações do app")
            return
        }
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            Log.w("ReservaCriadaReceiver", "Notificações do app desativadas pelo usuário")
            return
        }
        NotificationUtils.createNotificationChannel(context)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val vagaId = intent.getStringExtra("vagaId")
        val numeroVaga = intent.getStringExtra("numeroVaga") ?: vagaId
        val estacionamentoNome = intent.getStringExtra("estacionamentoNome")
        val estacionamentoId = intent.getStringExtra("estacionamentoId")

        if (vagaId == null || estacionamentoNome == null) {
            Log.e("ReservaCriadaReceiver", "Dados incompletos: vagaId=$vagaId, estacionamentoNome=$estacionamentoNome")
            return
        }

        val activityIntent = Intent(context, ReservaActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("vagaId", vagaId)
            putExtra("estacionamentoId", estacionamentoId)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            vagaId.hashCode(),
            activityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificationId = vagaId.hashCode()

        val notification = NotificationCompat.Builder(context, NotificationConstants.RESERVA_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_parking)
            .setContentTitle("Reserva Confirmada! ✅")
            .setContentText("Vaga $numeroVaga no $estacionamentoNome reservada com sucesso!")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("Sua reserva na vaga $numeroVaga do estacionamento $estacionamentoNome foi iniciada com sucesso. Você receberá avisos antes do término."))
            .build()

        try {
            notificationManager.notify(notificationId, notification)
        } catch (e: SecurityException) {
            Log.w("ReservaCriadaReceiver", "Permissão de notificação revogada antes da exibição", e)
            return
        }
        Log.d("ReservaCriadaReceiver", "Notificação de reserva criada exibida para vaga: $vagaId")
    }
}


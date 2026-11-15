package br.edu.fatecpg.valletprojeto.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import br.edu.fatecpg.valletprojeto.worker.CheckReservaWorker
import java.util.concurrent.TimeUnit

class ReservaExpiradaReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.d("ReservaExpiredReceiver", "Receiver acionado - FINALIZANDO RESERVA")

        val reservaId = intent.getStringExtra("reservaId")
        val vagaId = intent.getStringExtra("vagaId")

        if (reservaId == null || vagaId == null) {
            Log.e("ReservaExpiredReceiver", "Dados incompletos")
            return
        }

        // 🔥 AGORA USA O WORKER PARA FINALIZAR A RESERVA
        val inputData = Data.Builder()
            .putString("reservaId", reservaId)
            .putString("vagaId", vagaId)
            .build()

        val workRequest = OneTimeWorkRequestBuilder<CheckReservaWorker>()
            .setInputData(inputData)
            .setInitialDelay(0, TimeUnit.SECONDS) // Executa imediatamente
            .build()

        WorkManager.getInstance(context).enqueue(workRequest)
        Log.d("ReservaExpiredReceiver", "Worker enfileirado para finalizar reserva: $reservaId")
    }
}
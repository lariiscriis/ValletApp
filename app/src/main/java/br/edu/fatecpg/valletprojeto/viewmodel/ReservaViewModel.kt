package br.edu.fatecpg.valletprojeto.viewmodel

import android.app.AlarmManager
import android.app.Application
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.CountDownTimer
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import br.edu.fatecpg.valletprojeto.model.Reserva
import br.edu.fatecpg.valletprojeto.model.Vaga
import br.edu.fatecpg.valletprojeto.model.Veiculo
import br.edu.fatecpg.valletprojeto.receiver.ReservaAvisoReceiver
import br.edu.fatecpg.valletprojeto.receiver.ReservaCriadaReceiver
import br.edu.fatecpg.valletprojeto.receiver.ReservaExpiredReceiver
import br.edu.fatecpg.valletprojeto.worker.CheckReservaWorker
import br.edu.fatecpg.valletprojeto.worker.ReservaNotificationWorker
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.*
import java.util.concurrent.TimeUnit

sealed class ReservaUIState {
    object Initial : ReservaUIState()
    data class Idle(val vaga: Vaga, val veiculo: Veiculo) : ReservaUIState()
    object Loading : ReservaUIState()
    data class Active(val reserva: Reserva, val vaga: Vaga, val veiculo: Veiculo) : ReservaUIState()
    data class Finished(val message: String) : ReservaUIState()
    data class Error(val message: String) : ReservaUIState()
}

class ReservaViewModel(application: Application) : AndroidViewModel(application) {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val _uiState = MutableLiveData<ReservaUIState>(ReservaUIState.Initial)
    val uiState: LiveData<ReservaUIState> = _uiState

    private val _tempoRestante = MutableLiveData<String>()
    val tempoRestante: LiveData<String> = _tempoRestante

    private val _temReservaAtiva = MutableLiveData<Boolean>()
    val temReservaAtiva: LiveData<Boolean> = _temReservaAtiva

    private var timer: CountDownTimer? = null
    private var idReservaAtiva: String? = null
    private var idVagaAtiva: String? = null

    // 🔥 NOVO: Controlar IDs de notificações agendadas
    private var currentReservaId: String? = null
    private var currentAvisoPendingIntent: PendingIntent? = null
    private var currentExpiredPendingIntent: PendingIntent? = null

    fun verificarReservaAtiva() {
        viewModelScope.launch {
            try {
                val reservaAtiva = buscarQualquerReservaAtiva()
                _temReservaAtiva.value = reservaAtiva != null

                if (reservaAtiva != null) {
                    carregarDadosReservaAtiva(reservaAtiva)
                }
            } catch (e: Exception) {
                Log.e("ReservaViewModel", "Erro ao verificar reserva ativa: ${e.message}")
            }
        }
    }

    fun verificarEFinalizarReservasExpiradas() {
        viewModelScope.launch {
            try {
                val userId = auth.currentUser?.uid ?: return@launch
                val agora = Timestamp.now()

                val reservasExpiradas = db.collection("reserva")
                    .whereEqualTo("usuarioId", userId)
                    .whereEqualTo("status", "ativa")
                    .whereLessThan("fimReserva", agora)
                    .get()
                    .await()

                for (document in reservasExpiradas.documents) {
                    val reserva = document.toObject(Reserva::class.java)
                    if (reserva != null) {
                        Log.d("ReservaViewModel", "🔍 Encontrada reserva expirada: ${reserva.id}")
                        finalizarReservaExpirada(reserva.id, reserva.vagaId)
                    }
                }
            } catch (e: Exception) {
                Log.e("ReservaViewModel", "Erro ao verificar reservas expiradas", e)
            }
        }
    }

    private fun finalizarReservaExpirada(reservaId: String, vagaId: String) {
        val inputData = workDataOf(
            "reservaId" to reservaId,
            "vagaId" to vagaId,
            "tipo" to "cleanup_expirada"
        )

        val workRequest = OneTimeWorkRequestBuilder<CheckReservaWorker>()
            .setInputData(inputData)
            .setInitialDelay(0, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(getApplication()).enqueue(workRequest)
    }

    private suspend fun buscarQualquerReservaAtiva(): Reserva? {
        val userId = auth.currentUser?.uid ?: return null
        val snapshot = db.collection("reserva")
            .whereEqualTo("usuarioId", userId)
            .whereEqualTo("status", "ativa")
            .limit(1)
            .get()
            .await()

        if (snapshot.isEmpty) return null

        val doc = snapshot.documents.first()
        return doc.toObject(Reserva::class.java)?.apply {
            id = doc.id
        }
    }

    private suspend fun carregarDadosReservaAtiva(reserva: Reserva) {
        try {
            val vagaDoc = db.collection("vaga").document(reserva.vagaId).get().await()
            val vaga = vagaDoc.toObject(Vaga::class.java)?.apply { id = vagaDoc.id }

            val veiculo = buscarVeiculoPadrao()

            if (vaga != null && veiculo != null) {
                _uiState.value = ReservaUIState.Active(reserva, vaga, veiculo)
                iniciarTimer(reserva.fimReserva!!.toDate())
                idVagaAtiva = reserva.vagaId
                currentReservaId = reserva.id
            }
        } catch (e: Exception) {
            Log.e("ReservaViewModel", "Erro ao carregar dados da reserva ativa: ${e.message}")
        }
    }

    fun carregarDadosIniciais(vagaId: String) {
        viewModelScope.launch {
            _uiState.value = ReservaUIState.Loading
            val reservaAtivaGlobal = buscarQualquerReservaAtiva()

            if (reservaAtivaGlobal != null && reservaAtivaGlobal.vagaId != vagaId) {
                // 🔥 BUSCAR O NÚMERO DA VAGA ATIVA PARA MOSTRAR NO ERRO
                try {
                    val vagaAtivaDoc = db.collection("vaga").document(reservaAtivaGlobal.vagaId).get().await()
                    val numeroVagaAtiva = vagaAtivaDoc.getString("numero") ?: reservaAtivaGlobal.vagaId

                    _uiState.value = ReservaUIState.Error("Você já tem uma reserva ativa na vaga $numeroVagaAtiva. Finalize-a antes de reservar outra.")
                } catch (e: Exception) {
                    _uiState.value = ReservaUIState.Error("Você já tem uma reserva ativa em outra vaga. Finalize-a antes de reservar outra.")
                }
                return@launch
            }


            try {
                val vagaDoc = db.collection("vaga").document(vagaId).get().await()
                val vaga = vagaDoc.toObject(Vaga::class.java)?.apply {
                    id = vagaDoc.id
                }

                val veiculoAsync = async { buscarVeiculoPadrao() }
                val reservaAtivaAsync = async { buscarReservaAtiva(vagaId) }

                val veiculo = veiculoAsync.await()
                val reservaAtiva = reservaAtivaAsync.await()

                if (vaga != null && veiculo != null) {
                    if (reservaAtiva != null) {
                        _uiState.value = ReservaUIState.Active(reservaAtiva, vaga, veiculo)
                        iniciarTimer(reservaAtiva.fimReserva!!.toDate())
                        currentReservaId = reservaAtiva.id
                    } else {
                        _uiState.value = ReservaUIState.Idle(vaga, veiculo)
                    }
                } else {
                    _uiState.value = ReservaUIState.Error("Vaga ou veículo padrão não encontrado.")
                }
            } catch (e: Exception) {
                _uiState.value = ReservaUIState.Error("Erro ao carregar dados: ${e.message}")
            }
        }
    }

    // 🔥 ATUALIZADO: Cancelar notificações antigas antes de agendar novas
    fun agendarNotificacoes(context: Context, reserva: Reserva) {
        // 🔥 CANCELAR NOTIFICAÇÕES ANTERIORES PRIMEIRO
        cancelarNotificacoesAgendadas(context)

        val fimReservaMillis = reserva.fimReserva?.toDate()?.time ?: return
        Log.d("Notificacoes", "Agendando notificações para reserva: ${reserva.id}")

        currentReservaId = reserva.id

        // 🔥 AGENDAR AVISO DE 10 MINUTOS
        val avisoMillis = fimReservaMillis - TimeUnit.MINUTES.toMillis(10)
        if (avisoMillis > System.currentTimeMillis()) {
            val avisoDelay = avisoMillis - System.currentTimeMillis()

            val avisoData = workDataOf(
                "tipo" to "aviso",
                "reservaId" to reserva.id,
                "vagaId" to reserva.vagaId,
                "estacionamentoId" to reserva.estacionamentoId
            )

            val avisoRequest = OneTimeWorkRequestBuilder<ReservaNotificationWorker>()
                .setInitialDelay(avisoDelay, TimeUnit.MILLISECONDS)
                .setInputData(avisoData)
                .addTag("reserva_${reserva.id}") // 🔥 ADICIONAR TAG TAMBÉM AQUI
                .build()

            WorkManager.getInstance(context).enqueue(avisoRequest)
            Log.d("Notificacoes", "Aviso agendado para: ${Date(avisoMillis)} (${avisoDelay/1000}s)")
        }

        // 🔥 AGENDAR FINALIZAÇÃO AUTOMÁTICA
        val expiraIntent = Intent(context, ReservaExpiredReceiver::class.java).apply {
            putExtra("reservaId", reserva.id)
            putExtra("vagaId", reserva.vagaId)
        }

        // 🔥 CORREÇÃO: Criar PendingIntent e armazenar em variável local primeiro
        val expiredPendingIntent = PendingIntent.getBroadcast(
            context,
            reserva.id.hashCode(),
            expiraIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 🔥 AGORA SIM ATRIBUIR À PROPRIEDADE
        currentExpiredPendingIntent = expiredPendingIntent

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        fimReservaMillis,
                        expiredPendingIntent // 🔥 USAR VARIÁVEL LOCAL
                    )
                }
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    fimReservaMillis,
                    expiredPendingIntent // 🔥 USAR VARIÁVEL LOCAL
                )
            }
            Log.d("Notificacoes", "✅ Finalização automática agendada para: ${Date(fimReservaMillis)}")
        } catch (e: SecurityException) {
            Log.e("Notificacoes", "❌ Permissão negada para agendar alarme exato", e)
            agendarFinalizacaoWorkManager(context, reserva, fimReservaMillis)
        }
    }

    // 🔥 NOVO: Cancelar todas as notificações agendadas
    private fun cancelarNotificacoesAgendadas(context: Context) {
        Log.d("Notificacoes", "🔄 Cancelando notificações agendadas anteriores")

        // 🔥 CORREÇÃO: Usar variáveis locais para evitar problemas de concorrência
        val avisoPendingIntent = currentAvisoPendingIntent
        val expiredPendingIntent = currentExpiredPendingIntent
        val reservaId = currentReservaId

        // Cancelar PendingIntents do AlarmManager
        avisoPendingIntent?.let { pendingIntent ->
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.cancel(pendingIntent)
            Log.d("Notificacoes", "✅ PendingIntent de aviso cancelado")
        }

        expiredPendingIntent?.let { pendingIntent ->
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.cancel(pendingIntent)
            Log.d("Notificacoes", "✅ PendingIntent de expiração cancelado")
        }

        // Cancelar Workers do WorkManager
        if (reservaId != null) {
            WorkManager.getInstance(context).cancelAllWorkByTag("reserva_$reservaId")
            Log.d("Notificacoes", "✅ Workers cancelados para reserva: $reservaId")
        }

        // Limpar referências
        currentAvisoPendingIntent = null
        currentExpiredPendingIntent = null
    }

    private fun agendarFinalizacaoWorkManager(context: Context, reserva: Reserva, fimReservaMillis: Long) {
        val delay = fimReservaMillis - System.currentTimeMillis()

        if (delay > 0) {
            val finalizacaoData = workDataOf(
                "reservaId" to reserva.id,
                "vagaId" to reserva.vagaId,
                "tipo" to "finalizacao_automatica"
            )

            val finalizacaoRequest = OneTimeWorkRequestBuilder<CheckReservaWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setInputData(finalizacaoData)
                .addTag("reserva_${reserva.id}") // 🔥 ADICIONAR TAG PARA FACILITAR CANCELAMENTO
                .build()

            WorkManager.getInstance(context).enqueue(finalizacaoRequest)
            Log.d("Notificacoes", "⚠️  Fallback: Finalização agendada via WorkManager")
        }
    }

    fun criarReserva(vagaId: String, estacionamentoId: String, estacionamentoNome: String, horas: Int) {
        viewModelScope.launch {
            val reservaAtiva = buscarQualquerReservaAtiva()
            if (reservaAtiva != null) {
                _uiState.value = ReservaUIState.Error("Você já tem uma reserva ativa na vaga ${reservaAtiva.vagaId}. Finalize-a antes de criar outra.")
                return@launch
            }

            _uiState.value = ReservaUIState.Loading
            val userId = auth.currentUser?.uid ?: return@launch

            try {
                val agora = Timestamp.now()
                val fimReserva = Timestamp(Date(agora.toDate().time + TimeUnit.HOURS.toMillis(horas.toLong())))

                val novaReserva = Reserva(
                    usuarioId = userId,
                    vagaId = vagaId,
                    estacionamentoId = estacionamentoId,
                    estacionamentoNome = estacionamentoNome,
                    status = "ativa",
                    inicioReserva = agora,
                    fimReserva = fimReserva
                )

                val docRef = db.collection("reserva").add(novaReserva).await()
                idReservaAtiva = docRef.id
                idVagaAtiva = vagaId

                db.collection("vaga").document(vagaId).update("disponivel", false).await()
                val reservaCriada = novaReserva.apply { id = docRef.id }

                enviarNotificacaoReservaCriada(getApplication(), vagaId, estacionamentoNome)
                agendarNotificacoes(getApplication(), reservaCriada)

                _temReservaAtiva.value = true
                carregarDadosIniciais(vagaId)

            } catch (e: Exception) {
                _uiState.value = ReservaUIState.Error("Erro ao criar reserva: ${e.message}")
            }
        }
    }
    private fun enviarNotificacaoReservaCriada(context: Context, vagaId: String, estacionamentoNome: String) {
        viewModelScope.launch {
            try {
                // 🔥 BUSCAR O NÚMERO DA VAGA
                val vagaDoc = db.collection("vaga").document(vagaId).get().await()
                val numeroVaga = vagaDoc.getString("numero") ?: vagaId

                val intent = Intent(context, ReservaCriadaReceiver::class.java).apply {
                    putExtra("vagaId", vagaId)
                    putExtra("numeroVaga", numeroVaga) // 🔥 ENVIAR O NÚMERO
                    putExtra("estacionamentoNome", estacionamentoNome)
                }
                context.sendBroadcast(intent)
                Log.d("Notificacoes", "Notificação de reserva criada enviada para vaga: $numeroVaga")
            } catch (e: Exception) {
                Log.e("Notificacoes", "Erro ao buscar número da vaga: ${e.message}")
                // Fallback com ID
                val intent = Intent(context, ReservaCriadaReceiver::class.java).apply {
                    putExtra("vagaId", vagaId)
                    putExtra("numeroVaga", vagaId)
                    putExtra("estacionamentoNome", estacionamentoNome)
                }
                context.sendBroadcast(intent)
            }
        }
    }
    // 🔥 ATUALIZADO: Renovar reserva com cancelamento de notificações antigas
    fun renovarReserva(reserva: Reserva) {
        viewModelScope.launch {
            val idReserva = reserva.id
            val idVaga = reserva.vagaId

            Log.d("Renovacao", "Iniciando renovação para reservaId: $idReserva, vagaId: $idVaga")

            _uiState.value = ReservaUIState.Loading
            try {
                // 🔥 CANCELAR NOTIFICAÇÕES ANTIGAS ANTES DE RENOVAR
                cancelarNotificacoesAgendadas(getApplication())

                val proximaReserva = db.collection("reserva")
                    .whereEqualTo("vagaId", idVaga)
                    .whereGreaterThan("inicioReserva", reserva.fimReserva!!)
                    .orderBy("inicioReserva")
                    .limit(1)
                    .get().await()

                if (!proximaReserva.isEmpty) {
                    Log.w("Renovacao", "Falha: Já existe uma reserva futura para a vaga $idVaga.")
                    _uiState.value = ReservaUIState.Error("Não é possível renovar. Vaga já reservada para o próximo horário.")
                    carregarDadosIniciais(idVaga)
                    return@launch
                }

                val novoFim = Timestamp(Date(reserva.fimReserva.toDate().time + TimeUnit.HOURS.toMillis(1)))

                Log.d("Renovacao", "Atualizando reserva $idReserva com novo fim: $novoFim")
                db.collection("reserva").document(idReserva).update("fimReserva", novoFim).await()

                // 🔥 REAGENDAR NOTIFICAÇÕES COM NOVO HORÁRIO
                val reservaAtualizada = reserva.copy(fimReserva = novoFim)
                agendarNotificacoes(getApplication(), reservaAtualizada)

                Log.d("Renovacao", "Renovação bem-sucedida. Recarregando dados...")
                carregarDadosIniciais(idVaga)

            } catch (e: Exception) {
                Log.e("Renovacao", "ERRO CRÍTICO durante a renovação: ${e.message}", e)
                _uiState.value = ReservaUIState.Error("Erro ao renovar: ${e.message}")
            }
        }
    }

    fun cancelarReserva(vaga: Vaga) {
        viewModelScope.launch {
            _uiState.value = ReservaUIState.Loading
            val userId = auth.currentUser?.uid

            if (userId == null) {
                _uiState.value = ReservaUIState.Error("Usuário não autenticado.")
                return@launch
            }

            try {
                // 🔥 CANCELAR NOTIFICAÇÕES ANTES DE CANCELAR A RESERVA
                cancelarNotificacoesAgendadas(getApplication())

                val reservaAtivaSnapshot = db.collection("reserva")
                    .whereEqualTo("usuarioId", userId)
                    .whereEqualTo("vagaId", vaga.id)
                    .whereEqualTo("status", "ativa")
                    .limit(1)
                    .get()
                    .await()

                val reservaDoc = reservaAtivaSnapshot.documents.firstOrNull()

                if (reservaDoc == null) {
                    _uiState.value = ReservaUIState.Error("Nenhuma reserva ativa encontrada para cancelar.")
                    return@launch
                }

                val batch = db.batch()
                batch.update(reservaDoc.reference, "status", "cancelada")
                val vagaRef = db.collection("vaga").document(vaga.id)
                batch.update(vagaRef, "disponivel", true)
                batch.commit().await()

                timer?.cancel()

                _temReservaAtiva.value = false
                idVagaAtiva = null
                currentReservaId = null

                _uiState.value = ReservaUIState.Finished("Reserva cancelada com sucesso.")

            } catch (e: Exception) {
                _uiState.value = ReservaUIState.Error("Erro ao cancelar: ${e.message}")
            }
        }
    }

    fun finalizarReservaAposPagamento(
        vaga: Vaga,
        onConcluido: () -> Unit
    ) {
        viewModelScope.launch {
            val userId = auth.currentUser?.uid

            if (userId == null) {
                Log.e("ReservaViewModel", "Usuário não autenticado.")
                return@launch
            }

            try {
                cancelarNotificacoesAgendadas(getApplication())

                val reservaAtivaSnapshot = db.collection("reserva")
                    .whereEqualTo("usuarioId", userId)
                    .whereEqualTo("vagaId", vaga.id)
                    .whereEqualTo("status", "ativa")
                    .limit(1)
                    .get()
                    .await()

                val reservaDoc = reservaAtivaSnapshot.documents.firstOrNull()

                if (reservaDoc == null) {
                    Log.e("ReservaViewModel", "Nenhuma reserva ativa encontrada.")
                    return@launch
                }

                val batch = db.batch()

                // Finaliza a reserva
                batch.update(
                    reservaDoc.reference,
                    "status",
                    "cancelada"
                )

                // Libera a vaga
                val vagaRef = db.collection("vaga").document(vaga.id)

                batch.update(
                    vagaRef,
                    "disponivel",
                    true
                )

                // Salva tudo no Firebase
                batch.commit().await()

                // Para o cronômetro
                timer?.cancel()

                _temReservaAtiva.value = false
                idVagaAtiva = null
                currentReservaId = null

                Log.d(
                    "ReservaViewModel",
                    "Reserva finalizada após pagamento."
                )

                // Só avisa que terminou DEPOIS do Firebase confirmar
                onConcluido()

            } catch (e: Exception) {
                Log.e(
                    "ReservaViewModel",
                    "Erro ao finalizar reserva após pagamento: ${e.message}",
                    e
                )
            }
        }
    }

    private suspend fun buscarVeiculoPadrao(): Veiculo? {
        val userId = auth.currentUser?.uid ?: return null
        return db.collection("veiculo")
            .whereEqualTo("usuarioId", userId)
            .whereEqualTo("padrao", true)
            .limit(1).get().await()
            .toObjects(Veiculo::class.java).firstOrNull()
    }

    private suspend fun buscarReservaAtiva(vagaId: String): Reserva? {
        val userId = auth.currentUser?.uid ?: return null
        val snapshot = db.collection("reserva")
            .whereEqualTo("usuarioId", userId)
            .whereEqualTo("vagaId", vagaId)
            .whereEqualTo("status", "ativa")
            .limit(1)
            .get()
            .await()

        if (snapshot.isEmpty) return null

        val doc = snapshot.documents.first()
        return doc.toObject(Reserva::class.java)?.apply {
            id = doc.id
        }
    }

    private fun iniciarTimer(dataFim: Date) {
        timer?.cancel()
        val tempoRestanteMs = dataFim.time - System.currentTimeMillis()
        if (tempoRestanteMs <= 0) {
            _tempoRestante.postValue("00:00:00")
            return
        }
        timer = object : CountDownTimer(tempoRestanteMs, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val horas = TimeUnit.MILLISECONDS.toHours(millisUntilFinished)
                val minutos = TimeUnit.MILLISECONDS.toMinutes(millisUntilFinished) % 60
                val segundos = TimeUnit.MILLISECONDS.toSeconds(millisUntilFinished) % 60
                _tempoRestante.postValue(String.format("%02d:%02d:%02d", horas, minutos, segundos))
            }
            override fun onFinish() {
                _tempoRestante.postValue("Expirada")
            }
        }.start()
    }

    override fun onCleared() {
        super.onCleared()
        timer?.cancel()
    }
}

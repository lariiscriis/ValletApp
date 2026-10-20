package br.edu.fatecpg.valletprojeto.service

import br.edu.fatecpg.valletprojeto.model.Reserva
import br.edu.fatecpg.valletprojeto.model.Vaga
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit

/**
 * Serviço para gerenciar a lógica de estacionamento e reservas.
 *
 * Este serviço simula as interações com o Firestore e a lógica de notificação/criação de reserva.
 * Em um ambiente real, as funções de notificação e interação com o usuário (perguntar/confirmar)
 * seriam implementadas por meio de callbacks ou eventos assíncronos (como LiveData ou Flow no Android).
 */
class EstacionamentoService(private val db: FirebaseFirestore) {

    companion object {
        // Tempo mínimo de reserva em milissegundos (1 hora)
        private val MIN_RESERVATION_TIME_MS = TimeUnit.HOURS.toMillis(1)
    }

    /**
     * Interface para lidar com as ações que exigem interação externa (UI ou Notificação).
     */
    interface ParkingActionListener {
        /**
         * Chamado quando o usuário estaciona em uma vaga sem reserva válida.
         * @param vaga A vaga onde o usuário estacionou.
         * @param usuarioId O ID do usuário.
         * @param onConfirm Callback a ser chamado se o usuário confirmar a criação da reserva.
         * @param onCancel Callback a ser chamado se o usuário cancelar a criação da reserva.
         */
        fun askToCreateReservation(
            vaga: Vaga,
            usuarioId: String,
            onConfirm: () -> Unit,
            onCancel: () -> Unit
        )

        /**
         * Chamado para enviar uma notificação ao administrador.
         * @param vaga A vaga envolvida.
         * @param motivo O motivo da notificação (e.g., "Estacionou sem reserva", "Não-usuário em vaga reservada").
         * @param detalhes Detalhes adicionais, como o ID do usuário, se aplicável.
         */
        fun notifyAdmin(vaga: Vaga, motivo: String, detalhes: Map<String, Any?>)
    }

    /**
     * Lógica principal para lidar com o evento de um usuário estacionar em uma vaga.
     *
     * @param vagaId O ID da vaga onde o usuário estacionou.
     * @param usuarioId O ID do usuário que estacionou.
     * @param listener O objeto que implementa as ações de interação e notificação.
     */
    suspend fun handleUserParking(
        vagaId: String,
        usuarioId: String,
        listener: ParkingActionListener
    ) {
        val vaga = getVaga(vagaId) ?: run {
            // Caso a vaga não exista, notificar um erro crítico.
            listener.notifyAdmin(
                Vaga(id = vagaId),
                "Vaga Inexistente",
                mapOf("usuarioId" to usuarioId)
            )
            return
        }

        // 1. Verificar se o usuário tem uma reserva válida para esta vaga
        val reservaValida = getActiveReservationForVaga(usuarioId, vagaId)

        if (reservaValida != null) {
            // Cenário 1: Usuário tem reserva válida para a vaga.
            // Ação: Nenhuma ação necessária, o estacionamento está correto.
            println("Usuário $usuarioId estacionou corretamente na vaga $vagaId com reserva ${reservaValida.id}.")
            return
        }

        // 2. Verificar se a vaga está reservada por outro usuário
        val reservaDeOutro = getActiveReservationForVaga(vagaId = vagaId, excludeUserId = usuarioId)

        if (reservaDeOutro != null) {
            // Cenário 2: Usuário estacionou em vaga reservada por outro.
            // Ação: Notificar o administrador.
            listener.notifyAdmin(
                vaga,
                "Estacionamento em Vaga Reservada por Outro Motorista",
                mapOf(
                    "usuarioEstacionouId" to usuarioId,
                    "usuarioReservaId" to reservaDeOutro.usuarioId,
                    "reservaId" to reservaDeOutro.id
                )
            )
            return
        }

        // 3. Usuário estacionou em vaga sem reserva (própria ou de outro).
        // Verificar se ele tem *qualquer* reserva ativa em outro lugar.
        val qualquerReservaAtiva = getAnyActiveReservation(usuarioId)

        if (qualquerReservaAtiva != null) {
            // Cenário 3a: Usuário tem reserva ativa em outro lugar.
            // Ação: Notificar o administrador sobre o estacionamento incorreto.
            listener.notifyAdmin(
                vaga,
                "Estacionamento em Vaga Incorreta",
                mapOf(
                    "usuarioId" to usuarioId,
                    "vagaReservadaId" to qualquerReservaAtiva.vagaId,
                    "reservaId" to qualquerReservaAtiva.id
                )
            )
            return
        }

        // Cenário 3b: Usuário não tem nenhuma reserva ativa.
        // Ação: Perguntar se deseja criar uma reserva.
        listener.askToCreateReservation(
            vaga,
            usuarioId,
            onConfirm = {
                createReservation(vaga, usuarioId)
            },
            onCancel = {
                // Se não confirmar, notificar o administrador.
                listener.notifyAdmin(
                    vaga,
                    "Estacionou sem Reserva e Recusou Criar",
                    mapOf("usuarioId" to usuarioId)
                )
            }
        )
    }

    /**
     * Lógica para lidar com o evento de um não-usuário estacionar em uma vaga reservada.
     *
     * Este cenário é mais difícil de detectar apenas com o ID do usuário, pois o "não-usuário"
     * não terá um ID de usuário no sistema. Assumimos que a detecção de "não-usuário"
     * é feita por um sistema externo (e.g., câmera/sensor) que identifica a placa e
     * não a associa a um `usuarioId` válido, mas consegue identificar a vaga ocupada.
     *
     * @param vagaId O ID da vaga ocupada.
     * @param placaVeiculo A placa do veículo (ou outro identificador de não-usuário).
     */
    suspend fun handleNonUserParking(vagaId: String, placaVeiculo: String) {
        val vaga = getVaga(vagaId) ?: return // Ignorar se a vaga não existir

        // 1. Verificar se a vaga está reservada por um usuário
        val reservaAtiva = getActiveReservationForVaga(vagaId = vagaId)

        if (reservaAtiva != null) {
            // Cenário: Não-usuário estacionou em vaga reservada.
            // Ação: Notificar o administrador.
            val listener = object : ParkingActionListener {
                override fun askToCreateReservation(vaga: Vaga, usuarioId: String, onConfirm: () -> Unit, onCancel: () -> Unit) {}
                override fun notifyAdmin(vaga: Vaga, motivo: String, detalhes: Map<String, Any?>) {
                    println("Notificação para Admin: $motivo na vaga ${vaga.numero}")
                }
            }

            listener.notifyAdmin(
                vaga,
                "Não-usuário em Vaga Reservada",
                mapOf(
                    "placaVeiculo" to placaVeiculo,
                    "usuarioReservaId" to reservaAtiva.usuarioId,
                    "reservaId" to reservaAtiva.id
                )
            )
        }
        // Se a vaga não estiver reservada, o não-usuário está em uma vaga livre.
        // Ação: Depende da política do estacionamento (pode ser ignorado ou notificado).
        // Por enquanto, focamos apenas no cenário solicitado: "Se uma pessoa que não é usuária do aplicativo estacionar em uma vaga reservada".
    }

    // --- Funções de Interação com o Firestore (Simuladas/Simplificadas) ---

    /**
     * Busca uma vaga pelo ID.
     */
    private suspend fun getVaga(vagaId: String): Vaga? {
        return try {
            db.collection("vaga").document(vagaId).get().await()
                .toObject(Vaga::class.java)?.apply { id = vagaId }
        } catch (e: Exception) {
            println("Erro ao buscar vaga $vagaId: ${e.message}")
            null
        }
    }

    /**
     * Busca uma reserva ativa para uma vaga específica, opcionalmente excluindo um usuário.
     */
    private suspend fun getActiveReservationForVaga(
        usuarioId: String? = null,
        vagaId: String,
        excludeUserId: String? = null
    ): Reserva? {
        var query = db.collection("reserva")
            .whereEqualTo("vagaId", vagaId)
            .whereEqualTo("status", "ativa")
            .whereGreaterThan("fimReserva", Timestamp.now()) // A reserva deve estar no futuro ou no presente

        if (usuarioId != null) {
            query = query.whereEqualTo("usuarioId", usuarioId)
        }

        val snapshot = try {
            query.limit(1).get().await()
        } catch (e: Exception) {
            println("Erro ao buscar reserva ativa para vaga $vagaId: ${e.message}")
            return null
        }

        return snapshot.documents.firstOrNull()?.let { doc ->
            val reserva = doc.toObject(Reserva::class.java)?.apply { id = doc.id }
            if (excludeUserId == null || reserva?.usuarioId != excludeUserId) {
                reserva
            } else {
                null
            }
        }
    }

    /**
     * Busca qualquer reserva ativa para um usuário.
     */
    private suspend fun getAnyActiveReservation(usuarioId: String): Reserva? {
        return try {
            db.collection("reserva")
                .whereEqualTo("usuarioId", usuarioId)
                .whereEqualTo("status", "ativa")
                .whereGreaterThan("fimReserva", Timestamp.now())
                .limit(1)
                .get().await()
                .documents.firstOrNull()
                ?.toObject(Reserva::class.java)?.apply { id = this.id }
        } catch (e: Exception) {
            println("Erro ao buscar qualquer reserva ativa para usuário $usuarioId: ${e.message}")
            null
        }
    }

    /**
     * Cria uma nova reserva de 1 hora para a vaga.
     */
    private fun createReservation(vaga: Vaga, usuarioId: String) {
        val inicio = Timestamp.now()
        val fim = Timestamp(inicio.seconds + TimeUnit.HOURS.toSeconds(1), inicio.nanoseconds)

        val novaReserva = Reserva(
            usuarioId = usuarioId,
            vagaId = vaga.id,
            inicioReserva = inicio,
            fimReserva = fim,
            estacionamentoId = vaga.estacionamentoId,
            estacionamentoNome = vaga.numero, // Assumindo que o nome do estacionamento pode ser obtido de outra forma
            status = "ativa"
        )

        db.collection("reserva").add(novaReserva)
            .addOnSuccessListener { docRef ->
                println("✅ Reserva automática criada com sucesso: ${docRef.id} para vaga ${vaga.numero}.")
                // Em um app real, você notificaria o usuário sobre a nova reserva.
            }
            .addOnFailureListener { e ->
                println("❌ Erro ao criar reserva automática: ${e.message}")
                // Em um app real, você notificaria o administrador sobre a falha.
            }
    }
}

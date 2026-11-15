package br.edu.fatecpg.valletprojeto.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.Exclude

data class Reserva(
    @get:Exclude var id: String = "",
    val usuarioId: String = "",
    val vagaId: String = "",
    val inicioReserva: Timestamp? = null,
    val fimReserva: Timestamp? = null,
    val estacionamentoId : String = "",
    val estacionamentoNome: String = "",
    val status: String = "ativa",
    val valorTotal: Double? = null,
    val formaPagamento: String? = null
)

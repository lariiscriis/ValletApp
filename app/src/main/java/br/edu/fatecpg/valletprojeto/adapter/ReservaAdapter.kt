package br.edu.fatecpg.valletprojeto.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import br.edu.fatecpg.valletprojeto.R
import br.edu.fatecpg.valletprojeto.model.Reserva
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class ReservaAdapter : ListAdapter<Reserva, ReservaAdapter.ReservaViewHolder>(ReservaDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReservaViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_reserva, parent, false)
        return ReservaViewHolder(view)
    }

    override fun onBindViewHolder(holder: ReservaViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ReservaViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvVaga: TextView? = itemView.findViewById(R.id.tvVaga)
        private val tvEstacionamentoNome: TextView = itemView.findViewById(R.id.tvEstacionamentoNome)
        private val tvPeriodo: TextView = itemView.findViewById(R.id.tvPeriodo)
        private val tvStatus: TextView = itemView.findViewById(R.id.tvStatus)
        private val ivArrow: ImageView? = itemView.findViewById(R.id.iv_arrow)

        private val sdfData = SimpleDateFormat("dd/MM", Locale.getDefault())
        private val sdfHora = SimpleDateFormat("HH:mm", Locale.getDefault())

        fun bind(reserva: Reserva) {
            ivArrow?.visibility = View.GONE

            tvEstacionamentoNome.text = reserva.estacionamentoNome.ifBlank { "Estacionamento" }

            val vagaExibicao = when {
                reserva.vagaId.startsWith("vaga_") -> "A-${reserva.vagaId.removePrefix("vaga_")}"
                reserva.vagaId.length <= 5 && reserva.vagaId.isNotBlank() -> reserva.vagaId
                else -> reserva.estacionamentoNome.trim().take(1).uppercase(Locale.getDefault()).ifEmpty { "V" }
            }
            tvVaga?.text = vagaExibicao

            val inicio = reserva.inicioReserva?.toDate()
            val fim = reserva.fimReserva?.toDate()

            if (inicio != null && fim != null) {
                val dataStr = sdfData.format(inicio)
                val inicioHoraStr = sdfHora.format(inicio)
                val fimHoraStr = sdfHora.format(fim)
                tvPeriodo.text = "$dataStr • $inicioHoraStr - $fimHoraStr"

                val diffMs = fim.time - inicio.time
                val horas = TimeUnit.MILLISECONDS.toHours(diffMs)
                val mins = TimeUnit.MILLISECONDS.toMinutes(diffMs) % 60
                tvStatus.text = if (mins > 0) "${horas}h ${mins}min" else "${horas}h 0min"
            } else {
                tvPeriodo.text = "—"
                val statusFormatado = reserva.status.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
                tvStatus.text = statusFormatado
            }
        }
    }
}

class ReservaDiffCallback : DiffUtil.ItemCallback<Reserva>() {
    override fun areItemsTheSame(oldItem: Reserva, newItem: Reserva): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: Reserva, newItem: Reserva): Boolean {
        return oldItem == newItem
    }
}

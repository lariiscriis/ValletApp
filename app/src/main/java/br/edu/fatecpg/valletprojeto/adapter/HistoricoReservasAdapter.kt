package br.edu.fatecpg.valletprojeto.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import br.edu.fatecpg.valletprojeto.databinding.ItemReservaBinding
import br.edu.fatecpg.valletprojeto.model.ReservaHistorico

class HistoricoReservasAdapter(
    private val listaReservas: List<ReservaHistorico>
) : RecyclerView.Adapter<HistoricoReservasAdapter.ViewHolder>() {

    inner class ViewHolder(val binding: ItemReservaBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemReservaBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val reserva = listaReservas[position]

        holder.binding.tvVaga.text = reserva.vaga

        holder.binding.tvEstacionamentoNome.text = reserva.estacionamentoNome

        val periodo = "${reserva.data} • ${reserva.horario}"
        holder.binding.tvPeriodo.text = periodo

        holder.binding.tvStatus.text = reserva.duracao
    }

    override fun getItemCount() = listaReservas.size
}
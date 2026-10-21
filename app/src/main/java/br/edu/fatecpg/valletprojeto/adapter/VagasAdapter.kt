package br.edu.fatecpg.valletprojeto.adapter

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import br.edu.fatecpg.valletprojeto.R
import br.edu.fatecpg.valletprojeto.databinding.ItemListaVagasBinding
import br.edu.fatecpg.valletprojeto.model.Vaga

class VagasAdapter(
    private val onVagaSelecionada: (Vaga) -> Unit
) : ListAdapter<Vaga, VagasAdapter.VagaViewHolder>(DiffCallback) {

    var vagaSelecionadaId: String? = null

    inner class VagaViewHolder(private val binding: ItemListaVagasBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(vaga: Vaga) {
            binding.tvSpotNumber.text = vaga.numero

            val isSelected = vaga.id == vagaSelecionadaId
            val isMoto = vaga.tipo.lowercase().contains("moto")

            when {
                !vaga.disponivel -> {
                    // VAGA OCUPADA
                    binding.ivVehicle.setImageResource(
                        if (isMoto) R.drawable.moto_vagaocupada else R.drawable.carro_vagaocupada
                    )
                    binding.ivVehicle.visibility = View.VISIBLE

                    binding.tvSpotStatus.text = " Ocupada"
                    binding.cardVaga.setCardBackgroundColor(Color.parseColor("#F0F2ED"))
                    binding.tvSpotNumber.setTextColor(Color.parseColor("#778078"))
                    binding.tvSpotStatus.setTextColor(Color.parseColor("#929992"))
                }
                isSelected -> {
                    // VAGA SELECIONADA
                    binding.ivVehicle.visibility = View.INVISIBLE

                    binding.tvSpotStatus.text = "Selecionada"
                    binding.cardVaga.setCardBackgroundColor(Color.parseColor("#3B5942"))
                    binding.tvSpotNumber.setTextColor(Color.WHITE)
                    binding.tvSpotStatus.setTextColor(Color.WHITE)
                }
                else -> {
                    // VAGA LIVRE
                    binding.ivVehicle.visibility = View.INVISIBLE

                    binding.tvSpotStatus.text = " Livre"
                    binding.cardVaga.setCardBackgroundColor(Color.parseColor("#CBD9C5"))
                    binding.tvSpotNumber.setTextColor(Color.parseColor("#3B5942"))
                    binding.tvSpotStatus.setTextColor(Color.parseColor("#637365"))
                }
            }

            binding.root.setOnClickListener {
                if (vaga.disponivel) {
                    vagaSelecionadaId = vaga.id
                    notifyDataSetChanged()
                    onVagaSelecionada(vaga)
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VagaViewHolder {
        val binding = ItemListaVagasBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VagaViewHolder(binding)
    }

    override fun onBindViewHolder(holder: VagaViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<Vaga>() {
            override fun areItemsTheSame(oldItem: Vaga, newItem: Vaga): Boolean = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: Vaga, newItem: Vaga): Boolean = oldItem == newItem
        }
    }
}
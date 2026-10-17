package br.edu.fatecpg.valletprojeto.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import br.edu.fatecpg.valletprojeto.databinding.ItemVagaBinding
import br.edu.fatecpg.valletprojeto.model.Vaga

class VagasAdapter(
    private val onVagaClick: (Vaga) -> Unit
) : ListAdapter<Vaga, VagasAdapter.VagaViewHolder>(DiffCallback) {
    inner class VagaViewHolder(private val binding: ItemVagaBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(vaga: Vaga) {
            binding.tvTitulo.text = "${vaga.numero}"
            binding.tvLocalizacao.text = vaga.localizacao
            binding.tvPreco.text = "R$ %.2f/h".format(vaga.preco)

            binding.btnEdit.visibility = View.GONE
            binding.btnExcluir.visibility = View.GONE
            binding.root.setOnClickListener { onVagaClick(vaga) }


        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VagaViewHolder {
        val binding = ItemVagaBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VagaViewHolder(binding)
    }

    override fun onBindViewHolder(holder: VagaViewHolder, position: Int) {
        val vaga = getItem(position)
        holder.bind(vaga)

    }

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<Vaga>() {
            override fun areItemsTheSame(oldItem: Vaga, newItem: Vaga): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(oldItem: Vaga, newItem: Vaga): Boolean {
                return oldItem == newItem &&
                        oldItem.disponivel == newItem.disponivel &&
                        oldItem.numero == newItem.numero &&
                        oldItem.preco == newItem.preco
            }
        }
    }
}
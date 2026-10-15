package br.edu.fatecpg.valletprojeto.adapter

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import br.edu.fatecpg.valletprojeto.R
import br.edu.fatecpg.valletprojeto.databinding.ItemFavoriteParkingBinding
import br.edu.fatecpg.valletprojeto.model.Estacionamento
import com.bumptech.glide.Glide
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class FavoriteParkingAdapter(
    private val onFavoriteClicked: (Estacionamento) -> Unit,
    private val onItemClicked: (Estacionamento) -> Unit
) : ListAdapter<Estacionamento, FavoriteParkingAdapter.ViewHolder>(ParkingDiffCallback) {

    private val listeners = mutableMapOf<String, ListenerRegistration>()
    private val db = FirebaseFirestore.getInstance()

    inner class ViewHolder(val binding: ItemFavoriteParkingBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(estacionamento: Estacionamento) {
            binding.txvNomeEstacionamento.text = estacionamento.nome
            binding.txvEnderecoEstacionamento.text = estacionamento.enderecoCompleto

            val isOpen = estacionamento.estaAberto()
            binding.tvParkingStatus.text = if (isOpen) "ABERTO" else "FECHADO"
            binding.tvParkingStatus.setBackgroundColor(
                if (isOpen) Color.parseColor("#00A676") else Color.RED
            )


            binding.txvPreco.text = "R$%.2f/h".format(estacionamento.valorHora)

            binding.tvParkingDistance.text = formatarDistancia(estacionamento.distanciaMetros)
            binding.btnVerVagas.setOnClickListener { onItemClicked(estacionamento) }


            Glide.with(binding.root.context)
                .load(estacionamento.fotoEstacionamentoUri)
                .placeholder(R.drawable.estacionamento_foto)
                .error(R.drawable.estacionamento_foto)
                .into(binding.ivParkingImage)

            binding.ivFavorite.setOnClickListener { onFavoriteClicked(estacionamento) }

            binding.root.setOnClickListener { onItemClicked(estacionamento) }
            binding.ivFavorite.setImageResource(R.drawable.btn_star_on)

            setupVagasListener(estacionamento)
        }

        private fun formatarDistancia(metros: Int?): String {
            return metros?.let {
                if (it >= 1000) "%.1f km".format(it / 1000.0) else "$it m"
            } ?: "N/A"
        }

        private fun setupVagasListener(estacionamento: Estacionamento) {
            listeners[estacionamento.id]?.remove()
            val listener = db.collection("vaga")
                .whereEqualTo("estacionamentoId", estacionamento.id)
                .whereEqualTo("disponivel", true)
                .addSnapshotListener { snapshot, e ->
                    binding.tvTavInfo.text = "Vagas disponíveis: ${snapshot?.size() ?: "N/A"}"
                }
            listeners[estacionamento.id] = listener
        }
    }

    companion object {
        private val ParkingDiffCallback = object : DiffUtil.ItemCallback<Estacionamento>() {
            override fun areItemsTheSame(oldItem: Estacionamento, newItem: Estacionamento): Boolean {
                return oldItem.id == newItem.id
            }
            override fun areContentsTheSame(oldItem: Estacionamento, newItem: Estacionamento): Boolean {
                return oldItem == newItem && oldItem.distanciaMetros == newItem.distanciaMetros
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemFavoriteParkingBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        listeners.values.forEach { it.remove() }
        listeners.clear()
    }
}

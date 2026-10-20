package br.edu.fatecpg.valletprojeto.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import br.edu.fatecpg.valletprojeto.R
import br.edu.fatecpg.valletprojeto.databinding.ItemEstacionamentoFavoritoBinding
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

    inner class ViewHolder(val binding: ItemEstacionamentoFavoritoBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(estacionamento: Estacionamento) {
            val context = binding.root.context

            binding.txvNomeEstacionamento.text = estacionamento.nome
            binding.txvEnderecoEstacionamento.text = estacionamento.enderecoCompleto
            binding.txvPreco.text = "R$%.2f/h".format(estacionamento.valorHora)
            binding.tvParkingDistance.text = formatarDistancia(estacionamento.distanciaMetros)

            // ==========================================
            // 1. LÓGICA: ABERTO vs FECHADO (Novo Design)
            // ==========================================
            val isOpen = estacionamento.estaAberto()
            if (isOpen) {
                binding.root.alpha = 1.0f // Normal
                binding.tvParkingStatus.text = "Aberto"
                binding.tvParkingStatus.setBackgroundResource(R.drawable.bg_status_aberto)
                binding.tvParkingStatus.setTextColor(ContextCompat.getColor(context, R.color.vallet_green_dark))
                binding.btnVerVagas.backgroundTintList = ContextCompat.getColorStateList(context, R.color.vallet_green_dark)
            } else {
                binding.root.alpha = 0.6f // Desbotado
                binding.tvParkingStatus.text = "Fechado"
                binding.tvParkingStatus.setBackgroundResource(R.drawable.bg_status_fechado)
                binding.tvParkingStatus.setTextColor(ContextCompat.getColor(context, R.color.vallet_status_closed_text))
                binding.btnVerVagas.backgroundTintList = ContextCompat.getColorStateList(context, R.color.vallet_green_muted)
            }

            Glide.with(context)
                .load(estacionamento.fotoEstacionamentoUri)
                .placeholder(R.drawable.estacionamento_foto)
                .error(R.drawable.estacionamento_foto)
                .centerCrop()
                .into(binding.ivParkingImage)

            // ==========================================
            // 2. LISTENERS
            // ==========================================
            binding.root.setOnClickListener { onItemClicked(estacionamento) }
            binding.btnVerVagas.setOnClickListener { onItemClicked(estacionamento) }
            binding.ivFavorite.setOnClickListener { onFavoriteClicked(estacionamento) }

            // ==========================================
            // 3. ÍCONE DE CORAÇÃO PREENCHIDO
            // ==========================================
            // Como este adapter é exclusivo para favoritos, usamos o coração preenchido
            binding.ivFavorite.setImageResource(R.drawable.ic_heart_filled)

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
                .addSnapshotListener { snapshot, _ ->
                    binding.tvTavInfo.text = "Vagas: ${snapshot?.size() ?: "N/A"}"
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
        val binding = ItemEstacionamentoFavoritoBinding.inflate(LayoutInflater.from(parent.context), parent, false)
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
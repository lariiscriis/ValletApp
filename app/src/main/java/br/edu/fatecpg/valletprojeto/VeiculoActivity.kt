package br.edu.fatecpg.valletprojeto

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import br.edu.fatecpg.valletprojeto.databinding.ActivityVeiculoBinding
import br.edu.fatecpg.valletprojeto.databinding.ItemCardVeiculoBinding
import br.edu.fatecpg.valletprojeto.fragments.VeiculoFragment
import br.edu.fatecpg.valletprojeto.model.Veiculo
import br.edu.fatecpg.valletprojeto.viewmodel.VeiculoViewModel
import com.google.firebase.auth.FirebaseAuth

class VeiculoActivity : AppCompatActivity() {
    private lateinit var binding: ActivityVeiculoBinding
    private val viewModel: VeiculoViewModel by viewModels()
    private lateinit var veiculosAdapter: VeiculosAdapter

    private val editarVeiculoLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            carregarVeiculosDoUsuario()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVeiculoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.fabAddCar.setOnClickListener {
            val veiculoFragment = VeiculoFragment()
            veiculoFragment.show(supportFragmentManager, "VeiculoFragment")
        }

        setupRecyclerView()
        carregarVeiculosDoUsuario()
    }

    fun carregarVeiculosDoUsuario() {
        viewModel.listarVeiculosDoUsuario(
            onSuccess = { lista ->
                // Controle do Estado Vazio (Empty State)
                if (lista.isEmpty()) {
                    binding.rvCarList.visibility = View.GONE
                    binding.llEmptyState.visibility = View.VISIBLE
                } else {
                    binding.rvCarList.visibility = View.VISIBLE
                    binding.llEmptyState.visibility = View.GONE
                }

                veiculosAdapter.submitList(lista)
            },
            onFailure = { erro ->
                Toast.makeText(this, "Erro ao carregar veículos: $erro", Toast.LENGTH_LONG).show()
            }
        )
    }

    fun onVeiculoCadastrado() {
        carregarVeiculosDoUsuario()
    }

    private fun setupRecyclerView() {
        veiculosAdapter = VeiculosAdapter(
            onToggleClick = { veiculo ->
                val userId = FirebaseAuth.getInstance().currentUser?.uid
                if (userId != null) {
                    viewModel.definirVeiculoPadrao(veiculo.id, userId) { sucesso ->
                        if (sucesso) {
                            carregarVeiculosDoUsuario()
                        }
                    }
                }
            },
            onItemClick = { veiculo ->
                val intent = Intent(this, EditarVeiculoActivity::class.java).apply {
                    putExtra("veiculoId", veiculo.id)
                    putExtra("placa", veiculo.placa)
                    putExtra("marca", veiculo.marca)
                    putExtra("modelo", veiculo.modelo)
                    putExtra("apelido", veiculo.apelido)
                    putExtra("ano", veiculo.ano)
                    putExtra("tipo", veiculo.tipo)
                    putExtra("cor", veiculo.cor)
                    putExtra("padrao", veiculo.padrao)
                    putExtra("fotoUrl", veiculo.fotoUrl)
                }
                editarVeiculoLauncher.launch(intent)
            }
        )
        binding.rvCarList.apply {
            layoutManager = LinearLayoutManager(this@VeiculoActivity)
            adapter = veiculosAdapter
        }
    }
}

class VeiculosAdapter(
    private val onToggleClick: (Veiculo) -> Unit,
    private val onItemClick: (Veiculo) -> Unit
) : ListAdapter<Veiculo, VeiculosAdapter.VeiculoViewHolder>(VeiculoDiffCallback) {

    inner class VeiculoViewHolder(val binding: ItemCardVeiculoBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(veiculo: Veiculo) {
            binding.txvApelidoVeiculo.text = if (!veiculo.apelido.isNullOrEmpty()) veiculo.apelido else veiculo.modelo

            binding.txvDetalhesVeiculo.text = "${veiculo.marca} ${veiculo.modelo} - ${veiculo.ano}"
            binding.txvPlacaVeiculo.text = veiculo.placa

            android.util.Log.d("VeiculoDebug", "Veiculo ${veiculo.modelo} placa ${veiculo.placa} fotoUrl = '${veiculo.fotoUrl}'")

            if (!veiculo.fotoUrl.isNullOrEmpty()) {
                android.util.Log.d("GlideDebug", "Loading vehicle image URL: '${veiculo.fotoUrl}'")
                com.bumptech.glide.Glide.with(binding.root.context)
                    .load(veiculo.fotoUrl)
                    .placeholder(
                        if (veiculo.tipo.equals("Moto", ignoreCase = true)) R.drawable.moto_option_resized else R.drawable.carro_option_resized
                    )
                    .error(
                        if (veiculo.tipo.equals("Moto", ignoreCase = true)) R.drawable.moto_option_resized else R.drawable.carro_option_resized
                    )
                    .centerCrop()
                    .into(binding.ivVehicleIcon)
            } else {
                binding.ivVehicleIcon.setImageResource(
                    if (veiculo.tipo.equals("Moto", ignoreCase = true)) {
                        R.drawable.moto_option_resized
                    } else {
                        R.drawable.carro_option_resized
                    }
                )
            }

            // Controle da Badge "Veículo principal" e ícone de Favorito
            if (veiculo.padrao) {
                binding.llBadgePrincipal.visibility = View.VISIBLE
                binding.ivFavoriteToggle.setImageResource(R.drawable.ic_heart_filled)
            } else {
                binding.llBadgePrincipal.visibility = View.GONE
                binding.ivFavoriteToggle.setImageResource(R.drawable.ic_heart_outline)
            }

            binding.ivFavoriteToggle.setOnClickListener {
                if (!veiculo.padrao) {
                    onToggleClick(veiculo)
                }
            }

            itemView.setOnClickListener {
                onItemClick(veiculo)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VeiculoViewHolder {
        val binding = ItemCardVeiculoBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VeiculoViewHolder(binding)
    }

    override fun onBindViewHolder(holder: VeiculoViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    companion object {
        private val VeiculoDiffCallback = object : DiffUtil.ItemCallback<Veiculo>() {
            override fun areItemsTheSame(oldItem: Veiculo, newItem: Veiculo): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(oldItem: Veiculo, newItem: Veiculo): Boolean {
                return oldItem == newItem
            }
        }
    }
}
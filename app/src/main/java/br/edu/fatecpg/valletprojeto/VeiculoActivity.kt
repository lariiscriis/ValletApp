package br.edu.fatecpg.valletprojeto

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
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
                    viewModel.definirVeiculoPadrao(veiculo.id, userId) { sucesso -> }
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
            binding.txvApelidoVeiculo.text = veiculo.modelo
            binding.txvDetalhesVeiculo.text = "${veiculo.marca} - ${veiculo.ano}"
            binding.txvPlacaVeiculo.text = veiculo.placa

            binding.ivVehicleIcon.setImageResource(
                if (veiculo.tipo.equals("Moto", ignoreCase = true)) {
                    R.drawable.moto_option_resized
                } else {
                    R.drawable.carro_option_resized
                }
            )

            if (veiculo.padrao) {
                binding.ivFavoriteToggle.setImageResource(R.drawable.btn_star_on)
            } else {
                binding.ivFavoriteToggle.setImageResource(R.drawable.btn_star_off)
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

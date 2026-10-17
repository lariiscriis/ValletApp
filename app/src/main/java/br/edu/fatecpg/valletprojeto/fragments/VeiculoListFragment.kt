package br.edu.fatecpg.valletprojeto.fragments

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import br.edu.fatecpg.valletprojeto.EditarVeiculoActivity
import br.edu.fatecpg.valletprojeto.R
import br.edu.fatecpg.valletprojeto.dao.UsuarioDao
import br.edu.fatecpg.valletprojeto.dao.VeiculoDao
import br.edu.fatecpg.valletprojeto.databinding.ActivityVeiculoBinding
import br.edu.fatecpg.valletprojeto.databinding.ItemCardVeiculoBinding
import br.edu.fatecpg.valletprojeto.model.Veiculo
import br.edu.fatecpg.valletprojeto.viewmodel.VeiculoViewModel
import br.edu.fatecpg.valletprojeto.viewmodel.VeiculoViewModelFactory
import com.google.firebase.auth.FirebaseAuth

class VeiculoListFragment : Fragment() {

    private var _binding: ActivityVeiculoBinding? = null
    private val binding get() = _binding!!

    private val viewModel: VeiculoViewModel by activityViewModels {
        VeiculoViewModelFactory()
    }

    private lateinit var veiculosAdapter: VeiculosAdapter

    private val editarVeiculoLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == AppCompatActivity.RESULT_OK) {
            carregarVeiculosDoUsuario()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = ActivityVeiculoBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        carregarVeiculosDoUsuario()

        binding.fabAddCar.setOnClickListener {
            showCadastroVeiculoFragment()
        }
    }

    private fun showCadastroVeiculoFragment() {
        val veiculoFragment = VeiculoFragment()
        veiculoFragment.show(parentFragmentManager, "VeiculoFragment")
    }

    fun carregarVeiculosDoUsuario() {
        viewModel.listarVeiculosDoUsuario(
            onSuccess = { lista ->
                Log.d("VeiculoListFragment", "Veículos carregados: ${lista.size}")
                veiculosAdapter.submitList(lista)
            },
            onFailure = { erro ->
                Toast.makeText(requireContext(), "Erro ao carregar veículos: $erro", Toast.LENGTH_LONG).show()
            }
        )
    }

    private fun setupRecyclerView() {
        veiculosAdapter = VeiculosAdapter(
            onToggleClick = { veiculo ->
                val userId = FirebaseAuth.getInstance().currentUser?.uid
                if (userId != null) {
                    viewModel.definirVeiculoPadrao(veiculo.id, userId) { sucesso ->
                        if (sucesso) {
                            Toast.makeText(requireContext(), "${veiculo.modelo} definido como padrão.", Toast.LENGTH_SHORT).show()
                            carregarVeiculosDoUsuario()
                        } else {
                            Toast.makeText(requireContext(), "Erro ao definir veículo padrão.", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            },
            onItemClick = { veiculo ->
                val intent = Intent(requireContext(), EditarVeiculoActivity::class.java).apply {
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
            layoutManager = LinearLayoutManager(requireContext())
            adapter = veiculosAdapter
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

class VeiculosAdapter(
    private val onToggleClick: (Veiculo) -> Unit,
    private val onItemClick: (Veiculo) -> Unit
) : ListAdapter<Veiculo, VeiculosAdapter.VeiculoViewHolder>(VeiculoDiffCallback) {

    inner class VeiculoViewHolder(val binding: ItemCardVeiculoBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(veiculo: Veiculo) {
            if (veiculo.apelido.isNullOrEmpty()) {
                binding.txvApelidoVeiculo.visibility = View.GONE
                binding.txvDetalhesVeiculo.text = "${veiculo.marca} ${veiculo.modelo} - ${veiculo.ano}"
                binding.txvDetalhesVeiculo.setTextColor(ContextCompat.getColor(itemView.context, R.color.verdeescuro))
                binding.txvDetalhesVeiculo.textSize = 18f
                binding.txvPlacaVeiculo.text = veiculo.placa
                binding.txvPlacaVeiculo.textSize = 18f
            } else {
                binding.txvApelidoVeiculo.visibility = View.VISIBLE
                binding.txvApelidoVeiculo.text = veiculo.apelido
                binding.txvDetalhesVeiculo.text = "${veiculo.marca} ${veiculo.modelo} - ${veiculo.ano}"
                binding.txvDetalhesVeiculo.setTextColor(Color.parseColor("#444444"))
                binding.txvDetalhesVeiculo.textSize = 16f
                binding.txvPlacaVeiculo.text = veiculo.placa
                binding.txvPlacaVeiculo.textSize = 16f
            }

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

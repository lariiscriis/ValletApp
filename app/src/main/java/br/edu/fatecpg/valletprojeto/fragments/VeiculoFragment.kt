package br.edu.fatecpg.valletprojeto.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.ViewModelProvider
import br.edu.fatecpg.valletprojeto.R
import br.edu.fatecpg.valletprojeto.VeiculoActivity
import br.edu.fatecpg.valletprojeto.dao.UsuarioDao
import br.edu.fatecpg.valletprojeto.dao.VeiculoDao
import br.edu.fatecpg.valletprojeto.databinding.FragmentCadastroVeiculosBinding
import br.edu.fatecpg.valletprojeto.model.Veiculo
import br.edu.fatecpg.valletprojeto.viewmodel.VeiculoViewModel
import br.edu.fatecpg.valletprojeto.viewmodel.VeiculoViewModelFactory

class VeiculoFragment : DialogFragment() {

    private var _binding: FragmentCadastroVeiculosBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: VeiculoViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCadastroVeiculosBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val factory = VeiculoViewModelFactory(UsuarioDao(), VeiculoDao)
        viewModel = ViewModelProvider(this, factory).get(VeiculoViewModel::class.java)

        binding.btnConfirmar.setOnClickListener {
            cadastrarVeiculo()
        }

        binding.btnCancelar.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Cancelar cadastro")
                .setMessage("Deseja realmente cancelar o cadastro do veículo?")
                .setPositiveButton("Sim") { _, _ -> dismiss() }
                .setNegativeButton("Não", null)
                .show()
        }

    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT)
            setBackgroundDrawableResource(android.R.color.transparent)
        }
    }

    private fun cadastrarVeiculo() {
        val placa = binding.editPlaca.text.toString().trim()
        val marca = binding.editMarca.text.toString().trim()
        val modelo = binding.editModelo.text.toString().trim()
        val apelido = binding.editApelido.text.toString().trim()
        val ano = binding.editAno.text.toString().trim()

        if (placa.isEmpty() || marca.isEmpty() || modelo.isEmpty() || ano.isEmpty()) {
            Toast.makeText(requireContext(), "Preencha todos os campos obrigatórios (*)", Toast.LENGTH_SHORT).show()
            return
        }

        val tipoSelecionado = when (binding.rgVehicleType.checkedRadioButtonId) {
            R.id.rb_moto -> "moto"
            else -> "carro"
        }

        viewModel.listarVeiculosDoUsuario(
            onSuccess = { veiculos ->
                val novoVeiculo = Veiculo(
                    placa = placa,
                    marca = marca,
                    modelo = modelo,
                    apelido = apelido,
                    ano = ano,
                    tipo = tipoSelecionado,
                    padrao = veiculos.isEmpty() // Define como padrão se for o primeiro veículo
                )

                viewModel.cadastrarVeiculo(
                    novoVeiculo,
                    onSuccess = {
                        Toast.makeText(requireContext(), "Veículo salvo!", Toast.LENGTH_SHORT).show()
                        // Atualiza a lista de veículos na atividade principal
                        (activity as? VeiculoActivity)?.onVeiculoCadastrado()
                        dismiss()
                    },
                    onFailure = { erro ->
                        Toast.makeText(requireContext(), "Erro ao salvar: $erro", Toast.LENGTH_LONG).show()
                    }
                )
            },
            onFailure = { erro ->
                Toast.makeText(requireContext(), "Erro ao verificar veículos existentes: $erro", Toast.LENGTH_LONG).show()
            }
        )
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

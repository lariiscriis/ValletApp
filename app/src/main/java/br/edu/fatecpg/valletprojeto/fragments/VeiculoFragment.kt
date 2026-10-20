package br.edu.fatecpg.valletprojeto.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.ViewModelProvider
import br.edu.fatecpg.valletprojeto.R
import br.edu.fatecpg.valletprojeto.VeiculoActivity
import br.edu.fatecpg.valletprojeto.databinding.FragmentCadastroVeiculosBinding
import br.edu.fatecpg.valletprojeto.model.Veiculo
import br.edu.fatecpg.valletprojeto.viewmodel.VeiculoViewModel
import br.edu.fatecpg.valletprojeto.viewmodel.VeiculoViewModelFactory
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody

class VeiculoFragment : DialogFragment() {

    private var _binding: FragmentCadastroVeiculosBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: VeiculoViewModel

    private var fotoSelecionadaUri: android.net.Uri? = null
    private val cloudinaryUrl = "https://api.cloudinary.com/v1_1/dkmbs6lyk/image/upload"
    private val uploadPreset = "appEstacionamento"

    private val pickImageLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            fotoSelecionadaUri = uri
            com.bumptech.glide.Glide.with(requireContext())
                .load(uri)
                .centerCrop()
                .into(binding.ivVehiclePhoto)
        }
    }

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

        val factory = VeiculoViewModelFactory()
        viewModel = ViewModelProvider(this, factory).get(VeiculoViewModel::class.java)

        setupDropdowns()

        binding.btnConfirmar.setOnClickListener {
            cadastrarVeiculo()
        }

        binding.ivChangePhoto.setOnClickListener { pickImageLauncher.launch("image/*") }
        binding.cvVehiclePhoto.setOnClickListener { pickImageLauncher.launch("image/*") }

        binding.ivBack.setOnClickListener {
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
            setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
            setBackgroundDrawableResource(android.R.color.transparent)
        }
    }

    private fun setupDropdowns() {
        val tipos = arrayOf("Carro", "Moto")
        val tipoAdapter = ArrayAdapter(requireContext(), R.layout.item_dropdown, tipos)
        binding.editTipo.setAdapter(tipoAdapter)

        val cores = arrayOf("Branco", "Preto", "Prata", "Cinza", "Vermelho", "Azul", "Outra")
        val corAdapter = ArrayAdapter(requireContext(), R.layout.item_dropdown, cores)
        binding.editCor.setAdapter(corAdapter)
    }

    private fun cadastrarVeiculo() {
        val placa = binding.editPlaca.text.toString().trim()
        val marca = binding.editMarca.text.toString().trim()
        val modelo = binding.editModelo.text.toString().trim()
        val apelido = binding.editApelido.text.toString().trim()
        val ano = binding.editAno.text.toString().trim()
        val tipoSelecionado = binding.editTipo.text.toString().trim()
        val corSelecionada = binding.editCor.text.toString().trim()

        if (placa.isEmpty() || marca.isEmpty() || modelo.isEmpty() || ano.isEmpty() || tipoSelecionado.isEmpty() || corSelecionada.isEmpty()) {
            Toast.makeText(requireContext(), "Preencha todos os campos obrigatórios (*)", Toast.LENGTH_SHORT).show()
            return
        }

        if (fotoSelecionadaUri != null) {
            processarUploadEcadastrar(placa, marca, modelo, apelido, ano, tipoSelecionado, corSelecionada)
        } else {
            salvarVeiculoNoBanco(placa, marca, modelo, apelido, ano, tipoSelecionado, corSelecionada, null)
        }
    }

    private fun processarUploadEcadastrar(
        placa: String,
        marca: String,
        modelo: String,
        apelido: String,
        ano: String,
        tipo: String,
        cor: String
    ) {
        val file = java.io.File(requireContext().cacheDir, "upload_veiculo.jpg")
        try {
            requireContext().contentResolver.openInputStream(fotoSelecionadaUri!!)?.use { input ->
                java.io.FileOutputStream(file).use { output ->
                    input.copyTo(output)
                }
            }

            val requestBody = okhttp3.MultipartBody.Builder()
                .setType(okhttp3.MultipartBody.FORM)
                .addFormDataPart("file", file.name, file.asRequestBody("image/*".toMediaTypeOrNull()))
                .addFormDataPart("upload_preset", uploadPreset)
                .build()

            val request = okhttp3.Request.Builder()
                .url(cloudinaryUrl)
                .post(requestBody)
                .build()

            okhttp3.OkHttpClient().newCall(request).enqueue(object : okhttp3.Callback {
                override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                    val act = activity ?: return
                    act.runOnUiThread {
                        if (isAdded) {
                            Toast.makeText(requireContext(), "Erro ao enviar imagem", Toast.LENGTH_SHORT).show()
                            salvarVeiculoNoBanco(placa, marca, modelo, apelido, ano, tipo, cor, null)
                        }
                    }
                }

                override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                    val json = response.body?.string()
                    android.util.Log.d("CloudinaryDebug", "Response json: $json")
                    val urlRegex = """"secure_url":"(.*?)"""".toRegex()
                    val match = urlRegex.find(json ?: "")
                    val imageUrl = match?.groups?.get(1)?.value?.replace("\\/", "/")
                    android.util.Log.d("CloudinaryDebug", "Extracted imageUrl: $imageUrl")

                    val act = activity ?: return
                    act.runOnUiThread {
                        if (isAdded) {
                            salvarVeiculoNoBanco(placa, marca, modelo, apelido, ano, tipo, cor, imageUrl)
                        }
                    }
                }
            })
        } catch (_: Exception) {
            salvarVeiculoNoBanco(placa, marca, modelo, apelido, ano, tipo, cor, null)
        }
    }

    private fun salvarVeiculoNoBanco(
        placa: String,
        marca: String,
        modelo: String,
        apelido: String,
        ano: String,
        tipo: String,
        cor: String,
        fotoUrl: String?
    ) {
        viewModel.listarVeiculosDoUsuario(
            onSuccess = { veiculos ->
                val novoVeiculo = Veiculo(
                    placa = placa,
                    marca = marca,
                    modelo = modelo,
                    apelido = apelido,
                    ano = ano,
                    tipo = tipo.lowercase(),
                    cor = cor,
                    padrao = veiculos.isEmpty(),
                    fotoUrl = fotoUrl
                )

                viewModel.cadastrarVeiculo(
                    novoVeiculo,
                    onSuccess = {
                        Toast.makeText(requireContext(), "Veículo salvo!", Toast.LENGTH_SHORT).show()
                        (activity as? VeiculoActivity)?.onVeiculoCadastrado()
                        parentFragmentManager.setFragmentResult("atualizar_lista", Bundle())
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
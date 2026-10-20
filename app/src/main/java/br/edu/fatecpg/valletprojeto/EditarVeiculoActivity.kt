package br.edu.fatecpg.valletprojeto

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import br.edu.fatecpg.valletprojeto.databinding.ActivityEditarVeiculoBinding
import com.bumptech.glide.Glide
import com.google.firebase.firestore.FirebaseFirestore
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class EditarVeiculoActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEditarVeiculoBinding
    private val db = FirebaseFirestore.getInstance()
    private var veiculoId: String? = null

    private var fotoSelecionadaUri: android.net.Uri? = null
    private val cloudinaryUrl = "https://api.cloudinary.com/v1_1/dkmbs6lyk/image/upload"
    private val uploadPreset = "appEstacionamento"

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            fotoSelecionadaUri = uri
            Glide.with(this)
                .load(uri)
                .centerCrop()
                .into(binding.ivVehiclePhoto)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditarVeiculoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        veiculoId = intent.getStringExtra("veiculoId")

        setupDropdowns()
        preencherCampos()

        binding.ivBack.setOnClickListener { finish() }
        binding.btnConfirmar.setOnClickListener { atualizarVeiculo() }
        binding.btnExcluir.setOnClickListener { confirmarExclusao() }

        binding.ivChangePhoto.setOnClickListener { pickImageLauncher.launch("image/*") }
        binding.cvVehiclePhoto.setOnClickListener { pickImageLauncher.launch("image/*") }
    }

    private fun setupDropdowns() {
        val tipos = arrayOf("Carro", "Moto")
        val tipoAdapter = ArrayAdapter(this, R.layout.item_dropdown, tipos)
        binding.editTipo.setAdapter(tipoAdapter)

        val cores = arrayOf("Branco", "Preto", "Prata", "Cinza", "Vermelho", "Azul", "Outra")
        val corAdapter = ArrayAdapter(this, R.layout.item_dropdown, cores)
        binding.editCor.setAdapter(corAdapter)
    }

    private fun preencherCampos() {
        val placa = intent.getStringExtra("placa")
        val marca = intent.getStringExtra("marca")
        val modelo = intent.getStringExtra("modelo")
        val apelido = intent.getStringExtra("apelido")
        val ano = intent.getStringExtra("ano")
        val isPrincipal = intent.getBooleanExtra("padrao", false)
        val fotoUrl = intent.getStringExtra("fotoUrl")

        val tipoOriginal = intent.getStringExtra("tipo")
        val tipoFormatado = tipoOriginal?.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() } ?: "Carro"

        val corOriginal = intent.getStringExtra("cor")
        val corFormatada = if (!corOriginal.isNullOrEmpty()) {
            corOriginal.trim().replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        } else {
            "Outra"
        }

        binding.editPlaca.setText(placa)
        binding.editMarca.setText(marca)
        binding.editModelo.setText(modelo)
        binding.editApelido.setText(apelido)
        binding.editAno.setText(ano)

        binding.editTipo.setText(tipoFormatado, false)
        binding.editCor.setText(corFormatada, false)

        binding.llBadgePrincipal.visibility = if (isPrincipal) View.VISIBLE else View.GONE

        if (!fotoUrl.isNullOrEmpty()) {
            Glide.with(this)
                .load(fotoUrl)
                .centerCrop()
                .into(binding.ivVehiclePhoto)
        } else {
            if (tipoFormatado.equals("Moto", ignoreCase = true)) {
                binding.ivVehiclePhoto.setImageResource(R.drawable.moto_option_resized)
            } else {
                binding.ivVehiclePhoto.setImageResource(R.drawable.carro_option_resized)
            }
        }
    }

    private fun confirmarExclusao() {
        AlertDialog.Builder(this)
            .setTitle("Excluir veículo")
            .setMessage("Tem certeza que deseja excluir este veículo? Esta ação não pode ser desfeita.")
            .setPositiveButton("Excluir") { _, _ -> excluirVeiculo() }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun excluirVeiculo() {
        if (veiculoId.isNullOrEmpty()) {
            Toast.makeText(this, "ID do veículo não encontrado", Toast.LENGTH_SHORT).show()
            return
        }

        db.collection("veiculo").document(veiculoId!!)
            .delete()
            .addOnSuccessListener {
                Toast.makeText(this, "Veículo excluído com sucesso!", Toast.LENGTH_SHORT).show()
                setResult(RESULT_OK)
                finish()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Erro ao excluir veículo: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun atualizarVeiculo() {
        val novaPlaca = binding.editPlaca.text.toString().trim()
        val novaMarca = binding.editMarca.text.toString().trim()
        val novoModelo = binding.editModelo.text.toString().trim()
        val novoApelido = binding.editApelido.text.toString().trim()
        val novoAno = binding.editAno.text.toString().trim()
        val novoTipo = binding.editTipo.text.toString().trim().lowercase()

        val novaCor = if (binding.editCor.text.toString().trim().isEmpty()) "Outra" else binding.editCor.text.toString().trim()

        if (veiculoId.isNullOrEmpty()) {
            Toast.makeText(this, "ID do veículo não encontrado", Toast.LENGTH_SHORT).show()
            return
        }

        if (novaPlaca.isEmpty() || novaMarca.isEmpty() || novoModelo.isEmpty() || novoAno.isEmpty() || novoTipo.isEmpty()) {
            val camposVazios = mutableListOf<String>()
            if (novaPlaca.isEmpty()) camposVazios.add("Placa")
            if (novaMarca.isEmpty()) camposVazios.add("Marca")
            if (novoModelo.isEmpty()) camposVazios.add("Modelo")
            if (novoAno.isEmpty()) camposVazios.add("Ano")
            if (novoTipo.isEmpty()) camposVazios.add("Tipo")

            Toast.makeText(this, "Preencha: ${camposVazios.joinToString(", ")}", Toast.LENGTH_LONG).show()
            return
        }

        if (fotoSelecionadaUri != null) {
            val file = File(cacheDir, "upload_veiculo_edit.jpg")
            try {
                contentResolver.openInputStream(fotoSelecionadaUri!!)?.use { input ->
                    FileOutputStream(file).use { output ->
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
                    override fun onFailure(call: okhttp3.Call, e: IOException) {
                        runOnUiThread {
                            Toast.makeText(this@EditarVeiculoActivity, "Erro ao enviar imagem", Toast.LENGTH_SHORT).show()
                            salvarAtualizacaoNoBanco(novaPlaca, novaMarca, novoModelo, novoApelido, novoAno, novoTipo, novaCor, null)
                        }
                    }

                    override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                        val json = response.body?.string()
                        val urlRegex = """"secure_url":"(.*?)"""".toRegex()
                        val match = urlRegex.find(json ?: "")
                        val imageUrl = match?.groups?.get(1)?.value?.replace("\\/", "/")

                        runOnUiThread {
                            salvarAtualizacaoNoBanco(novaPlaca, novaMarca, novoModelo, novoApelido, novoAno, novoTipo, novaCor, imageUrl)
                        }
                    }
                })
            } catch (_: Exception) {
                salvarAtualizacaoNoBanco(novaPlaca, novaMarca, novoModelo, novoApelido, novoAno, novoTipo, novaCor, null)
            }
        } else {
            salvarAtualizacaoNoBanco(novaPlaca, novaMarca, novoModelo, novoApelido, novoAno, novoTipo, novaCor, null)
        }
    }

    private fun salvarAtualizacaoNoBanco(
        placa: String,
        marca: String,
        modelo: String,
        apelido: String,
        ano: String,
        tipo: String,
        cor: String,
        fotoUrl: String?
    ) {
        val veiculoAtualizado = mutableMapOf<String, Any>(
            "placa" to placa,
            "marca" to marca,
            "modelo" to modelo,
            "apelido" to apelido,
            "ano" to ano,
            "tipo" to tipo,
            "cor" to cor
        )

        if (fotoUrl != null) {
            veiculoAtualizado["fotoUrl"] = fotoUrl
        }

        db.collection("veiculo").document(veiculoId!!)
            .update(veiculoAtualizado)
            .addOnSuccessListener {
                Toast.makeText(this, "Veículo atualizado com sucesso!", Toast.LENGTH_SHORT).show()
                setResult(RESULT_OK)
                finish()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Erro ao atualizar veículo: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }
}

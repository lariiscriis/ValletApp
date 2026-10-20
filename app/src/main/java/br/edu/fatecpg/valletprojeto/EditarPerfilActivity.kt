package br.edu.fatecpg.valletprojeto

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import br.edu.fatecpg.valletprojeto.databinding.ActivityEditarPerfilBinding
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class EditarPerfilActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEditarPerfilBinding
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    private var fotoSelecionadaUri: Uri? = null
    private var docIdUsuario: String? = null
    private val cloudinaryUrl = "https://api.cloudinary.com/v1_1/dkmbs6lyk/image/upload"
    private val uploadPreset = "appEstacionamento"

    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) {
                fotoSelecionadaUri = uri
                Glide.with(this).load(uri).circleCrop().into(binding.imgFotoEditar)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditarPerfilBinding.inflate(layoutInflater)
        setContentView(binding.root)

        carregarDadosParaEdicao()

        binding.btnAlterarFoto.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }

        binding.btnSalvar.setOnClickListener {
            salvarAlteracoes()
        }

        binding.btnCancelar.setOnClickListener {
            finish()
        }
    }

    private fun carregarDadosParaEdicao() {
        val email = auth.currentUser?.email ?: return finishComErro("Usuário não autenticado")

        db.collection("usuario")
            .whereEqualTo("email", email)
            .get()
            .addOnSuccessListener { querySnapshot ->
                if (!querySnapshot.isEmpty) {
                    val doc = querySnapshot.documents[0]
                    docIdUsuario = doc.id

                    binding.edtNome.setText(doc.getString("nome") ?: "")
                    binding.edtEmail.setText(doc.getString("email") ?: "")
                    binding.edtTelefone.setText(doc.getString("telefone") ?: "")
                    binding.edtCnh.setText(doc.getString("cnh") ?: "")

                    val fotoPerfilUrl = doc.getString("fotoPerfil")
                    if (!fotoPerfilUrl.isNullOrEmpty()) {
                        Glide.with(this).load(fotoPerfilUrl).circleCrop().into(binding.imgFotoEditar)
                    }
                } else {
                    Toast.makeText(this, "Dados não encontrados", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Erro ao carregar dados: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun salvarAlteracoes() {
        val nome = binding.edtNome.text.toString()
        val emailNovo = binding.edtEmail.text.toString()
        val telefone = binding.edtTelefone.text.toString()
        val cnh = binding.edtCnh.text.toString()

        val user = auth.currentUser ?: return finishComErro("Usuário não autenticado")

        if (emailNovo != user.email) {
            user.updateEmail(emailNovo)
                .addOnSuccessListener {
                    Toast.makeText(this, "Email atualizado com sucesso", Toast.LENGTH_SHORT).show()
                    processarUploadOuSalvar(nome, emailNovo, telefone, cnh)
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Erro ao atualizar email: ${it.message}", Toast.LENGTH_SHORT).show()
                    processarUploadOuSalvar(nome, emailNovo, telefone, cnh)
                }
        } else {
            processarUploadOuSalvar(nome, emailNovo, telefone, cnh)
        }
    }

    private fun processarUploadOuSalvar(nome: String, email: String, telefone: String, cnh: String) {
        if (fotoSelecionadaUri != null) {
            val file = File(cacheDir, "upload.jpg")
            contentResolver.openInputStream(fotoSelecionadaUri!!)?.use { input ->
                FileOutputStream(file).use { output ->
                    input.copyTo(output)
                }
            }

            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", file.name, file.asRequestBody("image/*".toMediaTypeOrNull()))
                .addFormDataPart("upload_preset", uploadPreset)
                .build()

            val request = Request.Builder()
                .url(cloudinaryUrl)
                .post(requestBody)
                .build()

            OkHttpClient().newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    runOnUiThread {
                        Toast.makeText(this@EditarPerfilActivity, "Erro ao enviar imagem", Toast.LENGTH_SHORT).show()
                        atualizarFirestore(nome, email, telefone, cnh, null)
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    val json = response.body?.string()
                    val urlRegex = """"secure_url":"(.*?)"""".toRegex()
                    val match = urlRegex.find(json ?: "")
                    val imageUrl = match?.groups?.get(1)?.value?.replace("\\/", "/")

                    runOnUiThread {
                        atualizarFirestore(nome, email, telefone, cnh, imageUrl)
                    }
                }
            })

        } else {
            atualizarFirestore(nome, email, telefone, cnh, null)
        }
    }

    private fun atualizarFirestore(nome: String, email: String, telefone: String, cnh: String, imageUrl: String?) {
        val dados = mutableMapOf<String, Any>(
            "nome" to nome,
            "email" to email,
            "telefone" to telefone,
            "cnh" to cnh
        )
        if (imageUrl != null) {
            dados["fotoPerfil"] = imageUrl
        }

        if (docIdUsuario == null) {
            Toast.makeText(this, "Erro interno: docId nulo", Toast.LENGTH_LONG).show()
            return
        }

        db.collection("usuario").document(docIdUsuario!!)
            .update(dados)
            .addOnSuccessListener {
                Toast.makeText(this, "Perfil atualizado com sucesso", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Erro ao atualizar dados: ${it.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun finishComErro(msg: String): Nothing {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        finish()
        throw IllegalStateException(msg)
    }
}

package br.edu.fatecpg.valletprojeto.dao

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Usa a configuração de Cloudinary já existente no cadastro de veículos. */
object UploadFotoVeiculo {
    const val URL = "https://api.cloudinary.com/v1_1/dkmbs6lyk/image/upload"
    const val PRESET = "appEstacionamento"
    private val client = OkHttpClient.Builder().callTimeout(45, TimeUnit.SECONDS).build()

    suspend fun enviar(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
        val file = File.createTempFile("upload_veiculo_", ".jpg", context.cacheDir)
        try {
            val input = context.contentResolver.openInputStream(uri)
                ?: throw IOException("Não foi possível abrir a foto")
            input.use { source -> file.outputStream().use { source.copyTo(it) } }
            val body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", file.name, file.asRequestBody("image/*".toMediaTypeOrNull()))
                .addFormDataPart("upload_preset", PRESET)
                .build()
            val call = client.newCall(Request.Builder().url(URL).post(body).build())
            suspendCancellableCoroutine { continuation ->
                continuation.invokeOnCancellation { call.cancel() }
                call.enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        if (continuation.isActive) continuation.resumeWithException(e)
                    }

                    override fun onResponse(call: Call, response: Response) {
                        val result = runCatching {
                            response.use {
                                check(it.isSuccessful) { "Erro ao enviar foto: HTTP ${it.code}" }
                                val url = JSONObject(it.body?.string().orEmpty()).getString("secure_url")
                                check(url.startsWith("https://")) { "Resposta de imagem inválida" }
                                url
                            }
                        }
                        if (continuation.isActive) result.fold(
                            onSuccess = { continuation.resume(it) },
                            onFailure = { continuation.resumeWithException(it) }
                        )
                    }
                })
            }
        } finally {
            file.delete()
        }
    }
}

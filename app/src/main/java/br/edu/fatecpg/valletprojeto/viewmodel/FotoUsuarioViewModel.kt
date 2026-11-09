package br.edu.fatecpg.valletprojeto.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.edu.fatecpg.valletprojeto.dao.UploadFotoVeiculo
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class FotoUsuarioViewModel : ViewModel() {
    val estado = MutableLiveData(EstadoCadastro())
    var foto: Uri? = null
    private var enviada: Uri? = null
    private var urlEnviada: String? = null

    fun concluir(context: Context, semFoto: Boolean) {
        if (estado.value?.salvando == true || estado.value?.sucesso == true) return
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        if (!semFoto && foto == null) return
        val selecionada = if (semFoto) null else foto
        val app = context.applicationContext
        estado.value = EstadoCadastro(salvando = true)
        viewModelScope.launch {
            try {
                val url = selecionada?.let { uri ->
                    urlEnviada?.takeIf { enviada == uri } ?: UploadFotoVeiculo.enviar(app, uri).also {
                        enviada = uri
                        urlEnviada = it
                    }
                }
                check(FirebaseAuth.getInstance().currentUser?.uid == uid)
                val db = FirebaseFirestore.getInstance()
                val ref = db.collection("usuario").document(uid)
                db.runTransaction { transaction ->
                    val perfil = transaction.get(ref)
                    check(perfil.getString("tipo_user")?.trim().equals("motorista", true))
                    val dados = mutableMapOf<String, Any>("fotoCadastroConcluida" to true)
                    if (url != null) dados["fotoPerfil"] = url
                    transaction.update(ref, dados)
                }.await()
                check(FirebaseAuth.getInstance().currentUser?.uid == uid)
                estado.value = EstadoCadastro(sucesso = true)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                estado.value = EstadoCadastro(erro = "Não foi possível salvar. Confira a conexão e tente novamente.")
            }
        }
    }
}

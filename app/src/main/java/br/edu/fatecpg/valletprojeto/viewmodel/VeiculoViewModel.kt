package br.edu.fatecpg.valletprojeto.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.viewModelScope
import br.edu.fatecpg.valletprojeto.dao.UploadFotoVeiculo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import br.edu.fatecpg.valletprojeto.dao.VeiculoDao
import br.edu.fatecpg.valletprojeto.model.Veiculo
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.Source

data class EstadoCadastroVeiculo(
    val salvando: Boolean = false, val sucesso: Boolean = false, val erro: String? = null
)

class VeiculoViewModel : ViewModel() {
    private val _cadastro = MutableLiveData(EstadoCadastroVeiculo())
    val cadastro: LiveData<EstadoCadastroVeiculo> = _cadastro
    private var persistindo = false
    private var fotoEnviadaUri: Uri? = null
    private var fotoEnviadaUrl: String? = null

    fun iniciarCadastro(): Boolean {
        if (_cadastro.value?.salvando == true || _cadastro.value?.sucesso == true) return false
        _cadastro.value = EstadoCadastroVeiculo(salvando = true)
        return true
    }

    /** Envia a foto opcional antes de persistir o primeiro veículo, sem reter a Activity. */
    fun salvarFormularioComFoto(veiculo: Veiculo, foto: Uri?, context: Context) {
        if (!iniciarCadastro()) return
        if (foto == null) {
            salvarFormulario(veiculo)
            return
        }
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid == null) {
            falhou("Usuário não autenticado")
            return
        }
        val appContext = context.applicationContext
        viewModelScope.launch {
            try {
                // Uma falha posterior no Firestore não exige reenviar a mesma foto.
                val url = fotoEnviadaUrl?.takeIf { fotoEnviadaUri == foto }
                    ?: UploadFotoVeiculo.enviar(appContext, foto).also {
                        fotoEnviadaUri = foto
                        fotoEnviadaUrl = it
                    }
                if (FirebaseAuth.getInstance().currentUser?.uid != uid) {
                    falhou("A sessão foi alterada")
                } else salvarFormulario(veiculo.copy(fotoUrl = url))
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                falhou("Não foi possível enviar a foto. Verifique sua conexão e tente novamente.")
            }
        }
    }

    /** O salvamento e seu resultado sobrevivem à recriação do Fragment. */
    fun salvarFormulario(veiculo: Veiculo) {
        if (persistindo || _cadastro.value?.sucesso == true) return
        persistindo = true
        _cadastro.value = EstadoCadastroVeiculo(salvando = true)
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid == null) {
            falhou("Usuário não autenticado")
            return
        }
        VeiculoDao.listarVeiculosDoUsuario(
            onSuccess = { existentes ->
                if (FirebaseAuth.getInstance().currentUser?.uid != uid) {
                    falhou("A sessão foi alterada")
                } else {
                    VeiculoDao.cadastrarVeiculo(
                        veiculo.copy(padrao = existentes.isEmpty()),
                        onSuccess = {
                            persistindo = false
                            if (FirebaseAuth.getInstance().currentUser?.uid == uid) {
                                _cadastro.value = EstadoCadastroVeiculo(sucesso = true)
                            } else falhou("A sessão foi alterada")
                        },
                        onFailure = { falhou("Erro ao salvar: $it") }
                    )
                }
            },
            onFailure = { falhou("Erro ao verificar veículos existentes: $it") },
            source = Source.SERVER
        )
    }

    private fun falhou(mensagem: String) {
        persistindo = false
        _cadastro.value = EstadoCadastroVeiculo(erro = mensagem)
    }

    // API do gerenciamento já existente, mantida para seus callbacks e foto opcional.
    fun cadastrarVeiculo(veiculo: Veiculo, onSuccess: () -> Unit, onFailure: (String) -> Unit) {
        VeiculoDao.cadastrarVeiculo(veiculo, onSuccess, onFailure)
    }

    fun definirVeiculoPadrao(veiculoId: String, usuarioId: String, onComplete: (Boolean) -> Unit) {
        VeiculoDao.definirVeiculoPadrao(veiculoId, usuarioId, onComplete)
    }

    fun listarVeiculosDoUsuario(onSuccess: (List<Veiculo>) -> Unit, onFailure: (String) -> Unit) {
        VeiculoDao.listarVeiculosDoUsuario(onSuccess, onFailure)
    }
}

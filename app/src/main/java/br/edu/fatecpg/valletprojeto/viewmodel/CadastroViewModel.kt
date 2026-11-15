package br.edu.fatecpg.valletprojeto.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import br.edu.fatecpg.valletprojeto.dao.UsuarioDao
import br.edu.fatecpg.valletprojeto.model.Usuario

data class EstadoCadastro(val salvando: Boolean = false, val sucesso: Boolean = false, val erro: String? = null)

class CadastroViewModel : ViewModel() {
    private val usuarioDao = UsuarioDao()
    private val _estado = MutableLiveData(EstadoCadastro())
    val estado: LiveData<EstadoCadastro> = _estado

    fun cadastrarUsuario(usuario: Usuario) {
        if (_estado.value?.salvando == true || _estado.value?.sucesso == true) return
        _estado.value = EstadoCadastro(salvando = true)
        usuarioDao.cadastrarUsuario(usuario,
            onSuccess = { _estado.value = EstadoCadastro(sucesso = true) },
            onError = { _estado.value = EstadoCadastro(erro = it) })
    }
}

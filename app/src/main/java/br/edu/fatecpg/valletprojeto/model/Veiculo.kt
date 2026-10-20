package br.edu.fatecpg.valletprojeto.model

import com.google.firebase.firestore.PropertyName

data class Veiculo(
    val placa: String = "",
    val marca: String = "",
    val modelo: String = "",
    val apelido: String = "",
    val ano: String = "",
    val tipo: String="",
    var id: String = "",
    var usuarioEmail: String = "",
    var dataCadastro: String = "",
    var padrao: Boolean = false,
    var usuarioId: String = "",

    @get:PropertyName("cor")
    @set:PropertyName("cor")
    var cor: String = "",

    @get:PropertyName("fotoUrl")
    @set:PropertyName("fotoUrl")
    var fotoUrl: String? = null
)

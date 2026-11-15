package br.edu.fatecpg.valletprojeto.model

data class Usuario(
    val email: String = "",
    val nome: String = "",
    val senha: String = "",
    val tipoUser: String = "",
    val cnh: String? = null,
    val nomeEmpresa: String? = null,
    val cargo: String? = null,
    val telefone: String = ""
)

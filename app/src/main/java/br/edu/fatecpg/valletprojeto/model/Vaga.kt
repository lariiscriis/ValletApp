package br.edu.fatecpg.valletprojeto.model

data class Vaga(
        var id: String = "",
        val numero: String = "",
        val localizacao: String = "",
        val preco: Double = 0.0,
        val tipo: String = "",
        val preferencial: Boolean = false,
        val disponivel: Boolean = true,
        val estacionamentoId: String = ""
)


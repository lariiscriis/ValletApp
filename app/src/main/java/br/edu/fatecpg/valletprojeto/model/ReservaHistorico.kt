package br.edu.fatecpg.valletprojeto.model

data class ReservaHistorico(
    val vaga: String,
    val data: String,
    val horario: String,
    val estacionamentoNome: String,
    val duracao: String = ""
)
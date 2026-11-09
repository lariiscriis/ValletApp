package br.edu.fatecpg.valletprojeto.onboarding

enum class DestinoCadastro { INICIO, CONTA, FOTO, VEICULO, DASHBOARD, RESTRITO }

/** Regras compartilhadas pela abertura do app e pelo login. */
object PoliticaCadastro {
    fun perfil(existe: Boolean, tipo: String?, nome: String?, cnh: String?): DestinoCadastro? {
        if (!existe) return DestinoCadastro.CONTA
        if (!tipo?.trim().equals("motorista", ignoreCase = true)) return DestinoCadastro.RESTRITO
        if (nome.isNullOrBlank() || cnh.isNullOrBlank()) return DestinoCadastro.CONTA
        return null // Perfil válido: ainda é obrigatório consultar os veículos.
    }

    fun veiculos(possuiVeiculo: Boolean) =
        if (possuiVeiculo) DestinoCadastro.DASHBOARD else DestinoCadastro.VEICULO
}

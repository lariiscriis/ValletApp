package br.edu.fatecpg.valletprojeto.model

/** Relógio local serve apenas à apresentação. A API decide com horário do servidor. */
object JanelaCancelamento {
    const val DURACAO_MS = 10 * 60 * 1000L
    fun restanteMs(status: String, inicioMs: Long?, agoraMs: Long): Long {
        if (status != "ativa" || inicioMs == null || inicioMs <= 0 || agoraMs < inicioMs) return 0
        val decorrido = agoraMs - inicioMs
        return if (decorrido < DURACAO_MS) DURACAO_MS - decorrido else 0
    }
}

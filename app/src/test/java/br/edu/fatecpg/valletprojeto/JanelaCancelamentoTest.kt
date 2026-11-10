package br.edu.fatecpg.valletprojeto

import br.edu.fatecpg.valletprojeto.model.JanelaCancelamento
import org.junit.Assert.assertEquals
import org.junit.Test

class JanelaCancelamentoTest {
    private val inicio = 1_000_000L
    @Test fun antesDoLimite() { assertEquals(1L, JanelaCancelamento.restanteMs("ativa", inicio, inicio + 599_999)) }
    @Test fun exatamenteDezMinutos() { assertEquals(0L, JanelaCancelamento.restanteMs("ativa", inicio, inicio + 600_000)) }
    @Test fun depoisDoLimite() { assertEquals(0L, JanelaCancelamento.restanteMs("ativa", inicio, inicio + 600_001)) }
    @Test fun renovacaoNaoReiniciaJanela() {
        // fimReserva não faz parte da política: qualquer extensão mantém o início original.
        repeat(4) { assertEquals(0L, JanelaCancelamento.restanteMs("ativa", inicio, inicio + 700_000)) }
    }
    @Test fun inicioInvalido() {
        for (data in listOf(null, 0L, -1L, inicio + 1)) {
            assertEquals(0L, JanelaCancelamento.restanteMs("ativa", data, inicio))
        }
    }
    @Test fun reservaNaoAtiva() {
        for (status in listOf("cancelada", "finalizada", "", "concluida"))
            assertEquals(0L, JanelaCancelamento.restanteMs(status, inicio, inicio + 1))
    }
}

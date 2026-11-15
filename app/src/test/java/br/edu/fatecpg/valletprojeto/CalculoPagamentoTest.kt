package br.edu.fatecpg.valletprojeto

import br.edu.fatecpg.valletprojeto.viewmodel.CalculoPagamento
import org.junit.Assert.assertEquals
import org.junit.Test

class CalculoPagamentoTest {
    @Test fun preservaMinimoDeUmaHora() {
        assertEquals(1L, CalculoPagamento.minutos(1000L, 1000L))
        assertEquals(1, CalculoPagamento.horas(1))
        assertEquals(1, CalculoPagamento.horas(59))
    }
    @Test fun arredondaHorasComoNoFluxoExistente() {
        assertEquals(1, CalculoPagamento.horas(60))
        assertEquals(2, CalculoPagamento.horas(61))
        assertEquals(2, CalculoPagamento.horas(120))
        assertEquals(3, CalculoPagamento.horas(121))
    }
    @Test fun utilizaMinutosInteirosSemAlterarRegraAnterior() {
        assertEquals(60L, CalculoPagamento.minutos(1L, 3_659_001L))
        assertEquals(61L, CalculoPagamento.minutos(1L, 3_660_001L))
    }
}

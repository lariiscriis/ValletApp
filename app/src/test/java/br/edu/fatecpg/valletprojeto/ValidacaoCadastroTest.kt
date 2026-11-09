package br.edu.fatecpg.valletprojeto

import br.edu.fatecpg.valletprojeto.onboarding.ValidacaoCadastro as V
import org.junit.Assert.*
import org.junit.Test

class ValidacaoCadastroTest {
    @Test fun cnhComVerificadoresValidosEPreservacaoDeZeroInicial() {
        listOf("02650306461", "69044271146", "62472927637", "58316794534", "00000000119").forEach {
            assertTrue(it, V.cnhValida(it))
        }
        assertTrue(V.cnhValida(" 02650306461 "))
    }
    @Test fun cnhComDigitosAlterados() {
        assertFalse(V.cnhValida("62472927638"))
        assertFalse(V.cnhValida("62472927647"))
        assertFalse(V.cnhValida("12345678901"))
    }
    @Test fun cnhComFormatoInvalidoOuSequenciaRepetida() {
        listOf("", "123", "026503064610", "2650306461", "0265030646A", "026503064-61", "０２６５０３０６４６１").forEach {
            assertFalse(it, V.cnhValida(it))
        }
        ('0'..'9').forEach { assertFalse(V.cnhValida(it.toString().repeat(11))) }
    }
    @Test fun cnhComRestoDeDivisaoExcepcional() {
        assertTrue(V.cnhValida("88853444109"))
        assertTrue(V.cnhValida("48929785300"))
        assertTrue(V.cnhValida("29742991308"))
    }

    @Test fun telefonesComDdd() {
        assertTrue(V.telefoneValido("(11) 98765-4321"))
        assertTrue(V.telefoneValido("+55 (13) 3456-7890"))
        listOf("123", "(20) 98765-4321", "(11) 88765-4321", "119999999999", "abc11987654321").forEach {
            assertFalse(it, V.telefoneValido(it))
        }
    }
    @Test fun carroFormatoSolicitado() {
        assertTrue(V.placaValida("abc1d23", "Carro"))
        assertFalse(V.placaValida("ABC1234", "Carro"))
        assertFalse(V.placaValida("ABC1D2", "Carro"))
    }
    @Test fun motoFormatoSolicitado() {
        assertTrue(V.placaValida("ABC-1234", "Moto"))
        assertFalse(V.placaValida("ABC1D23", "Moto"))
        assertFalse(V.placaValida("123ABCD", "Moto"))
        assertFalse(V.placaValida("ABC1234", "Outro"))
    }
}

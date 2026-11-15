package br.edu.fatecpg.valletprojeto

import br.edu.fatecpg.valletprojeto.model.Vaga
import br.edu.fatecpg.valletprojeto.viewmodel.FiltroVagas
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FiltroVagasTest {
    private val carroLivre = Vaga(id = "c1", tipo = "carro", preco = 10.0)
    private val carroOcupado = Vaga(
        id = "c2", tipo = " Carro ", disponivel = false, preferencial = true, preco = 20.0
    )
    private val motoLivre = Vaga(id = "m1", tipo = " MOTO ", preferencial = true, preco = 5.0)
    private val motoOcupada = Vaga(id = "m2", tipo = "moto", disponivel = false, preco = 8.0)
    private val semTipo = Vaga(id = "s1", preferencial = true)
    private val todas = listOf(carroLivre, carroOcupado, motoLivre, motoOcupada, semTipo)
    private val filtro = FiltroVagas()

    @Test
    fun inicioEmTodosIncluiLivresOcupadasEVagaSemTipo() {
        assertEquals(todas, filtro.atualizarVagas(todas))
    }

    @Test
    fun carroNormalizaCaixaEEspacosSemExcluirOcupadasOuPreferenciais() {
        filtro.atualizarVagas(todas)

        assertEquals(listOf(carroLivre, carroOcupado), filtro.selecionarFiltro(" Carro "))
    }

    @Test
    fun motoIncluiLivresEOcupadas() {
        filtro.atualizarVagas(todas)

        assertEquals(listOf(motoLivre, motoOcupada), filtro.selecionarFiltro("Moto"))
    }

    @Test
    fun preferencialUsaBooleanoIndependenteDoTipoEDaDisponibilidade() {
        filtro.atualizarVagas(todas)

        assertEquals(
            listOf(carroOcupado, motoLivre, semTipo),
            filtro.selecionarFiltro("Preferencial")
        )
    }

    @Test
    fun cadaSelecaoRecalculaDaListaCompletaEVoltarParaTodosRestauraTudo() {
        filtro.atualizarVagas(todas)

        assertEquals(listOf(motoLivre, motoOcupada), filtro.selecionarFiltro("moto"))
        assertEquals(listOf(carroLivre, carroOcupado), filtro.selecionarFiltro("carro"))
        assertEquals(
            listOf(carroOcupado, motoLivre, semTipo),
            filtro.selecionarFiltro("preferencial")
        )
        assertEquals(todas, filtro.selecionarFiltro("Todos"))
    }

    @Test
    fun toqueAntesDoCarregamentoEAplicadoQuandoOsDadosChegam() {
        assertTrue(filtro.selecionarFiltro("carro").isEmpty())
        assertTrue(filtro.selecionarFiltro("moto").isEmpty())

        assertEquals(listOf(motoLivre, motoOcupada), filtro.atualizarVagas(todas))
    }

    @Test
    fun atualizacaoDosDadosPreservaFiltroEAtualizaOcupacao() {
        filtro.atualizarVagas(todas)
        filtro.selecionarFiltro("moto")
        val motoAgoraOcupada = motoLivre.copy(disponivel = false)
        val motoNova = Vaga(id = "m3", tipo = "moto")

        assertEquals(
            listOf(motoAgoraOcupada, motoNova),
            filtro.atualizarVagas(listOf(carroLivre, motoAgoraOcupada, motoNova))
        )
    }

    @Test
    fun recargaSemFiltroExplicitoPreservaEscolhaAtual() {
        filtro.atualizarVagas(todas)
        filtro.selecionarFiltro("moto")

        assertEquals(listOf(motoLivre, motoOcupada), filtro.selecionarFiltro())
    }

    @Test
    fun limiteDePrecoContinuaCombinadoComTipoOuPreferencia() {
        filtro.atualizarVagas(todas)

        assertEquals(listOf(carroLivre), filtro.selecionarFiltro("carro", precoMax = 10.0))
        assertEquals(
            listOf(motoLivre, semTipo),
            filtro.selecionarFiltro("preferencial", precoMax = 10.0)
        )
        assertEquals(todas, filtro.selecionarFiltro("todos"))
    }
}

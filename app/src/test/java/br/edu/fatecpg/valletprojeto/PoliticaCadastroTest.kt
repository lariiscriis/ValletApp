package br.edu.fatecpg.valletprojeto

import br.edu.fatecpg.valletprojeto.onboarding.DestinoCadastro
import br.edu.fatecpg.valletprojeto.onboarding.PoliticaCadastro
import org.junit.Assert.*
import org.junit.Test

class PoliticaCadastroTest {
    @Test fun contaAutenticadaSemPerfilRetomaConta() {
        assertEquals(DestinoCadastro.CONTA, PoliticaCadastro.perfil(false, null, null, null))
    }

    @Test fun perfilParcialDeMotoristaRetomaConta() {
        assertEquals(DestinoCadastro.CONTA, PoliticaCadastro.perfil(true, "motorista", "Ana", ""))
        assertEquals(DestinoCadastro.CONTA, PoliticaCadastro.perfil(true, "motorista", " ", "123"))
    }

    @Test fun administradorNuncaEntraNoCadastroDeMotorista() {
        assertEquals(DestinoCadastro.RESTRITO, PoliticaCadastro.perfil(true, "admin", "Ana", "123"))
    }

    @Test fun tipoAusenteOuDesconhecidoNaoAutorizaAcesso() {
        assertEquals(DestinoCadastro.RESTRITO, PoliticaCadastro.perfil(true, null, "Ana", "123"))
        assertEquals(DestinoCadastro.RESTRITO, PoliticaCadastro.perfil(true, "operador", "", ""))
    }

    @Test fun motoristaValidoAindaPrecisaConsultarVeiculos() {
        assertNull(PoliticaCadastro.perfil(true, " Motorista ", "Ana", "123"))
    }

    @Test fun motoristaSemVeiculosRetomaFormulario() {
        assertEquals(DestinoCadastro.VEICULO, PoliticaCadastro.veiculos(false))
    }

    @Test fun motoristaComVeiculoSegueAoDashboard() {
        assertEquals(DestinoCadastro.DASHBOARD, PoliticaCadastro.veiculos(true))
    }
}

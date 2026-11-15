package br.edu.fatecpg.valletprojeto

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import br.edu.fatecpg.valletprojeto.onboarding.RoteadorCadastro

/** Compatibilidade com entradas antigas: retoma a etapa persistida, sem tela intermediária. */
class IntroCadastroVeiculo : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        RoteadorCadastro(this).verificar()
    }
}

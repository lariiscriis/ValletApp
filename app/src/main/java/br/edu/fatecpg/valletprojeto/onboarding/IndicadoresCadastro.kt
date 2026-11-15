package br.edu.fatecpg.valletprojeto.onboarding

import android.view.View
import android.widget.TextView
import br.edu.fatecpg.valletprojeto.R
import com.google.android.material.progressindicator.LinearProgressIndicator

object IndicadoresCadastro {
    /** Etapas 1..3; a barra representa as etapas já concluídas. */
    fun mostrar(root: View, etapa: Int, concluida: Boolean = false) {
        root.findViewById<View>(R.id.cadastro_progresso)?.visibility = View.VISIBLE
        root.findViewById<View>(R.id.cadastro_bolinhas)?.visibility = View.VISIBLE
        val nome = arrayOf("Início", "Conta", "Veículo")[etapa - 1]
        root.findViewById<TextView>(R.id.cadastro_etapa)?.text =
            if (concluida && etapa == 3) "Cadastro concluído" else "Etapa $etapa de 3 · $nome"
        root.findViewById<LinearProgressIndicator>(R.id.cadastro_barra)?.apply {
            val anterior = ((etapa - 2).coerceAtLeast(0) * 100) / 3
            val destino = ((etapa - 1 + if (concluida) 1 else 0) * 100) / 3
            if (!concluida) setProgressCompat(anterior, false)
            post { setProgressCompat(destino, true) }
        }
        listOf(R.id.cadastro_bolinha_inicio, R.id.cadastro_bolinha_conta, R.id.cadastro_bolinha_veiculo)
            .forEachIndexed { index, id ->
                root.findViewById<View>(id)?.apply {
                    setBackgroundResource(if (index + 1 <= etapa) R.drawable.bg_dot_active else R.drawable.bg_dot_inactive)
                    animate().scaleX(if (index + 1 == etapa) 1.25f else 1f)
                        .scaleY(if (index + 1 == etapa) 1.25f else 1f).setDuration(280).start()
                }
            }
    }
}

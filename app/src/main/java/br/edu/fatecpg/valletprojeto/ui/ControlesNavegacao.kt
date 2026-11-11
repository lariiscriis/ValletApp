package br.edu.fatecpg.valletprojeto.ui

import android.graphics.Rect
import android.view.TouchDelegate
import android.view.View

/** Amplia a região clicável sem alterar o desenho ou as dimensões do layout. */
fun View.prepararVoltar() {
    contentDescription = "Voltar"
    isFocusable = true
    val container = parent as? View ?: return
    container.post {
        val area = Rect()
        getHitRect(area)
        val minimo = (48 * resources.displayMetrics.density).toInt()
        val horizontal = ((minimo - area.width()) / 2).coerceAtLeast(0)
        val vertical = ((minimo - area.height()) / 2).coerceAtLeast(0)
        area.inset(-horizontal, -vertical)
        container.touchDelegate = TouchDelegate(area, this)
    }
}

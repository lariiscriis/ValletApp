package br.edu.fatecpg.valletprojeto.ui

import android.graphics.Rect
import android.view.View
import android.view.ViewParent
import android.view.ViewTreeObserver
import android.widget.EditText
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsAnimationCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.textfield.TextInputLayout

/** Um único proprietário dos insets por janela. Instalar uma vez por criação da view. */
object FormularioInsets {
    fun instalar(root: View) {
        val left = root.paddingLeft
        val top = root.paddingTop
        val right = root.paddingRight
        val bottom = root.paddingBottom
        val tipos = WindowInsetsCompat.Type.systemBars() or
            WindowInsetsCompat.Type.displayCutout() or WindowInsetsCompat.Type.ime()
        var animando = false
        var ultimo: WindowInsetsCompat? = null
        var imeVisivel = false

        fun revelarFoco() {
            if (!imeVisivel) return
            val campo = root.findFocus() as? EditText ?: return
            var alvo: View = campo
            var parent: ViewParent? = campo.parent
            while (parent is View && parent !== root) {
                if (parent is TextInputLayout) { alvo = parent; break }
                parent = parent.parent
            }
            if (alvo.width > 0 && alvo.height > 0) {
                alvo.requestRectangleOnScreen(Rect(0, 0, alvo.width, alvo.height), true)
            }
        }
        fun aplicar(insets: WindowInsetsCompat) {
            val seguro = insets.getInsets(tipos) // União = máximo, nunca soma IME + navegação.
            imeVisivel = insets.isVisible(WindowInsetsCompat.Type.ime())
            root.setPadding(left + seguro.left, top + seguro.top, right + seguro.right, bottom + seguro.bottom)
        }
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            ultimo = insets
            if (!animando) aplicar(insets)
            // Descendentes não devem reaplicar as áreas já tratadas nesta raiz.
            WindowInsetsCompat.Builder(insets).setInsets(tipos, Insets.NONE).build()
        }
        ViewCompat.setWindowInsetsAnimationCallback(root,
            object : WindowInsetsAnimationCompat.Callback(DISPATCH_MODE_CONTINUE_ON_SUBTREE) {
                override fun onPrepare(animation: WindowInsetsAnimationCompat) {
                    if (animation.typeMask and WindowInsetsCompat.Type.ime() != 0) animando = true
                }
                override fun onProgress(
                    insets: WindowInsetsCompat,
                    runningAnimations: MutableList<WindowInsetsAnimationCompat>
                ): WindowInsetsCompat {
                    if (animando) aplicar(insets)
                    return insets
                }
                override fun onEnd(animation: WindowInsetsAnimationCompat) {
                    if (animation.typeMask and WindowInsetsCompat.Type.ime() != 0) {
                        animando = false
                        ultimo?.let { aplicar(it) }
                        revelarFoco()
                    }
                }
            })
        // O layout também muda quando TextInputLayout passa a exibir a mensagem de erro.
        val layout = ViewTreeObserver.OnGlobalLayoutListener { revelarFoco() }
        val foco = ViewTreeObserver.OnGlobalFocusChangeListener { _, _ -> revelarFoco() }
        fun observar() {
            root.viewTreeObserver.addOnGlobalLayoutListener(layout)
            root.viewTreeObserver.addOnGlobalFocusChangeListener(foco)
            ViewCompat.requestApplyInsets(root)
        }
        root.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) = observar()
            override fun onViewDetachedFromWindow(v: View) {
                if (v.viewTreeObserver.isAlive) {
                    v.viewTreeObserver.removeOnGlobalLayoutListener(layout)
                    v.viewTreeObserver.removeOnGlobalFocusChangeListener(foco)
                }
            }
        })
        if (ViewCompat.isAttachedToWindow(root)) observar()
    }
}

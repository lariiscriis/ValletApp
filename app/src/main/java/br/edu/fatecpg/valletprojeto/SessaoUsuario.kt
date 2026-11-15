package br.edu.fatecpg.valletprojeto

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth

/** Encerra apenas a sessão. Dados e tarefas de reservas permanecem preservados. */
object SessaoUsuario {
    fun sair(activity: AppCompatActivity) {
        FirebaseAuth.getInstance().signOut()
        activity.startActivity(Intent(activity, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        activity.finish()
    }
}

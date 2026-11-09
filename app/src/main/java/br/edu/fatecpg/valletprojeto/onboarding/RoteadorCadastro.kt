package br.edu.fatecpg.valletprojeto.onboarding

import android.content.Intent
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import br.edu.fatecpg.valletprojeto.*
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/** Consulta cancelada com a Activity; a mesma política atende todos os pontos de entrada. */
class RoteadorCadastro(private val activity: AppCompatActivity) {
    private var consultando = false
    private var navegou = false
    private var erroDialog: AlertDialog? = null

    fun verificar(
        carregando: (Boolean) -> Unit = {},
        destinoAtual: (DestinoCadastro) -> Unit = { abrir(it) }
    ) {
        if (consultando || navegou || erroDialog?.isShowing == true || activity.isFinishing || activity.isDestroyed) return
        consultando = true
        carregando(true)
        activity.lifecycleScope.launch {
            try {
                val uid = FirebaseAuth.getInstance().currentUser?.uid
                val destino = withTimeout(20_000) { RepositorioCadastro().destino() }
                // Não navegar por uma Activity que já está coberta ou em segundo plano.
                activity.lifecycle.currentStateFlow
                    .first { it.isAtLeast(Lifecycle.State.RESUMED) }
                if (activity.isFinishing) return@launch
                check(FirebaseAuth.getInstance().currentUser?.uid == uid) { "A sessão foi alterada." }
                if (destino == DestinoCadastro.RESTRITO) {
                    FirebaseAuth.getInstance().signOut()
                    Toast.makeText(activity,
                        "Este aplicativo é exclusivo para motoristas. Administradores devem acessar o Desktop.",
                        Toast.LENGTH_LONG).show()
                    abrirLogin()
                } else {
                    destinoAtual(destino)
                }
            } catch (e: Exception) {
                if (e is CancellationException && e !is TimeoutCancellationException) throw e
                activity.lifecycle.currentStateFlow.first { it.isAtLeast(Lifecycle.State.RESUMED) }
                if (!activity.isFinishing && !activity.isDestroyed) {
                    erroDialog = AlertDialog.Builder(activity)
                        .setTitle("Não foi possível verificar o cadastro")
                        .setMessage("Confira sua conexão e tente novamente. Seu progresso foi mantido.")
                        .setPositiveButton("Tentar novamente") { _, _ ->
                            erroDialog = null
                            verificar(carregando, destinoAtual)
                        }
                        .setNegativeButton("Sair") { _, _ ->
                            erroDialog = null
                            FirebaseAuth.getInstance().signOut()
                            abrirLogin()
                        }
                        .setCancelable(false)
                        .show()
                }
            } finally {
                consultando = false
                carregando(false)
            }
        }
    }

    fun abrir(destino: DestinoCadastro) {
        if (navegou || activity.isFinishing || activity.isDestroyed) return
        val classe = when (destino) {
            DestinoCadastro.INICIO -> MainActivity::class.java
            DestinoCadastro.CONTA -> CadastroUsuarioActivity::class.java
            DestinoCadastro.FOTO -> CadastroFotoUsuarioActivity::class.java
            DestinoCadastro.VEICULO -> VeiculoActivity::class.java
            DestinoCadastro.DASHBOARD -> DashboardActivity::class.java
            DestinoCadastro.RESTRITO -> return
        }
        navegou = true
        activity.startActivity(Intent(activity, classe).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("email_usuario", FirebaseAuth.getInstance().currentUser?.email.orEmpty())
            if (destino == DestinoCadastro.VEICULO) {
                putExtra(VeiculoActivity.EXTRA_CADASTRO_INICIAL, true)
            }
        })
        activity.finish()
    }

    fun abrirLogin() {
        if (navegou || activity.isFinishing || activity.isDestroyed) return
        navegou = true
        activity.startActivity(Intent(activity, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        activity.finish()
    }
}

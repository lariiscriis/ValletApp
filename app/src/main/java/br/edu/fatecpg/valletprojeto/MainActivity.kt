package br.edu.fatecpg.valletprojeto

import android.content.Intent
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.work.WorkManager
import br.edu.fatecpg.valletprojeto.databinding.ActivityIntroBinding
import br.edu.fatecpg.valletprojeto.worker.NotificationUtils
import br.edu.fatecpg.valletprojeto.worker.VerificarReservasExpiradasWorker
import com.google.firebase.auth.FirebaseAuth
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityIntroBinding
    private lateinit var auth: FirebaseAuth
    private val roteador by lazy { br.edu.fatecpg.valletprojeto.onboarding.RoteadorCadastro(this) }
    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                NotificationUtils.createNotificationChannel(this)
            } else {
                Log.d("usuario","Otario")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityIntroBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                requestNotificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            } else {
                NotificationUtils.createNotificationChannel(this)
            }
        } else {
            NotificationUtils.createNotificationChannel(this)
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        binding.btnIniciar.setOnClickListener {
            binding.btnIniciar.isEnabled = false
            binding.tvLoginLink.isEnabled = false
            br.edu.fatecpg.valletprojeto.onboarding.IndicadoresCadastro.mostrar(binding.root, 1, true)
            lifecycleScope.launch {
                kotlinx.coroutines.delay(320)
                startActivity(Intent(this@MainActivity, CadastroUsuarioActivity::class.java))
            }
        }
        binding.tvLoginLink.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
        }
    }

    override fun onStart() {
        super.onStart()
        binding.btnIniciar.isEnabled = auth.currentUser == null
        binding.tvLoginLink.isEnabled = auth.currentUser == null
        if (auth.currentUser == null) {
            binding.progressOverlay.visibility = View.GONE
            br.edu.fatecpg.valletprojeto.onboarding.IndicadoresCadastro.mostrar(binding.root, 1)
        } else {
            binding.progressOverlay.visibility = View.VISIBLE
            roteador.verificar(destinoAtual = { destino ->
                roteador.abrir(destino)
            })
        }
    }

    // No seu Application class ou MainActivity
    private fun configurarVerificacaoPeriodica() {
        val constraints = androidx.work.Constraints.Builder()
            .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
            .build()

        val periodicWorkRequest = androidx.work.PeriodicWorkRequestBuilder<VerificarReservasExpiradasWorker>(
            15, TimeUnit.MINUTES // Verifica a cada 15 minutos
        )
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "verificar_reservas_expiradas",
            androidx.work.ExistingPeriodicWorkPolicy.KEEP,
            periodicWorkRequest
        )
    }
}

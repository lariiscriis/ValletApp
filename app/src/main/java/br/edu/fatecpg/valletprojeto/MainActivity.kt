package br.edu.fatecpg.valletprojeto

import android.content.Intent
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
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityIntroBinding
    private lateinit var auth: FirebaseAuth
    private val db = Firebase.firestore
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

        binding.progressOverlay.visibility = View.VISIBLE
        binding.btnIniciar.visibility = View.GONE

        val user = auth.currentUser
        if (user != null) {
            checkUserTypeAndRedirect(user.uid, user.email ?: "")
        } else {
            binding.progressOverlay.visibility = View.GONE
            binding.btnIniciar.visibility = View.VISIBLE

            binding.btnIniciar.setOnClickListener {
                val intent = Intent(this, LoginActivity::class.java)
                startActivity(intent)
                finish()
            }
        }
    }

    private fun checkUserTypeAndRedirect(uid: String, email: String) {
        db.collection("usuario").document(uid)
            .get(com.google.firebase.firestore.Source.SERVER)
            .addOnSuccessListener { document ->
                val tipoUsuario = document.getString("tipo_user")
                    ?.trim()?.lowercase(java.util.Locale.ROOT)
                if (document.exists() && tipoUsuario == "motorista") {
                    redirectToHome(email)
                } else {
                    android.widget.Toast.makeText(this, "Este aplicativo é exclusivo para motoristas. Administradores devem acessar o Desktop.",
                        android.widget.Toast.LENGTH_LONG).show()
                    auth.signOut()
                    goToLogin()
                }
            }
            .addOnFailureListener {
                auth.signOut()
                goToLogin()
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
    private fun redirectToHome(email: String) {
        val intent = Intent(this, DashboardActivity::class.java)
        intent.putExtra("email_usuario", email)
        startActivity(intent)
        finish()
    }

    private fun goToLogin() {
        binding.progressOverlay.visibility = View.GONE
        val intent = Intent(this, LoginActivity::class.java)
        startActivity(intent)
        finish()
    }
}

package br.edu.fatecpg.valletprojeto

import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import br.edu.fatecpg.valletprojeto.fragments.MotoristaFragment
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import androidx.lifecycle.lifecycleScope
import br.edu.fatecpg.valletprojeto.databinding.ActivityDashboardBinding
import br.edu.fatecpg.valletprojeto.fragments.EstacionamentosFragment
import br.edu.fatecpg.valletprojeto.fragments.VeiculoListFragment
import br.edu.fatecpg.valletprojeto.worker.NotificationUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Locale

class DashboardActivity : AppCompatActivity() {
    private lateinit var binding: ActivityDashboardBinding
    private val auth = FirebaseAuth.getInstance()

    private val globalVoiceLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val data = result.data
            val results = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val spokenText = results?.getOrNull(0)
            if (!spokenText.isNullOrBlank()) {
                handleGlobalVoiceCommand(spokenText)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        NotificationUtils.createNotificationChannel(this)
        carregarDadosUsuario()

        setupNavigation()
        loadInitialFragment()
        setupProfileClickListeners()

        binding.fabVoiceAssistant.setOnClickListener {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Diga um comando (ex: cancelar reserva, meus veículos, histórico)")
            }
            try {
                globalVoiceLauncher.launch(intent)
            } catch (_: Exception) {
                Toast.makeText(this, "Reconhecimento de voz não disponível", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Adicione esta opção no menu ou em uma tela de configurações
    private fun mostrarOpcaoDebugToken() {
        val intent = Intent(this, GerarTokenActivity::class.java)
        startActivity(intent)
    }

    private fun setupProfileClickListeners() {
        val tvUserName = findViewById<TextView>(R.id.tv_user_name)
        val ivProfileImage = findViewById<ImageView>(R.id.iv_profile_image)

        val clickListener = {
            startActivity(Intent(this, PerfilMotoristaActivity::class.java))
        }

        tvUserName.setOnClickListener { clickListener() }
        ivProfileImage.setOnClickListener { clickListener() }
        setupButtonListeners()
    }



    private fun setupButtonListeners() {
        binding.btnLogout.setOnClickListener {
            performLogout()
        }
    }

    private fun carregarDadosUsuario() {
        val email = FirebaseAuth.getInstance().currentUser?.email ?: return

        lifecycleScope.launch(Dispatchers.Main) {
            try {
                val usuarioSnapshot = FirebaseFirestore.getInstance()
                    .collection("usuario")
                    .whereEqualTo("email", email)
                    .get()
                    .await()

                if (!usuarioSnapshot.isEmpty) {
                    val userDoc = usuarioSnapshot.documents.first()
                    val nome = userDoc.getString("nome") ?: "Usuário"
                    val tipo = userDoc.getString("tipo_user") ?: "Motorista"
                    val saldo = userDoc.getDouble("saldo") ?: 0.0

                    findViewById<TextView>(R.id.tv_user_name).text = nome
                    findViewById<TextView>(R.id.tv_account_type).text = tipo.replaceFirstChar { it.uppercase() }

                    val fotoUrl = userDoc.getString("fotoPerfil")
                    if (!fotoUrl.isNullOrEmpty()) {
                        Glide.with(this@DashboardActivity)
                            .load(fotoUrl)
                            .placeholder(R.drawable.ic_default_profile)
                            .into(findViewById(R.id.iv_profile_image))

                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun setupNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_spots -> { replaceFragment(EstacionamentosFragment()); true }
                R.id.nav_dashboard -> { replaceFragment(MotoristaFragment()); true }
                R.id.nav_vehicles -> { replaceFragment(VeiculoListFragment()); true }
                else -> false
            }
        }
    }

    private fun loadInitialFragment() {
        binding.bottomNavigation.selectedItemId = R.id.nav_dashboard
    }

    private fun replaceFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.content_container, fragment)
            .commit()
    }

    private fun redirectToLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun performLogout() {
        auth.signOut()
        Toast.makeText(this, "Logout realizado com sucesso", Toast.LENGTH_SHORT).show()
        redirectToLogin()
    }

    private fun handleGlobalVoiceCommand(spokenText: String) {
        val command = spokenText.lowercase(Locale.getDefault())

        when {
            command.contains("cancel") -> {
                cancelarReservaAtivaGlobal()
            }
            command.contains("veículo") || command.contains("veiculo") || command.contains("carro") -> {
                binding.bottomNavigation.selectedItemId = R.id.nav_vehicles
                Toast.makeText(this, "Exibindo veículos", Toast.LENGTH_SHORT).show()
            }
            command.contains("histórico") || command.contains("historico") || command.contains("reservas") -> {
                startActivity(Intent(this, HistoricoReservasActivity::class.java))
            }
            command.contains("perfil") -> {
                startActivity(Intent(this, PerfilMotoristaActivity::class.java))
            }
            command.contains("sair") || command.contains("deslogar") || command.contains("logout") || command.contains("desconectar") -> {
                performLogout()
            }
            else -> {
                binding.bottomNavigation.selectedItemId = R.id.nav_spots
                Toast.makeText(this, "Comando não reconhecido, indo para estacionamentos...", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun cancelarReservaAtivaGlobal() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId == null) {
            Toast.makeText(this, "Usuário não autenticado", Toast.LENGTH_SHORT).show()
            return
        }

        Toast.makeText(this, "Cancelando reserva ativa por voz...", Toast.LENGTH_SHORT).show()
        val db = FirebaseFirestore.getInstance()
        db.collection("reserva")
            .whereEqualTo("usuarioId", userId)
            .whereEqualTo("status", "ativa")
            .limit(1)
            .get()
            .addOnSuccessListener { snapshot ->
                val doc = snapshot.documents.firstOrNull()
                if (doc != null) {
                    val reservaId = doc.id
                    val vagaId = doc.getString("vagaId")

                    val batch = db.batch()
                    batch.update(doc.reference, "status", "cancelada")
                    if (!vagaId.isNullOrEmpty()) {
                        val vagaRef = db.collection("vaga").document(vagaId)
                        batch.update(vagaRef, "disponivel", true)
                    }

                    batch.commit().addOnSuccessListener {
                        Toast.makeText(this, "Reserva cancelada com sucesso!", Toast.LENGTH_LONG).show()
                        try {
                            androidx.work.WorkManager.getInstance(this).cancelAllWorkByTag("reserva_$reservaId")
                        } catch (_: Exception) {}
                    }.addOnFailureListener { e ->
                        Toast.makeText(this, "Erro ao cancelar reserva: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this, "Nenhuma reserva ativa encontrada para cancelar", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Erro ao buscar reserva: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

}

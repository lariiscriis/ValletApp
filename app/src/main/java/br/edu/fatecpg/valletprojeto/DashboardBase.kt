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
import br.edu.fatecpg.valletprojeto.databinding.ActivityDashboardBaseBinding
import br.edu.fatecpg.valletprojeto.fragments.AdminFragment
import br.edu.fatecpg.valletprojeto.fragments.MotoristaFragment
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import androidx.lifecycle.lifecycleScope
import br.edu.fatecpg.valletprojeto.fragments.OccupationFragment
import br.edu.fatecpg.valletprojeto.fragments.SpotsFragment
import br.edu.fatecpg.valletprojeto.fragments.VagaFragment
import br.edu.fatecpg.valletprojeto.fragments.VeiculoListFragment
import br.edu.fatecpg.valletprojeto.worker.NotificationUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Locale


class DashboardBase : AppCompatActivity() {
    private lateinit var binding: ActivityDashboardBaseBinding
    private var isAdmin = true
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
        binding = ActivityDashboardBaseBinding.inflate(layoutInflater)
        setContentView(binding.root)



        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->

            val statusBar = insets.getInsets(
                androidx.core.view.WindowInsetsCompat.Type.statusBars()
            )

            val navigationBar = insets.getInsets(
                androidx.core.view.WindowInsetsCompat.Type.navigationBars()
            )

            // Evita adicionar o espaço inferior ao layout inteiro
            view.setPadding(
                view.paddingLeft,
                statusBar.top,
                view.paddingRight,
                0
            )

            // Mantém a tabbar acima dos botões do sistema
            val params = binding.bottomNavigation.layoutParams as
                    androidx.constraintlayout.widget.ConstraintLayout.LayoutParams

            params.bottomMargin = (12 * resources.displayMetrics.density).toInt() +
                    navigationBar.bottom

            binding.bottomNavigation.layoutParams = params

            insets
        }


        NotificationUtils.createNotificationChannel(this)
        verificarTipoUsuario()
        carregarDadosUsuario()

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
            if (isAdmin) {
                startActivity(Intent(this, PerfilAdminActivity::class.java))
            } else {
                startActivity(Intent(this, PerfilMotoristaActivity::class.java))
            }
        }

        tvUserName.setOnClickListener { clickListener() }
        ivProfileImage.setOnClickListener { clickListener() }
        setupButtonListeners()
    }

    private fun verificarTipoUsuario() {
        val email = FirebaseAuth.getInstance().currentUser?.email

        if (email == null) {
            Toast.makeText(this, "Usuário não autenticado.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        FirebaseFirestore.getInstance().collection("usuario")
            .whereEqualTo("email", email)
            .get()
            .addOnSuccessListener { documents ->
                if (!documents.isEmpty) {
                    val document = documents.first()
                    val tipo = document.getString("tipo_user") ?: "motorista"
                    isAdmin = tipo == "admin"
                    setupNavigation()
                    loadInitialFragment()
                } else {
                    Toast.makeText(this, "Perfil não encontrado.", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Erro ao verificar tipo de usuário.", Toast.LENGTH_SHORT).show()
                finish()
            }
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
                        Glide.with(this@DashboardBase)
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
        val menu = binding.bottomNavigation.menu

        menu.findItem(R.id.nav_vehicles).isVisible = !isAdmin
/*
        menu.findItem(R.id.nav_management).isVisible = isAdmin
*/

        val navSpots = menu.findItem(R.id.nav_spots)
        if (isAdmin) {
            navSpots.title = "Vagas Ocupadas"
            navSpots.setIcon(R.drawable.ic_parking)
        } else {
            navSpots.title = "Vagas"
            navSpots.setIcon(R.drawable.ic_parking)
        }

        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_spots -> {
                    if (isAdmin) replaceFragment(OccupationFragment())
                    else replaceFragment(SpotsFragment())
                    true
                }
                R.id.nav_dashboard -> {
                    if (isAdmin) replaceFragment(AdminFragment())
                    else replaceFragment(MotoristaFragment())
                    true
                }
                R.id.nav_placeholder -> {
                    // Item central reservado para o FAB de IA (Não faz nada ao clicar)
                    false
                }
                R.id.nav_vehicles -> {
                    replaceFragment(VeiculoListFragment())
                    true
                }
/*                R.id.nav_management -> {
                    if (isAdmin) replaceFragment(VagaFragment())
                    true
                }*/
                else -> false
            }
        }
    }

    private fun loadInitialFragment() {
        when (isAdmin) {
            true -> {
                replaceFragment(AdminFragment())
                binding.bottomNavigation.selectedItemId = R.id.nav_dashboard
            }
            false -> {
                replaceFragment(MotoristaFragment())
                binding.bottomNavigation.selectedItemId = R.id.nav_dashboard
            }
        }
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
                if (!isAdmin) {
                    binding.bottomNavigation.selectedItemId = R.id.nav_vehicles
                    Toast.makeText(this, "Exibindo veículos", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Comando disponível apenas para motoristas", Toast.LENGTH_SHORT).show()
                }
            }
            command.contains("histórico") || command.contains("historico") || command.contains("reservas") -> {
                startActivity(Intent(this, HistoricoReservasActivity::class.java))
            }
            command.contains("perfil") -> {
                if (isAdmin) {
                    startActivity(Intent(this, PerfilAdminActivity::class.java))
                } else {
                    startActivity(Intent(this, PerfilMotoristaActivity::class.java))
                }
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

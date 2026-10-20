package br.edu.fatecpg.valletprojeto

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.Patterns
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import br.edu.fatecpg.valletprojeto.databinding.ActivityLoginBinding
import com.google.android.gms.security.ProviderInstaller
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.messaging.FirebaseMessaging
import java.util.concurrent.Executor

class LoginActivity : AppCompatActivity(),
    ProviderInstaller.ProviderInstallListener {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var auth: FirebaseAuth

    private val db = Firebase.firestore

    private val mainHandler = Handler(Looper.getMainLooper())

    private var isLoading = false
    private var currentLoadingStep = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // =========================================================
        // CONFIGURAÇÃO DO FIRESTORE
        // =========================================================

        try {
            val settings = FirebaseFirestoreSettings.Builder()
                .setPersistenceEnabled(false)
                .build()

            db.firestoreSettings = settings

            Log.d(
                "Firestore",
                "✅ Persistência local do Firestore desabilitada."
            )

        } catch (e: Exception) {

            Log.e(
                "Firestore",
                "❌ Erro ao configurar FirestoreSettings: ${e.message}"
            )
        }

        // =========================================================
        // CONFIGURAÇÕES DO FIREBASE
        // =========================================================

        tryUpdateTlsProvider()
        blockFirebaseRecaptcha()

        // =========================================================
        // LAYOUT
        // =========================================================

        enableEdgeToEdge()

        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = Firebase.auth

        setupUI()
    }

    // =============================================================
    // CONFIGURAÇÃO DO TLS
    // =============================================================

    private fun tryUpdateTlsProvider() {

        ProviderInstaller.installIfNeededAsync(
            this,
            this
        )
    }

    override fun onProviderInstalled() {

        Log.d(
            "ProviderInstaller",
            "✅ TLS Provider atualizado com sucesso"
        )
    }

    override fun onProviderInstallFailed(
        errorCode: Int,
        recoveryIntent: Intent?
    ) {

        Log.w(
            "ProviderInstaller",
            "⚠️ Falha ao atualizar TLS Provider: $errorCode"
        )
    }

    // =============================================================
    // FIREBASE RECAPTCHA
    // =============================================================

    private fun blockFirebaseRecaptcha() {

        try {

            val field = FirebaseAuth::class.java
                .getDeclaredField("executor")

            field.isAccessible = true

            field.set(
                Firebase.auth,
                Executor { runnable ->
                    runnable.run()
                }
            )

        } catch (_: Exception) {
            // Mantém o comportamento original caso não seja possível
        }
    }

    // =============================================================
    // CONFIGURAÇÃO DA INTERFACE
    // =============================================================

    private fun setupUI() {

        setupWindowInsets()
        setupListeners()
    }

    // =============================================================
    // WINDOW INSETS
    // =============================================================

    private fun setupWindowInsets() {

        ViewCompat.setOnApplyWindowInsetsListener(
            findViewById(R.id.main)
        ) { v, insets ->

            val systemBars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars()
            )

            v.setPadding(
                systemBars.left,
                systemBars.top,
                systemBars.right,
                systemBars.bottom
            )

            insets
        }
    }

    // =============================================================
    // LISTENERS
    // =============================================================

    private fun setupListeners() {

        // Ir para cadastro
        binding.botaoCadastro.setOnClickListener {

            if (!isLoading) {

                val intent = Intent(
                    this,
                    CadastroUsuarioActivity::class.java
                )

                startActivity(intent)
            }
        }

        // Toggle Password Visibility
        var isPasswordVisible = false
        binding.senhaimglogin.setOnClickListener {
            isPasswordVisible = !isPasswordVisible
            if (isPasswordVisible) {
                binding.editTextSenha.transformationMethod = android.text.method.HideReturnsTransformationMethod.getInstance()
                binding.senhaimglogin.setImageResource(R.drawable.ic_eye)
            } else {
                binding.editTextSenha.transformationMethod = android.text.method.PasswordTransformationMethod.getInstance()
                binding.senhaimglogin.setImageResource(R.drawable.ic_eye_off)
            }
            binding.editTextSenha.setSelection(binding.editTextSenha.text?.length ?: 0)
        }

        // Botão de login
        binding.button3.setOnClickListener {

            if (isLoading) {
                return@setOnClickListener
            }

            val email = binding.editTextText
                .text
                .toString()
                .trim()

            val senha = binding.editTextSenha
                .text
                .toString()
                .trim()

            if (validateCredentials(email, senha)) {

                startLoginProcess(
                    email,
                    senha
                )
            }
        }
    }

    // =============================================================
    // INÍCIO DO LOGIN
    // =============================================================

    private fun startLoginProcess(
        email: String,
        senha: String
    ) {

        isLoading = true

        updateLoadingStep(
            "Verificando credenciais..."
        )

        showButtonLoading()
        disableUI()

        loginUser(
            email,
            senha
        )
    }

    // =============================================================
    // LOADING DO BOTÃO
    // =============================================================

    private fun showButtonLoading() {

        binding.button3.visibility = View.GONE

//        binding.loadingButtonLogin.visibility =
//            View.VISIBLE
    }

    private fun hideButtonLoading() {

        binding.button3.visibility =
            View.VISIBLE

//        binding.loadingButtonLogin.visibility =
//            View.GONE
    }

    // =============================================================
    // TEXTO DO LOADING
    // =============================================================

    private fun updateLoadingStep(step: String) {

        currentLoadingStep = step

        Log.d(
            "LoginLoading",
            step
        )
    }

    // =============================================================
    // DESABILITAR INTERFACE
    // =============================================================

    private fun disableUI() {

        binding.botaoCadastro.isEnabled = false

        binding.editTextText.isEnabled = false

        binding.editTextSenha.isEnabled = false
    }

    // =============================================================
    // HABILITAR INTERFACE
    // =============================================================

    private fun enableUI() {

        binding.botaoCadastro.isEnabled = true

        binding.editTextText.isEnabled = true

        binding.editTextSenha.isEnabled = true
    }

    // =============================================================
    // RESETAR LOADING
    // =============================================================

    private fun resetLoadingState() {

        isLoading = false

        hideButtonLoading()

        enableUI()
    }

    // =============================================================
    // VALIDAÇÃO
    // =============================================================

    private fun validateCredentials(
        email: String,
        senha: String
    ): Boolean {

        if (email.isBlank() || senha.isBlank()) {

            Toast.makeText(
                this,
                "Preencha todos os campos",
                Toast.LENGTH_SHORT
            ).show()

            return false
        }

        if (
            !Patterns.EMAIL_ADDRESS
                .matcher(email)
                .matches()
        ) {

            Toast.makeText(
                this,
                "Formato de email inválido",
                Toast.LENGTH_SHORT
            ).show()

            return false
        }

        if (senha.length < 6) {

            Toast.makeText(
                this,
                "A senha deve ter no mínimo 6 caracteres",
                Toast.LENGTH_SHORT
            ).show()

            return false
        }

        return true
    }

    // =============================================================
    // LOGIN NO FIREBASE AUTH
    // =============================================================

    private fun loginUser(
        email: String,
        senha: String
    ) {

        Log.d(
            "Login",
            "Tentando login: $email"
        )

        updateLoadingStep(
            "Conectando ao servidor..."
        )

        auth.signInWithEmailAndPassword(
            email,
            senha
        )
            .addOnSuccessListener {

                Log.d(
                    "Login",
                    "✅ Firebase Auth OK: ${it.user?.uid}"
                )

                updateLoadingStep(
                    "Autenticação realizada com sucesso!"
                )

                val user = auth.currentUser

                if (user != null) {

                    checkUser(
                        user.uid,
                        user.email ?: ""
                    )

                } else {

                    resetLoadingState()

                    Toast.makeText(
                        this,
                        "Erro ao obter usuário",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .addOnFailureListener { e ->

                Log.e(
                    "Login",
                    "❌ Erro no Firebase Auth: ${e.message}"
                )

                resetLoadingState()

                handleLoginError(e)
            }
    }

    // =============================================================
    // BUSCAR USUÁRIO NO FIRESTORE
    // =============================================================

    private fun checkUser(
        uid: String,
        email: String
    ) {

        Log.d(
            "Firestore",
            "Buscando usuário por UID: $uid"
        )

        updateLoadingStep(
            "Buscando dados do usuário..."
        )

        var isTimedOut = false

        val timeoutRunnable = Runnable {

            isTimedOut = true
            auth.signOut()
            resetLoadingState()

            AlertDialog.Builder(this)
                .setTitle("⏱️ Tempo Esgotado")
                .setMessage(
                    "A conexão está muito lenta. " +
                            "Deseja tentar novamente?"
                )
                .setPositiveButton("Tentar Novamente") { _, _ ->

                    val emailAtual = binding.editTextText
                        .text
                        .toString()
                        .trim()

                    val senhaAtual = binding.editTextSenha
                        .text
                        .toString()
                        .trim()

                    if (
                        validateCredentials(
                            emailAtual,
                            senhaAtual
                        )
                    ) {
                        startLoginProcess(
                            emailAtual,
                            senhaAtual
                        )
                    }
                }
                .setNegativeButton("Cancelar") { _, _ ->

                    auth.signOut()
                }
                .setCancelable(false)
                .show()
        }

        mainHandler.postDelayed(
            timeoutRunnable,
            20000
        )

        db.collection("usuario")
            .document(uid)
            .get(com.google.firebase.firestore.Source.SERVER)
            .addOnSuccessListener { documentSnapshot ->

                mainHandler.removeCallbacks(
                    timeoutRunnable
                )

                if (isTimedOut) {

                    Log.w(
                        "Firestore",
                        "⚠️ Resposta recebida após timeout"
                    )

                    return@addOnSuccessListener
                }

                updateLoadingStep(
                    "Processando informações..."
                )

                if (!documentSnapshot.exists()) {

                    Log.e(
                        "Firestore",
                        "❌ Nenhum usuário encontrado com UID: $uid"
                    )

                    resetLoadingState()

                    handleUserNotFound()

                    return@addOnSuccessListener
                }

                Log.d(
                    "Firestore",
                    "✅ Usuário encontrado! ID: ${documentSnapshot.id}"
                )

                updateLoadingStep(
                    "Preparando ambiente..."
                )

                val tipoUsuario = documentSnapshot.getString("tipo_user")
                    ?.trim()?.lowercase(java.util.Locale.ROOT)
                if (tipoUsuario != "motorista") {
                    auth.signOut()
                    resetLoadingState()
                    AlertDialog.Builder(this)
                        .setTitle("Acesso restrito")
                        .setMessage("Este aplicativo é exclusivo para motoristas. Administradores devem acessar o Desktop.")
                        .setPositiveButton("OK", null)
                        .show()
                    return@addOnSuccessListener
                }
                if (auth.currentUser?.uid != uid) return@addOnSuccessListener

                redirectToHome(
                    uid,
                    email
                )
            }
            .addOnFailureListener { e ->

                mainHandler.removeCallbacks(
                    timeoutRunnable
                )

                if (isTimedOut) {

                    Log.w(
                        "Firestore",
                        "⚠️ Erro recebido após timeout"
                    )

                    return@addOnFailureListener
                }

                auth.signOut()
                resetLoadingState()

                Log.e(
                    "Firestore",
                    "❌ Erro na consulta: ${e.message}"
                )

                val errorMsg = when {

                    e.message?.contains(
                        "offline",
                        ignoreCase = true
                    ) == true ->
                        "Sem conexão com a internet"

                    e.message?.contains(
                        "permission",
                        ignoreCase = true
                    ) == true ->
                        "🔒 Permissão negada. Verifique as regras de segurança."

                    e.message?.contains(
                        "deadline",
                        ignoreCase = true
                    ) == true ->
                        "Servidor não respondeu a tempo"

                    else ->
                        "Erro ao conectar: ${e.message}"
                }

                AlertDialog.Builder(this)
                    .setTitle("Erro de Conexão")
                    .setMessage(
                        "$errorMsg\n\nDeseja tentar novamente?"
                    )
                    .setPositiveButton(
                        "Tentar Novamente"
                    ) { _, _ ->

                        val emailAtual =
                            binding.editTextText
                                .text
                                .toString()
                                .trim()

                        val senhaAtual =
                            binding.editTextSenha
                                .text
                                .toString()
                                .trim()

                        if (
                            validateCredentials(
                                emailAtual,
                                senhaAtual
                            )
                        ) {

                            startLoginProcess(
                                emailAtual,
                                senhaAtual
                            )
                        }
                    }
                    .setNegativeButton(
                        "Cancelar"
                    ) { _, _ ->

                        auth.signOut()
                    }
                    .setCancelable(false)
                    .show()
            }
    }

    // =============================================================
    // USUÁRIO NÃO ENCONTRADO
    // =============================================================

    private fun handleUserNotFound() {

        auth.signOut()

        AlertDialog.Builder(this)
            .setTitle("Usuário Não Encontrado")
            .setMessage(
                "Não foi possível encontrar seus dados " +
                        "no sistema. Deseja fazer o cadastro?"
            )
            .setPositiveButton("Fazer Cadastro") { _, _ ->

                startActivity(
                    Intent(
                        this,
                        CadastroUsuarioActivity::class.java
                    )
                )
            }
            .setNegativeButton(
                "Cancelar",
                null
            )
            .show()
    }

    // =============================================================
    // REDIRECIONAR PARA HOME
    // =============================================================

    private fun redirectToHome(
        uid: String,
        email: String
    ) {

        Log.d(
            "LOGIN",
            "🚀 Iniciando processo de redirecionamento"
        )

        updateLoadingStep(
            "Obtendo permissões de notificação..."
        )


        // Gera e salva o token antes de navegar
        gerarESalvarTokenFCM(uid) {
            if (auth.currentUser?.uid != uid || isFinishing || isDestroyed) return@gerarESalvarTokenFCM

            val intent = Intent(
                this,
                DashboardActivity::class.java
            )

            intent.putExtra(
                "email_usuario",
                email
            )

            startActivity(intent)

            finish()
        }
    }

    // =============================================================
    // GERAR TOKEN FCM
    // =============================================================

    private fun gerarESalvarTokenFCM(
        uid: String,
        onComplete: () -> Unit
    ) {

        Log.d(
            "FCM",
            "🔄 Iniciando geração do token FCM para UID: $uid"
        )

        FirebaseMessaging
            .getInstance()
            .token
            .addOnCompleteListener { task ->

                if (task.isSuccessful) {

                    val token = task.result

                    Log.d(
                        "FCM",
                        "✅ Token FCM gerado: ${token.take(10)}..."
                    )

                    salvarTokenNoFirestore(
                        uid,
                        token,
                        onComplete
                    )

                } else {

                    Log.e(
                        "FCM",
                        "❌ Falha ao gerar token FCM",
                        task.exception
                    )

                    // Mesmo se falhar, continua para o Dashboard
                    onComplete()
                }
            }
    }

    // =============================================================
    // SALVAR TOKEN NO FIRESTORE
    // =============================================================

    private fun salvarTokenNoFirestore(
        uid: String,
        token: String,
        onComplete: () -> Unit
    ) {

        val db = FirebaseFirestore.getInstance()

        val tokenData = hashMapOf(
            "fcm_token" to token,
            "ultima_atualizacao_token" to
                    com.google.firebase.Timestamp.now()
        )

        db.collection("usuario")
            .document(uid)
            .set(
                tokenData,
                com.google.firebase.firestore.SetOptions.merge()
            )
            .addOnSuccessListener {

                Log.d(
                    "FCM",
                    "✅ Token FCM salvo com sucesso para UID: $uid"
                )

                onComplete()
            }
            .addOnFailureListener { e ->

                Log.e(
                    "FCM",
                    "❌ Erro ao salvar Token FCM",
                    e
                )

                // Não trava o usuário caso o token falhe
                onComplete()
            }
    }

    // =============================================================
    // VERIFICAR TOKEN SALVO
    // =============================================================

    private fun verificarTokenSalvo(uid: String) {

        val db = FirebaseFirestore.getInstance()

        db.collection("usuario")
            .document(uid)
            .get(com.google.firebase.firestore.Source.SERVER)
            .addOnSuccessListener { document ->

                val tokenSalvo =
                    document.getString("fcm_token")

                if (tokenSalvo != null) {

                    Log.d(
                        "FCM",
                        "✅ Token confirmado no Firestore: " +
                                "${tokenSalvo.take(10)}..."
                    )

                } else {

                    Log.e(
                        "FCM",
                        "❌ Token NÃO foi salvo no Firestore!"
                    )
                }
            }
            .addOnFailureListener { e ->

                Log.e(
                    "FCM",
                    "❌ Erro ao verificar token salvo",
                    e
                )
            }
    }

    // =============================================================
    // ERRO DE LOGIN
    // =============================================================

    private fun handleLoginError(
        e: Exception?
    ) {

        val msg =
            e?.message ?: "Erro no login"

        val out = when {

            msg.contains(
                "badly formatted",
                ignoreCase = true
            ) ->
                "Email inválido"

            msg.contains(
                "password is invalid",
                ignoreCase = true
            ) ->
                "Senha incorreta"

            msg.contains(
                "no user record",
                ignoreCase = true
            ) ->
                "Usuário não encontrado"

            msg.contains(
                "network",
                ignoreCase = true
            ) ->
                "Erro de conexão. Verifique sua internet"

            msg.contains(
                "too many requests",
                ignoreCase = true
            ) ->
                "Muitas tentativas. Aguarde um momento"

            else ->
                "Erro no login: $msg"
        }

        Toast.makeText(
            this,
            out,
            Toast.LENGTH_LONG
        ).show()
    }

    // =============================================================
    // VERIFICAR SESSÃO EXISTENTE
    // =============================================================

    override fun onStart() {

        super.onStart()

        val current =
            FirebaseAuth
                .getInstance()
                .currentUser

        if (current != null) {

            Log.d(
                "Login",
                "🔄 Usuário já logado, verificando sessão..."
            )

            updateLoadingStep(
                "Restaurando sua sessão..."
            )

            mainHandler.postDelayed({

                if (isLoading) {

                    resetLoadingState()

                    AlertDialog.Builder(this)
                        .setTitle("Sessão Expirada")
                        .setMessage(
                            "Não foi possível restaurar sua sessão. " +
                                    "Faça login novamente."
                        )
                        .setPositiveButton("OK") { _, _ ->

                            auth.signOut()
                        }
                        .setCancelable(false)
                        .show()
                }

            }, 25000)

            checkUser(
                current.uid,
                current.email ?: ""
            )

        } else {

            isLoading = false
        }
    }

    // =============================================================
    // DESTRUIÇÃO DA ACTIVITY
    // =============================================================

    override fun onDestroy() {

        super.onDestroy()

        mainHandler.removeCallbacksAndMessages(
            null
        )
    }
}

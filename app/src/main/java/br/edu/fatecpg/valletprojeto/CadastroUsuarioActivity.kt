package br.edu.fatecpg.valletprojeto

import android.os.Bundle
import br.edu.fatecpg.valletprojeto.ui.prepararVoltar
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import br.edu.fatecpg.valletprojeto.databinding.ActivityCadastroUsuarioBinding
import br.edu.fatecpg.valletprojeto.model.Usuario
import br.edu.fatecpg.valletprojeto.onboarding.*
import br.edu.fatecpg.valletprojeto.viewmodel.CadastroViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class CadastroUsuarioActivity : AppCompatActivity() {
    private lateinit var binding: ActivityCadastroUsuarioBinding
    private val viewModel: CadastroViewModel by viewModels()
    private val roteador by lazy { RoteadorCadastro(this) }
    private var verificando = false
    private var avancando = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityCadastroUsuarioBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.btnVoltar.prepararVoltar()
        br.edu.fatecpg.valletprojeto.ui.FormularioInsets.instalar(binding.root)
        IndicadoresCadastro.mostrar(binding.root, 2)
        binding.botaoLogin.setOnClickListener {
            if (!ocupado()) {
                SessaoUsuario.sair(this)
            }
        }
        binding.btnVoltar.setOnClickListener { onBackPressedDispatcher.onBackPressed() }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (!ocupado()) {
                    if (FirebaseAuth.getInstance().currentUser != null) moveTaskToBack(true)
                    else finish()
                }
            }
        })
        binding.btnCadastrar.setOnClickListener { cadastrar() }
        viewModel.estado.observe(this) { estado ->
            atualizarCarregamento()
            ajustarSessao()
            estado.erro?.let { Toast.makeText(this, it, Toast.LENGTH_LONG).show() }
            if (estado.sucesso && !avancando) {
                avancando = true
                IndicadoresCadastro.mostrar(binding.root, 2)
                lifecycleScope.launch {
                    delay(320)
                    roteador.verificar(::carregando)
                }
            }
        }
        ajustarSessao()
        if (FirebaseAuth.getInstance().currentUser != null && viewModel.estado.value?.salvando != true &&
            viewModel.estado.value?.sucesso != true) {
            roteador.verificar(::carregando) { destino ->
                if (destino == DestinoCadastro.CONTA) {
                    // Campos digitados/restaurados pelo Android têm prioridade.
                    val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return@verificar
                    FirebaseFirestore.getInstance().collection("usuario").document(uid)
                        .get(Source.CACHE).addOnSuccessListener { perfil ->
                            if (isDestroyed || isFinishing) return@addOnSuccessListener
                            if (binding.edtNome.text.isNullOrBlank()) binding.edtNome.setText(perfil.getString("nome"))
                            if (binding.edtCnh.text.isNullOrBlank()) binding.edtCnh.setText(perfil.getString("cnh"))
                            if (binding.edtTelefone.text.isNullOrBlank()) binding.edtTelefone.setText(perfil.getString("telefone"))
                        }
                } else roteador.abrir(destino)
            }
        }
    }

    private fun ocupado() = verificando || avancando || viewModel.estado.value?.salvando == true

    private fun carregando(valor: Boolean) {
        verificando = valor
        atualizarCarregamento()
    }

    private fun atualizarCarregamento() {
        val ocupado = ocupado()
        binding.progressOverlay.visibility = if (ocupado && !avancando) View.VISIBLE else View.GONE
        binding.btnCadastrar.isEnabled = !ocupado
        binding.botaoLogin.isEnabled = !ocupado
    }

    private fun ajustarSessao() {
        val atual = FirebaseAuth.getInstance().currentUser
        binding.edtEmail.isEnabled = atual == null
        binding.edtSenha.isEnabled = atual == null
        (binding.edtSenha.parent as View).visibility = if (atual == null) View.VISIBLE else View.GONE
        if (atual != null) {
            binding.edtEmail.setText(atual.email)
            binding.btnCadastrar.text = "Continuar →"
        }
    }

    private fun cadastrar() {
        if (ocupado()) return
        val usuario = Usuario(
            email = binding.edtEmail.text.toString().trim(),
            nome = binding.edtNome.text.toString().trim(),
            cnh = binding.edtCnh.text.toString().trim(),
            senha = binding.edtSenha.text.toString().trim(),
            telefone = ValidacaoCadastro.telefoneNormalizado(binding.edtTelefone.text.toString()),
            tipoUser = "motorista"
        )
        if (usuario.email.isBlank() || usuario.nome.isBlank() || usuario.cnh.isNullOrBlank() ||
            (FirebaseAuth.getInstance().currentUser == null && usuario.senha.isBlank())) {
            Toast.makeText(this, "Preencha todos os campos obrigatórios", Toast.LENGTH_SHORT).show()
            return
        }
        if (!ValidacaoCadastro.cnhValida(usuario.cnh.orEmpty())) {
            binding.edtCnh.error = "CNH inválida. Confira os 11 dígitos do número de registro."
            binding.edtCnh.requestFocus()
            return
        }
        binding.edtCnh.error = null
        if (!ValidacaoCadastro.telefoneValido(binding.edtTelefone.text.toString())) {
            binding.edtTelefone.error = "Informe DDD e telefone válido (10 ou 11 números)"
            binding.edtTelefone.requestFocus()
            return
        }
        viewModel.cadastrarUsuario(usuario)
    }
}

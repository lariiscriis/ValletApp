package br.edu.fatecpg.valletprojeto

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import br.edu.fatecpg.valletprojeto.databinding.ActivityCadastroBinding
import br.edu.fatecpg.valletprojeto.model.Usuario
import br.edu.fatecpg.valletprojeto.viewmodel.CadastroViewModel

class CadastroActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCadastroBinding
    private val viewModel: CadastroViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        binding = ActivityCadastroBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())

            v.setPadding(
                systemBars.left,
                systemBars.top,
                systemBars.right,
                systemBars.bottom
            )

            insets
        }

        setupListeners()
    }

    private fun setupListeners() {

        // Voltar para o Login
        binding.botaoLogin.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }

        // Cadastrar usuário
        binding.btnCadastrar.setOnClickListener {

            val usuario = Usuario(
                email = binding.edtEmail.text.toString().trim(),
                cnh = binding.edtCnh.text.toString().trim(),
                nome = binding.edtNome.text.toString().trim(),
                senha = binding.edtSenha.text.toString().trim(),
                tipoUser = "motorista"
            )

            cadastrar(usuario)
        }
    }

    private fun cadastrar(usuario: Usuario) {

        if (
            usuario.email.isEmpty() ||
            usuario.senha.isEmpty() ||
            usuario.nome.isEmpty() ||
            usuario.cnh!!.isEmpty()
        ) {
            Toast.makeText(
                this,
                "Preencha todos os campos obrigatórios",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        binding.progressOverlay.visibility = View.VISIBLE

        viewModel.cadastrarUsuario(
            usuario,

            onSuccess = {
                binding.progressOverlay.visibility = View.GONE

                Toast.makeText(
                    this,
                    "Cadastro realizado com sucesso!",
                    Toast.LENGTH_SHORT
                ).show()

                startActivity(
                    Intent(this, LoginActivity::class.java)
                )

                finish()
            },

            onError = { msg ->

                binding.progressOverlay.visibility = View.GONE

                Toast.makeText(
                    this,
                    msg ?: "Erro ao cadastrar usuário",
                    Toast.LENGTH_SHORT
                ).show()
            }
        )
    }
}

package br.edu.fatecpg.valletprojeto

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import br.edu.fatecpg.valletprojeto.databinding.ActivityPerfilMotoristaBinding
import br.edu.fatecpg.valletprojeto.viewmodel.PerfilMotoristaViewModel
import com.bumptech.glide.Glide

class PerfilActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPerfilMotoristaBinding
    private lateinit var viewModel: PerfilMotoristaViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityPerfilMotoristaBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.hide()

        binding.btnVoltar.setOnClickListener {
            finish()
        }


        viewModel = ViewModelProvider(this).get(PerfilMotoristaViewModel::class.java)
        binding.btnEditarPerfil.setOnClickListener {
            startActivity(Intent(this, EditarPerfilActivity::class.java))
        }

        setupObservers()
        setupListeners()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun setupObservers() {
        viewModel.uiState.observe(this) { state ->

            binding.progressOverlay.visibility =
                if (state.isLoading) View.VISIBLE else View.GONE

            binding.scrollView.visibility =
                if (state.isLoading || state.errorMessage != null) View.GONE else View.VISIBLE

            if (state.errorMessage != null) {
                Toast.makeText(this, state.errorMessage, Toast.LENGTH_LONG).show()
            }

            if (!state.fotoPerfilUrl.isNullOrEmpty()) {
                Glide.with(this)
                    .load(state.fotoPerfilUrl)
                    .placeholder(R.drawable.homemfundo2)
                    .error(R.drawable.homemfundo2)
                    .into(binding.imgFotoPerfil)
            } else {
                binding.imgFotoPerfil.setImageResource(R.drawable.homemfundo2)
            }

            binding.txtNome.text = state.nome
            binding.txtEmail.text = state.email
            binding.txtTipoConta.text = state.tipoUser

            binding.txtInfoNome.text = state.nome
            binding.txtInfoEmail.text = state.email

            binding.txtTelefone.text = state.telefone.ifEmpty { "Não informado" }
            binding.txtCnh.text = state.cnh.ifEmpty { "Não informado" }
        }
    }
    private fun setupListeners() {
/*        binding.btnVerHistorico.setOnClickListener {
            startActivity(Intent(this, HistoricoReservasActivity::class.java))
        }*/
    }

    override fun onResume() {
        super.onResume()
        viewModel.carregarDadosPerfil()
    }
}

package br.edu.fatecpg.valletprojeto

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import br.edu.fatecpg.valletprojeto.databinding.ActivityEditarVeiculoBinding
import com.google.firebase.firestore.FirebaseFirestore

class EditarVeiculoActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEditarVeiculoBinding
    private val db = FirebaseFirestore.getInstance()
    private var veiculoId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditarVeiculoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        veiculoId = intent.getStringExtra("veiculoId")

        setupDropdowns()

        preencherCampos()

        binding.ivBack.setOnClickListener { finish() }
        binding.btnConfirmar.setOnClickListener { atualizarVeiculo() }
        binding.btnExcluir.setOnClickListener { confirmarExclusao() }
    }

    private fun setupDropdowns() {
        val tipos = arrayOf("Carro", "Moto")
        val tipoAdapter = ArrayAdapter(this, R.layout.item_dropdown, tipos)
        binding.editTipo.setAdapter(tipoAdapter)

        val cores = arrayOf("Branco", "Preto", "Prata", "Cinza", "Vermelho", "Azul", "Outra")
        val corAdapter = ArrayAdapter(this, R.layout.item_dropdown, cores)
        binding.editCor.setAdapter(corAdapter)
    }

    private fun preencherCampos() {
        val placa = intent.getStringExtra("placa")
        val marca = intent.getStringExtra("marca")
        val modelo = intent.getStringExtra("modelo")
        val apelido = intent.getStringExtra("apelido")
        val ano = intent.getStringExtra("ano")
        val isPrincipal = intent.getBooleanExtra("padrao", false)

        val tipoOriginal = intent.getStringExtra("tipo")
        val tipoFormatado = tipoOriginal?.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() } ?: "Carro"

        val corOriginal = intent.getStringExtra("cor")
        val corFormatada = if (!corOriginal.isNullOrEmpty()) {
            corOriginal.trim().replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        } else {
            "Outra"
        }

        binding.editPlaca.setText(placa)
        binding.editMarca.setText(marca)
        binding.editModelo.setText(modelo)
        binding.editApelido.setText(apelido)
        binding.editAno.setText(ano)

        binding.editTipo.setText(tipoFormatado, false)
        binding.editCor.setText(corFormatada, false)

        binding.llBadgePrincipal.visibility = if (isPrincipal) View.VISIBLE else View.GONE

        if (tipoFormatado.equals("Moto", ignoreCase = true)) {
            binding.ivVehiclePhoto.setImageResource(R.drawable.moto_option_resized)
        } else {
            binding.ivVehiclePhoto.setImageResource(R.drawable.carro_option_resized)
        }

        Log.d("EditarVeiculo", "cor recebida = '${intent.getStringExtra("cor")}'")
    }

    private fun confirmarExclusao() {
        AlertDialog.Builder(this)
            .setTitle("Excluir veículo")
            .setMessage("Tem certeza que deseja excluir este veículo? Esta ação não pode ser desfeita.")
            .setPositiveButton("Excluir") { _, _ -> excluirVeiculo() }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun excluirVeiculo() {
        if (veiculoId.isNullOrEmpty()) {
            Toast.makeText(this, "ID do veículo não encontrado", Toast.LENGTH_SHORT).show()
            return
        }

        db.collection("veiculo").document(veiculoId!!)
            .delete()
            .addOnSuccessListener {
                Toast.makeText(this, "Veículo excluído com sucesso!", Toast.LENGTH_SHORT).show()
                setResult(RESULT_OK)
                finish()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Erro ao excluir veículo: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun atualizarVeiculo() {
        val novaPlaca = binding.editPlaca.text.toString().trim()
        val novaMarca = binding.editMarca.text.toString().trim()
        val novoModelo = binding.editModelo.text.toString().trim()
        val novoApelido = binding.editApelido.text.toString().trim()
        val novoAno = binding.editAno.text.toString().trim()
        val novoTipo = binding.editTipo.text.toString().trim().lowercase()

        val novaCor = if (binding.editCor.text.toString().trim().isEmpty()) "Outra" else binding.editCor.text.toString().trim()

        if (veiculoId.isNullOrEmpty()) {
            Toast.makeText(this, "ID do veículo não encontrado", Toast.LENGTH_SHORT).show()
            return
        }

        if (novaPlaca.isEmpty() || novaMarca.isEmpty() || novoModelo.isEmpty() || novoAno.isEmpty() || novoTipo.isEmpty()) {
            val camposVazios = mutableListOf<String>()
            if (novaPlaca.isEmpty()) camposVazios.add("Placa")
            if (novaMarca.isEmpty()) camposVazios.add("Marca")
            if (novoModelo.isEmpty()) camposVazios.add("Modelo")
            if (novoAno.isEmpty()) camposVazios.add("Ano")
            if (novoTipo.isEmpty()) camposVazios.add("Tipo")

            Toast.makeText(this, "Preencha: ${camposVazios.joinToString(", ")}", Toast.LENGTH_LONG).show()
            return
        }

        val veiculoAtualizado = mapOf(
            "placa" to novaPlaca,
            "marca" to novaMarca,
            "modelo" to novoModelo,
            "apelido" to novoApelido,
            "ano" to novoAno,
            "tipo" to novoTipo,
            "cor" to novaCor
        )

        db.collection("veiculo").document(veiculoId!!)
            .update(veiculoAtualizado)
            .addOnSuccessListener {
                Toast.makeText(this, "Veículo atualizado com sucesso!", Toast.LENGTH_SHORT).show()
                setResult(RESULT_OK)
                finish()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Erro ao atualizar veículo: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }
}
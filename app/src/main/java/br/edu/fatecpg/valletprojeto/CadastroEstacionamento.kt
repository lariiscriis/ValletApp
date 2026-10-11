package br.edu.fatecpg.valletprojeto

import android.content.Intent
import android.location.Geocoder
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import br.edu.fatecpg.valletprojeto.databinding.ActivityCadastroEstacionamentoBinding
import br.edu.fatecpg.valletprojeto.model.Estacionamento
import br.edu.fatecpg.valletprojeto.viewmodel.EstacionamentoViewModel
import java.util.Locale

class CadastroEstacionamento : AppCompatActivity() {
    private lateinit var binding: ActivityCadastroEstacionamentoBinding
    private val vm: EstacionamentoViewModel by viewModels()

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        binding = ActivityCadastroEstacionamentoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnCadastrar.setOnClickListener {
            val cep = binding.edtCep.text.toString().trim()

            if (cep.isEmpty()) {
                Toast.makeText(this, "Digite um CEP válido", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            binding.progressOverlay.visibility = View.VISIBLE

            vm.buscarCep(
                cep,
                onSuccess = { endereco ->

                    val enderecoParaGeocoder = "${endereco.logradouro}, ${endereco.bairro}, ${endereco.localidade} - ${endereco.uf}"

                    val geocoder = Geocoder(this, Locale.getDefault())
                    val resultados = try {
                        geocoder.getFromLocationName(enderecoParaGeocoder, 1)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        null
                    }
                    val mapaEndereco = hashMapOf(
                        "cep" to endereco.cep,
                        "logradouro" to endereco.logradouro,
                        "complemento" to "",
                        "numero" to "",
                        "bairro" to endereco.bairro,
                        "cidade" to endereco.localidade,
                        "uf" to endereco.uf
                    )


                    val est = Estacionamento(
                        nome = binding.edtNomeEstacionamento.text.toString().trim(),
                        cnpj = binding.edtCnpj.text.toString().trim(),
                        telefone = binding.edtTelefone.text.toString().trim(),
                        endereco = mapaEndereco,
                        cep = endereco.cep,
                        quantidadeVagasTotal = binding.edtVagasTotal.text.toString().toIntOrNull() ?: 0,
                        tiposVagasComum = true,
                        tiposVagasIdosoPcd = false,
                        quantidadeVagasComum = null,
                        quantidadeVagasIdosoPcd = null,
                        possuiCobertura = false,
                        numeroPavimentos = null,
                        valorHora = binding.edtValorHora.text.toString().toDoubleOrNull() ?: 0.0,
                        valorDiario = 0.0,
                        tempoMaxReservaHoras = 2,
                        toleranciaReservaMinutos = 0,
                        fotoEstacionamentoUri = null,
                        horarioAbertura = formatarHora(binding.edtAbertura.text.toString()),
                        horarioFechamento = formatarHora(binding.edtFechamento.text.toString()),
                        latitude = resultados?.firstOrNull()?.latitude ?: 0.0,
                        longitude = resultados?.firstOrNull()?.longitude ?: 0.0
                    )

                    vm.cadastrar(
                        est,
                        onSuccess = {
                            Toast.makeText(this, "Estacionamento cadastrado!", Toast.LENGTH_SHORT).show()
                            startActivity(Intent(this, DashboardBase::class.java))
                            finish()
                        },
                        onFailure = { msg ->
                            Toast.makeText(this, "Erro: $msg", Toast.LENGTH_LONG).show()
                        }
                    )
                },
                onError = { msg ->
                    binding.progressOverlay.visibility = View.GONE
                    Toast.makeText(this, "Erro ao buscar endereço: $msg", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }

    private fun formatarHora(horaTexto: String): String {
        val textoLimpo = horaTexto.replace(":", "").trim()
        if (textoLimpo.isEmpty()) return "00:00"
        val horaPad = textoLimpo.padStart(4, '0')
        val horas = horaPad.substring(0, 2)
        val minutos = horaPad.substring(2, 4)
        return "$horas:$minutos"
    }
}
package br.edu.fatecpg.valletprojeto

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import br.edu.fatecpg.valletprojeto.adapter.ListaVagasAdapter
import br.edu.fatecpg.valletprojeto.dao.ListagemVagasDao
import br.edu.fatecpg.valletprojeto.databinding.ActivityPaginaDeReservaVagasBinding

class PaginaDeReservaVagas : AppCompatActivity() {

    private lateinit var binding: ActivityPaginaDeReservaVagasBinding
    private lateinit var parkingSpotDao: ListagemVagasDao

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPaginaDeReservaVagasBinding.inflate(layoutInflater)
        setContentView(binding.root)

        parkingSpotDao = ListagemVagasDao()

        binding.rvParkingSpots.layoutManager = GridLayoutManager(this, 3)

        parkingSpotDao.listarTodasAsVagas(
            onSuccess = { vagas ->
                runOnUiThread {
                    val adapter = ListaVagasAdapter(vagas) { vagaSelecionada ->
                        val estacionamentoId = vagaSelecionada.estacionamentoId
                        if (vagaSelecionada.id.isBlank() || estacionamentoId.isBlank()) {
                            Toast.makeText(
                                this,
                                "Não foi possível identificar a vaga ou seu estacionamento.",
                                Toast.LENGTH_LONG
                            ).show()
                            return@ListaVagasAdapter
                        }

                        val intent = Intent(this, ReservaActivity::class.java).apply {
                            putExtra("vagaId", vagaSelecionada.id)
                            putExtra("estacionamentoId", estacionamentoId)
                        }
                        startActivity(intent)

                        Toast.makeText(this, "Vaga selecionada: ${vagaSelecionada.numero}", Toast.LENGTH_SHORT).show()
                    }
                    binding.rvParkingSpots.adapter = adapter
                }
            },
            onError = { erro ->
                runOnUiThread {
                    Toast.makeText(this, "Erro ao carregar vagas: $erro", Toast.LENGTH_LONG).show()
                }
            }
        )

    }
}

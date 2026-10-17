package br.edu.fatecpg.valletprojeto.fragments

import br.edu.fatecpg.valletprojeto.model.ReservaHistorico
import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.viewModels
import androidx.work.WorkManager
import androidx.work.WorkInfo
import br.edu.fatecpg.valletprojeto.viewmodel.ReservaViewModel
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import br.edu.fatecpg.valletprojeto.DashboardActivity
import br.edu.fatecpg.valletprojeto.R
import br.edu.fatecpg.valletprojeto.ReservaActivity
import br.edu.fatecpg.valletprojeto.adapter.HistoricoReservasAdapter
import br.edu.fatecpg.valletprojeto.databinding.FragmentMotoristaDashboardBinding
import br.edu.fatecpg.valletprojeto.model.Vaga
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class MotoristaFragment : Fragment() {

    private var _binding: FragmentMotoristaDashboardBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: HistoricoReservasAdapter
    private val db = FirebaseFirestore.getInstance()
    private val reservaViewModel: ReservaViewModel by viewModels()

    private var vagaIdAtiva: String? = null
    private var estacionamentoIdAtivo: String? = null
    private var buscaReservasJob: Job? = null

    private val estacionamentoCache = mutableMapOf<String, String>()
    private val vagaCache = mutableMapOf<String, Vaga>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMotoristaDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupClickListeners()
        mostrarLoadingGeral()
        WorkManager.getInstance(requireContext())
            .getWorkInfosByTagLiveData("recuperacao_reservas_expiradas")
            .observe(viewLifecycleOwner) { trabalhos ->
                if (trabalhos.any { it.state == WorkInfo.State.SUCCEEDED }) {
                    buscarReservasOtimizado()
                }
            }
    }

    override fun onResume() {
        super.onResume()
        reservaViewModel.verificarEFinalizarReservasExpiradas()
        buscarReservasOtimizado()
    }

    override fun onPause() {
        super.onPause()
        buscaReservasJob?.cancel()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        buscaReservasJob?.cancel()
        _binding = null
    }

    private fun setupRecyclerView() {
        binding.rvHistoricoRecente.layoutManager = LinearLayoutManager(requireContext())
        binding.rvHistoricoRecente.setHasFixedSize(true)
        binding.rvHistoricoRecente.isNestedScrollingEnabled = false
    }

    private fun setupClickListeners() {
        binding.btnVerReserva.setOnClickListener {
            if (!isAdded || context == null) return@setOnClickListener

            if (vagaIdAtiva != null && estacionamentoIdAtivo != null) {
                val intent = Intent(requireContext(), ReservaActivity::class.java)
                intent.putExtra("vagaId", vagaIdAtiva)
                intent.putExtra("estacionamentoId", estacionamentoIdAtivo)
                startActivity(intent)
            } else {
                Toast.makeText(requireContext(), "Nenhuma reserva ativa encontrada.", Toast.LENGTH_SHORT).show()
            }
        }

        binding.cardNoReservation.setOnClickListener {
            abrirPaginaDeEstacionamentos()
        }
    }

    private fun mostrarLoadingGeral() {
        binding.loadingState.visibility = View.VISIBLE
        binding.contentState.visibility = View.GONE
    }

    private fun esconderLoadingGeral() {
        binding.loadingState.visibility = View.GONE
        binding.contentState.visibility = View.VISIBLE
    }

    private fun buscarReservasOtimizado() {
        val uidLogado = FirebaseAuth.getInstance().currentUser?.uid ?: return

        buscaReservasJob?.cancel()

        buscaReservasJob = viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
            try {
                if (!isAdded || context == null) return@launch

                val reservasSnapshot = db.collection("reserva")
                    .whereEqualTo("usuarioId", uidLogado)
                    .get()
                    .await()

                if (!isAdded || context == null) return@launch

                val resultado = processarReservasEmParalelo(reservasSnapshot)

                if (isAdded && context != null) {
                    esconderLoadingGeral()
                    atualizarUI(resultado)
                }

            } catch (e: Exception) {
                if (isAdded && context != null) {
                    Log.e("MotoristaFragment", "Erro ao carregar reservas", e)
                    esconderLoadingGeral()
                    Toast.makeText(requireContext(), "Erro ao carregar reservas", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private suspend fun processarReservasEmParalelo(reservasSnapshot: com.google.firebase.firestore.QuerySnapshot): ProcessamentoResultado {
        return viewLifecycleOwner.lifecycleScope.async(Dispatchers.IO) {
            var totalHoras = 0L
            val historico = mutableListOf<ReservaHistorico>()
            var temReservaAtiva = false
            var reservaAtiva: ReservaAtiva? = null

            val vagaIds = reservasSnapshot.documents.mapNotNull { it.getString("vagaId") }.toSet()
            val vagasMap = buscarVagasBatch(vagaIds)

            val estacionamentoIds = reservasSnapshot.documents.mapNotNull { it.getString("estacionamentoId") }.toSet()
            val estacionamentosMap = buscarEstacionamentosBatch(estacionamentoIds)

            for (doc in reservasSnapshot) {
                val status = doc.getString("status") ?: continue
                val vagaId = doc.getString("vagaId") ?: continue
                val estacionamentoId = doc.getString("estacionamentoId") ?: ""
                val estacionamentoNome = doc.getString("estacionamentoNome") ?: estacionamentosMap[estacionamentoId] ?: "Estacionamento"
                val inicio = doc.getTimestamp("inicioReserva")?.toDate()
                val fim = doc.getTimestamp("fimReserva")?.toDate()

                if (status != "ativa" && inicio != null && fim != null) {
                    totalHoras += (fim.time - inicio.time)
                }

                val vaga = vagasMap[vagaId] ?: Vaga(numero = "-", localizacao = "-")

                if (status == "ativa") {
                    temReservaAtiva = true
                    reservaAtiva = ReservaAtiva(
                        vaga = vaga,
                        vagaId = vagaId,
                        estacionamentoId = estacionamentoId,
                        estacionamentoNome = estacionamentoNome,
                        inicio = inicio,
                        fim = fim
                    )
                }else {
                    if (inicio != null) {
                        historico.add(
                            ReservaHistorico(
                                vaga = vaga.numero,
                                data = SimpleDateFormat("dd/MM", Locale.getDefault()).format(inicio),
                                horario = formatarHorario(inicio, fim),
                                estacionamentoNome = estacionamentoNome,
                                duracao = calcularDuracaoTexto(inicio, fim)
                            )
                        )
                    }
                }
            }

            val historicoLimitado = historico.takeLast(3).reversed()

            return@async ProcessamentoResultado(
                totalHoras = totalHoras,
                historico = historicoLimitado,
                temReservaAtiva = temReservaAtiva,
                totalReservas = reservasSnapshot.size(),
                reservaAtiva = reservaAtiva
            )
        }.await()
    }

    private suspend fun buscarVagasBatch(vagaIds: Set<String>): Map<String, Vaga> {
        if (vagaIds.isEmpty()) return emptyMap()

        return try {
            val vagasMap = mutableMapOf<String, Vaga>()

            val idsParaBuscar = vagaIds.filter { !vagaCache.containsKey(it) }

            if (idsParaBuscar.isNotEmpty()) {
                val vagasSnapshot = db.collection("vaga")
                    .whereIn(com.google.firebase.firestore.FieldPath.documentId(), idsParaBuscar)
                    .get()
                    .await()

                for (doc in vagasSnapshot) {
                    val vaga = Vaga(
                        numero = doc.getString("numero") ?: "-",
                        localizacao = doc.getString("localizacao") ?: "-"
                    )
                    vagasMap[doc.id] = vaga
                    vagaCache[doc.id] = vaga
                }
            }

            vagaIds.forEach { id ->
                vagaCache[id]?.let { vagasMap[id] = it }
            }

            vagasMap
        } catch (e: Exception) {
            Log.e("MotoristaFragment", "Erro ao buscar vagas em batch", e)
            emptyMap()
        }
    }

    private suspend fun buscarEstacionamentosBatch(estacionamentoIds: Set<String>): Map<String, String> {
        if (estacionamentoIds.isEmpty()) return emptyMap()

        return try {
            val estacionamentosMap = mutableMapOf<String, String>()

            val idsParaBuscar = estacionamentoIds.filter { !estacionamentoCache.containsKey(it) }

            if (idsParaBuscar.isNotEmpty()) {
                val estacionamentosSnapshot = db.collection("estacionamento")
                    .whereIn(com.google.firebase.firestore.FieldPath.documentId(), idsParaBuscar)
                    .get()
                    .await()

                for (doc in estacionamentosSnapshot) {
                    val nome = doc.getString("nome") ?: "Estacionamento"
                    estacionamentosMap[doc.id] = nome
                    estacionamentoCache[doc.id] = nome
                }
            }

            estacionamentoIds.forEach { id ->
                estacionamentoCache[id]?.let { estacionamentosMap[id] = it }
            }

            estacionamentosMap
        } catch (e: Exception) {
            Log.e("MotoristaFragment", "Erro ao buscar estacionamentos em batch", e)
            emptyMap()
        }
    }

    @SuppressLint("SetTextI18n")
    private suspend fun atualizarUI(resultado: ProcessamentoResultado) {
        binding.txvTotalReservas.text = resultado.totalReservas.toString()
        val horasTotais = TimeUnit.MILLISECONDS.toHours(resultado.totalHoras)
        binding.txvTempoTotal.text = "${horasTotais}h"

        adapter = HistoricoReservasAdapter(resultado.historico)
        binding.rvHistoricoRecente.adapter = adapter

        if (resultado.temReservaAtiva && resultado.reservaAtiva != null) {
            binding.cardReservaAtual.visibility = View.VISIBLE
            binding.cardNoReservation.visibility = View.GONE

            val reserva = resultado.reservaAtiva
            binding.txvNumeroVaga.text = reserva.vaga.numero
            binding.txvLocalizacao.text = "Local: ${reserva.vaga.localizacao}"
            binding.txvDuracao.text = formatarHorario(reserva.inicio, reserva.fim)
            binding.txvTempoRestante.text = "Reserva ativa"
            binding.txvEstacionamento.text = reserva.estacionamentoNome

            vagaIdAtiva = encontrarVagaIdPorNumero(reserva.vaga.numero, resultado.reservaAtiva.estacionamentoId)
            estacionamentoIdAtivo = reserva.estacionamentoId

            Log.d("MotoristaFragment", "Reserva ativa - VagaId: $vagaIdAtiva, EstacionamentoId: $estacionamentoIdAtivo")
        } else {
            binding.cardReservaAtual.visibility = View.GONE
            binding.cardNoReservation.visibility = View.VISIBLE
            vagaIdAtiva = null
            estacionamentoIdAtivo = null
        }
    }

    private suspend fun encontrarVagaIdPorNumero(numeroVaga: String, estacionamentoId: String): String? {
        return try {
            val vagaSnapshot = db.collection("vaga")
                .whereEqualTo("numero", numeroVaga)
                .whereEqualTo("estacionamentoId", estacionamentoId)
                .limit(1)
                .get()
                .await()

            if (!vagaSnapshot.isEmpty) {
                vagaSnapshot.documents.first().id
            } else {
                Log.e("MotoristaFragment", "Vaga não encontrada: $numeroVaga no estacionamento $estacionamentoId")
                null
            }
        } catch (e: Exception) {
            Log.e("MotoristaFragment", "Erro ao buscar vagaId: ${e.message}")
            null
        }
    }

    private fun abrirPaginaDeEstacionamentos() {
        if (!isAdded || activity == null) return

        val dashboardActivity = activity as? DashboardActivity
        dashboardActivity?.let {
            val btnVagas = it.findViewById<android.widget.ImageView>(R.id.nav_spots)

            btnVagas?.performClick()
        }
    }

    private fun formatarHorario(inicio: Date?, fim: Date?): String {
        val formato = SimpleDateFormat("HH:mm", Locale.getDefault())
        return if (inicio != null && fim != null) "${formato.format(inicio)} - ${formato.format(fim)}" else ""
    }

    private data class ProcessamentoResultado(
        val totalHoras: Long,
        val historico: List<ReservaHistorico>,
        val temReservaAtiva: Boolean,
        val totalReservas: Int,
        val reservaAtiva: ReservaAtiva?
    )

    private data class ReservaAtiva(
        val vaga: Vaga,
        val vagaId: String,
        val estacionamentoId: String,
        val estacionamentoNome: String,
        val inicio: Date?,
        val fim: Date?
    )

    private fun calcularDuracaoTexto(inicio: Date?, fim: Date?): String {
        if (inicio == null || fim == null) return ""
        val diferencaMillis = fim.time - inicio.time
        val horas = TimeUnit.MILLISECONDS.toHours(diferencaMillis)
        val minutos = TimeUnit.MILLISECONDS.toMinutes(diferencaMillis) % 60

        return if (horas > 0) {
            "${horas}h ${minutos}min"
        } else {
            "${minutos}min"
        }
    }

}

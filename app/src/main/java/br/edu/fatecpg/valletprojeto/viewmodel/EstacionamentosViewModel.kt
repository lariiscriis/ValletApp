package br.edu.fatecpg.valletprojeto.viewmodel

import android.annotation.SuppressLint
import android.app.Application
import android.location.Location
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import br.edu.fatecpg.valletprojeto.model.Estacionamento
import com.firebase.geofire.GeoFireUtils
import com.firebase.geofire.GeoLocation
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

class EstacionamentosViewModel(application: Application) : AndroidViewModel(application) {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(application)

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _parkings = MutableLiveData<List<Estacionamento>>()
    val parkings: LiveData<List<Estacionamento>> = _parkings

    private val _favoriteParkings = MutableLiveData<List<Estacionamento>>()
    val favoriteParkings: LiveData<List<Estacionamento>> = _favoriteParkings

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private var allParkingsMasterList: List<Estacionamento> = listOf()
    private val _navigateToVagas = MutableLiveData<Event<String>>()
    val navigateToVagas: LiveData<Event<String>> = _navigateToVagas

    private val _toastMessage = MutableLiveData<Event<String>>()
    val toastMessage: LiveData<Event<String>> = _toastMessage

    fun onEstacionamentoClicked(estacionamento: Estacionamento) {
        if (!estacionamento.estaAberto()) {
            _toastMessage.value = Event("O estacionamento está fechado.")
            return
        }
        viewModelScope.launch {
            try {
                val snapshot = withContext(Dispatchers.IO) {
                    db.collection("vaga")
                        .whereEqualTo("estacionamentoId", estacionamento.id)
                        .whereEqualTo("disponivel", true)
                        .limit(1)
                        .get()
                        .await()
                }
                if (!snapshot.isEmpty) {
                    _navigateToVagas.value = Event(estacionamento.id)
                } else {
                    _toastMessage.value = Event("Nenhuma vaga disponível no momento.")
                    val updatedParking = estacionamento.copy(vagasDisponiveis = 0)
                    updateParkingInLists(updatedParking)
                }
            } catch (e: Exception) {
                _toastMessage.value = Event("Erro ao verificar vagas: ${e.message}")
            }
        }
    }

    fun loadData(useLocation: Boolean = true) {
        if (_isLoading.value == true) return
        Log.d("EstacionamentosViewModel", "Iniciando loadData...")

        viewModelScope.launch {
            _isLoading.postValue(true)
            _error.postValue(null)

            try {
                val userLocation = if (useLocation) {
                    Log.d("EstacionamentosViewModel", "Buscando localização...")
                    withTimeoutOrNull(5000) { fetchUserLocation() }
                } else null

                if (useLocation && userLocation == null) {
                    Log.w("EstacionamentosViewModel", "Falha ao obter localização.")
                    _error.postValue("Não foi possível obter a localização.")
                } else {
                    Log.d("EstacionamentosViewModel", "Localização obtida: $userLocation")
                }

                val favoriteIdsDeferred = async { fetchFavoriteIds() }
                val parkingsDeferred = async {
                    if (userLocation != null) {
                        fetchParkingsNear(userLocation)
                    } else {
                        Log.d("EstacionamentosViewModel", "Fallback: buscando todos os estacionamentos (sem geofire).")
                        fetchAllParkings()
                    }
                }

                val favoriteIds = favoriteIdsDeferred.await()
                val allFetchedParkings = parkingsDeferred.await()
                Log.d("EstacionamentosViewModel", "Busca concluída. ${allFetchedParkings.size} estacionamentos encontrados.")

                val processedParkings = allFetchedParkings.map { est ->
                    val distancia = est.calcularDistancia(userLocation)
                    est.copy(distanciaMetros = distancia)
                }.sortedBy { it.distanciaMetros ?: Int.MAX_VALUE }

                allParkingsMasterList = processedParkings
                val (favorites, others) = allParkingsMasterList.partition { it.id in favoriteIds }

                Log.d("EstacionamentosViewModel", "Separando ${favorites.size} favoritos e ${others.size} outros.")
                _favoriteParkings.postValue(favorites)
                _parkings.postValue(others)

            } catch (e: Exception) {
                Log.e("EstacionamentosViewModel", "Erro em loadData", e)
                _error.postValue("Erro crítico: ${e.message}")
            } finally {
                _isLoading.postValue(false)
                Log.d("EstacionamentosViewModel", "loadData finalizado.")
            }
        }
    }

    private suspend fun fetchParkingsNear(location: Location): List<Estacionamento> = withContext(Dispatchers.IO) {
        Log.d("EstacionamentosViewModel", "Executando fetchParkingsNear em ${Thread.currentThread().name}")
        val center = GeoLocation(location.latitude, location.longitude)
        val radiusInM = 100 * 1000.0

        val bounds = GeoFireUtils.getGeoHashQueryBounds(center, radiusInM)
        val tasks = bounds.map { bound ->
            db.collection("estacionamento")
                .orderBy("geohash")
                .startAt(bound.startHash)
                .endAt(bound.endHash)
                .get()
        }

        val snapshots = Tasks.await(Tasks.whenAllSuccess<QuerySnapshot>(tasks))
        Log.d("EstacionamentosViewModel", "Geohash query retornou ${snapshots.sumOf { it.size() }} documentos brutos.")

        val matchingDocs = snapshots.flatMap { snap ->
            snap.documents.filter { doc ->
                val lat = doc.get("latitude") as? Double ?: (doc.get("latitude") as? String)?.toDoubleOrNull() ?: 0.0
                val lng = doc.get("longitude") as? Double ?: (doc.get("longitude") as? String)?.toDoubleOrNull() ?: 0.0

                if (lat == 0.0 || lng == 0.0) {
                    Log.w("EstacionamentosViewModel", "ALERTA: Coordenadas inválidas para o doc ${doc.id}. Lat: $lat, Lng: $lng")
                }

                val docLocation = GeoLocation(lat, lng)
                val distancia = GeoFireUtils.getDistanceBetween(docLocation, center)

                Log.d("EstacionamentosViewModel", "Filtrando doc ${doc.id}: Distância = ${"%.2f".format(distancia / 1000)} km. (Raio: ${radiusInM / 1000} km)")

                distancia <= radiusInM
            }
        }

        Log.d("EstacionamentosViewModel", "Documentos filtrados por distância: ${matchingDocs.size}")

        matchingDocs.mapNotNull { doc ->
            doc.toObject(Estacionamento::class.java)?.copy(id = doc.id)
        }
    }

    private suspend fun fetchAllParkings(): List<Estacionamento> = withContext(Dispatchers.IO) {
        Log.d("EstacionamentosViewModel", "Executando fetchAllParkings em ${Thread.currentThread().name}")
        val snapshot = db.collection("estacionamento").limit(100).get().await()
        snapshot.documents.mapNotNull { doc ->
            doc.toObject(Estacionamento::class.java)?.copy(id = doc.id)
        }
    }

    private suspend fun fetchFavoriteIds(): Set<String> = withContext(Dispatchers.IO) {
        Log.d("EstacionamentosViewModel", "Executando fetchFavoriteIds em ${Thread.currentThread().name}")
        val userId = auth.currentUser?.uid ?: return@withContext emptySet()
        try {
            val snapshot = db.collection("favoritos").whereEqualTo("usuarioId", userId).get().await()
            snapshot.documents.mapNotNull { it.getString("estacionamentoId") }.toSet()
        } catch (e: Exception) {
            emptySet()
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun fetchUserLocation(): Location? = withContext(Dispatchers.IO) {
        Log.d("EstacionamentosViewModel", "Executando fetchUserLocation em ${Thread.currentThread().name}")
        try {
            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null).await()
        } catch (e: Exception) {
            Log.e("EstacionamentosViewModel", "Erro ao obter localização", e)
            null
        }
    }

    fun toggleFavoriteStatus(estacionamento: Estacionamento) {
        val userId = auth.currentUser?.uid ?: return
        val isCurrentlyFavorite = _favoriteParkings.value?.any { it.id == estacionamento.id } == true

        viewModelScope.launch(Dispatchers.IO) {
            val favoritoRef = db.collection("favoritos")
                .whereEqualTo("usuarioId", userId)
                .whereEqualTo("estacionamentoId", estacionamento.id)

            if (isCurrentlyFavorite) {
                favoritoRef.get().await().documents.firstOrNull()?.reference?.delete()?.await()
            } else {
                db.collection("favoritos").add(
                    mapOf("usuarioId" to userId, "estacionamentoId" to estacionamento.id)
                ).await()
            }
        }

        val currentFavorites = _favoriteParkings.value?.toMutableList() ?: mutableListOf()
        val currentOthers = _parkings.value?.toMutableList() ?: mutableListOf()

        if (isCurrentlyFavorite) {
            currentFavorites.removeAll { it.id == estacionamento.id }
            currentOthers.add(0, estacionamento)
            _favoriteParkings.postValue(currentFavorites)
            _parkings.postValue(currentOthers.sortedBy { it.distanciaMetros ?: Int.MAX_VALUE })
        } else {
            currentOthers.removeAll { it.id == estacionamento.id }
            currentFavorites.add(estacionamento)
            _parkings.postValue(currentOthers)
            _favoriteParkings.postValue(currentFavorites)
        }
    }

    fun searchParkings(query: String) {
        val filteredList = if (query.isBlank()) {
            allParkingsMasterList.filterNot { it.id in (_favoriteParkings.value?.map { fav -> fav.id } ?: emptySet()) }
        } else {
            allParkingsMasterList.filter {
                (it.nome.contains(query, ignoreCase = true) || it.enderecoCompleto.contains(query, ignoreCase = true)) &&
                        it.id !in (_favoriteParkings.value?.map { fav -> fav.id } ?: emptySet())
            }
        }
        _parkings.value = filteredList
    }

    private fun updateParkingInLists(updatedParking: Estacionamento) {
        val currentFavorites = _favoriteParkings.value?.toMutableList() ?: mutableListOf()
        val currentOthers = _parkings.value?.toMutableList() ?: mutableListOf()

        val favIndex = currentFavorites.indexOfFirst { it.id == updatedParking.id }
        if (favIndex != -1) {
            currentFavorites[favIndex] = updatedParking
            _favoriteParkings.postValue(currentFavorites)
            return
        }

        val otherIndex = currentOthers.indexOfFirst { it.id == updatedParking.id }
        if (otherIndex != -1) {
            currentOthers[otherIndex] = updatedParking
            _parkings.postValue(currentOthers)
            return
        }
    }
}

open class Event<out T>(private val content: T) {
    var hasBeenHandled = false
        private set

    fun getContentIfNotHandled(): T? {
        return if (hasBeenHandled) {
            null
        } else {
            hasBeenHandled = true
            content
        }
    }
}
package app.clima.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.clima.data.LocationFinder
import app.clima.data.MAX_SAVED_PLACES
import app.clima.data.OpenMeteo
import app.clima.data.Place
import app.clima.data.Weather
import app.clima.data.WeatherRepository
import app.clima.widget.ClimaWidgets
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UiState(
    val place: Place,
    val saved: List<Place> = emptyList(),
    /** Última temperatura conhecida de cada local salvo, pela chave do lugar. */
    val temps: Map<String, Int> = emptyMap(),
    val weather: Weather? = null,
    val loading: Boolean = false,
    val error: String? = null,
    val results: List<Place> = emptyList(),
)

class WeatherViewModel(private val app: Application) : AndroidViewModel(app) {
    private val repo = WeatherRepository(app)
    private val _state = MutableStateFlow(UiState(place = repo.place, weather = repo.cached()).withSaved())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var searchJob: Job? = null
    private var refreshJob: Job? = null

    private fun UiState.withSaved(): UiState {
        val saved = repo.places
        return copy(saved = saved, temps = saved.mapNotNull { p -> repo.cached(p)?.let { p.key to it.temp } }.toMap())
    }

    init {
        refresh()
    }

    fun refresh() {
        // Trocar de local cancela a atualização do anterior, para ela não sobrescrever a tela com a cidade errada.
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                val weather = repo.refresh()
                _state.update { it.copy(weather = weather, loading = false).withSaved() }
                repo.refreshOthers()
                _state.update { it.withSaved() }
                ClimaWidgets.updateAll(app)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = "Não foi possível atualizar. Verifique a conexão.") }
            }
        }
    }

    /** Mostra na hora o que está em cache do local e atualiza em seguida. */
    private fun showSelected() {
        val place = repo.place
        _state.update { it.copy(place = place, weather = repo.cached(place), results = emptyList(), error = null).withSaved() }
        viewModelScope.launch { ClimaWidgets.updateAll(app) }
        refresh()
    }

    /** Troca para um local já salvo. */
    fun switchTo(place: Place) {
        repo.place = place
        showSelected()
    }

    /** Salva o local (se for novo) e o seleciona. */
    fun select(place: Place) {
        if (!repo.select(place)) {
            _state.update { it.copy(results = emptyList()) }
            showError("Limite de $MAX_SAVED_PLACES cidades salvas além da localização atual. Remova uma para adicionar outra.")
            return
        }
        showSelected()
    }

    fun remove(place: Place) {
        if (!repo.remove(place)) {
            showError("Mantenha pelo menos um local salvo.")
            return
        }
        showSelected()
    }

    fun search(query: String) {
        searchJob?.cancel()
        if (query.trim().length < 2) {
            _state.update { it.copy(results = emptyList()) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(350)
            val found = runCatching { OpenMeteo.search(query.trim()) }.getOrDefault(emptyList())
            ensureActive()
            _state.update { it.copy(results = found) }
        }
    }

    fun useMyLocation() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            val location = LocationFinder.current(app)
            if (location == null) {
                _state.update { it.copy(loading = false, error = "Não consegui obter sua localização.") }
                return@launch
            }
            repo.setMyLocation(LocationFinder.toPlace(app, location))
            showSelected()
        }
    }

    fun showError(message: String) {
        _state.update { it.copy(error = message) }
    }
}

package app.clima.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.clima.data.LocationFinder
import app.clima.data.OpenMeteo
import app.clima.data.Place
import app.clima.data.Weather
import app.clima.data.WeatherRepository
import app.clima.widget.ClimaWidgets
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
    val weather: Weather? = null,
    val loading: Boolean = false,
    val error: String? = null,
    val results: List<Place> = emptyList(),
)

class WeatherViewModel(private val app: Application) : AndroidViewModel(app) {
    private val repo = WeatherRepository(app)
    private val _state = MutableStateFlow(UiState(place = repo.place, weather = repo.cached()))
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var searchJob: Job? = null

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                val weather = repo.refresh()
                _state.update { it.copy(weather = weather, loading = false) }
                ClimaWidgets.updateAll(app)
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = "Não foi possível atualizar. Verifique a conexão.") }
            }
        }
    }

    fun select(place: Place) {
        repo.place = place
        _state.update { it.copy(place = place, weather = null, results = emptyList()) }
        refresh()
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
            select(LocationFinder.toPlace(app, location))
        }
    }

    fun showError(message: String) {
        _state.update { it.copy(error = message) }
    }
}

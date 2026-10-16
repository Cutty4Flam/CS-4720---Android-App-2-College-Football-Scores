package edu.virginia.footballscores

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.temporal.ChronoUnit

// Represents the three possible states of the scores screen.
sealed interface ScoresUiState {
    data object Loading : ScoresUiState
    data class Success(
        val games: List<GameEntity>,
        val refreshing: Boolean = false,
        val offline: Boolean = false
    ) : ScoresUiState
    data class Error(val message: String) : ScoresUiState
}
// Calculates the current football week based on the season start date.
fun currentFootballWeek(today: LocalDate = LocalDate.now()): Int {
    // Week 1 begins Thursday, September 3, 2026; each Thursday starts the next week.
    val seasonStart = LocalDate.of(today.year, 9, 3)
    return (ChronoUnit.DAYS.between(seasonStart, today) / 7 + 1).toInt().coerceIn(1, 14)
}

class ScoresViewModel(private val repo: ScoresRepository) : ViewModel() {
    // Stores the current UI state.
    private val _state = MutableStateFlow<ScoresUiState>(ScoresUiState.Loading)
    val state: StateFlow<ScoresUiState> = _state.asStateFlow()
    // Stores the selected week and division.
    private val _week = MutableStateFlow(currentFootballWeek())
    val week = _week.asStateFlow()
    private val _division = MutableStateFlow("fbs")
    val division = _division.asStateFlow()
    val year: Int = LocalDate.now().year
    // Keeps track of the active loading job.
    private var loadJob: Job? = null

    init { reload() }

    fun setWeek(week: Int) {
        if (week !in 1..14 || _week.value == week) return
        _week.value = week
        reload()
    }

    fun setDivision(division: String) {
        if (division !in listOf("fbs", "fcs") || _division.value == division) return
        _division.value = division
        reload()
    }

    fun refresh() = reload()

    private fun reload() {
        loadJob?.cancel()
        val d = _division.value
        val w = _week.value
        loadJob = viewModelScope.launch {
            _state.value = ScoresUiState.Loading
            try {
                val cached = repo.hasCache(d, year, w)
                if (cached) _state.value = ScoresUiState.Success(repo.games(d, year, w), refreshing = true)
                try {
                    // Fetches new scores and updates the Room database.
                    repo.refresh(d, year, w)
                    _state.value = ScoresUiState.Success(repo.games(d, year, w))
                } catch (cancel: CancellationException) {
                    throw cancel
                } catch (_: Exception) {

                    // Uses cached games if the network request fails.
                    if (repo.hasCache(d, year, w)) {
                        _state.value = ScoresUiState.Success(repo.games(d, year, w), offline = true)
                    } else {
                        _state.value = ScoresUiState.Error("Couldn't load scores. Check your connection and try again.")
                    }
                }
                // Observes Room so database updates appear on the screen.
                repo.observe(d, year, w).collectLatest { rows ->
                    val old = _state.value
                    if (old is ScoresUiState.Success) _state.value = old.copy(games = rows)
                }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Exception) {
                _state.value = ScoresUiState.Error("Unable to open saved scores. Please retry.")
            }
        }
    }
    // Provides the repository when creating the ViewModel.
    class Factory(private val repository: ScoresRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = ScoresViewModel(repository) as T
    }
}

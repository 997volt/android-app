package com.example.androidapp.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.androidapp.domain.DataError
import com.example.androidapp.domain.DataResult
import com.example.androidapp.domain.RestTimer
import com.example.androidapp.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The settings screen's state (ROADMAP N21).
 *
 * It renders what is *stored* rather than what was tapped: a choice that failed to reach
 * disk must not look applied, or the next workout silently uses the old rest.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepository.observeDefaultRestSeconds().collect { seconds ->
                _uiState.update { it.copy(defaultRestSeconds = seconds, isLoading = false) }
            }
        }
    }

    fun onSetDefaultRest(seconds: Int) {
        viewModelScope.launch {
            when (val result = settingsRepository.setDefaultRestSeconds(seconds)) {
                is DataResult.Success -> _uiState.update { it.copy(error = null) }
                is DataResult.Failure -> _uiState.update { it.copy(error = result.error) }
            }
        }
    }
}

/** What the settings screen shows. */
data class SettingsUiState(
    val isLoading: Boolean = true,
    val defaultRestSeconds: Int = RestTimer.DEFAULT_SECONDS,
    val error: DataError? = null,
) {
    val choices: List<Int> get() = REST_CHOICES

    companion object {
        /**
         * The rests worth offering as one tap.
         *
         * Covers a warm-up pause through a long strength single. It is a bounded list and
         * not a number field because a typed zero would be an alarm that fires instantly.
         */
        val REST_CHOICES = listOf(30, 45, 60, 75, 90, 120, 150, 180, 240, 300)
    }
}

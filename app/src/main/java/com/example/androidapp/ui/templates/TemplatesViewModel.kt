package com.example.androidapp.ui.templates

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.androidapp.domain.DataError
import com.example.androidapp.domain.DataResult
import com.example.androidapp.domain.model.WorkoutTemplate
import com.example.androidapp.domain.repository.TemplateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TemplatesUiState(
    val isLoading: Boolean = true,
    val templates: List<WorkoutTemplate> = emptyList(),
    /** Set when a write failed, so the screen can say so instead of lying. */
    val error: DataError? = null,
)

/**
 * The template list (ROADMAP N3).
 *
 * Creating a template takes a name from the screen — a ViewModel cannot read a
 * string resource — and then reports the new id so the list can open it straight
 * into the editor, which is where its exercises are added.
 */
@HiltViewModel
class TemplatesViewModel @Inject constructor(
    private val repository: TemplateRepository,
) : ViewModel() {

    private val error = MutableStateFlow<DataError?>(null)

    /** Emits the id of a just-created template, so the screen can open it. */
    private val _created = MutableStateFlow<String?>(null)
    val createdTemplateId: StateFlow<String?> = _created

    val uiState: StateFlow<TemplatesUiState> = combine(
        repository.observeTemplates(),
        error,
    ) { templates, currentError ->
        TemplatesUiState(isLoading = false, templates = templates, error = currentError)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = TemplatesUiState(),
    )

    fun onCreateTemplate(name: String) {
        viewModelScope.launch {
            when (val result = repository.createTemplate(name)) {
                is DataResult.Success -> {
                    error.value = null
                    _created.value = result.data
                }

                is DataResult.Failure -> error.value = result.error
            }
        }
    }

    /** Called once the screen has navigated, so a recomposition cannot re-open it. */
    fun onCreatedHandled() {
        _created.value = null
    }

    fun onErrorShown() {
        error.value = null
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

package com.example.androidapp.ui.templates

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.example.androidapp.domain.DataError
import com.example.androidapp.domain.DataResult
import com.example.androidapp.domain.model.TemplateExercise
import com.example.androidapp.domain.model.WorkoutTemplate
import com.example.androidapp.domain.repository.TemplateRepository
import com.example.androidapp.ui.navigation.TemplateEditor
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TemplateEditorUiState(
    val isLoading: Boolean = true,
    val template: WorkoutTemplate? = null,
    val exercises: List<TemplateExercise> = emptyList(),
    val error: DataError? = null,
) {
    /** The template was deleted, or never existed — either way there is no editor. */
    val notFound: Boolean get() = !isLoading && template == null
}

/**
 * One template's name and its ordered exercises (ROADMAP N3).
 *
 * Every write goes through [TemplateRepository] and surfaces a failure rather than
 * swallowing it (F7): a reorder that silently did not happen would leave the user
 * starting a workout in the wrong order.
 */
@HiltViewModel
class TemplateEditorViewModel @Inject constructor(
    private val repository: TemplateRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val templateId: String = savedStateHandle.toRoute<TemplateEditor>().templateId

    private val error = MutableStateFlow<DataError?>(null)

    /** True once the template is gone, so the screen can leave the editor. */
    private val _deleted = MutableStateFlow(false)
    val deleted: StateFlow<Boolean> = _deleted

    val uiState: StateFlow<TemplateEditorUiState> = combine(
        repository.observeTemplate(templateId),
        repository.observeExercises(templateId),
        error,
    ) { template, exercises, currentError ->
        TemplateEditorUiState(
            isLoading = false,
            template = template,
            exercises = exercises,
            error = currentError,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = TemplateEditorUiState(),
    )

    fun onRename(name: String) = write { repository.renameTemplate(templateId, name) }

    fun onRemoveExercise(templateExerciseId: String) = write {
        repository.removeExercise(templateExerciseId)
    }

    /** [delta] -1 moves the exercise up, +1 down. */
    fun onMoveExercise(templateExerciseId: String, delta: Int) = write {
        repository.moveExercise(templateExerciseId, delta)
    }

    fun onDeleteTemplate() {
        viewModelScope.launch {
            when (val result = repository.deleteTemplate(templateId)) {
                is DataResult.Success -> {
                    error.value = null
                    _deleted.value = true
                }

                is DataResult.Failure -> error.value = result.error
            }
        }
    }

    fun onErrorShown() {
        error.value = null
    }

    private fun write(action: suspend () -> DataResult<Unit>) {
        viewModelScope.launch {
            error.value = (action() as? DataResult.Failure)?.error
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

package com.example.androidapp.ui.templates

import java.time.DayOfWeek
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.example.androidapp.domain.DataError
import com.example.androidapp.domain.model.SetType
import com.example.androidapp.domain.model.warmUpRamp
import com.example.androidapp.domain.DataResult
import com.example.androidapp.domain.model.TemplateExercise
import com.example.androidapp.domain.model.WorkoutTemplate
import com.example.androidapp.domain.repository.TemplateRepository
import com.example.androidapp.domain.repository.TemplateSetEdit
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
    /** `A1`/`A2` per planned exercise, or empty when nothing is grouped (ROADMAP B16). */
    val supersetLabels: Map<String, String> = emptyMap(),
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
            // Giant-set notation, computed where the order is known (ROADMAP B16).
            supersetLabels = exercises.mapNotNull { exercise ->
                val group = exercise.supersetGroup ?: return@mapNotNull null
                val groups = exercises.mapNotNull { it.supersetGroup }.distinct().sorted()
                val letter = 'A' + groups.indexOf(group)
                val member = exercises.filter { it.supersetGroup == group }
                    .indexOfFirst { it.id == exercise.id } + 1
                exercise.id to "$letter$member"
            }.toMap(),
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

    /** Appends a planned set to an exercise (ROADMAP N14). */
    fun onAddSet(templateExerciseId: String, edit: TemplateSetEdit) = write {
        repository.addSet(templateExerciseId, edit)
    }

    /**
     * Writes a warm-up ramp in front of what the plan already prescribes (ROADMAP N28).
     *
     * The working weight is the heaviest the plan names for this exercise, so the ramp is derived from
     * the plan rather than from a number typed twice. Nothing is written when there is no weight to
     * take a fraction of — a bodyweight exercise gets no ramp, and the app does not invent one
     * (N15's rule about what a bodyweight set carries, applied to generating one).
     */
    fun onAddWarmUpSets(templateExerciseId: String) = write {
        val exercise = uiState.value.exercises.firstOrNull { it.id == templateExerciseId }
            ?: return@write DataError.NotFound.let { DataResult.Failure(it) }

        val workingWeight = exercise.sets
            .filter { it.role != SetType.WARMUP }
            .mapNotNull { it.targetWeightGrams }
            .maxOrNull() ?: 0L

        val ramp = warmUpRamp(workingWeight).map { target ->
            TemplateSetEdit(
                role = SetType.WARMUP,
                targetWeightGrams = target.weightGrams,
                targetRepsMin = target.reps,
                targetRepsMax = target.reps,
            )
        }
        // Nothing to ramp from means no write at all, not a write of nothing: the button is hidden in
        // that case, and a call that changes no rows would still be a call.
        if (ramp.isEmpty()) return@write DataResult.Success(Unit)

        // One call, in front of the work: appending the ramp put the warm-ups after the sets they
        // exist to prepare for (ROADMAP B34), and one call also makes it atomic (B27's rule).
        repository.prependSets(templateExerciseId, ramp)
        DataResult.Success(Unit)
    }

    fun onUpdateSet(templateSetId: String, edit: TemplateSetEdit) = write {
        repository.updateSet(templateSetId, edit)
    }

    fun onRemoveSet(templateSetId: String) = write { repository.removeSet(templateSetId) }

    /** Copy forward: the same shape again, to adjust (ROADMAP N14). */
    fun onDuplicateSets(templateExerciseId: String) = write {
        repository.duplicateSets(templateExerciseId)
    }

    /** The rest and cue this exercise's plan prescribes, over the library's (N14). */
    fun onSaveExercisePlan(
        templateExerciseId: String,
        restSeconds: Int?,
        techniqueNote: String?,
    ) = write {
        repository.setExercisePlan(templateExerciseId, restSeconds, techniqueNote)
    }

    /** Pins this plan to a weekday, or unpins it (ROADMAP N16). */
    fun onSetWeekday(weekday: DayOfWeek?) = write {
        repository.setWeekday(templateId, weekday)
    }

    /**
     * Pairs this planned exercise with the one above it, or leaves the group (ROADMAP B16).
     *
     * The same gesture as the workout screen, for the same reason: the order is already on
     * screen, and "these two, together" is what a tap on the second one means.
     */
    fun onToggleSuperset(templateExerciseId: String) {
        val ordered = uiState.value.exercises
        val index = ordered.indexOfFirst { it.id == templateExerciseId }
        if (index < 0) return
        val me = ordered[index]
        val previous = ordered.getOrNull(index - 1)

        val group = when {
            me.supersetGroup != null -> null
            previous?.supersetGroup != null -> previous.supersetGroup
            previous != null -> (ordered.mapNotNull { it.supersetGroup }.maxOrNull() ?: 0) + 1
            else -> null
        }

        write {
            // Leaving takes the whole group apart; joining pulls the exercise above in.
            val changed = if (group == null) {
                ordered.filter { it.supersetGroup == me.supersetGroup }.map { it.id }
            } else {
                listOf(templateExerciseId) +
                    listOfNotNull(previous?.takeIf { it.supersetGroup == null }?.id)
            }
            // One write for the whole group (ROADMAP B27), and its failure is the caller's.
            repository.setSupersetGroup(changed, group)
        }
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

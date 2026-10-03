package com.example.androidapp.ui.programs

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.example.androidapp.domain.DataError
import com.example.androidapp.domain.DataResult
import com.example.androidapp.domain.model.ProgramSlot
import com.example.androidapp.domain.model.SlotPrescription
import com.example.androidapp.domain.model.TemplateExercise
import com.example.androidapp.domain.model.WorkoutProgram
import com.example.androidapp.domain.model.WorkoutTemplate
import com.example.androidapp.domain.repository.ProgramRepository
import com.example.androidapp.domain.repository.SlotSetEdit
import com.example.androidapp.domain.repository.TemplateRepository
import com.example.androidapp.ui.navigation.ProgramEditor
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.DayOfWeek
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The slot whose prescription the dialog is authoring, and what it needs to draw (P3.8). */
data class SlotPrescriptionEditor(
    val slotId: String,
    val templateName: String,
    /** The template's exercises, in their order — the exercises a prescription can name. */
    val exercises: List<TemplateExercise>,
    /** What the slot already prescribes, keyed by exercise. */
    val prescriptions: List<SlotPrescription>,
) {
    /** What the slot prescribes for one exercise, or null when the template's targets stand. */
    fun forExercise(exerciseId: String): SlotPrescription? =
        prescriptions.firstOrNull { it.exerciseId == exerciseId }
}

data class ProgramEditorUiState(
    val isLoading: Boolean = true,
    val program: WorkoutProgram? = null,
    val slots: List<ProgramSlot> = emptyList(),
    /** The templates a slot can point at, in the list the picker shows. */
    val templates: List<WorkoutTemplate> = emptyList(),
    /** The slot whose prescription is open for editing, or null (ROADMAP P3.8). */
    val prescription: SlotPrescriptionEditor? = null,
    val error: DataError? = null,
) {
    /** The program was deleted, or never existed — either way there is no editor. */
    val notFound: Boolean get() = !isLoading && program == null
}

/**
 * One program: its name, its ordered slots, and whether it is the active one (ROADMAP P3.3).
 *
 * Every write goes through [ProgramRepository] and surfaces a failure rather than
 * swallowing it (F7): a reorder that silently did not happen would leave the user training
 * the wrong day.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ProgramEditorViewModel @Inject constructor(
    private val repository: ProgramRepository,
    private val templateRepository: TemplateRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val programId: String = savedStateHandle.toRoute<ProgramEditor>().programId

    private val error = MutableStateFlow<DataError?>(null)

    /** True once the program is gone, so the screen can leave the editor. */
    private val _deleted = MutableStateFlow(false)
    val deleted: StateFlow<Boolean> = _deleted

    /** The slot whose prescription is open, or null (P3.8). */
    private val prescriptionSlotId = MutableStateFlow<String?>(null)

    /**
     * What the open prescription dialog needs: the slot's template exercises and what the slot
     * already prescribes for them (ROADMAP P3.8).
     *
     * Read only while a slot is open, so a program with many slots costs nothing until one is
     * edited. The slot is looked up from the same slots flow the screen draws, so a removed slot
     * closes the dialog rather than leaving it authoring a slot that is gone.
     */
    private val prescription: kotlinx.coroutines.flow.Flow<SlotPrescriptionEditor?> =
        combine(repository.observeSlots(programId), prescriptionSlotId) { slots, slotId ->
            slots.firstOrNull { it.id == slotId }
        }.flatMapLatest { slot ->
            if (slot == null) {
                flowOf(null)
            } else {
                combine(
                    templateRepository.observeExercises(slot.templateId),
                    repository.observeSlotPrescriptions(slot.id),
                ) { exercises, prescriptions ->
                    SlotPrescriptionEditor(
                        slotId = slot.id,
                        templateName = slot.templateName,
                        exercises = exercises,
                        prescriptions = prescriptions,
                    )
                }
            }
        }

    val uiState: StateFlow<ProgramEditorUiState> = combine(
        repository.observeProgram(programId),
        repository.observeSlots(programId),
        templateRepository.observeTemplates(),
        error,
        prescription,
    ) { program, slots, templates, currentError, openPrescription ->
        ProgramEditorUiState(
            isLoading = false,
            program = program,
            slots = slots,
            templates = templates,
            prescription = openPrescription,
            error = currentError,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = ProgramEditorUiState(),
    )

    fun onRename(name: String) = write { repository.renameProgram(programId, name) }

    /**
     * Starts or stops following this program (P3.3, amended by P3.12).
     *
     * Off stops following **this** one and leaves the others alone; more than one may be
     * active, so there is no longer a "follow none" side effect to turning one off.
     */
    fun onSetActive(active: Boolean) = write {
        if (active) repository.activateProgram(programId) else repository.deactivateProgram(programId)
    }

    fun onAddSlot(templateId: String) = write { repository.addSlot(programId, templateId) }

    fun onSetSlotWeekday(slotId: String, weekday: DayOfWeek?) = write {
        repository.setSlotWeekday(slotId, weekday)
    }

    /** [delta] -1 moves the slot up, +1 down. */
    fun onMoveSlot(slotId: String, delta: Int) = write { repository.moveSlot(slotId, delta) }

    fun onRemoveSlot(slotId: String) = write { repository.removeSlot(slotId) }

    /** Opens the prescription dialog for one slot (ROADMAP P3.8). */
    fun onEditPrescription(slotId: String) {
        prescriptionSlotId.value = slotId
    }

    fun onClosePrescription() {
        prescriptionSlotId.value = null
    }

    /** Writes the rest and cue the slot prescribes for one exercise (P3.8). */
    fun onSetSlotExercisePlan(
        slotId: String,
        exerciseId: String,
        restSeconds: Int?,
        techniqueNote: String?,
    ) = write { repository.setSlotExercisePlan(slotId, exerciseId, restSeconds, techniqueNote) }

    fun onAddSlotSet(slotId: String, exerciseId: String, edit: SlotSetEdit) = write {
        repository.addSlotSet(slotId, exerciseId, edit)
    }

    fun onUpdateSlotSet(slotSetId: String, edit: SlotSetEdit) = write {
        repository.updateSlotSet(slotSetId, edit)
    }

    fun onRemoveSlotSet(slotSetId: String) = write { repository.removeSlotSet(slotSetId) }

    fun onDeleteProgram() {
        viewModelScope.launch {
            when (val result = repository.deleteProgram(programId)) {
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

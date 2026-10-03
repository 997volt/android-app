package com.example.androidapp.data

import androidx.room.withTransaction
import com.example.androidapp.data.local.ProgramDao
import com.example.androidapp.data.local.ProgramDeloadDao
import com.example.androidapp.data.local.ProgramDeloadEntity
import com.example.androidapp.data.local.ProgramPrescriptionDao
import com.example.androidapp.data.local.ProgramRunDao
import com.example.androidapp.data.local.ProgramSkipEntity
import com.example.androidapp.data.local.ProgramSlotEntity
import com.example.androidapp.data.local.ProgramSlotExerciseEntity
import com.example.androidapp.data.local.ProgramSlotSetEntity
import com.example.androidapp.data.local.ProgramEntity
import com.example.androidapp.data.local.WorkoutDatabase
import com.example.androidapp.data.local.toAdherenceSession
import com.example.androidapp.data.local.toDomain
import com.example.androidapp.data.local.toExerciseTrendRow
import com.example.androidapp.data.local.toProgramSession
import com.example.androidapp.data.local.toRunSession
import com.example.androidapp.domain.DataResult
import com.example.androidapp.domain.InvalidInputException
import com.example.androidapp.domain.NotFoundException
import com.example.androidapp.domain.TimeSource
import com.example.androidapp.domain.dataResultOf
import com.example.androidapp.domain.nowEpochMillis
import com.example.androidapp.domain.model.AdherenceReport
import com.example.androidapp.domain.model.ExerciseTrendMetric
import com.example.androidapp.domain.model.PendingOccurrence
import com.example.androidapp.domain.model.PreviousPerformance
import com.example.androidapp.domain.model.ProgramRun
import com.example.androidapp.domain.model.ProgramSchedule
import com.example.androidapp.domain.model.ProgramSlot
import com.example.androidapp.domain.model.RecordedDeload
import com.example.androidapp.domain.model.RecordedSkip
import com.example.androidapp.domain.model.Rpe
import com.example.androidapp.domain.model.SlotPrescription
import com.example.androidapp.domain.model.WorkoutProgram
import com.example.androidapp.domain.model.latestValue
import com.example.androidapp.domain.model.programRun
import com.example.androidapp.domain.model.toExerciseTrendPoints
import com.example.androidapp.domain.repository.ProgramRepository
import com.example.androidapp.domain.repository.SlotSetEdit
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToLong
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * Room-backed [ProgramRepository] (ROADMAP P3.3).
 *
 * The occurrence arithmetic lives in [ProgramSchedule], which is pure: this class only
 * gathers the three sources it needs — the active program's slots, the week's sessions
 * started from a template, and the recorded skips — and hands them over. That keeps the
 * part with a real answer testable without a database.
 */
@Singleton
class RoomProgramRepository @Inject constructor(
    private val database: WorkoutDatabase,
    private val timeSource: TimeSource,
) : ProgramRepository {

    private val dao: ProgramDao = database.programDao()

    private val prescriptionDao: ProgramPrescriptionDao = database.programPrescriptionDao()

    private val runDao: ProgramRunDao = database.programRunDao()

    private val deloadDao: ProgramDeloadDao = database.programDeloadDao()

    override fun observePrograms(): Flow<List<WorkoutProgram>> =
        dao.observePrograms().map { rows -> rows.map { it.toDomain() } }

    override fun observeProgram(programId: String): Flow<WorkoutProgram?> =
        dao.observeProgram(programId).map { it?.toDomain() }

    override fun observeActivePrograms(): Flow<List<WorkoutProgram>> =
        dao.observeActivePrograms().map { rows -> rows.map { it.toDomain() } }

    override fun observeSlots(programId: String): Flow<List<ProgramSlot>> =
        dao.observeSlotDetails(programId).map { rows -> rows.map { it.toDomain() } }

    override suspend fun createProgram(name: String): DataResult<String> = dataResultOf {
        val trimmed = requireName(name)
        val id = UUID.randomUUID().toString()
        val now = timeSource.nowEpochMillis()
        dao.insertProgram(
            ProgramEntity(
                id = id,
                name = trimmed,
                // Following a program is a deliberate choice made in its editor, not a
                // side effect of creating one.
                isActive = false,
                // Appended, so the authored order is the order they were created in until
                // the user moves one (P3.12).
                position = dao.maxProgramPosition() + 1,
                createdAt = now,
                updatedAt = now,
                deletedAt = null,
            ),
        )
        id
    }

    override suspend fun renameProgram(programId: String, name: String): DataResult<Unit> =
        dataResultOf {
            val updated = dao.renameProgram(
                id = programId,
                name = requireName(name),
                at = timeSource.nowEpochMillis(),
            )
            if (updated == 0) throw NotFoundException("program $programId")
        }

    override suspend fun deleteProgram(programId: String): DataResult<Unit> = dataResultOf {
        if (dao.softDeleteProgram(id = programId, at = timeSource.nowEpochMillis()) == 0) {
            throw NotFoundException("program $programId")
        }
    }

    override suspend fun activateProgram(programId: String): DataResult<Unit> = dataResultOf {
        // No transaction and no clearing: more than one program may be active (P3.12), and
        // "which others are followed" is not this write's business.
        if (dao.setProgramActive(programId, isActive = true, at = timeSource.nowEpochMillis()) == 0) {
            throw NotFoundException("program $programId")
        }
    }

    override suspend fun deactivateProgram(programId: String): DataResult<Unit> = dataResultOf {
        if (dao.setProgramActive(programId, isActive = false, at = timeSource.nowEpochMillis()) == 0) {
            throw NotFoundException("program $programId")
        }
    }

    override suspend fun moveProgram(programId: String, delta: Int): DataResult<Unit> = dataResultOf {
        val row = dao.findProgram(programId) ?: throw NotFoundException("program $programId")
        val ordered = dao.findPrograms()
        val index = ordered.indexOfFirst { it.id == programId }
        val neighbour = ordered.getOrNull(index + delta)
        // At the top or the bottom: nothing to do, and not an error.
        if (index >= 0 && neighbour != null) {
            dao.swapProgramPositions(
                firstId = row.id,
                firstPosition = neighbour.position,
                secondId = neighbour.id,
                secondPosition = row.position,
                at = timeSource.nowEpochMillis(),
            )
        }
    }

    override suspend fun addSlot(
        programId: String,
        templateId: String,
        weekday: DayOfWeek?,
    ): DataResult<Unit> = dataResultOf {
        if (dao.findProgram(programId) == null) {
            throw NotFoundException("program $programId")
        }
        val template = database.templateDao().findById(templateId)
            ?: throw NotFoundException("template $templateId")
        val now = timeSource.nowEpochMillis()
        dao.insertSlot(
            ProgramSlotEntity(
                id = UUID.randomUUID().toString(),
                programId = programId,
                templateId = template.id,
                // Appended, so a program is built in the order it is trained.
                position = dao.maxSlotPosition(programId) + 1,
                weekday = weekday,
                createdAt = now,
                updatedAt = now,
                deletedAt = null,
            ),
        )
    }

    override suspend fun setSlotWeekday(slotId: String, weekday: DayOfWeek?): DataResult<Unit> =
        dataResultOf {
            val updated = dao.setSlotWeekday(
                id = slotId,
                weekday = weekday,
                at = timeSource.nowEpochMillis(),
            )
            if (updated == 0) throw NotFoundException("program slot $slotId")
        }

    override suspend fun moveSlot(slotId: String, delta: Int): DataResult<Unit> = dataResultOf {
        val row = dao.findSlot(slotId) ?: throw NotFoundException("program slot $slotId")
        val ordered = dao.findSlotDetails(row.programId)
        val index = ordered.indexOfFirst { it.id == slotId }
        val neighbour = ordered.getOrNull(index + delta)
        // At the top or the bottom: nothing to do, and not an error.
        if (index >= 0 && neighbour != null) {
            dao.swapSlotPositions(
                firstId = row.id,
                firstPosition = neighbour.position,
                secondId = neighbour.id,
                secondPosition = row.position,
                at = timeSource.nowEpochMillis(),
            )
        }
    }

    override suspend fun removeSlot(slotId: String): DataResult<Unit> = dataResultOf {
        if (dao.softDeleteSlot(id = slotId, at = timeSource.nowEpochMillis()) == 0) {
            throw NotFoundException("program slot $slotId")
        }
    }

    override fun observeSlotPrescriptions(slotId: String): Flow<List<SlotPrescription>> =
        combine(
            prescriptionDao.observeSlotExercises(slotId),
            prescriptionDao.observeSlotSets(slotId),
        ) { exercises, sets ->
            val byExercise = sets.groupBy { it.slotExerciseId }
            exercises
                .map { it.toDomain(byExercise[it.id].orEmpty()) }
                // A row with nothing to say is not a prescription: the template's targets stand.
                .filterNot { it.isEmpty }
        }

    override suspend fun setSlotExercisePlan(
        slotId: String,
        exerciseId: String,
        restSeconds: Int?,
        techniqueNote: String?,
    ): DataResult<Unit> = dataResultOf {
        val slot = dao.findSlot(slotId) ?: throw NotFoundException("program slot $slotId")
        requireExerciseInTemplate(database, slot.templateId, exerciseId)
        if (restSeconds != null && restSeconds < 1) {
            throw InvalidInputException("A prescribed rest must be at least a second.")
        }

        val now = timeSource.nowEpochMillis()
        val existing = prescriptionDao.findSlotExercise(slotId, exerciseId)
        val saysNothing = restSeconds == null && techniqueNote == null
        when {
            // Nothing is said and there is no row: there is nothing to write.
            existing == null && saysNothing -> Unit
            existing == null -> prescriptionDao.insertSlotExercise(
                ProgramSlotExerciseEntity(
                    id = UUID.randomUUID().toString(),
                    slotId = slotId,
                    exerciseId = exerciseId,
                    restSeconds = restSeconds,
                    techniqueNote = techniqueNote,
                    createdAt = now,
                    updatedAt = now,
                    deletedAt = null,
                ),
            )

            // The row was only holding sets, and it has none: an empty prescription is absent.
            saysNothing && prescriptionDao.countSlotSets(existing.id) == 0 ->
                prescriptionDao.softDeleteSlotExercise(existing.id, now)

            else -> prescriptionDao.updateSlotExercise(
                existing.copy(
                    restSeconds = restSeconds,
                    techniqueNote = techniqueNote,
                    updatedAt = now,
                ),
            )
        }
    }

    override suspend fun addSlotSet(
        slotId: String,
        exerciseId: String,
        edit: SlotSetEdit,
    ): DataResult<Unit> = dataResultOf {
        val slot = dao.findSlot(slotId) ?: throw NotFoundException("program slot $slotId")
        requireExerciseInTemplate(database, slot.templateId, exerciseId)
        validateSlotSet(edit)

        val now = timeSource.nowEpochMillis()
        val parent = ensureSlotExercise(prescriptionDao, slotId, exerciseId, now)
        prescriptionDao.insertSlotSet(
            ProgramSlotSetEntity(
                id = UUID.randomUUID().toString(),
                slotExerciseId = parent.id,
                setIndex = prescriptionDao.maxSetIndex(parent.id) + 1,
                role = edit.role,
                targetWeightGrams = edit.targetWeightGrams,
                targetAssistanceGrams = edit.targetAssistanceGrams,
                targetRepsMin = edit.targetRepsMin,
                targetRepsMax = edit.targetRepsMax,
                targetRpeHalves = edit.targetRpeHalves,
                targetPercentOf1Rm = edit.targetPercentOf1Rm,
                note = edit.note,
                createdAt = now,
                updatedAt = now,
                deletedAt = null,
            ),
        )
    }

    override suspend fun updateSlotSet(
        slotSetId: String,
        edit: SlotSetEdit,
    ): DataResult<Unit> = dataResultOf {
        validateSlotSet(edit)
        val existing = prescriptionDao.findSlotSet(slotSetId)
            ?: throw NotFoundException("prescribed set $slotSetId")
        val updated = prescriptionDao.updateSlotSet(
            existing.copy(
                role = edit.role,
                targetWeightGrams = edit.targetWeightGrams,
                targetAssistanceGrams = edit.targetAssistanceGrams,
                targetRepsMin = edit.targetRepsMin,
                targetRepsMax = edit.targetRepsMax,
                targetRpeHalves = edit.targetRpeHalves,
                targetPercentOf1Rm = edit.targetPercentOf1Rm,
                note = edit.note,
                updatedAt = timeSource.nowEpochMillis(),
            ),
        )
        if (updated == 0) throw NotFoundException("prescribed set $slotSetId")
    }

    override suspend fun removeSlotSet(slotSetId: String): DataResult<Unit> = dataResultOf {
        val now = timeSource.nowEpochMillis()
        val existing = prescriptionDao.findSlotSet(slotSetId)
            ?: throw NotFoundException("prescribed set $slotSetId")
        if (prescriptionDao.softDeleteSlotSet(slotSetId, now) == 0) {
            throw NotFoundException("prescribed set $slotSetId")
        }
        // The parent exercise row exists to carry the sets: with none left and nothing else said,
        // it goes too, so an emptied prescription leaves no row behind.
        val parent = prescriptionDao.findSlotExerciseById(existing.slotExerciseId)
        if (parent != null) {
            val saysNothing = parent.restSeconds == null && parent.techniqueNote == null
            if (saysNothing && prescriptionDao.countSlotSets(parent.id) == 0) {
                prescriptionDao.softDeleteSlotExercise(parent.id, now)
            }
        }
    }

    override suspend fun slotPreviousPerformance(
        slotId: String,
        exerciseId: String,
        currentSessionId: String,
        zone: ZoneId,
    ): DataResult<PreviousPerformance> = dataResultOf {
        val slot = dao.findSlot(slotId) ?: throw NotFoundException("program slot $slotId")
        val slots = dao.findSlotDetails(slot.programId).map { it.toDomain() }
        val candidates = dao.finishedSessionsForTemplate(slot.templateId, currentSessionId)
            .map { it.toProgramSession(zone) }

        // A slot's own history is the P3.3 assignment: a session settles one occurrence, so two
        // slots naming one template each progress from the sessions that settled them.
        val settled = ProgramSchedule.sessionAssignments(slots, candidates)
            .lastOrNull { (_, occurrence) -> occurrence.slotId == slotId }
            ?: return@dataResultOf PreviousPerformance(emptyList())

        PreviousPerformance(
            sets = database.workoutDao().findSetsFor(settled.first.sessionId, exerciseId)
                .map { it.toDomain() },
        )
    }

    override suspend fun estimatedOneRepMax(exerciseId: String): DataResult<Long?> = dataResultOf {
        // The exercise's whole history, for the reason a record reads all of it: an estimate is a
        // fact about the lifter, and a chart's window would forget a heavy old set (N17, P3.8).
        database.trendsDao().observeExerciseTrendRows(exerciseId, limit = ALL_TREND_SESSIONS)
            .first()
            .map { it.toExerciseTrendRow() }
            .toExerciseTrendPoints()
            .latestValue(ExerciseTrendMetric.ESTIMATED_1RM)
            ?.roundToLong()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeProgramRun(programId: String): Flow<ProgramRun?> =
        dao.observeSlotDetails(programId).flatMapLatest { rows ->
            val slots = rows.map { it.toDomain() }
            if (slots.isEmpty()) {
                flowOf(null)
            } else {
                combine(
                    runDao.observeFinishedSessions(slots.map { it.templateId }.distinct()),
                    runDao.observeSkipsForSlots(slots.map { it.id }),
                ) { sessions, skips ->
                    // The zone is read when the data arrives rather than captured (B45); it is the
                    // fallback only for a session recorded before N25.
                    val zone = ZoneId.systemDefault()
                    programRun(
                        slots = slots,
                        sessions = sessions.mapNotNull { it.toRunSession(zone) },
                        skips = skips.map {
                            RecordedSkip(slotId = it.slotId, weekStart = LocalDate.ofEpochDay(it.weekStart))
                        },
                    )
                }
            }
        }

    override suspend fun pendingOccurrences(
        today: LocalDate,
        zone: ZoneId,
    ): DataResult<List<PendingOccurrence>> = dataResultOf {
        val active = dao.findActivePrograms()
        if (active.isEmpty()) return@dataResultOf emptyList()

        val weekStart = ProgramSchedule.weekStartOf(today)
        // A day of slack at each end: a session's week is taken in its own zone (N25), so a
        // workout performed near midnight elsewhere can fall in a week the device clock does
        // not name. The exact bucketing happens in ProgramSchedule, per session.
        val from = weekStart.minusDays(SLACK_DAYS).atStartOfDay(zone).toInstant().toEpochMilli()
        val to = weekStart.plusDays(DAYS_IN_WEEK + SLACK_DAYS).atStartOfDay(zone).toInstant().toEpochMilli()

        val sessions = dao.sessionsStartedBetween(from, to).map { it.toProgramSession(zone) }
        val skips = dao.findSkipsForWeek(weekStart.toEpochDay()).map {
            RecordedSkip(slotId = it.slotId, weekStart = LocalDate.ofEpochDay(it.weekStart))
        }

        // The union of every active program's misses (P3.12). Each program is resolved on its
        // own slots — a session settles an occurrence by its template, so the same session list
        // answers for all of them — and the misses are merged earliest first, with the programs'
        // own order breaking a tie on the same day.
        val pending = mutableListOf<PendingOccurrence>()
        active.forEach { program ->
            pending += ProgramSchedule.pendingOccurrences(
                slots = dao.findSlotDetails(program.id).map { it.toDomain() },
                sessions = sessions,
                skips = skips,
                today = today,
            )
        }
        pending.sortedBy { it.date }
    }

    override suspend fun skipOccurrences(
        slotIds: List<String>,
        weekStart: LocalDate,
    ): DataResult<Unit> = dataResultOf {
        if (slotIds.isEmpty()) return@dataResultOf
        val epochDay = weekStart.toEpochDay()
        val now = timeSource.nowEpochMillis()
        database.withTransaction {
            slotIds.distinct().forEach { slotId ->
                // Idempotent: a second Continue on the same week must not record twice.
                if (dao.countSkips(slotId, epochDay) == 0) {
                    dao.insertSkip(
                        ProgramSkipEntity(
                            id = UUID.randomUUID().toString(),
                            slotId = slotId,
                            weekStart = epochDay,
                            createdAt = now,
                            updatedAt = now,
                            deletedAt = null,
                        ),
                    )
                }
            }
        }
    }

    override suspend fun monthAdherence(
        month: YearMonth,
        today: LocalDate,
        zone: ZoneId,
    ): DataResult<AdherenceReport> = dataResultOf {
        val firstWeek = ProgramSchedule.weekStartOf(month.atDay(1))
        val lastWeek = ProgramSchedule.weekStartOf(month.atEndOfMonth())

        // The read covers whole weeks rather than the month's days: a slot inside the month can
        // be settled by a session earlier or later in its own week (P3.3), and a skip is keyed by
        // that week's Monday. The day of slack at each end is for sessions performed in another
        // zone, whose week the device clock does not name (N25).
        val from = firstWeek.minusDays(SLACK_DAYS).atStartOfDay(zone).toInstant().toEpochMilli()
        val to = lastWeek.plusDays(DAYS_IN_WEEK + SLACK_DAYS).atStartOfDay(zone).toInstant().toEpochMilli()
        val sessions = dao.finishedSessionsBetween(from, to).map { it.toAdherenceSession(zone) }

        val active = dao.findActivePrograms()
        // Read once, whether or not a program is active: the trained days are still drawn, and a
        // deload week's days are still scheduled (P3.10).
        val deloads = deloadDao.findDeloadsBetween(firstWeek.toEpochDay(), lastWeek.toEpochDay())
            .map { RecordedDeload(programId = it.programId, weekStart = LocalDate.ofEpochDay(it.weekStart)) }

        if (active.isEmpty()) {
            // A trained day needs no schedule, so the calendar is still drawn; there is simply
            // nothing to score it against.
            return@dataResultOf AdherenceReport(
                hasActiveProgram = false,
                adherence = ProgramSchedule.monthAdherence(
                    slots = emptyList(),
                    sessions = sessions,
                    skips = emptyList(),
                    deloads = emptyList(),
                    month = month,
                    today = today,
                ),
            )
        }

        // The union of every active program's slots (P3.12), in the authored order. Adherence
        // counts occurrences rather than days, so the programs' counts simply add up; the
        // per-program breakdown is a later row (P3.14).
        val slots = mutableListOf<ProgramSlot>()
        active.forEach { program ->
            slots += dao.findSlotDetails(program.id).map { it.toDomain() }
        }
        val skips = dao.findSkipsBetween(firstWeek.toEpochDay(), lastWeek.toEpochDay()).map {
            RecordedSkip(slotId = it.slotId, weekStart = LocalDate.ofEpochDay(it.weekStart))
        }
        val activeIds = active.map { it.id }.toSet()

        AdherenceReport(
            hasActiveProgram = true,
            adherence = ProgramSchedule.monthAdherence(slots, sessions, skips, deloads, month, today),
            programs = active.map { it.toDomain() },
            // Only the active programs' weeks: a program no longer followed has nothing to toggle.
            deloadWeeks = deloads
                .filter { it.programId in activeIds }
                .groupBy({ it.programId }, { it.weekStart })
                .mapValues { (_, weeks) -> weeks.toSet() },
        )
    }

    override suspend fun setDeloadWeek(
        programId: String,
        weekStart: LocalDate,
        marked: Boolean,
    ): DataResult<Unit> = dataResultOf {
        val epochDay = weekStart.toEpochDay()
        val now = timeSource.nowEpochMillis()
        if (marked) {
            if (dao.findProgram(programId) == null) throw NotFoundException("program $programId")
            // Idempotent: marking a marked week is not an error and must not double-record.
            if (deloadDao.countDeload(programId, epochDay) == 0) {
                deloadDao.insertDeload(
                    ProgramDeloadEntity(
                        id = UUID.randomUUID().toString(),
                        programId = programId,
                        weekStart = epochDay,
                        createdAt = now,
                        updatedAt = now,
                        deletedAt = null,
                    ),
                )
            }
        } else {
            // Unmarking an unmarked week is a no-op rather than a failure: the state asked for is
            // the state it is in.
            deloadDao.softDeleteDeload(programId, epochDay, now)
        }
    }

    private companion object {
        /** One day of slack at each end of the week, for sessions in another zone. */
        const val SLACK_DAYS = 1L

        const val DAYS_IN_WEEK = 7L
    }
}

/**
 * A slot prescribes only what its template trains (ROADMAP P3.8).
 *
 * Checked rather than trusted: a prescription for an exercise the template does not have is
 * invisible on every screen the moment it is written, which is the same trap `addSlot` guards a
 * slot against. File-level because the repository is at the function ceiling this project
 * enforces, and this reads one table rather than owning any state.
 */
private suspend fun requireExerciseInTemplate(
    database: WorkoutDatabase,
    templateId: String,
    exerciseId: String,
) {
    val planned = database.templateDao().findPlannedExercises(templateId)
    if (planned.none { it.exerciseId == exerciseId }) {
        throw NotFoundException("$exerciseId is not in the slot's template")
    }
}

/**
 * A prescribed set's targets use the plan's vocabulary, plus the percentage (ROADMAP P3.8).
 *
 * Two functions rather than one `when` for the reason the template repository states its own
 * validation apart: a load rule and an effort rule are different questions, and one of them
 * growing should not push the other past the complexity ceiling.
 */
private fun validateSlotSet(edit: SlotSetEdit) {
    validateSlotSetLoad(edit)
    validateSlotSetEffort(edit)
}

/** A prescribed load is a weight or a magnitude of assistance, never a negative either way. */
private fun validateSlotSetLoad(edit: SlotSetEdit) {
    val problem = when {
        edit.targetWeightGrams != null && edit.targetWeightGrams < 0 ->
            "A target weight cannot be negative."

        edit.targetAssistanceGrams != null && edit.targetAssistanceGrams < 0 ->
            "Assistance is a magnitude, not a negative weight."

        else -> null
    }
    if (problem != null) throw InvalidInputException(problem)
}

/** Reps, RPE and the percentage all sit on a scale, and the scale is checked (P3.8). */
private fun validateSlotSetEffort(edit: SlotSetEdit) {
    val problem = when {
        edit.targetRepsMin != null && edit.targetRepsMin < 1 -> "Target reps must be at least 1."
        edit.targetRepsMax != null && edit.targetRepsMax < 1 -> "Target reps must be at least 1."
        edit.targetRepsMin != null && edit.targetRepsMax != null &&
            edit.targetRepsMin > edit.targetRepsMax ->
            "The low end of a rep range cannot exceed the high end."

        !Rpe.isValid(edit.targetRpeHalves) ->
            "Target RPE must be between 1 and 10, in half steps."

        edit.targetPercentOf1Rm != null && edit.targetPercentOf1Rm !in 1..MAX_SLOT_PERCENT ->
            "A percentage of the estimated one-rep max must be between 1 and $MAX_SLOT_PERCENT."

        else -> null
    }
    if (problem != null) throw InvalidInputException(problem)
}

/** Above this, a "percentage of the max" is no longer a percentage of a max. */
private const val MAX_SLOT_PERCENT = 100

/** A program with no name is a list row nobody can tell apart from the next (ROADMAP P3.3). */
private fun requireName(name: String): String {
    val trimmed = name.trim()
    if (trimmed.isEmpty()) throw InvalidInputException("Give the program a name.")
    return trimmed
}

/** A stand-in for "all of them": no exercise has anywhere near this many sessions (N17). */
private const val ALL_TREND_SESSIONS = 100_000

/**
 * The slot's prescription row for one exercise, created on the first write (ROADMAP P3.8).
 *
 * File-level because the repository is at the function ceiling this project enforces, and this is
 * a read-or-insert of one row rather than a decision the repository owns.
 */
private suspend fun ensureSlotExercise(
    dao: ProgramPrescriptionDao,
    slotId: String,
    exerciseId: String,
    now: Long,
): ProgramSlotExerciseEntity =
    dao.findSlotExercise(slotId, exerciseId) ?: ProgramSlotExerciseEntity(
        id = UUID.randomUUID().toString(),
        slotId = slotId,
        exerciseId = exerciseId,
        createdAt = now,
        updatedAt = now,
        deletedAt = null,
    ).also { dao.insertSlotExercise(it) }

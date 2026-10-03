package com.example.androidapp.data

import com.example.androidapp.data.local.ExerciseDao
import com.example.androidapp.data.local.ProgramDao
import com.example.androidapp.data.local.ProgramDeloadDao
import com.example.androidapp.data.local.ProgramDeloadEntity
import com.example.androidapp.data.local.ProgramSkipDao
import com.example.androidapp.data.local.ProgramSkipEntity
import com.example.androidapp.data.local.ProgramSubstitutionDao
import com.example.androidapp.data.local.TemplateDao
import com.example.androidapp.data.local.WorkoutDatabase
import com.example.androidapp.data.local.toAdherenceSession
import com.example.androidapp.data.local.toDomain
import com.example.androidapp.domain.DataResult
import com.example.androidapp.domain.NotFoundException
import com.example.androidapp.domain.TimeSource
import com.example.androidapp.domain.dataResultOf
import com.example.androidapp.domain.nowEpochMillis
import com.example.androidapp.domain.model.AdherenceReport
import com.example.androidapp.domain.model.DayOccurrence
import com.example.androidapp.domain.model.ProgramSchedule
import com.example.androidapp.domain.model.ProgramSlot
import com.example.androidapp.domain.model.RecordedDeload
import com.example.androidapp.domain.model.RecordedSkip
import com.example.androidapp.domain.model.RecordedSubstitution
import com.example.androidapp.domain.repository.AdherenceRepository
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room-backed [AdherenceRepository] (ROADMAP P3.5, P3.13).
 *
 * The arithmetic lives in [ProgramSchedule], which is pure and tested without a database; this
 * class only gathers the rows a window needs — the active programs' slots, the week's finished
 * sessions, and the skips, deloads and substitutions recorded against them.
 */
@Singleton
class RoomAdherenceRepository @Inject constructor(
    private val database: WorkoutDatabase,
    private val timeSource: TimeSource,
) : AdherenceRepository {

    private val programDao: ProgramDao = database.programDao()

    private val skipDao: ProgramSkipDao = database.programSkipDao()

    private val deloadDao: ProgramDeloadDao = database.programDeloadDao()

    private val substitutionDao: ProgramSubstitutionDao = database.programSubstitutionDao()

    private val templateDao: TemplateDao = database.templateDao()

    private val exerciseDao: ExerciseDao = database.exerciseDao()

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
        val sessions = programDao.finishedSessionsBetween(from, to).map { it.toAdherenceSession(zone) }

        val active = programDao.findActivePrograms()
        // Read once, whether or not a program is active: the trained days are still drawn, and a
        // deload week's days are still scheduled (P3.10).
        val deloads = deloadsBetween(firstWeek, lastWeek)

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

        // The union of every active program's slots (P3.12), in the authored order.
        val slots = activeSlots(active.map { it.id })
        val skips = skipDao.findSkipsBetween(firstWeek.toEpochDay(), lastWeek.toEpochDay()).map {
            RecordedSkip(slotId = it.slotId, weekStart = LocalDate.ofEpochDay(it.weekStart))
        }
        // A substituted occurrence is scored against its slot: the day was scheduled, and it was
        // done, whatever it was done with (P3.11).
        val substitutions = substitutionsBetween(firstWeek, lastWeek)
        val activeIds = active.map { it.id }.toSet()

        // The per-lift breakdown's join (P3.14): what each slot's template prescribes, and what
        // those exercises are called. Read once per distinct template, not once per slot.
        val exercisesByTemplate = mutableMapOf<String, List<String>>()
        slots.map { it.templateId }.distinct().forEach { templateId ->
            exercisesByTemplate[templateId] =
                templateDao.findPlannedExercises(templateId).map { it.exerciseId }
        }
        val exerciseNames = exercisesByTemplate.values.flatten().distinct().mapNotNull { id ->
            exerciseDao.findById(id)?.let { id to it.name }
        }.toMap()

        AdherenceReport(
            hasActiveProgram = true,
            adherence = ProgramSchedule.monthAdherence(
                slots = slots,
                sessions = sessions,
                skips = skips,
                deloads = deloads,
                substitutions = substitutions,
                exercisesByTemplate = exercisesByTemplate,
                exerciseNames = exerciseNames,
                month = month,
                today = today,
            ),
            programs = active.map { it.toDomain() },
            // Only the active programs' weeks: a program no longer followed has nothing to toggle.
            deloadWeeks = deloads
                .filter { it.programId in activeIds }
                .groupBy({ it.programId }, { it.weekStart })
                .mapValues { (_, weeks) -> weeks.toSet() },
        )
    }

    override suspend fun occurrencesOn(
        date: LocalDate,
        today: LocalDate,
        zone: ZoneId,
    ): DataResult<List<DayOccurrence>> = dataResultOf {
        val active = programDao.findActivePrograms()
        if (active.isEmpty()) return@dataResultOf emptyList()

        val weekStart = ProgramSchedule.weekStartOf(date)
        // The same slack as the month's read: a session's own week comes from its own zone (N25),
        // so the day a week was trained can sit a day outside the device's idea of it.
        val from = weekStart.minusDays(SLACK_DAYS).atStartOfDay(zone).toInstant().toEpochMilli()
        val to = weekStart.plusDays(DAYS_IN_WEEK + SLACK_DAYS).atStartOfDay(zone).toInstant().toEpochMilli()
        val sessions = programDao.finishedSessionsBetween(from, to).map { it.toAdherenceSession(zone) }
        val skips = skipDao.findSkipsForWeek(weekStart.toEpochDay()).map {
            RecordedSkip(slotId = it.slotId, weekStart = LocalDate.ofEpochDay(it.weekStart))
        }

        ProgramSchedule.occurrencesOn(
            slots = activeSlots(active.map { it.id }),
            sessions = sessions,
            skips = skips,
            date = date,
            today = today,
            deloads = deloadsBetween(weekStart, weekStart),
            substitutions = substitutionsBetween(weekStart, weekStart),
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
            if (programDao.findProgram(programId) == null) throw NotFoundException("program $programId")
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

    override suspend fun setOccurrenceSkipped(
        slotId: String,
        weekStart: LocalDate,
        skipped: Boolean,
    ): DataResult<Unit> = dataResultOf {
        val epochDay = weekStart.toEpochDay()
        val now = timeSource.nowEpochMillis()
        if (skipped) {
            if (programDao.findSlot(slotId) == null) throw NotFoundException("program slot $slotId")
            // Idempotent, exactly as the prompt's whole-week write is: marking a marked occurrence
            // must not double-record.
            if (skipDao.countSkips(slotId, epochDay) == 0) {
                skipDao.insertSkip(
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
        } else {
            skipDao.softDeleteSkip(slotId, epochDay, now)
        }
    }

    /** The union of the active programs' slots, in the authored order (P3.12). */
    private suspend fun activeSlots(programIds: List<String>): List<ProgramSlot> {
        val slots = mutableListOf<ProgramSlot>()
        programIds.forEach { id ->
            slots += programDao.findSlotDetails(id).map { it.toDomain() }
        }
        return slots
    }

    private suspend fun deloadsBetween(from: LocalDate, to: LocalDate): List<RecordedDeload> =
        deloadDao.findDeloadsBetween(from.toEpochDay(), to.toEpochDay()).map {
            RecordedDeload(programId = it.programId, weekStart = LocalDate.ofEpochDay(it.weekStart))
        }

    private suspend fun substitutionsBetween(from: LocalDate, to: LocalDate): List<RecordedSubstitution> =
        substitutionDao.findSubstitutionsBetween(from.toEpochDay(), to.toEpochDay()).map {
            RecordedSubstitution(
                slotId = it.slotId,
                weekStart = LocalDate.ofEpochDay(it.weekStart),
                templateId = it.templateId,
            )
        }

    private companion object {
        /** One day of slack at each end of the week, for sessions in another zone. */
        const val SLACK_DAYS = 1L

        const val DAYS_IN_WEEK = 7L
    }
}

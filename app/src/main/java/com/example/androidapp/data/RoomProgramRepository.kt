package com.example.androidapp.data

import androidx.room.withTransaction
import com.example.androidapp.data.local.ProgramDao
import com.example.androidapp.data.local.ProgramSkipEntity
import com.example.androidapp.data.local.ProgramSlotEntity
import com.example.androidapp.data.local.ProgramEntity
import com.example.androidapp.data.local.WorkoutDatabase
import com.example.androidapp.data.local.toAdherenceSession
import com.example.androidapp.data.local.toDomain
import com.example.androidapp.data.local.toProgramSession
import com.example.androidapp.domain.DataResult
import com.example.androidapp.domain.InvalidInputException
import com.example.androidapp.domain.NotFoundException
import com.example.androidapp.domain.TimeSource
import com.example.androidapp.domain.dataResultOf
import com.example.androidapp.domain.nowEpochMillis
import com.example.androidapp.domain.model.AdherenceReport
import com.example.androidapp.domain.model.PendingOccurrence
import com.example.androidapp.domain.model.ProgramSchedule
import com.example.androidapp.domain.model.ProgramSlot
import com.example.androidapp.domain.model.RecordedSkip
import com.example.androidapp.domain.model.WorkoutProgram
import com.example.androidapp.domain.repository.ProgramRepository
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
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

    override fun observePrograms(): Flow<List<WorkoutProgram>> =
        dao.observePrograms().map { rows -> rows.map { it.toDomain() } }

    override fun observeProgram(programId: String): Flow<WorkoutProgram?> =
        dao.observeProgram(programId).map { it?.toDomain() }

    override fun observeActiveProgram(): Flow<WorkoutProgram?> =
        dao.observeActiveProgram().map { it?.toDomain() }

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

    override suspend fun setActiveProgram(programId: String): DataResult<Unit> = dataResultOf {
        // One transaction, so "exactly one is active" cannot be observed half-applied.
        val updated = dao.setActiveProgram(id = programId, at = timeSource.nowEpochMillis())
        if (updated == 0) throw NotFoundException("program $programId")
    }

    override suspend fun clearActiveProgram(): DataResult<Unit> = dataResultOf {
        dao.clearActiveProgram(at = timeSource.nowEpochMillis())
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

    override suspend fun pendingOccurrences(
        today: LocalDate,
        zone: ZoneId,
    ): DataResult<List<PendingOccurrence>> = dataResultOf {
        val active = dao.findActiveProgram() ?: return@dataResultOf emptyList()
        val slots = dao.findSlotDetails(active.id).map { it.toDomain() }
        if (slots.none { it.weekday != null }) return@dataResultOf emptyList()

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

        ProgramSchedule.pendingOccurrences(slots, sessions, skips, today)
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

        val active = dao.findActiveProgram()
        if (active == null) {
            // A trained day needs no schedule, so the calendar is still drawn; there is simply
            // nothing to score it against.
            return@dataResultOf AdherenceReport(
                hasActiveProgram = false,
                adherence = ProgramSchedule.monthAdherence(
                    slots = emptyList(),
                    sessions = sessions,
                    skips = emptyList(),
                    month = month,
                    today = today,
                ),
            )
        }

        val slots = dao.findSlotDetails(active.id).map { it.toDomain() }
        val skips = dao.findSkipsBetween(firstWeek.toEpochDay(), lastWeek.toEpochDay()).map {
            RecordedSkip(slotId = it.slotId, weekStart = LocalDate.ofEpochDay(it.weekStart))
        }

        AdherenceReport(
            hasActiveProgram = true,
            adherence = ProgramSchedule.monthAdherence(slots, sessions, skips, month, today),
        )
    }

    /** A program with no name is a list row nobody can tell apart from the next. */
    private fun requireName(name: String): String {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) throw InvalidInputException("Give the program a name.")
        return trimmed
    }

    private companion object {
        /** One day of slack at each end of the week, for sessions in another zone. */
        const val SLACK_DAYS = 1L

        const val DAYS_IN_WEEK = 7L
    }
}

package com.example.androidapp.data

import kotlinx.coroutines.flow.first
import com.example.androidapp.domain.model.PersonalRecords
import com.example.androidapp.domain.model.PerformedSetSpec
import com.example.androidapp.data.local.ExerciseTrendRowEntity
import androidx.room.withTransaction
import com.example.androidapp.data.local.SetEntryEntity
import com.example.androidapp.data.local.SessionExerciseEntity
import com.example.androidapp.data.local.WorkoutDatabase
import com.example.androidapp.data.local.toDomain
import com.example.androidapp.domain.DataResult
import com.example.androidapp.domain.InvalidInputException
import com.example.androidapp.domain.NotFoundException
import com.example.androidapp.domain.TimeSource
import com.example.androidapp.domain.dataResultOf
import com.example.androidapp.domain.model.PreviousPerformance
import com.example.androidapp.domain.model.TenPointScale
import com.example.androidapp.domain.model.SessionExercise
import com.example.androidapp.domain.model.SetEntry
import com.example.androidapp.domain.model.Rpe
import com.example.androidapp.domain.model.SetType
import com.example.androidapp.domain.model.WorkoutSession
import com.example.androidapp.domain.model.WorkoutSummary
import com.example.androidapp.domain.nowEpochMillis
import com.example.androidapp.domain.repository.StartedSession
import com.example.androidapp.domain.repository.WorkoutRepository
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Room-backed [WorkoutRepository] (ROADMAP P1.2, P1.3, P1.4, P1.8).
 *
 * Every write goes through [dataResultOf], so a database failure surfaces to the
 * caller as a [DataResult.Failure] instead of an exception that could disappear
 * inside a coroutine.
 */
@Singleton
class RoomWorkoutRepository @Inject constructor(
    private val database: WorkoutDatabase,
    private val timeSource: TimeSource,
) : WorkoutRepository {

    private val dao = database.workoutDao()

    /** Read only when a workout is started from a template (ROADMAP N3). */
    private val templateDao = database.templateDao()

    override fun observeActiveSession(): Flow<WorkoutSession?> =
        dao.observeActiveSession().map { it?.toDomain() }

    override fun observeSessionExercises(sessionId: String): Flow<List<SessionExercise>> =
        dao.observeSessionExerciseDetails(sessionId).map { rows -> rows.map { it.toDomain() } }

    override fun observeSets(sessionId: String): Flow<List<SetEntry>> =
        dao.observeSetsForSession(sessionId).map { rows -> rows.map { it.toDomain() } }

    override fun observeHistory(): Flow<List<WorkoutSummary>> =
        dao.observeHistory().map { rows -> rows.map { it.toDomain() } }

    override fun observeSession(sessionId: String): Flow<WorkoutSession?> =
        dao.observeSession(sessionId).map { it?.toDomain() }

    override suspend fun startOrResumeSession(templateId: String?): DataResult<StartedSession> =
        dataResultOf {
            // find-or-create and any template seeding are one transaction, so two
            // taps cannot open two sessions — and a failure cannot leave a session
            // holding half a template. The same transaction reports whether this
            // call is the one that opened it (ROADMAP N4).
            database.withTransaction {
                val start = dao.findOrCreateActiveSession(
                    id = UUID.randomUUID().toString(),
                    now = timeSource.nowEpochMillis(),
                )
                if (start.created && templateId != null) {
                    templateDao.findPlannedExercises(templateId).forEach { planned ->
                        // The plan's rest and cue come with it (ROADMAP N14): a plan
                        // that says "3m break" and a workout counting 90 seconds is
                        // the plan being ignored. Null leaves the library's showing
                        // through, which is the fallback N5 established.
                        appendExercise(
                            sessionId = start.session.id,
                            exerciseId = planned.exerciseId,
                            restSeconds = planned.restSeconds,
                            techniqueNote = planned.techniqueNote,
                        )
                    }
                }
                StartedSession(id = start.session.id, isNew = start.created)
            }
        }

    override suspend fun addExercise(sessionId: String, exerciseId: String): DataResult<Unit> =
        dataResultOf {
            if (dao.findSession(sessionId) == null) {
                throw NotFoundException("session $sessionId is not open")
            }
            appendExercise(sessionId, exerciseId)
        }

    /**
     * The one place a session exercise is appended, shared by a manual "add
     * exercise" and by starting a workout from a template (ROADMAP N3) — so the
     * order a template produces is the same order the picker would produce.
     */
    private suspend fun appendExercise(
        sessionId: String,
        exerciseId: String,
        restSeconds: Int? = null,
        techniqueNote: String? = null,
    ) {
        val now = timeSource.nowEpochMillis()
        dao.insertSessionExercise(
            SessionExerciseEntity(
                id = UUID.randomUUID().toString(),
                sessionId = sessionId,
                exerciseId = exerciseId,
                position = dao.maxPosition(sessionId) + 1,
                restSeconds = restSeconds,
                techniqueNote = techniqueNote,
                createdAt = now,
                updatedAt = now,
                deletedAt = null,
            ),
        )
    }

    override suspend fun removeExercise(sessionExerciseId: String): DataResult<Unit> =
        dataResultOf {
            val updated = dao.softDeleteSessionExercise(
                id = sessionExerciseId,
                at = timeSource.nowEpochMillis(),
            )
            if (updated == 0) throw NotFoundException("session exercise $sessionExerciseId")
        }

    override suspend fun finishExercise(sessionExerciseId: String): DataResult<Unit> = dataResultOf {
        // The session is read first so the rest can be cleared on the same row that
        // owns the exercise — ending a lift must not leave a timer armed for a break
        // the user has finished with (ROADMAP N7).
        val sessionId = dao.findSessionIdForSessionExercise(sessionExerciseId)
            ?: throw NotFoundException("session exercise $sessionExerciseId")
        val now = timeSource.nowEpochMillis()
        if (dao.setSessionExerciseFinished(id = sessionExerciseId, finishedAt = now, at = now) == 0) {
            throw NotFoundException("session exercise $sessionExerciseId")
        }
        dao.updateRestTimer(id = sessionId, restEndsAt = null, at = now)
    }

    override suspend fun setWorkoutNotes(sessionId: String, note: String?): DataResult<Unit> =
        dataResultOf {
            // Blank is stored as null rather than "": two representations of
            // "nothing" would show up differently on screen, the same rule the
            // readiness note follows.
            val updated = dao.setWorkoutNotes(
                id = sessionId,
                notes = note?.trim()?.ifEmpty { null },
                at = timeSource.nowEpochMillis(),
            )
            if (updated == 0) throw NotFoundException("session $sessionId")
        }

    override suspend fun reopenExercise(sessionExerciseId: String): DataResult<Unit> = dataResultOf {
        val updated = dao.setSessionExerciseFinished(
            id = sessionExerciseId,
            finishedAt = null,
            at = timeSource.nowEpochMillis(),
        )
        if (updated == 0) throw NotFoundException("session exercise $sessionExerciseId")
    }

    override suspend fun rateExercise(
        sessionExerciseId: String,
        muscleFeel: Int?,
        jointPain: Int?,
        jointPainNote: String?,
    ): DataResult<Unit> = dataResultOf {
        // Both sit on the same 1–10 scale as a set's RPE, and all three are
        // skippable, so the one validator covers them (ROADMAP N8).
        if (!TenPointScale.isValid(muscleFeel) || !TenPointScale.isValid(jointPain)) {
            throw InvalidInputException(
                "Ratings must be between ${TenPointScale.MIN} and ${TenPointScale.MAX}.",
            )
        }
        val updated = dao.setSessionExerciseRating(
            id = sessionExerciseId,
            muscleFeel = muscleFeel,
            jointPain = jointPain,
            // Blank is stored as null rather than "": two representations of
            // "nothing" would show up differently on screen (the same rule the
            // readiness note follows).
            jointPainNote = jointPainNote?.trim()?.ifEmpty { null },
            at = timeSource.nowEpochMillis(),
        )
        if (updated == 0) throw NotFoundException("session exercise $sessionExerciseId")
    }

    override suspend fun finishSession(sessionId: String): DataResult<Unit> = dataResultOf {
        // Finishing also stops any running rest: leaving a timer armed on a closed
        // session would fire a notification for a workout that is over.
        val now = timeSource.nowEpochMillis()
        if (dao.markFinished(id = sessionId, at = now) == 0) {
            throw NotFoundException("session $sessionId")
        }
        dao.updateRestTimer(id = sessionId, restEndsAt = null, at = now)
    }

    override suspend fun setReadinessNote(sessionId: String, note: String?): DataResult<Unit> =
        dataResultOf {
            // Blank is stored as null rather than "": two representations of "nothing
            // written" would show up differently on the workout header.
            val cleaned = note?.trim()?.ifEmpty { null }
            val updated = dao.updateReadinessNote(
                id = sessionId,
                note = cleaned,
                at = timeSource.nowEpochMillis(),
            )
            if (updated == 0) throw NotFoundException("session $sessionId")
        }

    override suspend fun deleteSession(sessionId: String): DataResult<Unit> = dataResultOf {
        if (dao.findSession(sessionId) == null) {
            throw NotFoundException("session $sessionId")
        }
        // Children first, then the parent, so a partial failure cannot leave the
        // session invisible but its exercises still live.
        val now = timeSource.nowEpochMillis()
        dao.softDeleteSessionExercises(sessionId = sessionId, at = now)
        dao.softDeleteSession(id = sessionId, at = now)
    }

    override suspend fun logSet(
        sessionExerciseId: String,
        reps: Int,
        weightGrams: Long,
        setType: SetType,
        assistanceGrams: Long,
    ): DataResult<Unit> = dataResultOf {
        // A stale screen can hold an id for an exercise that was removed, or whose
        // session was already finished. Writing anyway would file the set under
        // history the user cannot reach, so this fails as NotFound instead.
        if (dao.countLoggableSessionExercise(sessionExerciseId) == 0) {
            throw NotFoundException("session exercise $sessionExerciseId is not loggable")
        }
        val now = timeSource.nowEpochMillis()
        dao.insertSet(
            SetEntryEntity(
                id = UUID.randomUUID().toString(),
                sessionExerciseId = sessionExerciseId,
                setIndex = dao.maxSetIndex(sessionExerciseId) + 1,
                // A set of zero reps is not a set. Clamping here rather than
                // trusting the caller keeps a mistyped field out of the history.
                reps = reps.coerceAtLeast(1),
                weightGrams = weightGrams.coerceAtLeast(0L),
                // A magnitude, so a negative arriving here becomes none rather than
                // a weight that subtracts (ROADMAP N15).
                assistanceGrams = assistanceGrams.coerceAtLeast(0L),
                setType = setType,
                completedAt = now,
                createdAt = now,
                updatedAt = now,
                deletedAt = null,
            ),
        )
    }

    override suspend fun updateSet(
        setId: String,
        reps: Int,
        weightGrams: Long,
        rpeHalves: Int?,
        note: String?,
        setType: SetType,
        assistanceGrams: Long,
    ): DataResult<Unit> =
        dataResultOf {
            // The editor's field is the real guard; this is the boundary that keeps
            // an out-of-range value from reaching the database (ROADMAP N6).
            // RPE takes halves; the feel and pain ratings above do not (ROADMAP N6).
            if (!Rpe.isValid(rpeHalves)) {
                throw InvalidInputException("RPE must be between 1 and 10, in half steps.")
            }

            // Read the stored row first, so the columns an edit does not touch
            // (setIndex, completedAt, createdAt) are preserved rather than invented,
            // and so a soft-deleted set cannot be resurrected by an edit.
            val stored = dao.findSetById(setId) ?: throw NotFoundException("set $setId")
            val updated = stored.copy(
                reps = reps.coerceAtLeast(1),
                weightGrams = weightGrams.coerceAtLeast(0L),
                rpeHalves = rpeHalves,
                // The role is part of what the set was (ROADMAP N14), and so is
                // what the machine took off (N15).
                setType = setType,
                assistanceGrams = assistanceGrams.coerceAtLeast(0L),
                // A cleared comment is null, not "": one representation of nothing.
                note = note?.trim()?.ifEmpty { null },
                updatedAt = timeSource.nowEpochMillis(),
            )
            if (dao.updateSet(updated) == 0) throw NotFoundException("set $setId")
        }

    override suspend fun deleteSet(setId: String): DataResult<Unit> = dataResultOf {
        val updated = dao.softDeleteSet(id = setId, at = timeSource.nowEpochMillis())
        if (updated == 0) throw NotFoundException("set $setId")
    }

    override suspend fun previousPerformance(
        exerciseId: String,
        currentSessionId: String,
    ): DataResult<PreviousPerformance> = dataResultOf {
        // Two plain queries composed here rather than one correlated subquery:
        // this is the read a user waits on before their first set, and it is far
        // easier to reason about — and to test — as two obvious steps.
        val previousSessionId = dao.findPreviousSessionIdFor(exerciseId, currentSessionId)
            ?: return@dataResultOf PreviousPerformance(emptyList())

        PreviousPerformance(
            sets = dao.findSetsFor(previousSessionId, exerciseId).map { it.toDomain() },
        )
    }

    override suspend fun personalRecords(
        exerciseId: String,
        excludingSessionId: String?,
    ): DataResult<PersonalRecords> = dataResultOf {
        // Every session, because a record is against all of them — the window that suits a
        // chart would silently forget an old best, which is the one thing a record is for.
        val rows = database.trendsDao().observeExerciseTrendRows(exerciseId, limit = ALL_SESSIONS).first()
        PersonalRecords.from(
            rows.filter { it.sessionId != excludingSessionId }
                .mapNotNull { it.toPerformedSetSpec() },
        )
    }

    override suspend fun startRest(seconds: Int): DataResult<Instant> = dataResultOf {
        val session = dao.findActiveSession() ?: throw NotFoundException("no active session")
        val now = timeSource.now()
        val endsAt = now.plusSeconds(seconds.coerceAtLeast(0).toLong())
        if (dao.updateRestTimer(session.id, endsAt.toEpochMilli(), now.toEpochMilli()) == 0) {
            throw NotFoundException("session ${session.id}")
        }
        endsAt
    }

    override suspend fun adjustRest(deltaSeconds: Int): DataResult<Instant> = dataResultOf {
        val session = dao.findActiveSession() ?: throw NotFoundException("no active session")
        val now = timeSource.now()
        // Adding time to a rest that already ended restarts it from now rather
        // than computing a moment in the past.
        val base = session.restEndsAt?.let(Instant::ofEpochMilli) ?: now
        val moved = base.plusSeconds(deltaSeconds.toLong())
        val endsAt = if (moved.isAfter(now)) moved else now
        if (dao.updateRestTimer(session.id, endsAt.toEpochMilli(), now.toEpochMilli()) == 0) {
            throw NotFoundException("session ${session.id}")
        }
        endsAt
    }

    override suspend fun clearRest(): DataResult<Unit> = dataResultOf {
        val session = dao.findActiveSession() ?: throw NotFoundException("no active session")
        if (dao.updateRestTimer(session.id, null, timeSource.nowEpochMillis()) == 0) {
            throw NotFoundException("session ${session.id}")
        }
    }
}

/** A stand-in for "all of them": no exercise has anywhere near this many sessions. */
private const val ALL_SESSIONS = 100_000

/**
 * A history row as the record rule's input, or null when the row recorded no set.
 *
 * The projection keeps `setType` as its stored name (a database column, not an entity), so
 * the name is resolved here; an unknown name is read as a working set rather than dropped,
 * because silently discarding a set would make a record easier to beat than it is.
 */
private fun ExerciseTrendRowEntity.toPerformedSetSpec(): PerformedSetSpec? {
    val reps = reps ?: return null
    return PerformedSetSpec(
        role = SetType.entries.firstOrNull { it.name == setType } ?: SetType.NORMAL,
        weightGrams = weightGrams ?: 0L,
        assistanceGrams = assistanceGrams ?: 0L,
        reps = reps,
    )
}

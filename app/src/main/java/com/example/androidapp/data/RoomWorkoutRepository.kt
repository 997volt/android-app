package com.example.androidapp.data

import com.example.androidapp.data.local.SessionExerciseEntity
import com.example.androidapp.data.local.WorkoutDatabase
import com.example.androidapp.data.local.toDomain
import com.example.androidapp.domain.DataResult
import com.example.androidapp.domain.NotFoundException
import com.example.androidapp.domain.TimeSource
import com.example.androidapp.domain.dataResultOf
import com.example.androidapp.domain.model.SessionExercise
import com.example.androidapp.domain.model.WorkoutSession
import com.example.androidapp.domain.nowEpochMillis
import com.example.androidapp.domain.repository.WorkoutRepository
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Room-backed [WorkoutRepository] (ROADMAP P1.2, P1.8).
 *
 * Every write goes through [dataResultOf], so a database failure surfaces to the
 * caller as a [DataResult.Failure] instead of an exception that could disappear
 * inside a coroutine.
 */
@Singleton
class RoomWorkoutRepository @Inject constructor(
    database: WorkoutDatabase,
    private val timeSource: TimeSource,
) : WorkoutRepository {

    private val dao = database.workoutDao()

    override fun observeActiveSession(): Flow<WorkoutSession?> =
        dao.observeActiveSession().map { it?.toDomain() }

    override fun observeSessionExercises(sessionId: String): Flow<List<SessionExercise>> =
        dao.observeSessionExerciseDetails(sessionId).map { rows -> rows.map { it.toDomain() } }

    override suspend fun startOrResumeSession(): DataResult<String> = dataResultOf {
        // find-or-create is a single transaction, so two taps cannot open two
        // sessions and leave "the active session" ambiguous.
        val session = dao.findOrCreateActiveSession(
            id = UUID.randomUUID().toString(),
            now = timeSource.nowEpochMillis(),
        )
        session.id
    }

    override suspend fun addExercise(sessionId: String, exerciseId: String): DataResult<Unit> =
        dataResultOf {
            if (dao.findSession(sessionId) == null) {
                throw NotFoundException("session $sessionId is not open")
            }
            val now = timeSource.nowEpochMillis()
            dao.insertSessionExercise(
                SessionExerciseEntity(
                    id = UUID.randomUUID().toString(),
                    sessionId = sessionId,
                    exerciseId = exerciseId,
                    position = dao.maxPosition(sessionId) + 1,
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

    override suspend fun finishSession(sessionId: String): DataResult<Unit> = dataResultOf {
        val updated = dao.markFinished(id = sessionId, at = timeSource.nowEpochMillis())
        if (updated == 0) throw NotFoundException("session $sessionId")
    }

    override suspend fun discardSession(sessionId: String): DataResult<Unit> = dataResultOf {
        if (dao.findSession(sessionId) == null) {
            throw NotFoundException("session $sessionId")
        }
        // Children first, then the parent, so a partial failure cannot leave the
        // session invisible but its exercises still live.
        val now = timeSource.nowEpochMillis()
        dao.softDeleteSessionExercises(sessionId = sessionId, at = now)
        dao.softDeleteSession(id = sessionId, at = now)
    }
}

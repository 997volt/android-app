package com.example.androidapp.data

import com.example.androidapp.domain.dataResultOf
import kotlinx.coroutines.flow.first
import com.example.androidapp.domain.model.PersonalRecords
import com.example.androidapp.domain.model.PerformedSetSpec
import com.example.androidapp.data.local.ExerciseTrendRowEntity
import com.example.androidapp.domain.model.toExerciseTrendPoints
import com.example.androidapp.domain.model.SetType
import com.example.androidapp.domain.model.ExerciseTrendRow
import com.example.androidapp.domain.model.Rpe
import com.example.androidapp.domain.model.ExerciseTrendPoint
import com.example.androidapp.data.local.WorkoutDatabase
import com.example.androidapp.domain.DataResult
import com.example.androidapp.domain.model.TrendPoint
import com.example.androidapp.domain.repository.TrendsRepository
import com.example.androidapp.domain.toDataError
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map


/**
 * Room-backed [TrendsRepository] (ROADMAP N13).
 *
 * The two aggregate queries are merged here rather than in SQL, because averaging a
 * set's RPE and a session exercise's ratings in one statement multiplies the rows and
 * silently weights one by the other.
 *
 * A workout can appear in either query and not the other — RPE with no ratings is
 * normal — so the merge is a union keyed by session id, and the result is reversed
 * into the oldest-first order a chart reads in.
 */
@Singleton
class RoomTrendsRepository @Inject constructor(
    database: WorkoutDatabase,
) : TrendsRepository {

    private val dao = database.trendsDao()

    override fun observeExerciseTrends(
        exerciseId: String,
        limit: Int,
    ): Flow<DataResult<List<ExerciseTrendPoint>>> =
        dao.observeExerciseTrendRows(exerciseId, limit)
            .map<List<ExerciseTrendRowEntity>, DataResult<List<ExerciseTrendPoint>>> { rows ->
                // Grouping and arithmetic live in the domain type, where they are pure
                // and tested; this is only the storage-to-domain translation.
                DataResult.Success(
                    rows.map { row ->
                        ExerciseTrendRow(
                            sessionId = row.sessionId,
                            startedAt = Instant.ofEpochMilli(row.startedAt),
                            muscleFeel = row.muscleFeel,
                            jointPain = row.jointPain,
                            weightGrams = row.weightGrams,
                            reps = row.reps,
                            rpeHalves = row.rpeHalves,
                            setType = row.setType?.let(SetType::valueOf),
                            assistanceGrams = row.assistanceGrams,
                        )
                    }.toExerciseTrendPoints(),
                )
            }
            .catch { emit(DataResult.Failure(it.toDataError())) }

    override fun observeTrends(limit: Int): Flow<DataResult<List<TrendPoint>>> =
        combine(
            dao.observeRpeTrend(limit),
            dao.observeFeelTrend(limit),
        ) { rpeRows, feelRows ->
            val rpeBySession = rpeRows.associateBy { it.sessionId }
            val feelBySession = feelRows.associateBy { it.sessionId }

            (rpeBySession.keys + feelBySession.keys)
                .mapNotNull { sessionId ->
                    val rpeHalves = rpeBySession[sessionId]
                    val feel = feelBySession[sessionId]
                    val startedAt = rpeHalves?.startedAt ?: feel?.startedAt ?: return@mapNotNull null
                    TrendPoint(
                        startedAt = Instant.ofEpochMilli(startedAt),
                        // SQL averaged halves; the series is in RPE units (N6, N13).
                        averageRpe = rpeHalves?.averageRpe?.div(Rpe.HALVES_PER_POINT),
                        averageMuscleFeel = feel?.averageMuscleFeel,
                        averageJointPain = feel?.averageJointPain,
                    )
                }
                // The queries return newest first, which is right for a limit and
                // wrong for a chart.
                .sortedBy { it.startedAt }
        }
            .map<List<TrendPoint>, DataResult<List<TrendPoint>>> { DataResult.Success(it) }
            .catch { throwable ->
                if (throwable is CancellationException) throw throwable
                emit(DataResult.Failure(throwable.toDataError()))
            }
}


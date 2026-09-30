package com.example.androidapp.data

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

private const val HALVES_PER_POINT = 2.0

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
                        averageRpe = rpeHalves?.averageRpe?.div(HALVES_PER_POINT),
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

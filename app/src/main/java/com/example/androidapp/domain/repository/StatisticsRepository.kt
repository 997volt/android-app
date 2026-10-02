package com.example.androidapp.domain.repository

import kotlinx.coroutines.flow.Flow
import com.example.androidapp.domain.model.WorkoutSummary
import com.example.androidapp.domain.DataResult
import java.time.Instant

/**
 * The questions the Statistics screen asks that no other screen does (ROADMAP N35).
 *
 * Its own port rather than more methods on `WorkoutRepository`: statistics reads history to summarise it,
 * which is a different job from logging a set, and the workout repository is at its function ceiling.
 */
interface StatisticsRepository {

    /**
     * Finished workouts as the history list shows them, for the overview's totals.
     *
     * On this port rather than read from `WorkoutRepository` so the screen depends on statistics and
     * nothing else — and implemented with the history query that already exists, not a second copy of its
     * SQL: two queries totalling the same rows would be two things to keep in step.
     */
    fun observeWorkoutSummaries(): Flow<List<WorkoutSummary>>

    /**
     * Records set between [from] (inclusive) and [to] (exclusive).
     *
     * A record is judged against everything performed before it, so this cannot be answered by a stored
     * flag: nothing writes down that a set *was* a record, only what it weighed.
     */
    suspend fun countRecordsIn(from: Instant, to: Instant): DataResult<Int>
}

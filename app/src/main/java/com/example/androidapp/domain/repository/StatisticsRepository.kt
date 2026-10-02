package com.example.androidapp.domain.repository

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
     * Records set between [from] (inclusive) and [to] (exclusive).
     *
     * A record is judged against everything performed before it, so this cannot be answered by a stored
     * flag: nothing writes down that a set *was* a record, only what it weighed.
     */
    suspend fun countRecordsIn(from: Instant, to: Instant): DataResult<Int>
}

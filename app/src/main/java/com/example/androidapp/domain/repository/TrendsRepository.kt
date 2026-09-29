package com.example.androidapp.domain.repository

import com.example.androidapp.domain.DataResult
import com.example.androidapp.domain.model.TrendPoint
import kotlinx.coroutines.flow.Flow

/**
 * Reads the signals the app collects back out, as trends (ROADMAP N13).
 *
 * Read-only by design: nothing here writes, because a trend is a view of what was
 * already recorded rather than a thing the user maintains.
 */
interface TrendsRepository {

    /**
     * The most recent [limit] finished workouts that carry at least one signal,
     * **oldest first** — the order a chart is drawn in.
     *
     * Returns a [DataResult] like every other read (ROADMAP B4): a failure here is a
     * message on the screen, not an exception lost inside the flow.
     */
    fun observeTrends(limit: Int = DEFAULT_LIMIT): Flow<DataResult<List<TrendPoint>>>

    companion object {
        /**
         * Ten workouts: enough for a shape to appear, few enough that each point is
         * still a workout the user remembers rather than a smear of history.
         */
        const val DEFAULT_LIMIT = 10
    }
}

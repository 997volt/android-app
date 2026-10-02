package com.example.androidapp.domain.repository

import com.example.androidapp.domain.DataResult
import com.example.androidapp.domain.model.BodyMeasurement
import kotlinx.coroutines.flow.Flow

/**
 * Body measurements (ROADMAP N32).
 *
 * Its own port rather than more methods on an existing one: measurements are not training, and the two
 * never read each other.
 */
interface MeasurementRepository {

    /** Every live entry, newest first. */
    fun observeAll(): Flow<List<BodyMeasurement>>

    /**
     * Writes an entry, keyed by its day.
     *
     * **Saving onto a day that already has an entry edits it** rather than adding a second: the second
     * reading of a day is nearly always a correction of the first, and "what did I weigh today" should
     * not have two answers. An entry carrying an id updates that row; one without is placed by its day.
     */
    suspend fun save(measurement: BodyMeasurement): DataResult<Unit>

    suspend fun delete(id: String): DataResult<Unit>
}

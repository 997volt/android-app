package com.example.androidapp.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * The measurements table (ROADMAP N32).
 *
 * Newest first, because a measurement is read as "how has this been going" rather than "what happened
 * on a particular day" — the chart and the list both want the same order.
 */
@Dao
interface MeasurementDao {

    @Query("SELECT * FROM measurements WHERE deletedAt IS NULL ORDER BY measuredAt DESC, createdAt DESC")
    fun observeAll(): Flow<List<MeasurementEntity>>

    @Query("SELECT * FROM measurements WHERE id = :id AND deletedAt IS NULL")
    suspend fun find(id: String): MeasurementEntity?

    /**
     * Writes an edited entry. The caller builds the row, `updatedAt` included.
     *
     * `@Update` rather than a query with a parameter per column: thirteen parameters is not a
     * signature, and a caller that forgets one would silently write a default.
     *
     * Rows updated: 0 means it is gone, so a stale screen cannot write into nothing.
     */
    @Update
    suspend fun update(row: MeasurementEntity): Int

    @Insert
    suspend fun insert(row: MeasurementEntity)

    @Query("UPDATE measurements SET deletedAt = :at, updatedAt = :at WHERE id = :id AND deletedAt IS NULL")
    suspend fun softDelete(id: String, at: Long): Int
}

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

    /**
     * Every measurement, including soft-deleted ones, for an export (ROADMAP N32).
     *
     * Here rather than in BackupDao, which is at its function ceiling — and this is the table's own
     * DAO, which is where its reads belong anyway. A soft-deleted row is exported like every other
     * table's, because an export is a copy of the database rather than of the current screen.
     */
    @Query("SELECT * FROM measurements")
    suspend fun allForExport(): List<MeasurementEntity>

    @Insert
    suspend fun insertAll(rows: List<MeasurementEntity>): List<Long>

    @Query("SELECT * FROM measurements WHERE deletedAt IS NULL ORDER BY measuredAt DESC, createdAt DESC")
    fun observeAll(): Flow<List<MeasurementEntity>>

    /**
     * The live entry for a local day, if there is one (ROADMAP N32, and the decision in DECISIONS.md).
     *
     * The day is a half-open range in the caller's own zone rather than a stored date column: the entry
     * belongs to the day it was taken where it was taken, and the range is what makes that a query
     * instead of a second source of truth.
     */
    @Query(
        """
        SELECT * FROM measurements
        WHERE measuredAt >= :from AND measuredAt < :to AND deletedAt IS NULL
        ORDER BY measuredAt DESC LIMIT 1
        """,
    )
    suspend fun findOnDay(from: Long, to: Long): MeasurementEntity?

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

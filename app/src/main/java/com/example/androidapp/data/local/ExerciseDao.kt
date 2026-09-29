package com.example.androidapp.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ExerciseDao {

    /**
     * Every live exercise. The soft-delete filter lives in the query rather than
     * in each caller, so no screen can accidentally show a deleted row.
     */
    @Query("SELECT * FROM exercises WHERE deletedAt IS NULL ORDER BY name ASC")
    fun observeAll(): Flow<List<ExerciseEntity>>

    @Query("SELECT * FROM exercises WHERE id = :id AND deletedAt IS NULL")
    suspend fun findById(id: String): ExerciseEntity?

    /** Counts soft-deleted rows too, so it answers "has this database been populated?". */
    @Query("SELECT COUNT(*) FROM exercises")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(exercises: List<ExerciseEntity>)

    /**
     * Inserts one new row.
     *
     * Deliberately not `REPLACE`: REPLACE is DELETE + INSERT, and once a session
     * references an exercise that delete trips the `ON DELETE RESTRICT` foreign
     * key. A fresh custom exercise has a UUID id, so a collision is a bug worth
     * failing on rather than a case to smooth over.
     */
    @Insert
    suspend fun insert(exercise: ExerciseEntity)

    /**
     * Updates every column of the row keyed by [exercise]'s id, returning the rows
     * updated (ROADMAP N2).
     *
     * An UPDATE rather than a REPLACE for the same reason [insert] is: the row may
     * already be referenced by a logged session, and replacing it would delete it
     * first. The caller passes the row it read, so `createdAt` is preserved rather
     * than invented, and it filters soft-deleted rows before calling this — an
     * `@Update` matches on the primary key alone.
     */
    @Update
    suspend fun update(exercise: ExerciseEntity): Int

    @Query("UPDATE exercises SET deletedAt = :deletedAt, updatedAt = :deletedAt WHERE id = :id")
    suspend fun softDelete(id: String, deletedAt: Long)
}

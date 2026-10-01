package com.example.androidapp.data.local

import androidx.room.Dao
import androidx.room.Query

/**
 * The queries about a session *exercise* — the row joining a workout to one movement.
 *
 * Started for the superset write (ROADMAP N24) because `WorkoutDao` had reached its function
 * ceiling, and the honest answer to a ceiling in this repo is a split rather than another
 * `+1`. This is the boundary the rest of those queries belong on: adding, removing, finishing,
 * rating and grouping an exercise are all edits to this one row, while `WorkoutDao` keeps the
 * session and its sets.
 */
@Dao
interface SessionExerciseDao {

    /**
     * Joins or leaves a superset (ROADMAP N24).
     *
     * A nullable group, so grouping and ungrouping are the same write; the caller decides
     * which of them the tap meant. Rows updated: 0 means the exercise is gone.
     */
    @Query("UPDATE session_exercises SET supersetGroup = :group, updatedAt = :at WHERE id = :id AND deletedAt IS NULL")
    suspend fun setSupersetGroup(id: String, group: Int?, at: Long): Int
}

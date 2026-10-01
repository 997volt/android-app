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
     * A nullable group, so grouping and ungrouping are the same write; the caller decides which of
     * them the tap meant. **One statement for the whole group**, which is what makes pairing atomic
     * (ROADMAP B27): a failure cannot leave half a superset behind. Rows updated: how many of [ids]
     * still exist and are live.
     */
    @Query(
        """
        UPDATE session_exercises
        SET supersetGroup = :group, updatedAt = :at
        WHERE id IN (:ids) AND deletedAt IS NULL
        """,
    )
    suspend fun setSupersetGroup(ids: List<String>, group: Int?, at: Long): Int

    /**
     * The exercises of the last **finished** workout, in the order they were performed (ROADMAP N29).
     *
     * Two rules are in the SQL rather than in Kotlin, which is where they can be tested against a
     * real database:
     *
     *  - **The join to `exercises` drops one deleted since.** Repeating an exercise whose library row
     *    is gone would append a reference to nothing; the rest of the workout still repeats, because
     *    one missing movement is not a reason to refuse the whole session.
     *  - **No `DISTINCT`.** The same exercise performed twice was performed twice, and folding it into
     *    one entry would quietly rewrite what happened.
     */
    @Query(
        """
        SELECT se.exerciseId FROM session_exercises se
        JOIN exercises e ON e.id = se.exerciseId
        WHERE se.sessionId = (
            SELECT id FROM workout_sessions
            WHERE finishedAt IS NOT NULL AND deletedAt IS NULL
            ORDER BY finishedAt DESC LIMIT 1
        )
        AND se.deletedAt IS NULL AND e.deletedAt IS NULL
        ORDER BY se.position
        """,
    )
    suspend fun lastFinishedSessionExerciseIds(): List<String>

}

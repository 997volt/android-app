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
     *  - **The rest, the note and the grouping come with it** (ROADMAP B41). Copying only the movement
     *    silently discarded three things the append helper accepts: a repeated superset lost its
     *    grouping, and with `supersetGroup` null the round logic short-circuits, so the pair degraded
     *    into unrelated exercises resting separately — the behaviour N24 exists to prevent.
     *  - **`startedAt` breaks ties on `finishedAt`.** A backup import round-trips the value, so two
     *    sessions can share one, and without a second key "the last workout" is whichever the database
     *    happens to return — which need not be the one at the top of Recent.
     */
    @Query(
        """
        SELECT se.exerciseId AS exerciseId,
               se.restSeconds AS restSeconds,
               se.techniqueNote AS techniqueNote,
               se.supersetGroup AS supersetGroup
        FROM session_exercises se
        JOIN exercises e ON e.id = se.exerciseId
        WHERE se.sessionId = (
            SELECT id FROM workout_sessions
            WHERE finishedAt IS NOT NULL AND deletedAt IS NULL
            ORDER BY finishedAt DESC, startedAt DESC, id DESC LIMIT 1
        )
        AND se.deletedAt IS NULL AND e.deletedAt IS NULL
        ORDER BY se.position
        """,
    )
    suspend fun lastFinishedSessionExercises(): List<RepeatExerciseRow>

    /**
     * The live exercises of one session, in the order they were performed (ROADMAP N31).
     *
     * The join drops an exercise deleted from the library since, the same rule the repeat query
     * follows: a plan pointing at nothing would be worse than a plan missing one movement.
     */
    @Query(
        """
        SELECT se.* FROM session_exercises se
        JOIN exercises e ON e.id = se.exerciseId
        WHERE se.sessionId = :sessionId AND se.deletedAt IS NULL AND e.deletedAt IS NULL
        ORDER BY se.position
        """,
    )
    suspend fun findLiveSessionExercises(sessionId: String): List<SessionExerciseEntity>

    /** Every live set of those exercises, for the same session (ROADMAP N31). */
    @Query(
        """
        SELECT s.* FROM set_entries s
        JOIN session_exercises se ON se.id = s.sessionExerciseId
        JOIN exercises e ON e.id = se.exerciseId
        WHERE se.sessionId = :sessionId
          AND s.deletedAt IS NULL AND se.deletedAt IS NULL AND e.deletedAt IS NULL
        ORDER BY se.position, s.setIndex
        """,
    )
    suspend fun findLiveSetsForSession(sessionId: String): List<SetEntryEntity>
}

/**
 * One exercise of the last finished workout, with everything a repeat needs to reproduce it
 * (ROADMAP B41).
 *
 * A projection rather than the entity: the repeat reads a handful of columns across two tables, and
 * the row it comes from belongs to a workout that is already over.
 */
data class RepeatExerciseRow(
    val exerciseId: String,
    val restSeconds: Int?,
    val techniqueNote: String?,
    val supersetGroup: Int?,
)

package com.example.androidapp.data.local

import androidx.room.Dao
import androidx.room.Query

/**
 * The Statistics screen's own queries (ROADMAP N35).
 *
 * Its own DAO rather than another method on `WorkoutDao`, which is at its function ceiling — and the
 * statistics screen's questions are not the workout editor's.
 */
@Dao
interface StatisticsDao {

    /**
     * How many sets inside the window were records *when they were performed*.
     *
     * A record is the app's own rule (`PersonalRecords.isRecord`) applied over history: strictly heavier
     * than every earlier set at the same rep count for the same exercise, and never a warm-up. Matching a
     * best does not beat it, which is why the comparison is `>=` on the earlier set and `NOT EXISTS`
     * overall — so a repeat of yesterday's best counts as nothing rather than as a second record.
     *
     * **Earlier is by performance order, not by row id**: the session's start, then the exercise's position
     * in it, then the set's index. That is the order a lifter did them in, and it is the only order that
     * makes "earlier" mean anything when two sessions share a timestamp.
     *
     * **"Earlier" spans the whole of a session, including a second row for the same exercise.** The live path
     * (`onLogSet`) is narrower than that: it reads history excluding the current session and then merges only
     * the sets of the row being added to, so a second row for the same exercise does not see the first, and
     * each row's first set repeating the same weight is announced as a record twice. This query counts one.
     * The count is the rule `PersonalRecords.isRecord` states — matching a best is not beating it — and the
     * announce path is the one that diverges; narrowing this to match it was tried and rejected because it
     * made a single session's repeated set stop counting and contradicted this query's own tests. Fixing the
     * divergence means the live path merging the whole session's sets for the exercise rather than one row.
     *
     * Warm-ups are excluded on both sides, which is B17's rule — a heavy warm-up is not a record — and a
     * bodyweight set has no weight to compare, so it is excluded by the same `weightGrams > 0` the domain
     * rule uses rather than being counted as one. `reps > 0` matches `PersonalRecords.from`, which drops a
     * set with no reps; only a restored backup file can produce one, since every write path clamps.
     */
    @Query(
        """
        SELECT COUNT(*) FROM set_entries s
        JOIN session_exercises se ON se.id = s.sessionExerciseId
        JOIN workout_sessions ws ON ws.id = se.sessionId
        WHERE s.deletedAt IS NULL AND se.deletedAt IS NULL AND ws.deletedAt IS NULL
          AND ws.finishedAt IS NOT NULL
          AND s.setType <> 'WARMUP' AND s.weightGrams > 0 AND s.reps > 0
          AND ws.startedAt >= :from AND ws.startedAt < :to
          AND NOT EXISTS (
              SELECT 1 FROM set_entries earlier
              JOIN session_exercises ese ON ese.id = earlier.sessionExerciseId
              JOIN workout_sessions ews ON ews.id = ese.sessionId
              WHERE ese.exerciseId = se.exerciseId
                AND earlier.reps = s.reps
                AND earlier.deletedAt IS NULL AND ese.deletedAt IS NULL AND ews.deletedAt IS NULL
                AND ews.finishedAt IS NOT NULL
                AND earlier.setType <> 'WARMUP' AND earlier.weightGrams >= s.weightGrams
                AND (
                    ews.startedAt < ws.startedAt
                    OR (ews.startedAt = ws.startedAt AND ese.position < se.position)
                    OR (
                        ews.startedAt = ws.startedAt
                        AND ese.position = se.position
                        AND earlier.setIndex < s.setIndex
                    )
                )
          )
        """,
    )
    suspend fun countRecordsIn(from: Long, to: Long): Int
}

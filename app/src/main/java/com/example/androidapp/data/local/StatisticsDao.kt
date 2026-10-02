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
     * **Earlier means an earlier session**, which is the part worth stating: the live path (`onLogSet`) reads
     * history *excluding the current session* and then merges only the sets of the row being added to. So a
     * lift added twice in one workout is judged twice against history and never against its own twin, and
     * this count has to read the same way or the two disagree about a number the user was already told.
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
                -- Earlier means earlier *history*. A set is measured against what came before the session it
                -- is in, never against the other exercises of the same session: adding a lift twice and
                -- logging the same weight in both is one record's worth of work, but the live path announces
                -- it once per row (it merges only the current row's sets), and this count has to agree with
                -- what the user was told at the time.
                AND ews.id <> ws.id
          )
        """,
    )
    suspend fun countRecordsIn(from: Long, to: Long): Int
}

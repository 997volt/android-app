package com.example.androidapp.data.local

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Per-workout averages of what the app collects (ROADMAP N13).
 *
 * A DAO of its own rather than more methods on `WorkoutDao`: these are read-only
 * aggregates across three tables, which is a different shape from the session and set
 * writes that interface holds — and it is already at its ceiling.
 *
 * Two queries rather than one: averaging a set's RPE and a session exercise's ratings
 * in a single statement would multiply the rows against each other, so the RPE average
 * would be weighted by how many exercises were rated. The repository merges them.
 */
@Dao
interface TrendsDao {

    /**
     * Average RPE per finished workout, newest first.
     *
     * The join filters `rpe IS NOT NULL`, so a workout with nothing rated does not
     * appear at all rather than appearing as null: a gap in the chart should mean
     * "not recorded", and a row of nulls is harder to tell apart from a bug.
     */
    @Query(
        """
        SELECT ws.id AS sessionId,
               ws.startedAt AS startedAt,
               AVG(s.rpe) AS averageRpe
        FROM workout_sessions ws
        JOIN session_exercises se
          ON se.sessionId = ws.id AND se.deletedAt IS NULL
        JOIN set_entries s
          ON s.sessionExerciseId = se.id AND s.deletedAt IS NULL AND s.rpe IS NOT NULL
        WHERE ws.deletedAt IS NULL AND ws.finishedAt IS NOT NULL
        GROUP BY ws.id
        ORDER BY ws.startedAt DESC
        LIMIT :limit
        """,
    )
    fun observeRpeTrend(limit: Int): Flow<List<RpeTrendRow>>

    /**
     * Average muscle feel and joint pain per finished workout, newest first.
     *
     * `AVG` skips nulls, so an exercise that recorded only pain still contributes to
     * the pain average and not to the feel one — which is what "skippable" has to mean
     * for a trend to be readable.
     */
    @Query(
        """
        SELECT ws.id AS sessionId,
               ws.startedAt AS startedAt,
               AVG(se.muscleFeel) AS averageMuscleFeel,
               AVG(se.jointPain) AS averageJointPain
        FROM workout_sessions ws
        JOIN session_exercises se
          ON se.sessionId = ws.id
         AND se.deletedAt IS NULL
         AND (se.muscleFeel IS NOT NULL OR se.jointPain IS NOT NULL)
        WHERE ws.deletedAt IS NULL AND ws.finishedAt IS NOT NULL
        GROUP BY ws.id
        ORDER BY ws.startedAt DESC
        LIMIT :limit
        """,
    )
    fun observeFeelTrend(limit: Int): Flow<List<FeelTrendRow>>
}

/** One workout's average RPE. */
data class RpeTrendRow(
    val sessionId: String,
    val startedAt: Long,
    val averageRpe: Double?,
)

/** One workout's average muscle feel and joint pain, each null when not recorded. */
data class FeelTrendRow(
    val sessionId: String,
    val startedAt: Long,
    val averageMuscleFeel: Double?,
    val averageJointPain: Double?,
)

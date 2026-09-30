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
     * The join filters `rpeHalves IS NOT NULL`, so a workout with nothing rated does not
     * appear at all rather than appearing as null: a gap in the chart should mean
     * "not recorded", and a row of nulls is harder to tell apart from a bug.
     */
    @Query(
        """
        SELECT ws.id AS sessionId,
               ws.startedAt AS startedAt,
               AVG(s.rpeHalves) AS averageRpe
        FROM workout_sessions ws
        JOIN session_exercises se
          ON se.sessionId = ws.id AND se.deletedAt IS NULL
        JOIN set_entries s
          ON s.sessionExerciseId = se.id AND s.deletedAt IS NULL AND s.rpeHalves IS NOT NULL
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

    /**
     * One exercise's finished sessions, one row per logged set (ROADMAP N17).
     *
     * Flat on purpose: the grouping, the warm-up exclusion and the one-rep-max estimate
     * are decisions rather than aggregations, and they live in
     * `List<ExerciseTrendRow>.toExerciseTrendPoints()` where they are pure and tested.
     * A session that recorded the exercise but logged no set still returns one row, with
     * the set columns null, so its ratings are not lost.
     *
     * The inner subquery takes the *sessions*, not the rows: `LIMIT` on a join would cut
     * a workout in half and silently drop the sets that did not fit the window.
     */
    @Query(
        """
        SELECT se.sessionId AS sessionId,
               ws.startedAt AS startedAt,
               se.muscleFeel AS muscleFeel,
               se.jointPain AS jointPain,
               s.weightGrams AS weightGrams,
               s.reps AS reps,
               s.rpeHalves AS rpeHalves,
               s.setType AS setType,
               s.assistanceGrams AS assistanceGrams
        FROM session_exercises se
        JOIN workout_sessions ws ON ws.id = se.sessionId
        LEFT JOIN set_entries s ON s.sessionExerciseId = se.id AND s.deletedAt IS NULL
        WHERE se.exerciseId = :exerciseId
          AND se.deletedAt IS NULL
          AND ws.deletedAt IS NULL
          AND ws.finishedAt IS NOT NULL
          AND se.id IN (
              SELECT se2.id
              FROM session_exercises se2
              JOIN workout_sessions ws2 ON ws2.id = se2.sessionId
              WHERE se2.exerciseId = :exerciseId
                AND se2.deletedAt IS NULL
                AND ws2.deletedAt IS NULL
                AND ws2.finishedAt IS NOT NULL
              ORDER BY ws2.startedAt DESC
              LIMIT :limit
          )
        ORDER BY ws.startedAt ASC, s.setIndex ASC
        """,
    )
    fun observeExerciseTrendRows(
        exerciseId: String,
        limit: Int,
    ): Flow<List<ExerciseTrendRowEntity>>
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

/**
 * One set of one exercise in one finished session, as SQL returns it (ROADMAP N17).
 *
 * The set columns are nullable because the query left-joins them: a session that
 * recorded the exercise without logging a set still contributes its ratings.
 * `setType` stays a name here — the converter is for entities, and a projection is
 * mapped by the repository.
 */
data class ExerciseTrendRowEntity(
    val sessionId: String,
    val startedAt: Long,
    val muscleFeel: Int?,
    val jointPain: Int?,
    val weightGrams: Long?,
    val reps: Int?,
    val rpeHalves: Int?,
    val setType: String?,
    val assistanceGrams: Long?,
)

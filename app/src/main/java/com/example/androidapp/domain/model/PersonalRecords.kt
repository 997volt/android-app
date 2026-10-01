package com.example.androidapp.domain.model

/**
 * The best lift at each rep count (ROADMAP N23).
 *
 * A "rep max" is the honest shape of a record for this app: one number per rep count, so
 * "100 kg × 5" is beaten only by more weight at five reps, not by twelve reps at 90 kg. That
 * is why records are per rep count rather than one best-ever line — and why they are only
 * correct now: warm-up sets became excludable with N14's roles, so a 60 kg warm-up can no
 * longer be mistaken for the work.
 */
data class PersonalRecords(
    /** Heaviest working set at each rep count. */
    val bestByReps: Map<Int, Long> = emptyMap(),
) {
    /** The weight to beat at [reps], or null when nothing has been recorded there. */
    fun bestAt(reps: Int): Long? = bestByReps[reps]

    /**
     * True when this set is a record.
     *
     * Strictly heavier: matching the best is not beating it, and an app that celebrates a
     * repeat devalues the word. A bodyweight or assisted set has no weight to compare, and
     * says nothing either way rather than claiming a record on its assistance.
     */
    fun isRecord(reps: Int, weightGrams: Long): Boolean =
        reps > 0 && weightGrams > 0L && (bestAt(reps)?.let { weightGrams > it } ?: true)

    val isEmpty: Boolean get() = bestByReps.isEmpty()

    /**
     * These records and [later] ones together, keeping the heavier at each rep count.
     *
     * Needed because a set is judged against history *and* against what this session has
     * already logged: without the second half, every set after the first would beat the same
     * record and the app would announce a personal best on each of them.
     */
    fun mergedWith(later: PersonalRecords): PersonalRecords = PersonalRecords(
        bestByReps = (bestByReps.keys + later.bestByReps.keys).associateWith { reps ->
            maxOf(bestByReps[reps] ?: 0L, later.bestByReps[reps] ?: 0L)
        },
    )

    companion object {
        /**
         * Records from performed sets.
         *
         * Warm-ups are excluded, as they are everywhere else a target is measured (N17, N20,
         * N22), and a set with no added weight is not a record at any rep count.
         */
        fun from(sets: List<PerformedSetSpec>): PersonalRecords = PersonalRecords(
            bestByReps = sets
                .filter { it.role != SetType.WARMUP && it.reps > 0 && it.weightGrams > 0L }
                .groupBy { it.reps }
                .mapValues { (_, atReps) -> atReps.maxOf { it.weightGrams } },
        )
    }
}

/**
 * A record, the moment it happens (ROADMAP N23).
 *
 * The app has the numbers to say "that is the best five reps you have ever done" the second
 * it is true, and a record noticed a week later in a list is a record nobody feels.
 */
data class PersonalRecordMoment(
    val exerciseName: String,
    val reps: Int,
    val weightGrams: Long,
    /** What it beat, or null when nothing had been recorded at this rep count. */
    val previousBestGrams: Long?,
)

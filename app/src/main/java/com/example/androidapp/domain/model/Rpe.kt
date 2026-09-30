package com.example.androidapp.domain.model

/**
 * A set's perceived effort, in **half-points** (ROADMAP N6, extended for 9.5).
 *
 * Stored as halves in an `Int` rather than as a `Double`, for the same reason weights
 * are whole grams: `9.5` has no exact binary representation, and an RPE that reads
 * back as `9.499999` in a comparison — or drifts when it is averaged in a trend — is
 * worse than one extra unit of arithmetic. `19` halves is 9.5, exactly.
 *
 * Null is valid and means "not recorded": RPE is skippable by design.
 *
 * The muscle-feel and joint-pain ratings are a *different* scale (whole 1–10, see
 * [TenPointScale]); only RPE takes halves, which is why they do not share this.
 */
object Rpe {
    /**
     * Half steps per point: the divisor that turns stored halves back into an RPE.
     *
     * One definition, because it was three — the trends repository, the backup mapper and
     * the per-exercise series each carried their own, and a wrong divisor in any one of
     * them mislabels every RPE it touches.
     */
    const val HALVES_PER_POINT = 2

    /** 1.0, the easiest effort there is. */
    const val MIN_HALVES = 2

    /** 10.0, everything you had. */
    const val MAX_HALVES = 20

    fun isValid(halves: Int?): Boolean = halves == null || halves in MIN_HALVES..MAX_HALVES

    /**
     * Parses what a lifter types: `9`, `9.5`, `9,5`.
     *
     * A value off the scale or finer than a half is null rather than rounded — a
     * silent 9.3 → 9.5 would be a claim about the set that was never made.
     */
    fun parse(text: String): Int? {
        val normalized = text.trim().replace(',', '.')
        val halves = if (!HALF_STEP.matches(normalized)) {
            null
        } else {
            val whole = normalized.substringBefore('.').toIntOrNull()
            val half = normalized.substringAfter('.', "") == "5"
            whole?.let { it * 2 + if (half) 1 else 0 }
        }
        return halves?.takeIf(::isValid)
    }

    /**
     * The same value as a lifter reads it: `9.5`, or `9` when the half is zero.
     *
     * The trailing `.0` is dropped because a whole number is still the common case, and
     * `9` and `9.5` side by side in a list read as the same kind of thing.
     */
    fun format(halves: Int): String =
        if (halves % 2 == 0) (halves / 2).toString() else "${halves / 2}.5"

    /**
     * Matches what an RPE may be typed as: one or two digits, optionally `.5`.
     *
     * (It is RPE's own parser, not the whole-number 1–10 scale the ratings use — see
     * [TenPointScale] for those.)
     */
    private val HALF_STEP = Regex("""\d{1,2}(\.5)?""")
}

package com.example.androidapp.domain.model

/**
 * A 1–10 rating scale, shared by the set's RPE (ROADMAP N6) and by the muscle-feel
 * and joint-pain ratings (N8).
 *
 * The bounds live in one place so the three editors and the repository cannot
 * disagree about what is a usable value. Null is valid and means "not recorded":
 * all three are skippable by design.
 */
object TenPointScale {
    const val MIN = 1
    const val MAX = 10

    fun isValid(value: Int?): Boolean = value == null || value in MIN..MAX
}

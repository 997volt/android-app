package com.example.androidapp.ui.components

import com.example.androidapp.domain.model.SetType

/**
 * What the set editor produces (ROADMAP P1.3, N6).
 *
 * A payload rather than four positional arguments: the editor is used from the
 * live workout and from history, and a bare `(Int, Long, Int?, String?)` at both
 * call sites is exactly where a swapped RPE and reps would go unnoticed.
 */
data class SetEdit(
    val reps: Int,
    val weightGrams: Long,
    val rpeHalves: Int?,
    val note: String?,
    /** What kind of set it was (ROADMAP N14): warm-up, working, top set, drop, failure. */
    val setType: SetType = SetType.NORMAL,
    /** The machine's assistance, 0 for none (ROADMAP N15). */
    val assistanceGrams: Long = 0,
)

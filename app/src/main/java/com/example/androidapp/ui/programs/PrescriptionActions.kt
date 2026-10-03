package com.example.androidapp.ui.programs

import com.example.androidapp.domain.repository.SlotSetEdit

/**
 * The writes a slot's prescription dialog can make (ROADMAP P3.8).
 *
 * A bundle rather than four more parameters on `ProgramEditorScreen`: they are one job — authoring
 * what a slot prescribes — and the screen is already a long list of callbacks. Its own file because
 * a Kotlin file with one top-level class is expected to be named after it.
 */
data class PrescriptionActions(
    val onAddSet: (String, String, SlotSetEdit) -> Unit = { _, _, _ -> },
    val onUpdateSet: (String, SlotSetEdit) -> Unit = { _, _ -> },
    val onRemoveSet: (String) -> Unit = {},
    val onSetRestCue: (String, String, Int?, String?) -> Unit = { _, _, _, _ -> },
)

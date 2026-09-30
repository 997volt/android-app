package com.example.androidapp.data.local

import java.time.DayOfWeek

import com.example.androidapp.domain.model.Equipment
import com.example.androidapp.domain.model.MuscleGroup
import com.example.androidapp.domain.model.TemplateExercise
import com.example.androidapp.domain.model.TemplateSet

/**
 * A template exercise joined with the library exercise it refers to.
 *
 * The same shape as [SessionExerciseDetail]: one query fills the template editor,
 * instead of a query plus an N+1 walk over the library.
 */
data class TemplateExerciseDetail(
    val id: String,
    val templateId: String,
    val exerciseId: String,
    val position: Int,
    val exerciseName: String,
    val primaryMuscle: MuscleGroup,
    val equipment: Equipment,
    val restSeconds: Int?,
    val techniqueNote: String?,
)

internal fun TemplateExerciseDetail.toDomain(
    sets: List<TemplateSet> = emptyList(),
): TemplateExercise = TemplateExercise(
    id = id,
    templateId = templateId,
    exerciseId = exerciseId,
    position = position,
    exerciseName = exerciseName,
    primaryMuscle = primaryMuscle,
    equipment = equipment,
    restSeconds = restSeconds,
    techniqueNote = techniqueNote,
    sets = sets,
)

internal fun TemplateSetEntity.toDomain(): TemplateSet = TemplateSet(
    id = id,
    templateExerciseId = templateExerciseId,
    setIndex = setIndex,
    role = role,
    targetWeightGrams = targetWeightGrams,
    targetAssistanceGrams = targetAssistanceGrams,
    targetRepsMin = targetRepsMin,
    targetRepsMax = targetRepsMax,
    targetRpeHalves = targetRpeHalves,
    note = note,
)

/**
 * A template and how many exercises it holds.
 *
 * The count comes from SQL rather than from loading the exercises, so the list can
 * render every template without touching the join table.
 */
data class TemplateSummaryRow(
    val id: String,
    val name: String,
    val exerciseCount: Int,
    val weekday: DayOfWeek?,
)

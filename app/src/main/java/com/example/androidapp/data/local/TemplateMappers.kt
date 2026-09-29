package com.example.androidapp.data.local

import com.example.androidapp.domain.model.WorkoutTemplate

/**
 * Translation between the template storage types and the domain model (ROADMAP N3).
 *
 * Same boundary argument as the exercise mappers: the domain type stays free of
 * persistence metadata, so a storage change cannot ripple into the UI.
 * `TemplateExerciseDetail`'s mapper lives beside it.
 */
internal fun TemplateSummaryRow.toDomain(): WorkoutTemplate = WorkoutTemplate(
    id = id,
    name = name,
    exerciseCount = exerciseCount,
)

package com.example.androidapp.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.androidapp.domain.model.SetType

/**
 * One *planned* set on a template exercise (ROADMAP N14).
 *
 * A target, not a record: nothing verifies it, and the set the user actually logs is
 * a separate row in `set_entries` that is expected to differ. That is what keeps the
 * plan small — it describes the shape of a session rather than predicting it.
 *
 * Every target is nullable. A plan may say "3 sets of 5 at 100 kg" for one exercise
 * and "work up to a heavy single" for another, and the second has no weight to write
 * down: a zero would be a claim, and the app cannot check it.
 *
 * [setIndex] is stored rather than inferred, the same reason a session exercise's
 * position is: reordering must not depend on row ids sorting usefully.
 */
@Entity(
    tableName = "template_sets",
    foreignKeys = [
        ForeignKey(
            entity = TemplateExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["templateExerciseId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("templateExerciseId")],
)
data class TemplateSetEntity(
    @PrimaryKey val id: String,
    val templateExerciseId: String,
    val setIndex: Int,
    val role: SetType,
    val targetWeightGrams: Long?,
    /** How much assistance the plan prescribes, or null (ROADMAP N15). */
    val targetAssistanceGrams: Long?,
    /**
     * The target reps, as a range with both ends nullable (ROADMAP N14).
     *
     * A plan writes `(max 2)` and means "no more than two"; the lower bound is simply
     * not written down, so it must be able to be absent rather than defaulted to 2.
     */
    val targetRepsMin: Int?,
    val targetRepsMax: Int?,
    val targetRpeHalves: Int?,
    val note: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
)

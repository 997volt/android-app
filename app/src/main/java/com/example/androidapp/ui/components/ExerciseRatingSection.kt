package com.example.androidapp.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.androidapp.R

/**
 * How an exercise felt, and the editor behind it (ROADMAP N8, N10).
 *
 * Extracted at its second caller: the workout detail had this row, and N10 gives the
 * *active* workout the same one, because a rating that can only be given at the
 * moment an exercise is marked done is a rating given from memory. The Done prompt
 * stays as the last chance rather than the only one.
 *
 * [onRate] is called with the three values the dialog collects; a caller that also
 * needs to finish the exercise (the Done prompt) keeps its own dialog for that, since
 * "save these" and "save these and close the exercise" are different acts.
 */
@Composable
fun ExerciseRatingSection(
    muscleFeel: Int?,
    jointPain: Int?,
    jointPainNote: String?,
    onRate: (muscleFeel: Int?, jointPain: Int?, jointPainNote: String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var editing by remember { mutableStateOf(false) }
    val editLabel = stringResource(R.string.rating_edit_title)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag(TestTags.EXERCISE_RATING_ROW)
            .clickable(onClickLabel = editLabel) { editing = true }
            .padding(vertical = 8.dp),
    ) {
        Text(
            text = stringResource(R.string.rating_row_title),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = ratingSummary(muscleFeel, jointPain, jointPainNote)
                ?: stringResource(R.string.rating_row_add),
            style = MaterialTheme.typography.bodyMedium,
            color = if (muscleFeel == null && jointPain == null) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
    }

    if (editing) {
        ExerciseRatingDialog(
            initialMuscleFeel = muscleFeel,
            initialJointPain = jointPain,
            initialJointPainNote = jointPainNote.orEmpty(),
            isPrompt = false,
            onDismiss = { editing = false },
            onSave = { feel, pain, note ->
                editing = false
                onRate(feel, pain, note)
            },
        )
    }
}

/**
 * `Muscle feel 8 · Joint pain 2`, then where it hurt (N9), or null when nothing was
 * recorded.
 *
 * The location rides with the summary rather than being a line of its own: it is an
 * aside to the rating, and "left shoulder" means nothing on its own.
 */
@Composable
private fun ratingSummary(
    muscleFeel: Int?,
    jointPain: Int?,
    jointPainNote: String?,
): String? {
    val parts = listOfNotNull(
        muscleFeel?.let { stringResource(R.string.rating_muscle_value, it) },
        jointPain?.let { stringResource(R.string.rating_joint_value, it) },
        jointPainNote?.let { stringResource(R.string.rating_joint_note_value, it) },
    )
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

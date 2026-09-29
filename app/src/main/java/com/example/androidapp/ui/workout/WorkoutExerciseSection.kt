package com.example.androidapp.ui.workout

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.example.androidapp.R
import com.example.androidapp.domain.Weight
import com.example.androidapp.ui.components.ExerciseRatingDialog
import com.example.androidapp.ui.components.ExerciseRatingSection
import com.example.androidapp.ui.components.TestTags



/**
 * One exercise's block inside the active workout, and the list that stacks them.
 *
 * Split out of `ActiveWorkoutScreen.kt` when that file reached the function ceiling:
 * the screen is the scaffold, the clock and the workout-level actions, while this is
 * everything that belongs to a single exercise — its name and small print, its sets,
 * and the Done/Reopen actions (ROADMAP N7, N8).
 */

/** How far a done exercise's sets are faded (ROADMAP N7). */
private const val DIMMED = 0.45f

@Composable
internal fun ExerciseList(
    rows: List<SessionExerciseRow>,
    onLogSet: (String) -> Unit,
    onRemoveExercise: (String) -> Unit,
    onEditSet: (SetRow) -> Unit,
    onDeleteSet: (String) -> Unit,
    onFinishExercise: (String, Int?, Int?, String?) -> Unit,
    onRateExercise: (String, Int?, Int?, String?) -> Unit,
    onReopenExercise: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        // Leaves room for the extended FAB so it cannot cover the last row.
        contentPadding = PaddingValues(bottom = 96.dp),
    ) {
        items(items = rows, key = { it.id }) { row ->
            ExerciseSection(
                row = row,
                onLogSet = { onLogSet(row.id) },
                onRemoveExercise = { onRemoveExercise(row.id) },
                onEditSet = onEditSet,
                onDeleteSet = onDeleteSet,
                onFinishExercise = onFinishExercise,
                onRateExercise = onRateExercise,
                onReopenExercise = { onReopenExercise(row.id) },
            )
            HorizontalDivider()
        }
    }
}

/**
 * One exercise's whole block: its name and small print, its sets, and the actions
 * that belong to it.
 *
 * ROADMAP N7 adds the third state this renders — open, or done. A done exercise
 * keeps its sets on screen, dimmed and non-editable, offers **Reopen** instead of
 * **Done**, and loses its Log set button; the wording avoids *Finish*, which is the
 * workout-level action.
 */
@Composable
private fun ExerciseSection(
    row: SessionExerciseRow,
    onLogSet: () -> Unit,
    onRemoveExercise: () -> Unit,
    onEditSet: (SetRow) -> Unit,
    onDeleteSet: (String) -> Unit,
    onFinishExercise: (String, Int?, Int?, String?) -> Unit,
    onRateExercise: (String, Int?, Int?, String?) -> Unit,
    onReopenExercise: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            ExerciseNames(row = row, modifier = Modifier.weight(1f))
            if (row.isFinished) {
                TextButton(
                    onClick = onReopenExercise,
                    modifier = Modifier.testTag(TestTags.EXERCISE_REOPEN),
                ) {
                    Text(stringResource(R.string.active_workout_reopen))
                }
            } else {
                FinishExerciseAction(
                    exerciseId = row.id,
                    muscleFeel = row.muscleFeel,
                    jointPain = row.jointPain,
                    jointPainNote = row.jointPainNote,
                    onFinish = onFinishExercise,
                )
            }
            RemoveExerciseAction(name = row.name, onRemove = onRemoveExercise)
        }

        if (row.isFinished) {
            Text(
                text = stringResource(R.string.active_workout_exercise_is_done),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag(TestTags.EXERCISE_FINISHED_LABEL),
            )
        }

        ExerciseSets(
            row = row,
            onLogSet = onLogSet,
            onEditSet = onEditSet,
            onDeleteSet = onDeleteSet,
        )

        // N10: the ratings can be given while the exercise is still in front of you,
        // rather than only from memory when it is marked done. The Done prompt stays
        // as the last chance rather than the only one.
        ExerciseRatingSection(
            muscleFeel = row.muscleFeel,
            jointPain = row.jointPain,
            jointPainNote = row.jointPainNote.ifEmpty { null },
            onRate = { feel, pain, note -> onRateExercise(row.id, feel, pain, note) },
        )
    }
}

/**
 * The **Done** button for one exercise, and the rating prompt behind it (ROADMAP
 * N7, N8).
 *
 * Owning the dialog here keeps the transient "form is open" state next to the
 * button that opens it, the same shape as [ReadinessSection]. Saving writes the
 * ratings and then finishes; skipping finishes without them, because the whole
 * capture is optional.
 */
@Composable
private fun FinishExerciseAction(
    exerciseId: String,
    muscleFeel: Int?,
    jointPain: Int?,
    jointPainNote: String,
    onFinish: (String, Int?, Int?, String?) -> Unit,
) {
    var rating by remember { mutableStateOf(false) }

    TextButton(
        onClick = { rating = true },
        modifier = Modifier.testTag(TestTags.EXERCISE_DONE),
    ) {
        Text(stringResource(R.string.active_workout_done_exercise))
    }

    if (rating) {
        ExerciseRatingDialog(
            initialMuscleFeel = muscleFeel,
            initialJointPain = jointPain,
            initialJointPainNote = jointPainNote,
            isPrompt = true,
            onDismiss = {
                rating = false
                onFinish(exerciseId, null, null, null)
            },
            onSave = { feel, pain, note ->
                rating = false
                onFinish(exerciseId, feel, pain, note)
            },
        )
    }
}

/**
 * The remove control, and the confirmation behind it (ROADMAP B2).
 *
 * Removing an exercise soft-deletes it *and* takes its sets out of the session, and
 * — unlike deleting a set or marking one done — there is no undo to reach for. So
 * the guard is a question rather than a way back: a rare action, and easier to
 * reason about than restoring a row whose sets went with it.
 */
@Composable
private fun RemoveExerciseAction(
    name: String,
    onRemove: () -> Unit,
) {
    var confirming by remember { mutableStateOf(false) }

    IconButton(
        onClick = { confirming = true },
        modifier = Modifier.testTag(TestTags.EXERCISE_REMOVE),
    ) {
        Icon(
            imageVector = Icons.Filled.Delete,
            contentDescription = stringResource(R.string.active_workout_remove, name),
        )
    }

    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text(stringResource(R.string.active_workout_remove_confirm_title)) },
            text = { Text(stringResource(R.string.active_workout_remove_confirm_text, name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirming = false
                        onRemove()
                    },
                    modifier = Modifier.testTag(TestTags.EXERCISE_REMOVE_CONFIRM),
                ) {
                    Text(stringResource(R.string.active_workout_remove_confirm))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { confirming = false },
                    modifier = Modifier.testTag(TestTags.EXERCISE_REMOVE_CANCEL),
                ) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

/**
 * A done exercise's sets stay on screen, dimmed and non-editable, and it loses its
 * Log set button entirely (ROADMAP N7). Split from [ExerciseSection] so the section
 * stays a header plus its two blocks rather than one long function.
 */
@Composable
private fun ExerciseSets(
    row: SessionExerciseRow,
    onLogSet: () -> Unit,
    onEditSet: (SetRow) -> Unit,
    onDeleteSet: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        // Dimmed rather than hidden: the sets stay visible as a record of what was
        // done, and `editable` is what actually stops the taps.
        Column(modifier = if (row.isFinished) Modifier.alpha(DIMMED) else Modifier) {
            row.sets.forEach { set ->
                SetLine(
                    set = set,
                    editable = !row.isFinished,
                    onEdit = { onEditSet(set) },
                    onDelete = { onDeleteSet(set.id) },
                )
            }
        }

        // No Log set button once the exercise is done: that is the accident N7
        // exists to prevent.
        if (!row.isFinished) {
            FilledTonalButton(
                onClick = onLogSet,
                modifier = Modifier.padding(top = 8.dp),
            ) {
                Text(
                    text = stringResource(
                        R.string.set_log,
                        stringResource(
                            R.string.set_summary,
                            Weight.kilograms(row.suggestion.weightGrams),
                            row.suggestion.reps,
                        ),
                    ),
                )
            }
        }
    }
}

/**
 * The exercise's name and the small print under it: taxonomy, the technique cue
 * (ROADMAP N5) and what was lifted last time.
 *
 * Split out of [ExerciseSection] because that section has a set list and a button
 * too, and mixing the two made one function carry the whole row.
 */
@Composable
private fun ExerciseNames(row: SessionExerciseRow, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(text = row.name, style = MaterialTheme.typography.titleMedium)
        row.subtitle?.let { subtitle ->
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // The cue is the note you want *while* lifting, so it belongs here with the
        // name and not only on the detail screen.
        row.techniqueNote?.let { cue ->
            Text(
                text = cue,
                style = MaterialTheme.typography.bodySmall,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        row.lastTime?.let { last ->
            Text(
                text = stringResource(
                    R.string.set_last_time,
                    stringResource(R.string.set_summary, Weight.kilograms(last.weightGrams), last.reps),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * One logged set. [editable] is false once its exercise is done (ROADMAP N7): the
 * line stops being tappable and loses its delete button, and the accessibility
 * label goes with it so a screen reader does not advertise an edit that cannot
 * happen.
 */
@Composable
private fun SetLine(
    set: SetRow,
    editable: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val editLabel = stringResource(R.string.set_edit_action)
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .testTag(TestTags.SET_ROW)
                // Without a label a screen reader announces the row and gives no
                // hint that tapping it edits the set. A done exercise's sets are not
                // editable, so the label and the action both disappear (N7).
                .let { row ->
                    if (editable) {
                        row.clickable(onClickLabel = editLabel, onClick = onEdit)
                    } else {
                        row
                    }
                },
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.set_number, set.number),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(
                    R.string.set_summary,
                    Weight.kilograms(set.weightGrams),
                    set.reps,
                ),
                style = MaterialTheme.typography.bodyLarge,
            )
            SetExtrasMarker(set = set)
        }
        if (editable) {
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    // Names the action, not the outcome: a screen reader should
                    // announce "Delete set", not the confirmation that follows it.
                    contentDescription = stringResource(R.string.set_delete),
                )
            }
        }
    }
}

/**
 * The small marker a set carries when it has an RPE or a comment (ROADMAP N6).
 *
 * A marker, not the text: the workout row has to stay scannable mid-set, and the
 * comment itself belongs on the workout detail. Nothing is emitted when the set
 * carries neither, which is the state the one-tap log path leaves it in.
 */
@Composable
private fun SetExtrasMarker(set: SetRow, modifier: Modifier = Modifier) {
    val rpeMarker = set.rpe?.let { stringResource(R.string.set_rpe_marker, it) }
    val noteMarker = if (set.note != null) stringResource(R.string.set_note_marker) else null
    val marker = listOfNotNull(rpeMarker, noteMarker).joinToString(" · ")

    if (marker.isNotEmpty()) {
        Text(
            text = marker,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier,
        )
    }
}

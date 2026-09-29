package com.example.androidapp.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * A screen's whole-body message — "Loading…", "No workouts yet", "No exercises match".
 *
 * Three screens had each grown their own copy of this layout: the exercise library,
 * the workout history list, and the history detail. They differed only in whether
 * they showed a second line, which is exactly the kind of duplication that drifts —
 * one gets a padding or alignment fix and the others quietly do not.
 *
 * Screens keep their own thin, named wrapper (`EmptyState`, `HistoryMessage`,
 * `DetailMessage`) so call sites read in the screen's vocabulary; the *layout* lives
 * here and only here.
 *
 * [textStyle] is a parameter rather than a decision made here, because collapsing
 * three styles into one would silently restyle an existing screen. A refactor should
 * not change how anything looks.
 */
@Composable
fun CenteredMessage(
    text: String,
    modifier: Modifier = Modifier,
    hint: String? = null,
    textStyle: TextStyle = MaterialTheme.typography.titleMedium,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = text, style = textStyle)
        hint?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

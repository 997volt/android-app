package com.example.androidapp.ui.programs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.androidapp.ui.components.dataErrorMessage

/**
 * The program's point-of-start question, hosted by a screen (ROADMAP P3.3).
 *
 * Returns the `requestStart` the screen's start actions call, so every way into a workout —
 * an empty one, a repeat, a template row — goes through one rule: ask only when a scheduled
 * day this week has neither a session nor a recorded skip, and ask *at the point of
 * starting*. Extracted at its second caller (home and the template list) rather than copied,
 * because "which day did I miss" is one rule and two versions of it would drift.
 *
 * It returns a value rather than being a `@Composable` in the usual shape, which is why its
 * name is lowercase: the screen needs the `requestStart`, and a callback parameter could not
 * carry one back.
 *
 * [onStart] receives the start to carry out once the question is settled; [onError] receives
 * a sentence when recording the skips failed, because the workout still starts and the user
 * should know the question will come back.
 */
@Composable
fun programStartGate(
    onStart: (StartIntent) -> Unit,
    onError: (String) -> Unit,
    viewModel: ProgramStartGateViewModel = hiltViewModel(),
): (StartIntent) -> Unit {
    val prompt by viewModel.prompt.collectAsStateWithLifecycle()
    val started by viewModel.started.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()

    // Read through `rememberUpdatedState` so an effect cannot fire a stale callback after a
    // recomposition while the question is open.
    val currentOnStart by rememberUpdatedState(onStart)
    val currentOnError by rememberUpdatedState(onError)

    // Resolved during composition: a string resource cannot be read inside LaunchedEffect.
    val errorText = error?.let { dataErrorMessage(it) }

    LaunchedEffect(started) {
        val intent = started ?: return@LaunchedEffect
        currentOnStart(intent)
        viewModel.onStartHandled()
    }

    LaunchedEffect(errorText) {
        if (errorText != null) {
            currentOnError(errorText)
            viewModel.onErrorShown()
        }
    }

    prompt?.let { current ->
        ProgramSkipDialog(
            prompt = current,
            onDoItNow = viewModel::onDoItNow,
            onContinue = viewModel::onContinue,
            onDismiss = viewModel::onDismiss,
        )
    }

    return viewModel::requestStart
}

package com.example.androidapp.ui.components

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState

/**
 * Shows a screen's message once, then clears it.
 *
 * `LaunchedEffect` on the message rather than a one-shot event channel: the message
 * is already state the route owns, and showing a snackbar is idempotent for a given
 * string. Extracted when the home screen became its second caller (ROADMAP F8) —
 * the exercise library used it for a transfer result, the picker for a write that
 * did not land, and home now for the same two transfer outcomes.
 */
@Composable
fun MessageSnackbar(
    message: String?,
    hostState: SnackbarHostState,
    onDismiss: () -> Unit,
) {
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    LaunchedEffect(message) {
        if (message == null) return@LaunchedEffect
        hostState.showSnackbar(message)
        currentOnDismiss()
    }
}

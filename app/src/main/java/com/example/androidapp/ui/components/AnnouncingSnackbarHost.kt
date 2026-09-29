package com.example.androidapp.ui.components

import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics

/**
 * The app's snackbar host, with the announcement made explicit (ROADMAP P1.17).
 *
 * Every write failure in this app — `DataResult.Failure` from `logSet`, `updateSet`,
 * `deleteSession`, an import — is surfaced through a snackbar. That is what F7
 * exists to do, and a message a screen reader never reads surfaces nothing at all
 * to the user who needs it most.
 *
 * **Polite**, not assertive: a failed save should wait its turn behind whatever the
 * user is in the middle of, not cut across them mid-entry.
 *
 * This sets the live region itself rather than relying on `Snackbar` to do it. The
 * behaviour is then ours: it cannot change silently under a Material3 upgrade, and
 * the test that asserts it is testing this code rather than a library's internals.
 */
@Composable
fun AnnouncingSnackbarHost(
    hostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    SnackbarHost(hostState = hostState, modifier = modifier) { data ->
        AnnouncingSnackbar(data)
    }
}

/** One snackbar, carrying the live region. Separate so a test can render it alone. */
@Composable
fun AnnouncingSnackbar(data: SnackbarData, modifier: Modifier = Modifier) {
    Snackbar(
        snackbarData = data,
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
}

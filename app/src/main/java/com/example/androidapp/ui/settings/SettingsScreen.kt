package com.example.androidapp.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.androidapp.R
import com.example.androidapp.domain.RestTimer
import com.example.androidapp.ui.components.TestTags
import com.example.androidapp.ui.components.dataErrorMessage

/** Five chips fit a phone's width; a sixth runs off it. */
private const val CHOICES_PER_ROW = 5

/**
 * The app's settings (ROADMAP N21).
 *
 * One setting so far, and the reason the screen exists: the default rest was a hardcoded 90
 * seconds that nothing could change. The rows for units, screen-on and the rest sound belong
 * here too — this is the screen they were waiting for.
 */
@Composable
fun SettingsRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SettingsScreen(
        state = state,
        onSetDefaultRest = viewModel::onSetDefaultRest,
        onBack = onBack,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onSetDefaultRest: (Int) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize().testTag(TestTags.SETTINGS_SCREEN),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.settings_back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.settings_rest_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(
                    R.string.settings_rest_current,
                    RestTimer.format(state.defaultRestSeconds),
                ),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag(TestTags.SETTINGS_REST_CURRENT),
            )
            Text(
                text = stringResource(R.string.settings_rest_explainer),
                style = MaterialTheme.typography.bodySmall,
            )
            state.choices.chunked(CHOICES_PER_ROW).forEach { row ->
                RestChoiceRow(
                    choices = row,
                    selected = state.defaultRestSeconds,
                    onSelect = onSetDefaultRest,
                )
            }
            state.error?.let { error ->
                Text(
                    text = dataErrorMessage(error),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

/** One row of rest choices, each one tap. */
@Composable
private fun RestChoiceRow(
    choices: List<Int>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        choices.forEach { seconds ->
            FilterChip(
                selected = seconds == selected,
                onClick = { onSelect(seconds) },
                label = { Text(RestTimer.format(seconds)) },
                modifier = Modifier.testTag(TestTags.settingRest(seconds)),
            )
        }
    }
}

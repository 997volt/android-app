package com.example.androidapp.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.Alignment
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Switch
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
        onSetRestCue = viewModel::onSetRestCue,
        onSetKeepScreenOn = viewModel::onSetKeepScreenOn,
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
    onSetRestCue: (Boolean) -> Unit = {},
    onSetKeepScreenOn: (Boolean) -> Unit = {},
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
            RestDefaultSection(
                state = state,
                onSetDefaultRest = onSetDefaultRest,
            )
            RestCueSwitches(
                state = state,
                onSetRestCue = onSetRestCue,
                onSetKeepScreenOn = onSetKeepScreenOn,
            )
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

/** A labelled switch, with the sentence that says what turning it on does. */
@Composable
private fun SettingSwitch(
    label: String,
    hint: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = MaterialTheme.typography.titleMedium)
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                modifier = Modifier.testTag(testTag),
            )
        }
        Text(hint, style = MaterialTheme.typography.bodySmall)
    }
}

/** The two switches this screen exists for as much as the rest choices (ROADMAP N27). */
@Composable
private fun RestCueSwitches(
    state: SettingsUiState,
    onSetRestCue: (Boolean) -> Unit,
    onSetKeepScreenOn: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        HorizontalDivider()
        SettingSwitch(
            label = stringResource(R.string.settings_rest_cue),
            hint = stringResource(R.string.settings_rest_cue_hint),
            checked = state.restCueEnabled,
            onCheckedChange = onSetRestCue,
            testTag = TestTags.SETTINGS_REST_CUE,
        )
        SettingSwitch(
            label = stringResource(R.string.settings_keep_screen_on),
            hint = stringResource(R.string.settings_keep_screen_on_hint),
            checked = state.keepScreenOn,
            onCheckedChange = onSetKeepScreenOn,
            testTag = TestTags.SETTINGS_KEEP_SCREEN_ON,
        )
    }
}

/** The default rest: what is in force, what it means, and the choices (ROADMAP N21). */
@Composable
private fun RestDefaultSection(
    state: SettingsUiState,
    onSetDefaultRest: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
    }
}

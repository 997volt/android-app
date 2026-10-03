package com.example.androidapp.ui.programs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.androidapp.R
import com.example.androidapp.domain.model.WorkoutProgram
import com.example.androidapp.ui.components.CenteredMessage
import com.example.androidapp.ui.components.TestTags
import com.example.androidapp.ui.components.dataErrorMessage
import com.example.androidapp.ui.theme.AndroidAppTheme

/**
 * The program list (ROADMAP P3.3).
 *
 * A program is a set of templates in the order they are trained, so this list is where the
 * schedule is chosen; the editor behind a row is where it is written. Reached from the
 * home screen's overflow, one tap from the plan it changes.
 */
@Composable
fun ProgramsRoute(
    onOpenProgram: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProgramsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val created by viewModel.createdProgramId.collectAsStateWithLifecycle()
    val currentOnOpenProgram by rememberUpdatedState(onOpenProgram)

    // A newly created program opens straight into its editor: it has no slots yet and its
    // name is still the default, so there is nothing to see in a row.
    LaunchedEffect(created) {
        created?.let {
            currentOnOpenProgram(it)
            viewModel.onCreatedHandled()
        }
    }

    ProgramsScreen(
        state = state,
        onCreateProgram = viewModel::onCreateProgram,
        onOpenProgram = onOpenProgram,
        onSetActive = viewModel::onSetActive,
        onMoveProgram = viewModel::onMoveProgram,
        onDismissMessage = viewModel::onErrorShown,
        onBack = onBack,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgramsScreen(
    state: ProgramsUiState,
    onCreateProgram: (String) -> Unit,
    onOpenProgram: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onSetActive: (String) -> Unit = {},
    onMoveProgram: (String, Int) -> Unit = { _, _ -> },
    onDismissMessage: () -> Unit = {},
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val defaultName = stringResource(R.string.program_default_name)
    val currentOnDismissMessage by rememberUpdatedState(onDismissMessage)

    // The message is resolved during composition and shown from the effect, because a
    // string resource cannot be read inside LaunchedEffect.
    state.error?.let { failure ->
        val message = dataErrorMessage(failure)
        LaunchedEffect(message) {
            snackbarHostState.showSnackbar(message)
            currentOnDismissMessage()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.programs_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.nav_back),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onCreateProgram(defaultName) },
                text = { Text(stringResource(R.string.program_new)) },
                icon = { Icon(imageVector = Icons.Filled.Add, contentDescription = null) },
                modifier = Modifier.testTag(TestTags.Programs.NEW),
            )
        },
    ) { innerPadding ->
        ProgramsContent(
            state = state,
            onOpenProgram = onOpenProgram,
            onSetActive = onSetActive,
            onMoveProgram = onMoveProgram,
            modifier = Modifier.padding(innerPadding),
        )
    }
}

@Composable
private fun ProgramsContent(
    state: ProgramsUiState,
    onOpenProgram: (String) -> Unit,
    onSetActive: (String) -> Unit,
    onMoveProgram: (String, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        state.isLoading -> CenteredMessage(
            text = stringResource(R.string.programs_loading),
            modifier = modifier,
        )

        state.programs.isEmpty() -> CenteredMessage(
            text = stringResource(R.string.programs_empty),
            hint = stringResource(R.string.programs_empty_hint),
            modifier = modifier.testTag(TestTags.Programs.EMPTY),
        )

        else -> LazyColumn(
            modifier = modifier.fillMaxSize(),
            // Leaves room for the extended FAB so it cannot cover the last row.
            contentPadding = PaddingValues(bottom = 96.dp),
        ) {
            itemsIndexed(items = state.programs, key = { _, program -> program.id }) { index, program ->
                ProgramRow(
                    program = program,
                    isFirst = index == 0,
                    isLast = index == state.programs.lastIndex,
                    onOpen = { onOpenProgram(program.id) },
                    onUse = { onSetActive(program.id) },
                    onMoveUp = { onMoveProgram(program.id, -1) },
                    onMoveDown = { onMoveProgram(program.id, 1) },
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun ProgramRow(
    program: WorkoutProgram,
    isFirst: Boolean,
    isLast: Boolean,
    onOpen: () -> Unit,
    onUse: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ListItem(
        headlineContent = { Text(program.name) },
        supportingContent = {
            Text(
                pluralStringResource(
                    R.plurals.program_slots,
                    program.slotCount,
                    program.slotCount,
                ),
            )
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // The authored order (P3.12): which active program comes first is moved here,
                // rather than left to a name or a creation time.
                IconButton(
                    onClick = onMoveUp,
                    enabled = !isFirst,
                    modifier = Modifier.testTag(TestTags.Programs.moveProgram(program.id, up = true)),
                ) {
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowUp,
                        contentDescription = stringResource(R.string.program_move_program_up, program.name),
                    )
                }
                IconButton(
                    onClick = onMoveDown,
                    enabled = !isLast,
                    modifier = Modifier.testTag(TestTags.Programs.moveProgram(program.id, up = false)),
                ) {
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowDown,
                        contentDescription = stringResource(R.string.program_move_program_down, program.name),
                    )
                }
                if (program.isActive) {
                    // A label rather than a button: following is a state to read, and a second
                    // program may carry it too (P3.12). It is turned off in the editor.
                    Text(
                        text = stringResource(R.string.program_in_use),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    TextButton(
                        onClick = onUse,
                        modifier = Modifier.testTag(TestTags.Programs.use(program.id)),
                    ) {
                        Text(stringResource(R.string.program_use))
                    }
                }
            }
        },
        modifier = modifier
            .testTag(TestTags.Programs.row(program.id))
            .clickable(onClick = onOpen),
    )
}

@Preview(showBackground = true)
@Composable
private fun ProgramsScreenPreview() {
    AndroidAppTheme {
        ProgramsScreen(
            state = ProgramsUiState(
                isLoading = false,
                programs = listOf(
                    WorkoutProgram(id = "a", name = "Upper/Lower", slotCount = 4, isActive = true),
                    WorkoutProgram(id = "b", name = "PPL", slotCount = 6),
                ),
            ),
            onCreateProgram = {},
            onOpenProgram = {},
            onBack = {},
        )
    }
}

package com.example.androidapp.ui.templates

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.androidapp.R
import com.example.androidapp.domain.model.WorkoutTemplate
import com.example.androidapp.ui.components.CenteredMessage
import com.example.androidapp.ui.components.TestTags
import com.example.androidapp.ui.components.dataErrorMessage
import com.example.androidapp.ui.theme.AndroidAppTheme

/**
 * The template list (ROADMAP N3).
 *
 * A template is started from here rather than from home: the home start action
 * offers the *choice* (empty, or from a template), and this is where the choice is
 * made concrete. The same screen is also the way in to editing one.
 */
@Composable
fun TemplatesRoute(
    onOpenTemplate: (String) -> Unit,
    onStartTemplate: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TemplatesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val created by viewModel.createdTemplateId.collectAsStateWithLifecycle()
    val currentOnOpenTemplate by rememberUpdatedState(onOpenTemplate)

    // A newly created template opens straight into its editor: it has no exercises
    // yet and its name is still the default, so there is nothing to see in a row.
    LaunchedEffect(created) {
        created?.let {
            currentOnOpenTemplate(it)
            viewModel.onCreatedHandled()
        }
    }

    TemplatesScreen(
        state = state,
        onCreateTemplate = viewModel::onCreateTemplate,
        onOpenTemplate = onOpenTemplate,
        onStartTemplate = onStartTemplate,
        onDismissMessage = viewModel::onErrorShown,
        onBack = onBack,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplatesScreen(
    state: TemplatesUiState,
    onCreateTemplate: (String) -> Unit,
    onOpenTemplate: (String) -> Unit,
    onStartTemplate: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onDismissMessage: () -> Unit = {},
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val defaultName = stringResource(R.string.template_default_name)
    val currentOnDismissMessage by rememberUpdatedState(onDismissMessage)

    // The message is resolved during composition and shown from the effect, because
    // a string resource cannot be read inside LaunchedEffect.
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
                title = {
                    Text(
                        text = stringResource(R.string.templates_title),
                        modifier = Modifier.testTag(TestTags.TEMPLATES_TITLE),
                    )
                },
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
                onClick = { onCreateTemplate(defaultName) },
                text = { Text(stringResource(R.string.template_new)) },
                icon = { Icon(imageVector = Icons.Filled.Add, contentDescription = null) },
                modifier = Modifier.testTag(TestTags.TEMPLATES_NEW),
            )
        },
    ) { innerPadding ->
        TemplatesContent(
            state = state,
            onOpenTemplate = onOpenTemplate,
            onStartTemplate = onStartTemplate,
            modifier = Modifier.padding(innerPadding),
        )
    }
}

@Composable
private fun TemplatesContent(
    state: TemplatesUiState,
    onOpenTemplate: (String) -> Unit,
    onStartTemplate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        state.isLoading -> CenteredMessage(
            text = stringResource(R.string.templates_loading),
            modifier = modifier,
        )

        state.templates.isEmpty() -> CenteredMessage(
            text = stringResource(R.string.templates_empty),
            hint = stringResource(R.string.templates_empty_hint),
            modifier = modifier.testTag(TestTags.TEMPLATES_EMPTY),
        )

        else -> LazyColumn(
            modifier = modifier.fillMaxSize(),
            // Leaves room for the extended FAB so it cannot cover the last row.
            contentPadding = PaddingValues(bottom = 96.dp),
        ) {
            items(items = state.templates, key = { it.id }) { template ->
                TemplateRow(
                    template = template,
                    onOpen = { onOpenTemplate(template.id) },
                    onStart = { onStartTemplate(template.id) },
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun TemplateRow(
    template: WorkoutTemplate,
    onOpen: () -> Unit,
    onStart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ListItem(
        headlineContent = { Text(template.name) },
        supportingContent = {
            Text(
                pluralStringResource(
                    R.plurals.template_exercises,
                    template.exerciseCount,
                    template.exerciseCount,
                ),
            )
        },
        trailingContent = {
            TextButton(
                onClick = onStart,
                // Starting is the point of a template, so it gets the row's own
                // button; tapping the row itself edits it instead.
                modifier = Modifier.testTag(TestTags.templateStart(template.id)),
            ) {
                Text(stringResource(R.string.template_start))
            }
        },
        modifier = modifier
            .testTag(TestTags.templateRow(template.id))
            .clickable(onClick = onOpen),
    )
}

@Preview(showBackground = true)
@Composable
private fun TemplatesScreenPreview() {
    AndroidAppTheme {
        TemplatesScreen(
            state = TemplatesUiState(
                isLoading = false,
                templates = listOf(
                    WorkoutTemplate(id = "a", name = "Push day", exerciseCount = 5),
                    WorkoutTemplate(id = "b", name = "Legs", exerciseCount = 3),
                ),
            ),
            onCreateTemplate = {},
            onOpenTemplate = {},
            onStartTemplate = {},
            onBack = {},
        )
    }
}

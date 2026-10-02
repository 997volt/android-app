package com.example.androidapp.ui.measurements

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.androidapp.R
import com.example.androidapp.domain.Weight
import com.example.androidapp.domain.model.BodyMeasurement
import com.example.androidapp.domain.model.TapeSite
import com.example.androidapp.domain.model.at
import com.example.androidapp.ui.components.TestTags
import com.example.androidapp.ui.history.HistoryFormat
import java.time.Instant
import java.time.ZoneId

/** Measurements, reached from home (ROADMAP N32). */
@Composable
fun MeasurementsRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MeasurementsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    MeasurementsScreen(
        state = state,
        onSave = viewModel::onSave,
        onDelete = viewModel::onDelete,
        onBack = onBack,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeasurementsScreen(
    state: MeasurementsUiState,
    onSave: (BodyMeasurement) -> Unit,
    onDelete: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var adding by remember { mutableStateOf(false) }
    if (adding) {
        MeasurementDialog(
            onDismiss = { adding = false },
            onConfirm = { measurement ->
                adding = false
                onSave(measurement)
            },
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.measurements_title)) },
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
            FloatingActionButton(
                onClick = { adding = true },
                modifier = Modifier.testTag(TestTags.Measurements.ADD),
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = stringResource(R.string.measurements_add),
                )
            }
        },
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            if (state.entries.isEmpty() && !state.isLoading) {
                Text(
                    text = stringResource(R.string.measurements_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp),
                )
            }
            state.entries.forEach { entry ->
                MeasurementRow(entry = entry, onDelete = { onDelete(entry.id) })
            }
        }
    }
}

/**
 * One entry as a row.
 *
 * The date is rendered in the device's zone rather than the entry's own, which is the one place N25's
 * rule does not apply: a measurement is not performed anywhere in particular, and the day it belongs to
 * is already the local day the repository keyed it on.
 */
@Composable
private fun MeasurementRow(entry: BodyMeasurement, onDelete: () -> Unit) {
    ListItem(
        headlineContent = { Text(HistoryFormat.date(entry.measuredAt, zone = ZoneId.systemDefault())) },
        supportingContent = { Text(summary(entry)) },
        trailingContent = {
            TextButton(
                onClick = onDelete,
                modifier = Modifier.testTag(TestTags.Measurements.delete(entry.id)),
            ) {
                Text(stringResource(R.string.measurements_delete))
            }
        },
    )
}

/** What an entry reads as: the weight, and whichever of the optional numbers were taken. */
private fun summary(entry: BodyMeasurement): String {
    val parts = mutableListOf(Weight.kilograms(entry.weightGrams))
    entry.bodyFatTenths?.let { parts += MeasurementFormat.percent(it) }
    entry.muscleTenths?.let { parts += MeasurementFormat.percent(it) }
    TapeSite.entries.forEach { site -> entry.at(site)?.let { parts += MeasurementFormat.centimetres(it) } }
    return parts.joinToString(" · ")
}

/**
 * The entry form: a weight, and everything else only if it was measured (ROADMAP N32).
 *
 * A form rather than a single line, because the optional numbers are optional in the sense that a person
 * may have measured none of them — leaving one blank has to be as easy as filling it in.
 */
@Composable
private fun MeasurementDialog(
    onDismiss: () -> Unit,
    onConfirm: (BodyMeasurement) -> Unit,
) {
    var weight by remember { mutableStateOf("") }
    var bodyFat by remember { mutableStateOf("") }
    var muscle by remember { mutableStateOf("") }
    var tape by remember { mutableStateOf<Map<TapeSite, String>>(emptyMap()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.measurements_add)) },
        text = {
            MeasurementFields(
                weight = weight,
                onWeightChange = { weight = it },
                bodyFat = bodyFat,
                onBodyFatChange = { bodyFat = it },
                muscle = muscle,
                onMuscleChange = { muscle = it },
                tape = tape,
                onTapeChange = { site, text -> tape = tape + (site to text) },
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        BodyMeasurement(
                            // A blank id is a new entry, which the repository places by its day.
                            id = "",
                            measuredAt = Instant.now(),
                            weightGrams = Weight.parseKilograms(weight) ?: 0L,
                            bodyFatTenths = tenths(bodyFat),
                            muscleTenths = tenths(muscle),
                            tape = tape.mapNotNull { (site, text) ->
                                millimetres(text)?.let { site to it }
                            }.toMap(),
                        ),
                    )
                },
                // The weight is the one thing an entry cannot be without.
                enabled = Weight.parseKilograms(weight) != null,
                modifier = Modifier.testTag(TestTags.Measurements.CONFIRM),
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun NumberField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    testTag: String,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth().testTag(testTag),
    )
}

/**
 * `18.3` or `18,3` reads as 183 tenths; anything else is not a number, and absence is null.
 *
 * Guarded the way [Weight.parseKilograms] is, and for the same reason: `toDoubleOrNull` accepts `NaN` and
 * `Infinity`, and `Math.round(NaN * 10)` is `0`, so a typed "NaN" was stored as a real 0.0% reading — a
 * measurement nobody took, in a series that then draws it. Negative is refused too: no tape measurement or
 * body-fat percentage in this app is below zero.
 */
private fun tenths(text: String): Int? =
    finite(text)?.let { Math.round(it * TENTHS_PER_PERCENT).toInt() }

/** `86.4` reads as 864 millimetres, which is ten to the centimetre. */
private fun millimetres(text: String): Long? =
    finite(text)?.let { Math.round(it * MILLIMETRES_PER_CENTIMETRE) }

/**
 * A typed measurement, or null when it is not one.
 *
 * `Math.round` maps anything non-finite or out of `Int` range to a value that looks like a real reading, so the
 * check has to happen before the conversion rather than after it.
 */
private fun finite(text: String): Double? =
    text.replace(',', '.').trim().toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0.0 }

/** The units the form collects in, and the schema stores in tenths and millimetres. */
private const val TENTHS_PER_PERCENT = 10.0
private const val MILLIMETRES_PER_CENTIMETRE = 10.0

private fun tapeLabel(site: TapeSite): Int = when (site) {
    TapeSite.NECK -> R.string.measurements_neck
    TapeSite.CHEST -> R.string.measurements_chest
    TapeSite.WAIST -> R.string.measurements_waist
    TapeSite.HIPS -> R.string.measurements_hips
    TapeSite.UPPER_ARM -> R.string.measurements_upper_arm
    TapeSite.THIGH -> R.string.measurements_thigh
    TapeSite.CALF -> R.string.measurements_calf
}

/** The form's fields, together so the dialog reads as a dialog (ROADMAP N32). */
@Composable
private fun MeasurementFields(
    weight: String,
    onWeightChange: (String) -> Unit,
    bodyFat: String,
    onBodyFatChange: (String) -> Unit,
    muscle: String,
    onMuscleChange: (String) -> Unit,
    tape: Map<TapeSite, String>,
    onTapeChange: (TapeSite, String) -> Unit,
) {
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        NumberField(
            label = stringResource(R.string.measurements_weight),
            value = weight,
            onValueChange = onWeightChange,
            testTag = TestTags.Measurements.WEIGHT,
        )
        NumberField(
            label = stringResource(R.string.measurements_body_fat),
            value = bodyFat,
            onValueChange = onBodyFatChange,
            testTag = TestTags.Measurements.BODY_FAT,
        )
        NumberField(
            label = stringResource(R.string.measurements_muscle),
            value = muscle,
            onValueChange = onMuscleChange,
            testTag = TestTags.Measurements.MUSCLE,
        )
        TapeSite.entries.forEach { site ->
            NumberField(
                label = stringResource(tapeLabel(site)),
                value = tape[site].orEmpty(),
                onValueChange = { onTapeChange(site, it) },
                testTag = TestTags.Measurements.tape(site),
            )
        }
    }
}

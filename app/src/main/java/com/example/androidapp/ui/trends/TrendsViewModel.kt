package com.example.androidapp.ui.trends

import kotlinx.coroutines.flow.combine
import com.example.androidapp.domain.repository.MeasurementRepository
import com.example.androidapp.domain.model.at
import com.example.androidapp.domain.model.TapeSite
import com.example.androidapp.domain.model.BodyMeasurement
import com.example.androidapp.R
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.androidapp.domain.DataError
import com.example.androidapp.domain.DataResult
import com.example.androidapp.domain.model.TrendMetric
import com.example.androidapp.domain.model.TrendPoint
import com.example.androidapp.domain.model.averageValue
import com.example.androidapp.domain.model.latestValue
import com.example.androidapp.domain.model.recordedCount
import com.example.androidapp.domain.model.valuesOf
import com.example.androidapp.domain.repository.TrendsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * One metric's series, ready for the screen to draw and caption (ROADMAP N13).
 *
 * An average over sessions rather than over sets: a workout with nine rated sets
 * should not shout louder than one with a single rated set.
 */
data class TrendSection(
    val metric: TrendMetric,
    /** Oldest first, null where that workout did not record this metric. */
    val values: List<Double?>,
    val latest: Double?,
    val average: Double?,
    /** How many of the window's workouts recorded it at all. */
    val recorded: Int,
) {
    /**
     * One point is a number, not a trend, and the screen says so rather than drawing a
     * line through a single dot.
     */
    val hasLine: Boolean get() = recorded >= 2
}

/**
 * One measurement series, ready for the same chart the training trends use (ROADMAP N32).
 *
 * A point per entry rather than per session, and dated rather than counted — which is exactly why it is
 * not a [TrendSection]: that one reads its values off a workout.
 */
/**
 * The units a measurement series can be in (ROADMAP N32).
 *
 * The stored number is whatever the schema stores — grams, tenths, millimetres — and this is what turns
 * it into a reading. Without it a chart of bodyweight and a chart of body fat would be the same shape
 * with no way to tell which number was which.
 */
enum class MeasurementUnit { KILOGRAMS, PERCENT, CENTIMETRES }

data class MeasurementTrend(
    /** The label, as a string resource: the unit belongs to the language, not to the number. */
    val labelRes: Int,
    /** What the numbers are, so a caption can say them the way a person would. */
    val unit: MeasurementUnit,
    /** Oldest first, null where that entry did not take this measurement. */
    val values: List<Double?>,
    val latest: Double?,
    val recorded: Int,
) {
    /** One point is a number, not a trend. */
    val hasLine: Boolean get() = recorded >= 2
}

data class TrendsUiState(
    val isLoading: Boolean = true,
    /** How many workouts the window covers, for the screen's context line. */
    val windowSize: Int = 0,
    val sections: List<TrendSection> = emptyList(),
    /**
     * The measurement series, if any (ROADMAP N32).
     *
     * Separate from [sections] on purpose: those are indexed by workout, and a measurement is not a
     * workout. They share the chart, not the axis.
     */
    val measurements: List<MeasurementTrend> = emptyList(),
    /** The read failed (ROADMAP B4): shown in place of the charts. */
    val error: DataError? = null,
) {
    /** Nothing recorded at all — different from "the read failed". */
    val hasNothingRecorded: Boolean get() = !isLoading && error == null && sections.all { it.recorded == 0 }
}

/**
 * Reads the collected signals back (ROADMAP N13).
 *
 * The series are built here rather than in the composable so the arithmetic — which
 * workouts count, what an average is over, what "not recorded" does to a line — is
 * unit-testable without a device.
 */
@HiltViewModel
class TrendsViewModel @Inject constructor(
    repository: TrendsRepository,
    /** Measurements share the chart but not the window (ROADMAP N32). */
    measurements: MeasurementRepository,
) : ViewModel() {

    val uiState: StateFlow<TrendsUiState> = combine(
        repository.observeTrends(),
        measurements.observeAll(),
    ) { trends, entries ->
        when (trends) {
            is DataResult.Success -> TrendsUiState(
                isLoading = false,
                windowSize = trends.data.size,
                sections = TrendMetric.entries.map { metric -> trends.data.sectionFor(metric) },
                measurements = entries.toMeasurementTrends(),
            )

            is DataResult.Failure -> TrendsUiState(isLoading = false, error = trends.error)
        }
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = TrendsUiState(),
        )

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

private fun List<TrendPoint>.sectionFor(metric: TrendMetric): TrendSection {
    val values = valuesOf(metric)
    return TrendSection(
        metric = metric,
        values = values,
        latest = values.latestValue(),
        average = values.averageValue(),
        recorded = values.recordedCount(),
    )
}

/**
 * The measurement series, bodyweight first and each tape site after (ROADMAP N32).
 *
 * Every entry is a point, oldest first, and an entry that did not take a measurement contributes null
 * rather than zero — which is what makes the chart break its line instead of drawing through a reading
 * nobody took.
 */
private fun List<BodyMeasurement>.toMeasurementTrends(): List<MeasurementTrend> {
    val oldestFirst = sortedBy { it.measuredAt }
    fun trend(
        labelRes: Int,
        unit: MeasurementUnit,
        value: (BodyMeasurement) -> Double?,
    ): MeasurementTrend {
        val values = oldestFirst.map(value)
        return MeasurementTrend(
            labelRes = labelRes,
            unit = unit,
            values = values,
            latest = values.lastOrNull { it != null },
            recorded = values.count { it != null },
        )
    }

    return buildList {
        add(trend(R.string.measurements_weight, MeasurementUnit.KILOGRAMS) { it.weightGrams.toDouble() })
        add(trend(R.string.measurements_body_fat, MeasurementUnit.PERCENT) { it.bodyFatTenths?.toDouble() })
        add(trend(R.string.measurements_muscle, MeasurementUnit.PERCENT) { it.muscleTenths?.toDouble() })
        TapeSite.entries.forEach { site ->
            add(trend(measurementTapeLabel(site), MeasurementUnit.CENTIMETRES) { it.at(site)?.toDouble() })
        }
    }
}

private fun measurementTapeLabel(site: TapeSite): Int = when (site) {
    TapeSite.NECK -> R.string.measurements_neck
    TapeSite.CHEST -> R.string.measurements_chest
    TapeSite.WAIST -> R.string.measurements_waist
    TapeSite.HIPS -> R.string.measurements_hips
    TapeSite.UPPER_ARM -> R.string.measurements_upper_arm
    TapeSite.THIGH -> R.string.measurements_thigh
    TapeSite.CALF -> R.string.measurements_calf
}

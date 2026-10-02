package com.example.androidapp.ui.statistics

import com.example.androidapp.domain.repository.ExerciseRepository
import com.example.androidapp.domain.repository.MeasurementRepository
import com.example.androidapp.domain.repository.StatisticsRepository
import com.example.androidapp.domain.repository.TrendsRepository
import javax.inject.Inject

/**
 * The four reads a Statistics series can come from (ROADMAP N35).
 *
 * One dependency rather than four, because the ViewModel's parameter list is not the place to enumerate a
 * screen's data sources — and because these are one thing: the registry says how to show each of the
 * twenty-one series, `metricSeries` says how to read one, and this is where all of them come from.
 */
class StatisticsRepositories @Inject constructor(
    val statistics: StatisticsRepository,
    val trends: TrendsRepository,
    val measurements: MeasurementRepository,
    val exercises: ExerciseRepository,
)

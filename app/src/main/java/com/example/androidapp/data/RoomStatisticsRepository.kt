package com.example.androidapp.data

import com.example.androidapp.data.local.WorkoutDatabase
import com.example.androidapp.domain.DataResult
import com.example.androidapp.domain.dataResultOf
import com.example.androidapp.domain.repository.StatisticsRepository
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** Statistics over Room (ROADMAP N35). Thin: the rule lives in the query, where it can see all the rows. */
@Singleton
class RoomStatisticsRepository @Inject constructor(
    database: WorkoutDatabase,
) : StatisticsRepository {

    private val dao = database.statisticsDao()

    override suspend fun countRecordsIn(from: Instant, to: Instant): DataResult<Int> = dataResultOf {
        dao.countRecordsIn(from.toEpochMilli(), to.toEpochMilli())
    }
}

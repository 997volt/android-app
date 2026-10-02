package com.example.androidapp.data

import com.example.androidapp.data.local.MeasurementEntity
import com.example.androidapp.data.local.WorkoutDatabase
import com.example.androidapp.domain.DataResult
import com.example.androidapp.domain.NotFoundException
import com.example.androidapp.domain.TimeSource
import com.example.androidapp.domain.dataResultOf
import com.example.androidapp.domain.nowEpochMillis
import com.example.androidapp.domain.model.BodyMeasurement
import com.example.androidapp.domain.model.TapeSite
import com.example.androidapp.domain.repository.MeasurementRepository
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Measurements in Room (ROADMAP N32).
 *
 * Thin on purpose: the rules that matter — a day has one entry, an unmeasured site stays blank — are
 * stated in the port and in DECISIONS.md, and this only carries them out.
 */
@Singleton
class RoomMeasurementRepository @Inject constructor(
    database: WorkoutDatabase,
    private val timeSource: TimeSource,
) : MeasurementRepository {

    private val dao = database.measurementDao()

    override fun observeAll(): Flow<List<BodyMeasurement>> =
        dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override suspend fun save(measurement: BodyMeasurement): DataResult<Unit> = dataResultOf {
        val now = timeSource.nowEpochMillis()
        val existing = dao.find(measurement.id)
            ?: dao.findOnDay(
                from = startOfDay(measurement.measuredAt),
                to = startOfDay(measurement.measuredAt.plusSeconds(SECONDS_PER_DAY)),
            )
        val row = measurement.toEntity(
            // An existing row keeps its identity: an edit is an edit, not a replacement.
            id = existing?.id ?: measurement.id.ifBlank { UUID.randomUUID().toString() },
            createdAt = existing?.createdAt ?: now,
            now = now,
        )
        if (existing == null) {
            dao.insert(row)
        } else if (dao.update(row) == 0) {
            throw NotFoundException("measurement ${row.id}")
        }
    }

    override suspend fun delete(id: String): DataResult<Unit> = dataResultOf {
        if (dao.softDelete(id, timeSource.nowEpochMillis()) == 0) {
            throw NotFoundException("measurement $id")
        }
    }

    /**
     * Midnight where the entry was taken, which is the day it belongs to.
     *
     * The device's own zone, read at the moment of writing: N25's rule for a session's time, applied to
     * a measurement's date.
     */
    private fun startOfDay(instant: Instant): Long =
        instant.atZone(ZoneId.systemDefault()).toLocalDate().atStartOfDay(ZoneId.systemDefault())
            .toInstant().toEpochMilli()
}

private const val SECONDS_PER_DAY = 86_400L

private fun MeasurementEntity.toDomain() = BodyMeasurement(
    id = id,
    measuredAt = Instant.ofEpochMilli(measuredAt),
    weightGrams = weightGrams,
    bodyFatTenths = bodyFatTenths,
    muscleTenths = muscleTenths,
    tape = buildMap {
        neckMm?.let { put(TapeSite.NECK, it) }
        chestMm?.let { put(TapeSite.CHEST, it) }
        waistMm?.let { put(TapeSite.WAIST, it) }
        hipsMm?.let { put(TapeSite.HIPS, it) }
        upperArmMm?.let { put(TapeSite.UPPER_ARM, it) }
        thighMm?.let { put(TapeSite.THIGH, it) }
        calfMm?.let { put(TapeSite.CALF, it) }
    },
)

private fun BodyMeasurement.toEntity(id: String, createdAt: Long, now: Long) = MeasurementEntity(
    id = id,
    measuredAt = measuredAt.toEpochMilli(),
    weightGrams = weightGrams,
    bodyFatTenths = bodyFatTenths,
    muscleTenths = muscleTenths,
    // An absent site stays absent: carrying a value forward would invent a measurement.
    neckMm = tape[TapeSite.NECK],
    chestMm = tape[TapeSite.CHEST],
    waistMm = tape[TapeSite.WAIST],
    hipsMm = tape[TapeSite.HIPS],
    upperArmMm = tape[TapeSite.UPPER_ARM],
    thighMm = tape[TapeSite.THIGH],
    calfMm = tape[TapeSite.CALF],
    createdAt = createdAt,
    updatedAt = now,
    deletedAt = null,
)

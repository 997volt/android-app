package com.example.androidapp.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One dated set of body measurements (ROADMAP N32).
 *
 * **Weight is the only thing that is always there.** Everything else is nullable, because that is how
 * people measure: a waist taken on a morning the scale was not stepped on is a real entry, and it must
 * not need a second screen or a fake zero to exist. A zero would be indistinguishable from a measurement
 * of nothing, which is the one reading no body ever gives.
 *
 * Units follow what is settled elsewhere in the schema: grams in a `Long` for weight, **tenths of a
 * percent in an `Int`** for the percentages — the same reason RPE is held in halves, since 18.3 has no
 * exact binary representation — and **millimetres in a `Long`** for the tape, displayed as centimetres
 * so a half-centimetre is a value rather than a rounding.
 *
 * Sync-shaped like every other table (id, createdAt, updatedAt, deletedAt), so it rides the export.
 */
@Entity(tableName = "measurements")
data class MeasurementEntity(
    @PrimaryKey val id: String,
    /** When it was measured, which is the date the entry belongs to. */
    val measuredAt: Long,
    /** Whole grams. The only required measurement. */
    val weightGrams: Long,
    /** Body fat, in tenths of a percent. */
    val bodyFatTenths: Int? = null,
    /** Muscle mass, in tenths of a percent. */
    val muscleTenths: Int? = null,
    /** The tape sites, in millimetres, every one optional and left blank when not taken. */
    val neckMm: Long? = null,
    val chestMm: Long? = null,
    val waistMm: Long? = null,
    val hipsMm: Long? = null,
    val upperArmMm: Long? = null,
    val thighMm: Long? = null,
    val calfMm: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
)

package com.example.androidapp.domain.model

import java.time.Instant

/**
 * One dated set of body measurements (ROADMAP N32).
 *
 * Weight is the only value that is always there. Everything else is null when it was not taken, and
 * **null is not zero**: a waist measured on a morning the scale was not stepped on is a real entry, and
 * a zero would read as a measurement of nothing.
 *
 * The units are the ones the schema stores — grams, tenths of a percent, millimetres — because
 * converting at the edge is how a rounding error gets into a database. Percentages are tenths for the
 * same reason RPE is halves: 18.3 has no exact binary representation.
 */
data class BodyMeasurement(
    val id: String,
    val measuredAt: Instant,
    val weightGrams: Long,
    val bodyFatTenths: Int? = null,
    val muscleTenths: Int? = null,
    /** The tape sites, in millimetres, every one optional. */
    val tape: Map<TapeSite, Long> = emptyMap(),
)

/**
 * The tape sites v1 measures (ROADMAP N32).
 *
 * A fixed set rather than a user-defined list: seven named sites are answerable, and a free list is a
 * later row rather than a first one. An unmeasured site is simply absent from [BodyMeasurement.tape] —
 * it does not carry the last value forward, because carrying a number forward invents a measurement.
 */
enum class TapeSite {
    NECK,
    CHEST,
    WAIST,
    HIPS,
    UPPER_ARM,
    THIGH,
    CALF,
}

/** The value taken at [site], or null when it was not measured. */
fun BodyMeasurement.at(site: TapeSite): Long? = tape[site]

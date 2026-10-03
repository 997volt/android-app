package com.example.androidapp.data.local

import com.example.androidapp.domain.model.AdherenceSession
import com.example.androidapp.domain.model.ExerciseTrendRow
import com.example.androidapp.domain.model.ProgramSession
import com.example.androidapp.domain.model.ProgramSlot
import com.example.androidapp.domain.model.SetType
import com.example.androidapp.domain.model.SlotPrescription
import com.example.androidapp.domain.model.SlotSet
import com.example.androidapp.domain.model.WorkoutProgram
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Translation between program storage and the domain (ROADMAP P3.3).
 *
 * Same boundary argument as the template mappers: the domain type stays free of
 * persistence metadata, so a storage change cannot ripple into the UI.
 */
internal fun ProgramSummaryRow.toDomain(): WorkoutProgram = WorkoutProgram(
    id = id,
    name = name,
    slotCount = slotCount,
    isActive = isActive,
    position = position,
)

internal fun ProgramSlotDetail.toDomain(): ProgramSlot = ProgramSlot(
    id = id,
    programId = programId,
    templateId = templateId,
    position = position,
    weekday = weekday,
    templateName = templateName,
    exerciseCount = exerciseCount,
)

/**
 * What one slot prescribes for one exercise, with the sets read alongside it (ROADMAP P3.8).
 *
 * The sets are passed in rather than gathered here so the repository can group one read of all
 * of a slot's sets by their exercise row, which is a join the mapper should not be repeating.
 */
internal fun ProgramSlotExerciseEntity.toDomain(sets: List<ProgramSlotSetEntity>): SlotPrescription =
    SlotPrescription(
        exerciseId = exerciseId,
        restSeconds = restSeconds,
        techniqueNote = techniqueNote,
        sets = sets.map { it.toDomain() },
    )

internal fun ProgramSlotSetEntity.toDomain(): SlotSet = SlotSet(
    id = id,
    setIndex = setIndex,
    role = role,
    targetWeightGrams = targetWeightGrams,
    targetAssistanceGrams = targetAssistanceGrams,
    targetRepsMin = targetRepsMin,
    targetRepsMax = targetRepsMax,
    targetRpeHalves = targetRpeHalves,
    targetPercentOf1Rm = targetPercentOf1Rm,
    note = note,
)

/**
 * A finished session as the run reads it, or null when it carries no template (ROADMAP P3.9).
 *
 * The run is a rotation of slots, and a session started by hand named none — it moves no run.
 * The zone rule is the same as everywhere else: the session's own (N25), with [fallbackZone] only
 * for rows written before that column existed.
 */
internal fun FinishedSessionRow.toRunSession(fallbackZone: ZoneId): ProgramSession? =
    templateId?.let { template ->
        ProgramSession(
            sessionId = sessionId,
            templateId = template,
            startedAt = Instant.ofEpochMilli(startedAt),
            zone = zoneOffsetMinutes
                ?.let { minutes -> ZoneOffset.ofTotalSeconds(minutes * SECONDS_PER_MINUTE) }
                ?: fallbackZone,
        )
    }

/**
 * One trend row as N17's per-exercise series reads it (ROADMAP P3.8).
 *
 * The projection keeps `setType` as its stored name — a database column, not an entity — so it is
 * resolved here; null stays null, because a row that left-joined no set has no role to resolve.
 * The repository reads the series this way to take the estimated one-rep max a slot's percentage
 * prescription resolves against.
 */
internal fun ExerciseTrendRowEntity.toExerciseTrendRow(): ExerciseTrendRow = ExerciseTrendRow(
    sessionId = sessionId,
    startedAt = Instant.ofEpochMilli(startedAt),
    muscleFeel = muscleFeel,
    jointPain = jointPain,
    weightGrams = weightGrams,
    reps = reps,
    rpeHalves = rpeHalves,
    setType = setType?.let { name -> SetType.entries.firstOrNull { it.name == name } },
    assistanceGrams = assistanceGrams,
)

/**
 * A session row as occurrence matching reads it.
 *
 * The zone is the session's own (N25), with [fallbackZone] only for rows written before
 * that column existed — the same fallback every screen already applies to those.
 */
internal fun ProgramSessionRow.toProgramSession(fallbackZone: ZoneId): ProgramSession =
    ProgramSession(
        sessionId = sessionId,
        templateId = templateId,
        startedAt = Instant.ofEpochMilli(startedAt),
        zone = zoneOffsetMinutes
            ?.let { minutes -> ZoneOffset.ofTotalSeconds(minutes * SECONDS_PER_MINUTE) }
            ?: fallbackZone,
    )

/**
 * A finished session as adherence reads it (ROADMAP P3.5).
 *
 * The same zone rule as [toProgramSession]: the session's own where it has one, the reading zone
 * only for rows written before N25 recorded it.
 */
internal fun FinishedSessionRow.toAdherenceSession(fallbackZone: ZoneId): AdherenceSession =
    AdherenceSession(
        sessionId = sessionId,
        templateId = templateId,
        startedAt = Instant.ofEpochMilli(startedAt),
        zone = zoneOffsetMinutes
            ?.let { minutes -> ZoneOffset.ofTotalSeconds(minutes * SECONDS_PER_MINUTE) }
            ?: fallbackZone,
    )

private const val SECONDS_PER_MINUTE = 60

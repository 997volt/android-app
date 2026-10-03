package com.example.androidapp.data.local

import com.example.androidapp.domain.model.ProgramSession
import com.example.androidapp.domain.model.ProgramSlot
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
 * A session row as occurrence matching reads it.
 *
 * The zone is the session's own (N25), with [fallbackZone] only for rows written before
 * that column existed — the same fallback every screen already applies to those.
 */
internal fun ProgramSessionRow.toProgramSession(fallbackZone: ZoneId): ProgramSession =
    ProgramSession(
        templateId = templateId,
        startedAt = Instant.ofEpochMilli(startedAt),
        zone = zoneOffsetMinutes
            ?.let { minutes -> ZoneOffset.ofTotalSeconds(minutes * SECONDS_PER_MINUTE) }
            ?: fallbackZone,
    )

private const val SECONDS_PER_MINUTE = 60

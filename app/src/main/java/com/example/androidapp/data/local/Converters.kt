package com.example.androidapp.data.local

import androidx.room.TypeConverter
import com.example.androidapp.domain.model.Equipment
import com.example.androidapp.domain.model.MovementPattern
import com.example.androidapp.domain.model.MuscleGroup
import com.example.androidapp.domain.model.SetType
import java.time.DayOfWeek

/**
 * Stores the domain enums as their **names**, never their ordinals.
 *
 * An ordinal is a trap: inserting a constant in the middle of an enum would
 * silently reinterpret every row already on disk, and the corruption would only
 * surface as wrong data much later. Names survive reordering, and `valueOf`
 * throws loudly if a name ever disappears — which is the failure we want.
 */
class Converters {

    @TypeConverter
    fun fromDayOfWeek(value: DayOfWeek?): String? = value?.name

    @TypeConverter
    fun toDayOfWeek(value: String?): DayOfWeek? = value?.let(DayOfWeek::valueOf)

    @TypeConverter
    fun fromMuscleGroup(value: MuscleGroup): String = value.name

    @TypeConverter
    fun toMuscleGroup(value: String): MuscleGroup = MuscleGroup.valueOf(value)

    @TypeConverter
    fun fromEquipment(value: Equipment): String = value.name

    @TypeConverter
    fun toEquipment(value: String): Equipment = Equipment.valueOf(value)

    @TypeConverter
    fun fromMovementPattern(value: MovementPattern): String = value.name

    @TypeConverter
    fun toMovementPattern(value: String): MovementPattern = MovementPattern.valueOf(value)

    @TypeConverter
    fun fromSetType(value: SetType): String = value.name

    @TypeConverter
    fun toSetType(value: String): SetType = SetType.valueOf(value)

    @TypeConverter
    fun fromMuscleGroups(values: List<MuscleGroup>): String =
        values.joinToString(SEPARATOR) { it.name }

    @TypeConverter
    fun toMuscleGroups(value: String): List<MuscleGroup> =
        if (value.isEmpty()) emptyList() else value.split(SEPARATOR).map { MuscleGroup.valueOf(it) }

    private companion object {
        const val SEPARATOR = ","
    }
}

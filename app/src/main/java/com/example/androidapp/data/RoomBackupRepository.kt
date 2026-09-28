package com.example.androidapp.data

import androidx.room.withTransaction
import com.example.androidapp.data.local.WorkoutDatabase
import com.example.androidapp.data.transfer.BackupCodec
import com.example.androidapp.data.transfer.BackupFile
import com.example.androidapp.data.transfer.toDto
import com.example.androidapp.data.transfer.toEntity
import com.example.androidapp.domain.DataResult
import com.example.androidapp.domain.TimeSource
import com.example.androidapp.domain.dataResultOf
import com.example.androidapp.domain.nowEpochMillis
import com.example.androidapp.domain.repository.BackupRepository
import com.example.androidapp.domain.repository.ImportSummary
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room-backed backup and restore (ROADMAP P1.12).
 *
 * Both directions go through [dataResultOf], so a malformed file becomes a typed
 * `DataError.Invalid` carrying a message written for the user rather than an
 * exception that takes the screen down.
 */
@Singleton
class RoomBackupRepository @Inject constructor(
    private val database: WorkoutDatabase,
    private val timeSource: TimeSource,
) : BackupRepository {

    private val dao = database.backupDao()

    override suspend fun export(): DataResult<String> = dataResultOf {
        BackupCodec.encode(
            BackupFile(
                schemaVersion = BackupCodec.CURRENT_SCHEMA_VERSION,
                exportedAt = timeSource.nowEpochMillis(),
                exercises = dao.allExercises().map { it.toDto() },
                sessions = dao.allSessions().map { it.toDto() },
                sessionExercises = dao.allSessionExercises().map { it.toDto() },
                sets = dao.allSets().map { it.toDto() },
            ),
        )
    }

    override suspend fun import(text: String): DataResult<ImportSummary> = dataResultOf {
        // Throws InvalidInputException for a file we cannot use, before touching
        // anything — a rejected file must leave the database exactly as it was.
        val file = BackupCodec.decode(text)

        // One transaction, so a failure part-way cannot leave sessions without
        // their sets. Room returns one rowid per input row and -1 for a row that
        // was skipped as a duplicate, which is how the counts stay honest.
        database.withTransaction {
            // Parents before children: the foreign keys have to hold as rows go in.
            val exercises = dao.insertExercises(file.exercises.map { it.toEntity() })
                .count { it != SKIPPED }
            val sessions = dao.insertSessions(file.sessions.map { it.toEntity() })
                .count { it != SKIPPED }
            val sessionExercises = dao.insertSessionExercises(file.sessionExercises.map { it.toEntity() })
                .count { it != SKIPPED }
            val sets = dao.insertSets(file.sets.map { it.toEntity() })
                .count { it != SKIPPED }

            ImportSummary(
                exercises = exercises,
                sessions = sessions,
                sessionExercises = sessionExercises,
                sets = sets,
            )
        }
    }

    private companion object {
        /** Room's rowid for a row an `OnConflictStrategy.IGNORE` insert skipped. */
        const val SKIPPED = -1L
    }
}

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
import com.example.androidapp.platform.CrashLogStore
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
    private val crashLogStore: CrashLogStore,
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
                // A template is a plan, and the plan is the user's work too (N3).
                templates = dao.allTemplates().map { it.toDto() },
                templateExercises = dao.allTemplateExercises().map { it.toDto() },
                // Diagnostics ride along so they are reachable on a release
                // build; import ignores them, deliberately.
                crashLogs = crashLogStore.all(),
            ),
        )
    }

    override suspend fun import(text: String): DataResult<ImportSummary> = dataResultOf {
        // Throws InvalidInputException for a file we cannot use, before touching
        // anything — a rejected file must leave the database exactly as it was.
        val file = BackupCodec.decode(text)

        // One transaction, so a failure part-way cannot leave sessions without
        // their sets.
        database.withTransaction {
            // A delete in this app is a *soft* delete, so a deleted row is still
            // present under its own id and an insert-only import skips it. That is
            // the bug this shape fixes: restoring a workout you deleted reported
            // "nothing to do", because every row it needed to restore was already
            // there, just hidden. Find the hidden ids, restore those rows from the
            // file (which also clears `deletedAt`), then insert what is genuinely
            // missing. Live rows are touched by neither step.
            val hiddenExercises = dao.softDeletedExerciseIds().toSet()
            val hiddenSessions = dao.softDeletedSessionIds().toSet()
            val hiddenSessionExercises = dao.softDeletedSessionExerciseIds().toSet()
            val hiddenSets = dao.softDeletedSetIds().toSet()
            val hiddenTemplates = dao.softDeletedTemplateIds().toSet()
            val hiddenTemplateExercises = dao.softDeletedTemplateExerciseIds().toSet()

            val exercisesToRestore = file.exercises.filter { it.id in hiddenExercises }
            val sessionsToRestore = file.sessions.filter { it.id in hiddenSessions }
            val sessionExercisesToRestore = file.sessionExercises.filter { it.id in hiddenSessionExercises }
            val setsToRestore = file.sets.filter { it.id in hiddenSets }
            val templatesToRestore = file.templates.filter { it.id in hiddenTemplates }
            val templateExercisesToRestore =
                file.templateExercises.filter { it.id in hiddenTemplateExercises }

            // Updates, so no foreign-key ordering is involved: every row already
            // exists, and only its own columns change.
            dao.restoreExercises(exercisesToRestore.map { it.toEntity() })
            dao.restoreSessions(sessionsToRestore.map { it.toEntity() })
            dao.restoreSessionExercises(sessionExercisesToRestore.map { it.toEntity() })
            dao.restoreSets(setsToRestore.map { it.toEntity() })
            dao.restoreTemplates(templatesToRestore.map { it.toEntity() })
            dao.restoreTemplateExercises(templateExercisesToRestore.map { it.toEntity() })

            // Only a row the file itself has *live* actually came back. One the
            // file also records as deleted is still deleted, and counting it as
            // restored would overstate what the user got.
            val restored = exercisesToRestore.count { it.deletedAt == null } +
                sessionsToRestore.count { it.deletedAt == null } +
                sessionExercisesToRestore.count { it.deletedAt == null } +
                setsToRestore.count { it.deletedAt == null } +
                templatesToRestore.count { it.deletedAt == null } +
                templateExercisesToRestore.count { it.deletedAt == null }

            // Parents before children: the foreign keys have to hold as rows go in.
            // IGNORE skips live rows and the ones just restored, so this counts
            // only what was genuinely missing — Room returns -1 for a skipped row.
            val addedExercises = dao.insertExercises(file.exercises.map { it.toEntity() })
                .count { it != SKIPPED }
            val addedSessions = dao.insertSessions(file.sessions.map { it.toEntity() })
                .count { it != SKIPPED }
            val addedSessionExercises = dao.insertSessionExercises(file.sessionExercises.map { it.toEntity() })
                .count { it != SKIPPED }
            val addedSets = dao.insertSets(file.sets.map { it.toEntity() })
                .count { it != SKIPPED }
            val addedTemplates = dao.insertTemplates(file.templates.map { it.toEntity() })
                .count { it != SKIPPED }
            val addedTemplateExercises =
                dao.insertTemplateExercises(file.templateExercises.map { it.toEntity() })
                    .count { it != SKIPPED }

            ImportSummary(
                added = addedExercises + addedSessions + addedSessionExercises + addedSets +
                    addedTemplates + addedTemplateExercises,
                restored = restored,
            )
        }
    }

    private companion object {
        /** Room's rowid for a row an `OnConflictStrategy.IGNORE` insert skipped. */
        const val SKIPPED = -1L
    }
}

package com.example.androidapp.data

import java.io.IOException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import com.example.androidapp.data.local.seedMissingExercises
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
import com.example.androidapp.domain.repository.SettingsRepository
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
    private val settings: SettingsRepository,
    private val crashLogStore: CrashLogStore,
) : BackupRepository {

    private val dao = database.backupDao()

    /** The program tables' half of the same job, split out to keep both DAOs under the ceiling. */
    private val programBackup = database.programBackupDao()

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
                // A plan's sets are the plan (ROADMAP N14).
                templateSets = dao.allTemplateSets().map { it.toDto() },
                // Programs, their slots and their recorded skips (ROADMAP P3.3): the schedule is
                // authored setup, and a skip is history — both are the user's data.
                programs = programBackup.allPrograms().map { it.toDto() },
                programSlots = programBackup.allProgramSlots().map { it.toDto() },
                programSkips = programBackup.allProgramSkips().map { it.toDto() },
                // What each slot prescribes (ROADMAP P3.8): the authored setup that makes two
                // slots pointing at one template train it differently.
                programSlotExercises = programBackup.allProgramSlotExercises().map { it.toDto() },
                programSlotSets = programBackup.allProgramSlotSets().map { it.toDto() },
                // The weeks a program was backed off (ROADMAP P3.10): authored setup the app cannot
                // recompute, so dropping them would read every deload week as a miss.
                programDeloads = programBackup.allProgramDeloads().map { it.toDto() },
                // The workouts that stood in for a slot's own (ROADMAP P3.11): without them a
                // restored week reads as missed even though it was trained, with something else.
                programSubstitutions = programBackup.allProgramSubstitutions().map { it.toDto() },
                measurements = database.measurementDao().allForExport().map { it.toDto() },
                // The user's metric targets (ROADMAP N39). They are settings rather than rows,
                // which is exactly why they need naming here: the codec drops whatever it is not
                // told about, and a restore was losing every target in silence.
                goals = settings.observeGoals().first(),
                // Diagnostics ride along so they are reachable on a release
                // build; import ignores them, deliberately.
                crashLogs = crashLogStore.all(),
            ),
        )
    }

    override suspend fun clearAllUserData(): DataResult<Unit> = dataResultOf {
        // Off the main thread: `clearAllTables` is a blocking call and Room asserts where
        // it runs. The DAO's own suspending methods dispatch themselves; this one does not.
        withContext(Dispatchers.IO) {
            database.clearAllTables()

            // The seeder runs in `RoomDatabase.Callback.onOpen`, which fires when the
            // database is *opened*, not after its tables are emptied — so without this the
            // library would stay empty until the process restarted, and a clean start would
            // look broken (N18's trap).
            seedMissingExercises(
                db = database.openHelper.writableDatabase,
                seededAt = timeSource.nowEpochMillis(),
            )

            // Crash logs are diagnostics *about* the data that just went, and they live
            // outside the database, so nothing above touches them.
            if (!crashLogStore.clear()) {
                throw IOException("the crash logs could not be removed")
            }

            // Metric targets are the one setting the user *authored* rather than chose as a display
            // preference — which is why they are exported — so "delete everything" has to take them
            // too, or a clean start would still draw the old goal lines (N39, N18).
            settings.observeGoals().first().keys.forEach { metricId ->
                if (settings.setGoal(metricId, null) is DataResult.Failure) {
                    throw IOException("the goal for $metricId was not cleared")
                }
            }
        }
    }

    override suspend fun import(text: String): DataResult<ImportSummary> = dataResultOf {
        // Throws InvalidInputException for a file we cannot use, before touching
        // anything — a rejected file must leave the database exactly as it was.
        val file = BackupCodec.decode(text)

        // One transaction, so a failure part-way cannot leave sessions without their sets.
        // The shape is: a soft delete keeps the row under its id, so an insert-only import
        // skips exactly what a restore is meant to bring back — restore the hidden rows from
        // the file (which also clears `deletedAt`), then insert what is genuinely missing.
        //
        // `insertMissing` runs **first**, because the order is parent-before-child across the
        // two helpers and not just inside each one: `program_slots.templateId` and
        // `program_substitutions.templateId` reference `templates`, and running the program
        // block first made every restore into a fresh database fail with
        // `FOREIGN KEY constraint failed` and roll the whole import back.
        val summary = database.withTransaction {
            val inserted = insertMissing(file)
            val programs = importPrograms(file)
            ImportSummary(
                added = inserted + programs.added,
                restored = restoreSoftDeleted(file) + programs.restored,
            )
        }

        restoreMissingGoals(file.goals)

        summary
    }

    /**
     * Brings back the rows this device holds as soft-deleted, and counts what genuinely came
     * back: only a row the file itself has *live* did, because one the file also records as
     * deleted is still deleted.
     *
     * Extracted from [import] because that function had reached the length this project
     * enforces, and the seven tables here are one shape repeated — updates, so no foreign-key
     * ordering is involved: every row already exists and only its own columns change.
     */
    private suspend fun restoreSoftDeleted(file: BackupFile): Int {
        // Read once into sets: asking the database per row would be a query per element.
        val hiddenExercises = dao.softDeletedExerciseIds().toSet()
        val hiddenSessions = dao.softDeletedSessionIds().toSet()
        val hiddenSessionExercises = dao.softDeletedSessionExerciseIds().toSet()
        val hiddenSets = dao.softDeletedSetIds().toSet()
        val hiddenTemplates = dao.softDeletedTemplateIds().toSet()
        val hiddenTemplateExercises = dao.softDeletedTemplateExerciseIds().toSet()
        val hiddenTemplateSets = dao.softDeletedTemplateSetIds().toSet()

        val exercises = file.exercises.filter { it.id in hiddenExercises }
        val sessions = file.sessions.filter { it.id in hiddenSessions }
        val sessionExercises = file.sessionExercises.filter { it.id in hiddenSessionExercises }
        val sets = file.sets.filter { it.id in hiddenSets }
        val templates = file.templates.filter { it.id in hiddenTemplates }
        val templateExercises = file.templateExercises.filter { it.id in hiddenTemplateExercises }
        val templateSets = file.templateSets.filter { it.id in hiddenTemplateSets }

        dao.restoreExercises(exercises.map { it.toEntity() })
        dao.restoreSessions(sessions.map { it.toEntity() })
        dao.restoreSessionExercises(sessionExercises.map { it.toEntity() })
        dao.restoreSets(sets.map { it.toEntity() })
        dao.restoreTemplates(templates.map { it.toEntity() })
        dao.restoreTemplateExercises(templateExercises.map { it.toEntity() })
        dao.restoreTemplateSets(templateSets.map { it.toEntity() })

        return exercises.count { it.deletedAt == null } +
            sessions.count { it.deletedAt == null } +
            sessionExercises.count { it.deletedAt == null } +
            sets.count { it.deletedAt == null } +
            templates.count { it.deletedAt == null } +
            templateExercises.count { it.deletedAt == null } +
            templateSets.count { it.deletedAt == null }
    }

    /**
     * Inserts what the file has and this device does not, counting only the genuinely new
     * rows: `IGNORE` skips live rows and the ones just restored, and Room returns -1 for a
     * skipped row.
     *
     * Parents before children, so the foreign keys hold as rows go in.
     */
    private suspend fun insertMissing(file: BackupFile): Int =
        dao.insertExercises(file.exercises.map { it.toEntity() }).count { it != SKIPPED } +
            dao.insertSessions(file.sessions.map { it.toEntity() }).count { it != SKIPPED } +
            dao.insertSessionExercises(file.sessionExercises.map { it.toEntity() }).count { it != SKIPPED } +
            dao.insertSets(file.sets.map { it.toEntity() }).count { it != SKIPPED } +
            dao.insertTemplates(file.templates.map { it.toEntity() }).count { it != SKIPPED } +
            dao.insertTemplateExercises(file.templateExercises.map { it.toEntity() }).count { it != SKIPPED } +
            dao.insertTemplateSets(file.templateSets.map { it.toEntity() }).count { it != SKIPPED } +
            database.measurementDao().insertAll(file.measurements.map { it.toEntity() }).count { it != SKIPPED }

    /**
     * Restores and adds the program tables (ROADMAP P3.3), returning what each step did.
     *
     * The same hidden-then-insert shape as everything else in [import], extracted because the
     * enclosing function had reached the length this project enforces — and because the three
     * tables are a self-contained group that has to move together.
     */
    private suspend fun importPrograms(file: BackupFile): Imported {
        val hiddenPrograms = programBackup.softDeletedProgramIds().toSet()
        val hiddenSlots = programBackup.softDeletedProgramSlotIds().toSet()
        val hiddenSkips = programBackup.softDeletedProgramSkipIds().toSet()
        val hiddenSlotExercises = programBackup.softDeletedProgramSlotExerciseIds().toSet()
        val hiddenSlotSets = programBackup.softDeletedProgramSlotSetIds().toSet()
        val hiddenDeloads = programBackup.softDeletedProgramDeloadIds().toSet()
        val hiddenSubstitutions = programBackup.softDeletedProgramSubstitutionIds().toSet()

        val programsToRestore = file.programs.filter { it.id in hiddenPrograms }
        val slotsToRestore = file.programSlots.filter { it.id in hiddenSlots }
        val skipsToRestore = file.programSkips.filter { it.id in hiddenSkips }
        val slotExercisesToRestore = file.programSlotExercises.filter { it.id in hiddenSlotExercises }
        val slotSetsToRestore = file.programSlotSets.filter { it.id in hiddenSlotSets }
        val deloadsToRestore = file.programDeloads.filter { it.id in hiddenDeloads }
        val substitutionsToRestore = file.programSubstitutions.filter { it.id in hiddenSubstitutions }

        programBackup.restorePrograms(programsToRestore.map { it.toEntity() })
        programBackup.restoreProgramSlots(slotsToRestore.map { it.toEntity() })
        programBackup.restoreProgramSkips(skipsToRestore.map { it.toEntity() })
        programBackup.restoreProgramSlotExercises(slotExercisesToRestore.map { it.toEntity() })
        programBackup.restoreProgramSlotSets(slotSetsToRestore.map { it.toEntity() })
        programBackup.restoreProgramDeloads(deloadsToRestore.map { it.toEntity() })
        programBackup.restoreProgramSubstitutions(substitutionsToRestore.map { it.toEntity() })

        val restored = programsToRestore.count { it.deletedAt == null } +
            slotsToRestore.count { it.deletedAt == null } +
            skipsToRestore.count { it.deletedAt == null } +
            slotExercisesToRestore.count { it.deletedAt == null } +
            slotSetsToRestore.count { it.deletedAt == null } +
            deloadsToRestore.count { it.deletedAt == null } +
            substitutionsToRestore.count { it.deletedAt == null }

        // Parents before children, so a set's exercise row is there when the set goes in (P3.8).
        val added = programBackup.insertPrograms(file.programs.map { it.toEntity() })
            .count { it != SKIPPED } +
            programBackup.insertProgramSlots(file.programSlots.map { it.toEntity() })
                .count { it != SKIPPED } +
            programBackup.insertProgramSkips(file.programSkips.map { it.toEntity() })
                .count { it != SKIPPED } +
            programBackup.insertProgramSlotExercises(file.programSlotExercises.map { it.toEntity() })
                .count { it != SKIPPED } +
            programBackup.insertProgramSlotSets(file.programSlotSets.map { it.toEntity() })
                .count { it != SKIPPED } +
            programBackup.insertProgramDeloads(file.programDeloads.map { it.toEntity() })
                .count { it != SKIPPED } +
            programBackup.insertProgramSubstitutions(file.programSubstitutions.map { it.toEntity() })
                .count { it != SKIPPED }

        return Imported(added = added, restored = restored)
    }

    /**
     * Adds the targets in [goals] that this device does not already have.
     *
     * Outside the database transaction because goals live outside the database, and *missing-only*
     * for the reason a live row is left alone: a target set after the export was taken is newer
     * than the file, and guessing the other way would overwrite a deliberate change.
     */
    private suspend fun restoreMissingGoals(goals: Map<String, Double>) {
        val alreadyHere = settings.observeGoals().first()
        goals.forEach { (metricId, value) ->
            if (metricId !in alreadyHere) {
                val stored = settings.setGoal(metricId, value)
                if (stored is DataResult.Failure) {
                    throw IOException("the goal for $metricId was not stored")
                }
            }
        }
    }

    private companion object {
        /** Room's rowid for a row an `OnConflictStrategy.IGNORE` insert skipped. */
        const val SKIPPED = -1L
    }
}

/** What importing the program tables did: rows that genuinely came back, and rows added (P3.3). */
private data class Imported(val added: Int, val restored: Int)

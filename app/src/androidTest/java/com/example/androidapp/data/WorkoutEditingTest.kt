package com.example.androidapp.data

import com.example.androidapp.data.local.TemplateExerciseEntity
import com.example.androidapp.data.local.TemplateEntity
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.androidapp.data.local.ExerciseEntity
import com.example.androidapp.data.local.SetEntryEntity
import com.example.androidapp.data.local.SessionExerciseEntity
import com.example.androidapp.data.local.WorkoutDatabase
import com.example.androidapp.domain.DataError
import com.example.androidapp.domain.DataResult
import com.example.androidapp.domain.TimeSource
import com.example.androidapp.domain.ZoneOffsetSource
import com.example.androidapp.domain.model.Equipment
import com.example.androidapp.domain.model.MovementPattern
import com.example.androidapp.domain.model.MuscleGroup
import com.example.androidapp.domain.model.SetType
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented tests for correcting a past workout (ROADMAP P1.7).
 *
 * Before this, `Finish` was a one-way door: a mistyped weight appeared in history
 * and could never be fixed. These assert the correction actually lands, and that
 * the history list's SQL aggregates follow it — which is also what keeps the
 * list's volume formula and the detail screen's agreeing.
 */
@RunWith(AndroidJUnit4::class)
class WorkoutEditingTest {

    private lateinit var database: WorkoutDatabase
    private lateinit var repository: RoomWorkoutRepository

    private val clock = TimeSource { Instant.parse("2026-09-28T08:00:00Z") }

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            WorkoutDatabase::class.java,
        ).build()
        repository = RoomWorkoutRepository(database, clock, ZoneOffsetSource { 0 })
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun correctingASet_changesWhatHistoryReports() = runTest {
        seedFinishedWorkout()
        val setId = database.backupDao().allSets().single().id
        // 100 kg x 5 = 500,000 gram-reps to begin with.
        assertEquals(500_000L, historyVolume())

        val result = repository.updateSet(setId, reps = 8, weightGrams = 100_000L, rpeHalves = null, note = null)

        assertTrue(result is DataResult.Success)
        assertEquals("the list's aggregate must follow the edit", 800_000L, historyVolume())
    }

    @Test
    fun deletingAPastWorkout_removesItFromHistory() = runTest {
        val sessionId = seedFinishedWorkout()
        assertTrue(database.workoutDao().observeHistory().first().isNotEmpty())

        val result = repository.deleteSession(sessionId)

        assertTrue(result is DataResult.Success)
        assertTrue(
            "a deleted workout must not linger in the history list",
            database.workoutDao().observeHistory().first().isEmpty(),
        )
    }

    @Test
    fun correctingASetThatIsGone_failsAsNotFound() = runTest {
        seedFinishedWorkout()
        val setId = database.backupDao().allSets().single().id
        repository.deleteSet(setId)

        val result = repository.updateSet(setId, reps = 5, weightGrams = 1_000L, rpeHalves = null, note = null)

        // A stale screen holding a deleted set's id must not resurrect it.
        assertEquals(DataError.NotFound, (result as DataResult.Failure).error)
    }

    @Test
    fun correctingASet_recordsItsRpeAndComment() = runTest {
        seedFinishedWorkout()
        val setId = database.backupDao().allSets().single().id

        val result = repository.updateSet(
            setId,
            reps = 5,
            weightGrams = 100_000L,
            rpeHalves = 16,
            note = "Felt heavy",
        )

        assertTrue(result is DataResult.Success)
        val stored = database.workoutDao().findSetById(setId)!!
        assertEquals(16, stored.rpeHalves)
        assertEquals("Felt heavy", stored.note)
        // The columns an edit does not touch are preserved, not invented.
        assertEquals(0, stored.setIndex)
        assertEquals("se1", stored.sessionExerciseId)
    }

    @Test
    fun aBlankComment_isStoredAsNull_andRpeCanBeCleared() = runTest {
        seedFinishedWorkout()
        val setId = database.backupDao().allSets().single().id
        repository.updateSet(setId, reps = 5, weightGrams = 100_000L, rpeHalves = 16, note = "Felt heavy")

        repository.updateSet(setId, reps = 5, weightGrams = 100_000L, rpeHalves = null, note = "   ")

        val stored = database.workoutDao().findSetById(setId)!!
        assertNull(stored.note)
        assertNull("clearing an RPE is how a mistyped one is undone", stored.rpeHalves)
    }

    @Test
    fun anRpeOutsideTheScale_isRefused_withoutTouchingTheSet() = runTest {
        seedFinishedWorkout()
        val setId = database.backupDao().allSets().single().id

        val result = repository.updateSet(
            setId,
            reps = 5,
            weightGrams = 100_000L,
            rpeHalves = 21,
            note = null,
        )

        assertTrue((result as DataResult.Failure).error is DataError.Invalid)
        assertNull("a refused write must leave the set alone", database.workoutDao().findSetById(setId)!!.rpeHalves)
    }

    @Test
    fun finishingAnExercise_marksItDone_andClearsTheRest() = runTest {
        val sessionId = seedOpenWorkoutWithASet()
        // Arm the rest, so ending the exercise has something to clear.
        database.workoutDao().updateRestTimer(sessionId, restEndsAt = 9_999L, at = 1_000L)

        val result = repository.finishExercise("se1")

        assertTrue(result is DataResult.Success)
        val exercise = database.workoutDao().observeSessionExerciseDetails(sessionId).first().single()
        assertNotNull("the row must carry its done timestamp", exercise.finishedAt)
        assertNull(
            "ending a lift must not leave a rest armed (N7)",
            database.workoutDao().findSession(sessionId)?.restEndsAt,
        )
    }

    @Test
    fun aDoneExercise_cannotTakeAnotherSet() = runTest {
        seedOpenWorkoutWithASet()
        repository.finishExercise("se1")

        val result = repository.logSet("se1", reps = 5, weightGrams = 100_000L)

        assertEquals(DataError.NotFound, (result as DataResult.Failure).error)
        assertEquals("the refused set must not be written", 1, database.backupDao().allSets().size)
    }

    @Test
    fun reopeningAnExercise_restoresLogging_andClearsTheDoneState() = runTest {
        val sessionId = seedOpenWorkoutWithASet()
        repository.finishExercise("se1")

        val result = repository.reopenExercise("se1")

        assertTrue(result is DataResult.Success)
        assertNull(
            database.workoutDao().observeSessionExerciseDetails(sessionId).first().single().finishedAt,
        )
        assertTrue(repository.logSet("se1", reps = 5, weightGrams = 100_000L) is DataResult.Success)
    }

    @Test
    fun finishingAGoneExercise_isNotFound() = runTest {
        seedOpenWorkoutWithASet()
        repository.removeExercise("se1")

        assertEquals(
            DataError.NotFound,
            (repository.finishExercise("se1") as DataResult.Failure).error,
        )
    }

    @Test
    fun ratingAnExercise_storesMuscleFeelAndJointPain() = runTest {
        val sessionId = seedOpenWorkoutWithASet()

        val result = repository.rateExercise("se1", muscleFeel = 8, jointPain = 2)

        assertTrue(result is DataResult.Success)
        val stored = database.workoutDao().observeSessionExerciseDetails(sessionId).first().single()
        assertEquals(8, stored.muscleFeel)
        assertEquals(2, stored.jointPain)
    }

    @Test
    fun aJointPainLocation_isStored_andABlankOneBecomesNull() = runTest {
        val sessionId = seedOpenWorkoutWithASet()

        assertTrue(
            repository.rateExercise("se1", muscleFeel = 8, jointPain = 4, jointPainNote = "  left shoulder  ")
                is DataResult.Success,
        )
        assertEquals(
            "left shoulder",
            database.workoutDao().observeSessionExerciseDetails(sessionId).first().single().jointPainNote,
        )

        repository.rateExercise("se1", muscleFeel = 8, jointPain = 4, jointPainNote = "   ")

        // Two representations of "nothing" would render differently on screen, the
        // same rule the readiness note follows (ROADMAP N9).
        assertNull(
            database.workoutDao().observeSessionExerciseDetails(sessionId).first().single().jointPainNote,
        )
    }

    @Test
    fun aRatingOutsideTheScale_isRefused_withoutTouchingTheExercise() = runTest {
        val sessionId = seedOpenWorkoutWithASet()

        val result = repository.rateExercise("se1", muscleFeel = 11, jointPain = 2)

        assertTrue((result as DataResult.Failure).error is DataError.Invalid)
        assertNull(
            "a refused write must leave the exercise unrated",
            database.workoutDao().observeSessionExerciseDetails(sessionId).first().single().muscleFeel,
        )
    }

    @Test
    fun aWorkoutComment_isStored_andABlankOneClearsIt() = runTest {
        val sessionId = seedOpenWorkoutWithASet()

        assertTrue(repository.setWorkoutNotes(sessionId, "  Felt strong  ") is DataResult.Success)
        assertEquals("Felt strong", repository.observeSession(sessionId).first()?.notes)

        repository.setWorkoutNotes(sessionId, "   ")

        // One representation of "nothing", the same rule the readiness note follows.
        assertNull(repository.observeSession(sessionId).first()?.notes)
    }

    @Test
    fun aCommentOnAGoneWorkout_isNotFound() = runTest {
        assertEquals(
            DataError.NotFound,
            (repository.setWorkoutNotes("no-such-session", "x") as DataResult.Failure).error,
        )
    }

    @Test
    fun ratingsSurviveFinishingAndReopening() = runTest {
        val sessionId = seedOpenWorkoutWithASet()
        repository.rateExercise("se1", muscleFeel = 8, jointPain = 2)

        repository.finishExercise("se1")
        repository.reopenExercise("se1")

        // Reopening is for fixing a mis-tap; it must not wipe how it felt.
        val stored = database.workoutDao().observeSessionExerciseDetails(sessionId).first().single()
        assertEquals(8, stored.muscleFeel)
        assertEquals(2, stored.jointPain)
    }

    private suspend fun historyVolume(): Long =
        database.workoutDao().observeHistory().first().single().volumeGrams

    private suspend fun seedFinishedWorkout(): String {
        val sessionId = seedOpenWorkoutWithASet()
        database.workoutDao().markFinished(id = sessionId, at = 2_000L)
        return sessionId
    }

    /** An open session with one exercise and one logged set (`s1`, `se1`, `set1`). */
    private suspend fun seedOpenWorkoutWithASet(): String {
        database.exerciseDao().insertAll(
            listOf(
                ExerciseEntity(
                    id = "back-squat",
                    name = "Back Squat",
                    primaryMuscle = MuscleGroup.QUADS,
                    secondaryMuscles = emptyList(),
                    equipment = Equipment.BARBELL,
                    movementPattern = MovementPattern.SQUAT,
                    isCustom = false,
                    createdAt = 0L,
                    updatedAt = 0L,
                    deletedAt = null,
                ),
            ),
        )

        val session = database.workoutDao().findOrCreateActiveSession(id = "s1", now = 1_000L).session
        database.workoutDao().insertSessionExercise(
            SessionExerciseEntity(
                id = "se1",
                sessionId = session.id,
                exerciseId = "back-squat",
                position = 0,
                createdAt = 0L,
                updatedAt = 0L,
                deletedAt = null,
            ),
        )
        database.workoutDao().insertSet(
            SetEntryEntity(
                id = "set1",
                sessionExerciseId = "se1",
                setIndex = 0,
                reps = 5,
                weightGrams = 100_000L,
                setType = SetType.NORMAL,
                completedAt = 1_000L,
                createdAt = 1_000L,
                updatedAt = 1_000L,
                deletedAt = null,
            ),
        )
        return session.id
    }

    @Test
    fun anAssistedSet_takesNothingOffTheVolume() = runTest {
        // ROADMAP N15: assistance is a separate magnitude, so volume stays
        // `weight × reps`. A signed weight would have made this set subtract, which
        // is the whole reason it is not one.
        seedFinishedWorkout()
        repository.updateSet(
            setId = "set1",
            reps = 8,
            weightGrams = 20_000L,
            assistanceGrams = 20_000L,
        )

        val stored = database.workoutDao().findSetById("set1")!!
        assertEquals(20_000L, stored.assistanceGrams)
        assertEquals("the weight is what was added, not netted", 20_000L, stored.weightGrams)

        // 20 kg × 8 is 160 kg; the 20 kg of help is not subtracted from it.
        assertEquals(160_000L, historyVolume())
    }

    @Test
    fun theUiVolumeFormula_agreesWithTheSql() = runTest {
        // `WorkoutDetailViewModel.volumeGrams` says it is "one formula in two places" and
        // that "the instrumented history test asserts they agree" — which no test did
        // (ROADMAP B9). This is that assertion: the sum the detail screen computes over
        // its own rows, against the figure the history query returns for the same rows.
        seedFinishedWorkout()
        repository.logSet(
            sessionExerciseId = "se1",
            reps = 8,
            weightGrams = 0L,
            assistanceGrams = 20_000L,
        )

        val sets = database.workoutDao().observeSetsForSession("s1").first()
        val uiFormula = sets.sumOf { it.reps.toLong() * it.weightGrams }

        assertEquals("the assisted set contributes nothing, not a negative", 500_000L, uiFormula)
        assertEquals(uiFormula, historyVolume())
    }

    @Test
    fun personalRecords_ignoreWarmUps_andTheSessionBeingLogged() = runTest {
        // ROADMAP B24: this read met only hand-written fakes, and the SQL behind it has to do two
        // things no fake can prove — leave warm-ups out, and be able to exclude the session in
        // progress, which is what stops a set being compared against itself.
        val sessionId = seedFinishedWorkout()
        // A heavier warm-up at the same rep count: with the role ignored it would set the record.
        database.workoutDao().insertSet(
            SetEntryEntity(
                id = "warm-up",
                sessionExerciseId = "se1",
                setIndex = 1,
                reps = 5,
                weightGrams = 120_000L,
                setType = SetType.WARMUP,
                completedAt = 1L,
                createdAt = 1L,
                updatedAt = 1L,
                deletedAt = null,
            ),
        )

        val records = (repository.personalRecords("back-squat") as DataResult.Success).data
        assertEquals("the working set, not the heavier warm-up", 100_000L, records.bestAt(5))

        val excluding = (repository.personalRecords("back-squat", excludingSessionId = sessionId)
            as DataResult.Success).data
        assertTrue("excluding the session in progress leaves nothing", excluding.isEmpty)
    }

    @Test
    fun setSupersetGroup_isReadBackThroughTheProjection() = runTest {
        // The session projection already shipped once without selecting this column, so the row
        // arrived ungrouped while the write succeeded — a defect no fake could show, because a fake
        // never builds a projection from a query string (ROADMAP B24).
        seedOpenWorkoutWithASet()

        val written = repository.setSupersetGroup(listOf("se1"), group = 3)
        assertTrue(written is DataResult.Success)

        val row = repository.observeSessionExercises("s1").first().single()
        assertEquals("the write reaches the read", 3, row.supersetGroup)
    }

    @Test
    fun leavingASuperset_isWrittenToo() = runTest {
        seedOpenWorkoutWithASet()
        repository.setSupersetGroup(listOf("se1"), group = 3)

        repository.setSupersetGroup(listOf("se1"), group = null)

        val row = repository.observeSessionExercises("s1").first().single()
        assertNull("a nullable group is how leaving is expressed", row.supersetGroup)
    }

    @Test
    fun aWorkoutStartedFromAPlan_arrivesWithItsSupersets() = runTest {
        // ROADMAP B16's other half, and the reason the column exists at all: a plan that prescribes
        // a superset must arrive as one. The device walk could only show the editor's labels; this
        // is the seeding path, where the plan meets the session.
        database.exerciseDao().insertAll(
            listOf(
                ExerciseEntity(
                    id = "back-squat",
                    name = "Back Squat",
                    primaryMuscle = MuscleGroup.QUADS,
                    secondaryMuscles = emptyList(),
                    equipment = Equipment.BARBELL,
                    movementPattern = MovementPattern.SQUAT,
                    isCustom = false,
                    createdAt = 0L,
                    updatedAt = 0L,
                    deletedAt = null,
                ),
                ExerciseEntity(
                    id = "bench-press",
                    name = "Barbell Bench Press",
                    primaryMuscle = MuscleGroup.CHEST,
                    secondaryMuscles = emptyList(),
                    equipment = Equipment.BARBELL,
                    movementPattern = MovementPattern.HORIZONTAL_PUSH,
                    isCustom = false,
                    createdAt = 0L,
                    updatedAt = 0L,
                    deletedAt = null,
                ),
            ),
        )
        database.templateDao().insertTemplate(
            TemplateEntity(
                id = "t1",
                name = "Pair",
                createdAt = 0L,
                updatedAt = 0L,
                deletedAt = null,
            ),
        )
        listOf("te1" to "back-squat", "te2" to "bench-press").forEachIndexed { index, (id, exerciseId) ->
            database.templateDao().insertTemplateExercise(
                TemplateExerciseEntity(
                    id = id,
                    templateId = "t1",
                    exerciseId = exerciseId,
                    position = index,
                    supersetGroup = 1,
                    createdAt = 0L,
                    updatedAt = 0L,
                    deletedAt = null,
                ),
            )
        }

        val started = repository.startOrResumeSession(templateId = "t1")
        val sessionId = (started as DataResult.Success).data.id

        val rows = repository.observeSessionExercises(sessionId).first()
        assertEquals("both arrive", 2, rows.size)
        assertEquals(
            "in the plan's group",
            listOf(1, 1),
            rows.map { it.supersetGroup },
        )
    }
}

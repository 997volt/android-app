package com.example.androidapp.ui.home

import com.google.common.truth.Truth.assertThat
import java.time.DayOfWeek
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.androidapp.domain.model.WorkoutSummary
import com.example.androidapp.domain.model.WorkoutTemplate
import com.example.androidapp.ui.components.TestTags
import com.example.androidapp.ui.theme.AndroidAppTheme
import com.example.androidapp.ui.workout.WorkoutClock
import java.time.Instant
import org.junit.Rule
import com.example.androidapp.ui.history.HistoryFormat
import java.time.ZoneOffset
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The home screen (ROADMAP N1).
 *
 * Two of these moved here from the library's tests: the start/resume button used to
 * live there, and N1 is precisely the change that moved it. Leaving them behind
 * would have tested a button that no longer exists on that screen.
 *
 * Matched by tag, so a translation cannot break them.
 */
@RunWith(AndroidJUnit4::class)
class WorkoutsHomeScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    /**
     * Every callback the screen takes, grouped: the helper is a screen setter, and
     * one flat parameter per event stops reading as a call site long before it
     * stops compiling.
     */
    private data class Actions(
        val onStartWorkout: () -> Unit = {},
        val onStartFromTemplate: () -> Unit = {},
        val onRepeatLast: () -> Unit = {},
        val onStartTemplate: (TodayPlan) -> Unit = {},
        val onSubstituteTemplate: (TodayPlan, String?) -> Unit = { _, _ -> },
        val onOpenWorkout: (String) -> Unit = {},
        val onOpenHistory: () -> Unit = {},
        val onOpenPrograms: () -> Unit = {},
        // Null by default, mirroring the screen: a host that wired no transfer
        // actions gets no dead menu entries (ROADMAP B1).
        val onExportData: (() -> Unit)? = null,
        val onImportData: (() -> Unit)? = null,
    )

    private fun setScreen(
        state: WorkoutsHomeUiState,
        actions: Actions = Actions(),
        message: String? = null,
    ) {
        composeTestRule.setContent {
            AndroidAppTheme {
                WorkoutsHomeScreen(
                    state = state,
                    clock = remember { mutableStateOf(WorkoutClock()) },
                    onStartWorkout = actions.onStartWorkout,
                    onStartFromTemplate = actions.onStartFromTemplate,
                    onRepeatLast = actions.onRepeatLast,
                    onStartTemplate = actions.onStartTemplate,
                    onSubstituteTemplate = actions.onSubstituteTemplate,
                    onOpenWorkout = actions.onOpenWorkout,
                    onOpenHistory = actions.onOpenHistory,
                    onOpenPrograms = actions.onOpenPrograms,
                    onExportData = actions.onExportData,
                    onImportData = actions.onImportData,
                    message = message,
                )
            }
        }
    }

    @Test
    fun withNothingLogged_theScreenPointsAtStart() {
        var started = false
        setScreen(WorkoutsHomeUiState(isLoading = false), Actions(onStartWorkout = { started = true }))

        // An empty list with no explanation tells a first-run user nothing.
        composeTestRule.onNodeWithTag(TestTags.HOME_FIRST_RUN).assertIsDisplayed()
        composeTestRule.onNodeWithTag(TestTags.HOME_START).performClick()

        assertThat(started).isTrue()
    }

    @Test
    fun withAWorkoutRunning_theButtonOffersToResume() {
        setScreen(
            WorkoutsHomeUiState(
                isLoading = false,
                activeWorkout = ActiveWorkoutInfo(
                    startedAt = Instant.parse("2026-09-29T10:00:00Z"),
                    exerciseCount = 3,
                ),
            ),
        )

        composeTestRule.onNodeWithTag(TestTags.HOME_RESUME).assertIsDisplayed()
        composeTestRule.onNodeWithTag(TestTags.HOME_START).assertDoesNotExist()
    }

    @Test
    fun theStartAction_offersBothWaysToBegin() {
        // N3: the start action presents the choice — empty, or from a plan set up
        // in advance. With a workout already open there is no choice to make.
        var fromTemplate = false
        setScreen(
            WorkoutsHomeUiState(isLoading = false),
            Actions(onStartFromTemplate = { fromTemplate = true }),
        )

        composeTestRule.onNodeWithTag(TestTags.HOME_START).assertIsDisplayed()
        composeTestRule.onNodeWithTag(TestTags.HOME_START_FROM_TEMPLATE).performClick()

        assertThat(fromTemplate).isTrue()
    }

    @Test
    fun withAWorkoutRunning_theTemplateChoiceIsNotOffered() {
        setScreen(
            WorkoutsHomeUiState(
                isLoading = false,
                activeWorkout = ActiveWorkoutInfo(
                    startedAt = Instant.parse("2026-09-29T10:00:00Z"),
                    exerciseCount = 3,
                ),
            ),
        )

        composeTestRule.onNodeWithTag(TestTags.HOME_START_FROM_TEMPLATE).assertDoesNotExist()
    }

    @Test
    fun theOverflowMenu_offersExportAndImport() {
        // ROADMAP B1: these used to be two menus deep — home, then the library.
        var exported = false
        var imported = false
        setScreen(
            WorkoutsHomeUiState(isLoading = false),
            Actions(onExportData = { exported = true }, onImportData = { imported = true }),
        )

        composeTestRule.onNodeWithTag(TestTags.HOME_MENU).performClick()
        composeTestRule.onNodeWithTag(TestTags.DATA_EXPORT).performClick()
        assertThat(exported).isTrue()

        composeTestRule.onNodeWithTag(TestTags.HOME_MENU).performClick()
        composeTestRule.onNodeWithTag(TestTags.DATA_IMPORT).performClick()
        assertThat(imported).isTrue()
    }

    @Test
    fun withoutTransferActions_theMenuDoesNotOfferThem() {
        // A preview or a host that wired none; the entries must not appear dead.
        setScreen(WorkoutsHomeUiState(isLoading = false))

        composeTestRule.onNodeWithTag(TestTags.HOME_MENU).performClick()

        composeTestRule.onNodeWithTag(TestTags.DATA_EXPORT).assertDoesNotExist()
        composeTestRule.onNodeWithTag(TestTags.DATA_IMPORT).assertDoesNotExist()
    }

    @Test
    fun theOverflowMenu_offersPrograms() {
        // ROADMAP P3.3: the schedule the today's-plan section is read from is one tap from it.
        var opened = false
        setScreen(WorkoutsHomeUiState(isLoading = false), Actions(onOpenPrograms = { opened = true }))

        composeTestRule.onNodeWithTag(TestTags.HOME_MENU).performClick()
        composeTestRule.onNodeWithTag(TestTags.HOME_PROGRAMS).performClick()

        assertThat(opened).isTrue()
    }

    @Test
    fun aTransferMessage_isShown() {
        setScreen(WorkoutsHomeUiState(isLoading = false), message = "Exported 42 rows")

        composeTestRule.onNodeWithText("Exported 42 rows").assertIsDisplayed()
    }

    @Test
    fun recentWorkouts_areListed_andOpenTheirDetail() {
        var opened: String? = null
        setScreen(
            WorkoutsHomeUiState(isLoading = false, recent = listOf(summary("session-1"))),
            Actions(onOpenWorkout = { opened = it }),
        )

        composeTestRule.onNodeWithTag(TestTags.HOME_RECENT_ROW).performClick()

        assertThat(opened).isEqualTo("session-1")
    }

    @Test
    fun seeAll_leadsToTheFullHistory() {
        // Home is a home-sized view; the month-grouped history is still the full one.
        var openedHistory = false
        setScreen(
            WorkoutsHomeUiState(isLoading = false, recent = listOf(summary("session-1"))),
            Actions(onOpenHistory = { openedHistory = true }),
        )

        composeTestRule.onNodeWithTag(TestTags.HOME_SEE_ALL).performClick()

        assertThat(openedHistory).isTrue()
    }

    private fun summary(id: String) = WorkoutSummary(
        id = id,
        startedAt = Instant.parse("2026-09-29T08:00:00Z"),
        finishedAt = Instant.parse("2026-09-29T09:00:00Z"),
        exerciseCount = 3,
        setCount = 12,
        volumeGrams = 1_000_000L,
    )

    @Test
    fun todaysPlan_isShownUnderToday_withAStartAction() {
        // ROADMAP N16: home shows what is scheduled for today and offers to start it.
        val started = mutableListOf<TodayPlan>()
        setScreen(
            state = WorkoutsHomeUiState(
                isLoading = false,
                today = DayOfWeek.FRIDAY,
                todaysPlan = listOf(
                    TodayPlan(
                        id = "slot-1",
                        templateId = "t1",
                        name = "Heavy lower",
                        exerciseCount = 4,
                        // The slot travels with the row so its prescription seeds the workout
                        // (ROADMAP P3.8).
                        slotId = "slot-1",
                    ),
                ),
            ),
            actions = Actions(onStartTemplate = { started += it }),
        )

        composeTestRule.onNodeWithText("Today · Friday").assertExists()
        composeTestRule.onNodeWithText("Heavy lower").assertExists()
        composeTestRule.onNodeWithTag(TestTags.homeStartPlan("slot-1")).performClick()

        // The row's identity is the slot's; what starts is the template it points at, and the
        // slot travels with it (P3.3, P3.8).
        assertThat(started.single().templateId).isEqualTo("t1")
        assertThat(started.single().slotId).isEqualTo("slot-1")
    }

    @Test
    fun aDayWithNothingScheduled_showsNoTodaySection() {
        // An empty "Today" heading would be a promise the app cannot keep.
        setScreen(WorkoutsHomeUiState(isLoading = false, today = DayOfWeek.MONDAY))

        composeTestRule.onNodeWithText("Today · Monday").assertDoesNotExist()
    }

    @Test
    fun aProgramWithNothingToday_offersWhereItsRunIs() {
        // ROADMAP P3.9: the run gives "which one is next" an answer on a day nothing is scheduled.
        val started = mutableListOf<TodayPlan>()
        setScreen(
            state = WorkoutsHomeUiState(
                isLoading = false,
                nextUp = listOf(
                    NextUp(
                        plan = TodayPlan(
                            id = "slot-2",
                            templateId = "t2",
                            name = "Push",
                            exerciseCount = 5,
                            slotId = "slot-2",
                        ),
                        programName = "Upper/Lower",
                        isAtStart = true,
                    ),
                ),
            ),
            actions = Actions(onStartTemplate = { started += it }),
        )

        composeTestRule.onNodeWithText("Next up").assertExists()
        composeTestRule.onNodeWithText("Upper/Lower · 5 exercises").assertExists()
        composeTestRule.onNodeWithTag(TestTags.homeNextUp("slot-2")).performClick()

        // The slot travels with the start, so its prescription seeds the workout (P3.8).
        assertThat(started.single().slotId).isEqualTo("slot-2")
        assertThat(started.single().templateId).isEqualTo("t2")
    }

    @Test
    fun severalPlansOnADay_areAllListed() {
        setScreen(
            state = WorkoutsHomeUiState(
                isLoading = false,
                today = DayOfWeek.FRIDAY,
                todaysPlan = listOf(
                    TodayPlan(id = "t1", templateId = "t1", name = "Heavy lower", exerciseCount = 0),
                    TodayPlan(id = "t2", templateId = "t2", name = "Push", exerciseCount = 0),
                ),
            ),
        )

        composeTestRule.onNodeWithText("Heavy lower").assertExists()
        composeTestRule.onNodeWithText("Push").assertExists()
    }

    @Test
    fun theSameTemplateTwiceInADay_isTwoRows() {
        // ROADMAP P3.3: a program may schedule one template in two slots, so a row is keyed
        // by the slot rather than by the template — keying by template would collide and crash.
        setScreen(
            state = WorkoutsHomeUiState(
                isLoading = false,
                today = DayOfWeek.FRIDAY,
                todaysPlan = listOf(
                    TodayPlan(id = "slot-1", templateId = "t1", name = "Heavy lower", exerciseCount = 3),
                    TodayPlan(id = "slot-2", templateId = "t1", name = "Heavy lower", exerciseCount = 3),
                ),
            ),
        )

        composeTestRule.onNodeWithTag(TestTags.homeStartPlan("slot-1")).assertExists()
        composeTestRule.onNodeWithTag(TestTags.homeStartPlan("slot-2")).assertExists()
    }

    @Test
    fun withNoFinishedWorkout_theRepeatAction_isNotOffered() {
        // ROADMAP N29: there is nothing to repeat, so the control is absent rather than disabled —
        // a button that does nothing invites a tap and teaches the wrong thing.
        setScreen(WorkoutsHomeUiState(isLoading = false, recent = emptyList()))

        composeTestRule.onNodeWithTag(TestTags.HOME_REPEAT_LAST).assertDoesNotExist()
    }

    @Test
    fun withAFinishedWorkout_itIsOffered_andTapsThrough() {
        var repeated = 0
        setScreen(
            state = WorkoutsHomeUiState(
                isLoading = false,
                recent = listOf(summary("session-1")),
                canRepeatLast = true,
            ),
            actions = Actions(onRepeatLast = { repeated++ }),
        )

        composeTestRule.onNodeWithTag(TestTags.HOME_REPEAT_LAST).assertIsDisplayed().performClick()
        assertThat(repeated).isEqualTo(1)
    }

    @Test
    fun aPastWorkoutsDate_isRenderedInItsOwnZone() {
        // ROADMAP B33 and B39: grouping was well covered while the rendering was not, and the
        // rendering is where the bug was. Home and history now agree — and the expected string is
        // produced by the same formatter, so this cannot drift with the machine's locale.
        val tokyo = summary("session-1").copy(
            startedAt = Instant.parse("2026-09-30T20:00:00Z"),
            zoneOffsetMinutes = 540,
        )
        setScreen(WorkoutsHomeUiState(isLoading = false, recent = listOf(tokyo)))

        val expected = HistoryFormat.date(
            tokyo.startedAt,
            zone = ZoneOffset.ofHours(9),
        )
        composeTestRule.onNodeWithText(expected, substring = true).assertIsDisplayed()
    }

    @Test
    fun whileAWorkoutIsOpen_neitherStartChoiceIsOffered() {
        // ROADMAP B43's last gap. The absence test above passes an empty history, which is the *other*
        // rule; this one has history and an open session, and asserts the pair is gone either way — a
        // second way to start a workout while one is running is not a choice, it is a way to lose one.
        setScreen(
            state = WorkoutsHomeUiState(
                isLoading = false,
                recent = listOf(summary("session-1")),
                activeWorkout = ActiveWorkoutInfo(
                    startedAt = Instant.parse("2026-10-01T10:00:00Z"),
                    exerciseCount = 2,
                ),
            ),
        )

        composeTestRule.onNodeWithTag(TestTags.HOME_REPEAT_LAST).assertDoesNotExist()
        composeTestRule.onNodeWithTag(TestTags.HOME_START_FROM_TEMPLATE).assertDoesNotExist()
    }

    @Test
    fun theSubstituteAction_offersTheTemplates_andReportsThePick() {
        // ROADMAP P3.11: the pick is made at the point of starting, from the row being started.
        var picked: Pair<TodayPlan, String?>? = null
        setScreen(
            state = todayPlanWithTemplates,
            actions = Actions(onSubstituteTemplate = { plan, templateId -> picked = plan to templateId }),
        )

        composeTestRule.onNodeWithTag(TestTags.homeSubstitute("slot-1")).performClick()
        composeTestRule.onNodeWithTag(TestTags.HOME_SUBSTITUTE_DIALOG).assertExists()
        composeTestRule.onNodeWithTag(TestTags.homeSubstituteTemplate("t2")).performClick()

        assertThat(picked?.first?.slotId).isEqualTo("slot-1")
        assertThat(picked?.second).isEqualTo("t2")
    }

    @Test
    fun theScheduledWorkout_clearsThePick_ratherThanStandingIn() {
        var picked: Pair<TodayPlan, String?>? = null
        setScreen(
            state = todayPlanWithTemplates,
            actions = Actions(onSubstituteTemplate = { plan, templateId -> picked = plan to templateId }),
        )

        composeTestRule.onNodeWithTag(TestTags.homeSubstitute("slot-1")).performClick()
        composeTestRule.onNodeWithTag(TestTags.HOME_SUBSTITUTE_CLEAR).performClick()

        // Null is "restore the slot's own workout", and the route starts nothing for it.
        assertThat(picked?.first?.slotId).isEqualTo("slot-1")
        assertThat(picked?.second).isNull()
    }

    private companion object {
        val todayPlanWithTemplates = WorkoutsHomeUiState(
            isLoading = false,
            today = DayOfWeek.FRIDAY,
            todaysPlan = listOf(
                TodayPlan(
                    id = "slot-1",
                    templateId = "t1",
                    name = "Heavy lower",
                    exerciseCount = 4,
                    slotId = "slot-1",
                ),
            ),
            templates = listOf(WorkoutTemplate(id = "t2", name = "Dumbbell version", exerciseCount = 3)),
        )
    }
}

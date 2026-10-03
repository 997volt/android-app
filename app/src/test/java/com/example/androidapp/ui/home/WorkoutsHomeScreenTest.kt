package com.example.androidapp.ui.home

import org.junit.Assert.assertEquals
import com.example.androidapp.domain.model.WorkoutTemplate
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
        val onStartTemplate: (String) -> Unit = {},
        val onOpenWorkout: (String) -> Unit = {},
        val onOpenHistory: () -> Unit = {},
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
                    onOpenWorkout = actions.onOpenWorkout,
                    onOpenHistory = actions.onOpenHistory,
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

        assert(started) { "the start button did not reach its callback" }
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

        assert(fromTemplate) { "Start from template did not open the template list" }
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
        assert(exported) { "Export did not reach the transfer action" }

        composeTestRule.onNodeWithTag(TestTags.HOME_MENU).performClick()
        composeTestRule.onNodeWithTag(TestTags.DATA_IMPORT).performClick()
        assert(imported) { "Import did not reach the transfer action" }
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

        assert(opened == "session-1") { "expected the row to open its workout, got $opened" }
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

        assert(openedHistory) { "See all did not open the history" }
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
        val started = mutableListOf<String>()
        setScreen(
            state = WorkoutsHomeUiState(
                isLoading = false,
                today = DayOfWeek.FRIDAY,
                todaysPlans = listOf(
                    WorkoutTemplate(id = "t1", name = "Heavy lower", exerciseCount = 4),
                ),
            ),
            actions = Actions(onStartTemplate = { started += it }),
        )

        composeTestRule.onNodeWithText("Today · Friday").assertExists()
        composeTestRule.onNodeWithText("Heavy lower").assertExists()
        composeTestRule.onNodeWithTag(TestTags.homeStartPlan("t1")).performClick()

        assertEquals(listOf("t1"), started)
    }

    @Test
    fun aDayWithNothingScheduled_showsNoTodaySection() {
        // An empty "Today" heading would be a promise the app cannot keep.
        setScreen(WorkoutsHomeUiState(isLoading = false, today = DayOfWeek.MONDAY))

        composeTestRule.onNodeWithText("Today · Monday").assertDoesNotExist()
    }

    @Test
    fun severalPlansOnADay_areAllListed() {
        setScreen(
            state = WorkoutsHomeUiState(
                isLoading = false,
                today = DayOfWeek.FRIDAY,
                todaysPlans = listOf(
                    WorkoutTemplate(id = "t1", name = "Heavy lower"),
                    WorkoutTemplate(id = "t2", name = "Push"),
                ),
            ),
        )

        composeTestRule.onNodeWithText("Heavy lower").assertExists()
        composeTestRule.onNodeWithText("Push").assertExists()
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
        assertEquals(1, repeated)
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
}

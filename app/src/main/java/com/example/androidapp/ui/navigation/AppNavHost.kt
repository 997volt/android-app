package com.example.androidapp.ui.navigation

import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.Scaffold
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import com.example.androidapp.ui.measurements.MeasurementsRoute
import com.example.androidapp.ui.settings.SettingsRoute
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.androidapp.ui.exercises.ExerciseDetailRoute
import com.example.androidapp.ui.exercises.ExerciseLibraryRoute
import com.example.androidapp.ui.history.WorkoutDetailRoute
import com.example.androidapp.ui.history.WorkoutHistoryRoute
import com.example.androidapp.ui.home.WorkoutsHomeRoute
import com.example.androidapp.ui.programs.ProgramEditorRoute
import com.example.androidapp.ui.programs.ProgramsRoute
import com.example.androidapp.ui.templates.TemplateEditorRoute
import com.example.androidapp.ui.statistics.StatisticsRoute
import com.example.androidapp.ui.templates.TemplatesRoute
import com.example.androidapp.ui.workout.ActiveWorkoutRoute
import com.example.androidapp.ui.workout.ExercisePickerRoute

/**
 * The app's single navigation graph (ROADMAP F2, P1.2).
 *
 * Destinations are registered by route *type*, so [ExerciseDetail]'s
 * `exerciseId` is read back with `SavedStateHandle.toRoute()` in its ViewModel
 * instead of being plucked out of a stringly-typed bundle.
 *
 * The graph is grouped into one extension per area rather than listed flat here:
 * a single function holding every destination stops being readable long before it
 * stops compiling, and the group is usually what a new screen belongs to.
 */
@Composable
fun AppNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination

    // A pushed detail keeps its tab highlighted, which is what makes the bar a map of where you are
    // rather than five buttons that forget the moment you open something.
    var lastTab by rememberSaveable { mutableStateOf(AppTab.WORKOUTS) }
    val tabRoot = AppTab.forRoute(destination?.route)
    LaunchedEffect(tabRoot) { tabRoot?.let { lastTab = it } }

    val showBar = showsTabBar(destination?.route)
    Scaffold(
        // The shell owns the bar's height and nothing else: each screen keeps its own top bar and its own
        // status-bar inset, which is what stops the two from padding the same content twice.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBar) AppTabBar(selected = lastTab, onSelect = navController::switchTab)
        },
        modifier = modifier,
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = WorkoutsHome,
            // Applied once here instead of by every screen (ROADMAP N34).
            modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding()),
        ) {
            homeDestinations(navController)
            workoutDestinations(navController)
            templateDestinations(navController)
            programDestinations(navController)
            historyDestinations(navController)
        }
    }
}

/** Home, the exercise library, one exercise's detail, and the app's settings. */
private fun NavGraphBuilder.homeDestinations(navController: NavHostController) {
    composable<Measurements> {
        MeasurementsRoute(onBack = { navController.popBackStack() })
    }

    composable<Settings> {
        SettingsRoute(onBack = { navController.popBackStack() })
    }

    composable<WorkoutsHome> {
        WorkoutsHomeRoute(
            onStartWorkout = { navController.navigate(ActiveWorkout()) },
            onRepeatLast = { navController.navigate(ActiveWorkout(repeatLast = true)) },
            // The start action's other half: home offers the choice, the template
            // list makes it (ROADMAP N3).
            onStartFromTemplate = { navController.navigate(WorkoutTemplates) },
            // Today's plan starts directly, with the plan's id — the same destination
            // the template list reaches (ROADMAP N16).
            onStartTemplate = { templateId -> navController.navigate(ActiveWorkout(templateId)) },
            onOpenWorkout = { sessionId -> navController.navigate(WorkoutDetail(sessionId)) },
            onOpenHistory = { navController.navigate(WorkoutHistory) },
            // The schedule the today's-plan section is read from (ROADMAP P3.3).
            onOpenPrograms = { navController.navigate(Programs) },
        )
    }

    composable<Statistics> {
        StatisticsRoute(
            // Recording and reading are different jobs, so the measurements screen stays a destination of
            // its own — reached from here, which is the tab that answers how everything is going (N35).
            onOpenMeasurements = { navController.navigate(Measurements) },
        )
    }

    composable<ExerciseLibrary> {
        ExerciseLibraryRoute(
            onExerciseClick = { exerciseId ->
                navController.navigate(ExerciseDetail(exerciseId))
            },
            onOpenHistory = { navController.navigate(WorkoutHistory) },
            onBack = { navController.popBackStack() },
        )
    }

    composable<ExerciseDetail> {
        ExerciseDetailRoute(
            onBack = { navController.popBackStack() },
            // The lift's own trends (ROADMAP N17), reached from the library.
            onOpenTrends = { exerciseId -> navController.navigate(Statistics(exerciseId)) },
        )
    }

}

/** The in-progress workout and the picker it opens. */
private fun NavGraphBuilder.workoutDestinations(navController: NavHostController) {
    composable<ActiveWorkout> {
        ActiveWorkoutRoute(
            onAddExercise = { navController.navigate(ExercisePicker()) },
            onDone = { navController.popBackStack() },
            onBack = { navController.popBackStack() },
        )
    }

    composable<ExercisePicker> {
        ExercisePickerRoute(
            onAddExercise = { navController.popBackStack() },
            onBack = { navController.popBackStack() },
        )
    }
}

/** Templates: the list and one template's editor (ROADMAP N3). */
private fun NavGraphBuilder.templateDestinations(navController: NavHostController) {
    composable<WorkoutTemplates> {
        TemplatesRoute(
            onOpenTemplate = { templateId -> navController.navigate(TemplateEditor(templateId)) },
            // Starting from a template is a navigation, not a write: the workout
            // screen opens the session and seeds it, so seeding cannot happen
            // twice and cannot happen with no screen to show it.
            onStartTemplate = { templateId ->
                navController.navigate(ActiveWorkout(templateId = templateId))
            },
            onBack = { navController.popBackStack() },
        )
    }

    composable<TemplateEditor> {
        TemplateEditorRoute(
            onAddExercise = { templateId ->
                navController.navigate(ExercisePicker(templateId = templateId))
            },
            onBack = { navController.popBackStack() },
        )
    }
}

/** Programs: the list and one program's editor (ROADMAP P3.3). */
private fun NavGraphBuilder.programDestinations(navController: NavHostController) {
    composable<Programs> {
        ProgramsRoute(
            onOpenProgram = { programId -> navController.navigate(ProgramEditor(programId)) },
            onBack = { navController.popBackStack() },
        )
    }

    composable<ProgramEditor> {
        ProgramEditorRoute(onBack = { navController.popBackStack() })
    }
}

/** Finished workouts and one workout's detail. */
private fun NavGraphBuilder.historyDestinations(navController: NavHostController) {    composable<WorkoutHistory> {
        WorkoutHistoryRoute(
            onOpenWorkout = { sessionId -> navController.navigate(WorkoutDetail(sessionId)) },
            onBack = { navController.popBackStack() },
        )
    }

    composable<WorkoutDetail> {
        WorkoutDetailRoute(
            onBack = { navController.popBackStack() },
            // The lift you just did, tapped to see how it is going (ROADMAP N17).
            onOpenExerciseTrends = { exerciseId ->
                navController.navigate(Statistics(exerciseId))
            },
            // Saving a workout as a plan offers to go straight to it (ROADMAP N31) — to the editor,
            // not the list, because the point of the offer is the plan that was just made.
            onOpenTemplate = { templateId -> navController.navigate(TemplateEditor(templateId)) },
        )
    }
}

package com.example.androidapp.ui.navigation

import com.example.androidapp.ui.settings.SettingsRoute
import com.example.androidapp.ui.trends.ExerciseTrendsRoute
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
import com.example.androidapp.ui.templates.TemplateEditorRoute
import com.example.androidapp.ui.trends.TrendsRoute
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
    NavHost(
        navController = navController,
        startDestination = WorkoutsHome,
        modifier = modifier,
    ) {
        homeDestinations(navController)
        workoutDestinations(navController)
        templateDestinations(navController)
        historyDestinations(navController)
    }
}

/** Home, the exercise library, and one exercise's detail. */
private fun NavGraphBuilder.homeDestinations(navController: NavHostController) {
    composable<WorkoutsHome> {
        WorkoutsHomeRoute(
            onStartWorkout = { navController.navigate(ActiveWorkout()) },
            // The start action's other half: home offers the choice, the template
            // list makes it (ROADMAP N3).
            onStartFromTemplate = { navController.navigate(WorkoutTemplates) },
            // Today's plan starts directly, with the plan's id — the same destination
            // the template list reaches (ROADMAP N16).
            onStartTemplate = { templateId -> navController.navigate(ActiveWorkout(templateId)) },
            onOpenWorkout = { sessionId -> navController.navigate(WorkoutDetail(sessionId)) },
            onOpenHistory = { navController.navigate(WorkoutHistory) },
            onOpenLibrary = { navController.navigate(ExerciseLibrary) },
            onOpenTemplates = { navController.navigate(WorkoutTemplates) },
            onOpenTrends = { navController.navigate(WorkoutTrends) },
            onOpenSettings = { navController.navigate(Settings) },
        )
    }

    composable<WorkoutTrends> {
        TrendsRoute(onBack = { navController.popBackStack() })
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
            onOpenTrends = { exerciseId -> navController.navigate(ExerciseTrends(exerciseId)) },
        )
    }

    composable<ExerciseTrends> {
        ExerciseTrendsRoute(onBack = { navController.popBackStack() })
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

/** Finished workouts and one workout's detail. */
private fun NavGraphBuilder.historyDestinations(navController: NavHostController) {
    composable<Settings> {
        SettingsRoute(onBack = { navController.popBackStack() })
    }

    composable<WorkoutHistory> {
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
                navController.navigate(ExerciseTrends(exerciseId))
            },
        )
    }
}

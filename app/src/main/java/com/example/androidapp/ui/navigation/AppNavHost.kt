package com.example.androidapp.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.androidapp.ui.exercises.ExerciseDetailRoute
import com.example.androidapp.ui.exercises.ExerciseLibraryRoute
import com.example.androidapp.ui.history.WorkoutDetailRoute
import com.example.androidapp.ui.history.WorkoutHistoryRoute
import com.example.androidapp.ui.workout.ActiveWorkoutRoute
import com.example.androidapp.ui.workout.ExercisePickerRoute

/**
 * The app's single navigation graph (ROADMAP F2, P1.2).
 *
 * Destinations are registered by route *type*, so [ExerciseDetail]'s
 * `exerciseId` is read back with `SavedStateHandle.toRoute()` in its ViewModel
 * instead of being plucked out of a stringly-typed bundle.
 */
@Composable
fun AppNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    NavHost(
        navController = navController,
        startDestination = ExerciseLibrary,
        modifier = modifier,
    ) {
        composable<ExerciseLibrary> {
            ExerciseLibraryRoute(
                onExerciseClick = { exerciseId ->
                    navController.navigate(ExerciseDetail(exerciseId))
                },
                onStartWorkout = { navController.navigate(ActiveWorkout) },
                onOpenHistory = { navController.navigate(WorkoutHistory) },
            )
        }

        composable<ExerciseDetail> {
            ExerciseDetailRoute(onBack = { navController.popBackStack() })
        }

        composable<ActiveWorkout> {
            ActiveWorkoutRoute(
                onAddExercise = { navController.navigate(ExercisePicker) },
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

        composable<WorkoutHistory> {
            WorkoutHistoryRoute(
                onOpenWorkout = { sessionId -> navController.navigate(WorkoutDetail(sessionId)) },
                onBack = { navController.popBackStack() },
            )
        }

        composable<WorkoutDetail> {
            WorkoutDetailRoute(onBack = { navController.popBackStack() })
        }
    }
}

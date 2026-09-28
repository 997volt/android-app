package com.example.androidapp.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.androidapp.ui.exercises.ExerciseDetailRoute
import com.example.androidapp.ui.exercises.ExerciseLibraryRoute

/**
 * The app's single navigation graph (ROADMAP F2).
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
            )
        }

        composable<ExerciseDetail> {
            ExerciseDetailRoute(onBack = { navController.popBackStack() })
        }
    }
}

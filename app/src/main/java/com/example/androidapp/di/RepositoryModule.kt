package com.example.androidapp.di

import com.example.androidapp.data.RoomExerciseRepository
import com.example.androidapp.domain.repository.ExerciseRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds repository interfaces to their implementations (ROADMAP F4, F5).
 *
 * The move from an in-memory library to Room was a one-line change here plus a
 * new implementation — no ViewModel or composable moved. That is the seam
 * paying for itself.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindExerciseRepository(impl: RoomExerciseRepository): ExerciseRepository
}

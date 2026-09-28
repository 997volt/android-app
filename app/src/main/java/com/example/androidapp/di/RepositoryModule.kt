package com.example.androidapp.di

import com.example.androidapp.data.InMemoryExerciseRepository
import com.example.androidapp.domain.repository.ExerciseRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds repository interfaces to their implementations (ROADMAP F4).
 *
 * Swapping the exercise library onto Room (F5) means changing one line here —
 * the single seam that makes that migration cheap.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindExerciseRepository(impl: InMemoryExerciseRepository): ExerciseRepository
}

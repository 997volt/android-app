package com.example.androidapp.di

import com.example.androidapp.data.RoomBackupRepository
import com.example.androidapp.data.RoomExerciseRepository
import com.example.androidapp.data.RoomWorkoutRepository
import com.example.androidapp.domain.repository.BackupRepository
import com.example.androidapp.domain.repository.ExerciseRepository
import com.example.androidapp.domain.repository.WorkoutRepository
import com.example.androidapp.domain.RestNotifier
import com.example.androidapp.platform.RestAlarmScheduler
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

    @Binds
    @Singleton
    abstract fun bindWorkoutRepository(impl: RoomWorkoutRepository): WorkoutRepository

    /** Backup/restore is a repository like any other (P1.12). */
    @Binds
    @Singleton
    abstract fun bindBackupRepository(impl: RoomBackupRepository): BackupRepository

    /** The rest-timer alert is an output port, bound to its Android implementation. */
    @Binds
    @Singleton
    abstract fun bindRestNotifier(impl: RestAlarmScheduler): RestNotifier
}

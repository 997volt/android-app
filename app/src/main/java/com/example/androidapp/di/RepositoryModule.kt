package com.example.androidapp.di

import com.example.androidapp.domain.repository.StatisticsRepository
import com.example.androidapp.data.RoomStatisticsRepository
import com.example.androidapp.domain.repository.MeasurementRepository
import com.example.androidapp.data.RoomMeasurementRepository
import com.example.androidapp.data.RoomBackupRepository
import com.example.androidapp.data.RoomExerciseRepository
import com.example.androidapp.data.RoomAdherenceRepository
import com.example.androidapp.data.RoomProgramRepository
import com.example.androidapp.data.RoomTemplateRepository
import com.example.androidapp.data.RoomTrendsRepository
import com.example.androidapp.data.RoomWorkoutRepository
import com.example.androidapp.domain.repository.BackupRepository
import com.example.androidapp.domain.repository.ExerciseRepository
import com.example.androidapp.domain.repository.AdherenceRepository
import com.example.androidapp.domain.repository.ProgramRepository
import com.example.androidapp.domain.repository.TemplateRepository
import com.example.androidapp.domain.repository.TrendsRepository
import com.example.androidapp.domain.repository.WorkoutRepository
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

    /** Measurements are their own store, and they never read training data (ROADMAP N32). */
    @Binds
    @Singleton
    abstract fun bindMeasurementRepository(
        impl: RoomMeasurementRepository,
    ): MeasurementRepository

    /** Statistics reads history to summarise it, which is its own concern (ROADMAP N35). */
    @Binds
    @Singleton
    abstract fun bindStatisticsRepository(
        impl: RoomStatisticsRepository,
    ): StatisticsRepository

    /** Templates and their exercises (N3). */
    @Binds
    @Singleton
    abstract fun bindTemplateRepository(impl: RoomTemplateRepository): TemplateRepository

    /** Programs: templates in an order, with the days they fall on (P3.3). */
    @Binds
    @Singleton
    abstract fun bindProgramRepository(impl: RoomProgramRepository): ProgramRepository

    /** How often the plan happened, and the corrections to it (P3.5, P3.13). */
    @Binds
    @Singleton
    abstract fun bindAdherenceRepository(impl: RoomAdherenceRepository): AdherenceRepository

    /** The read-only trends over what the app collects (N13). */
    @Binds
    @Singleton
    abstract fun bindTrendsRepository(impl: RoomTrendsRepository): TrendsRepository

    /** Backup/restore is a repository like any other (P1.12). */
    @Binds
    @Singleton
    abstract fun bindBackupRepository(impl: RoomBackupRepository): BackupRepository

}

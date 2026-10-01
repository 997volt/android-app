package com.example.androidapp.di

import com.example.androidapp.data.PreferencesSettingsRepository
import com.example.androidapp.domain.repository.SettingsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * The settings store's binding (ROADMAP B31).
 *
 * Its own file because `DatabaseModule` says database, and this binds a `SharedPreferences`
 * repository — a name that lies is worse than a file that is short.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class SettingsModule {
    @Binds
    @Singleton
    abstract fun bindSettingsRepository(
        implementation: PreferencesSettingsRepository,
    ): SettingsRepository
}

package com.example.androidapp.di

import com.example.androidapp.domain.TimeSource
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Instant
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object TimeModule {

    /** Production clock. Tests inject a fixed [TimeSource] instead. */
    @Provides
    @Singleton
    fun provideTimeSource(): TimeSource = TimeSource { Instant.now() }
}

package com.example.androidapp.di

import com.example.androidapp.domain.TimeSource
import com.example.androidapp.domain.ZoneOffsetSource
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Instant
import java.time.OffsetDateTime
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object TimeModule {

    /** Production clock. Tests inject a fixed [TimeSource] instead. */
    @Provides
    @Singleton
    fun provideTimeSource(): TimeSource = TimeSource { Instant.now() }

    /**
     * The device's current offset (ROADMAP N25).
     *
     * Read once, when a session opens, and stored on that row — so a session keeps the zone it was
     * performed in even after the phone comes home.
     */
    @Provides
    @Singleton
    fun provideZoneOffsetSource(): ZoneOffsetSource =
        ZoneOffsetSource { OffsetDateTime.now().offset.totalSeconds / SECONDS_PER_MINUTE }
}

/** Both offsets and durations here are in whole minutes. */
private const val SECONDS_PER_MINUTE = 60

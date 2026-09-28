package com.example.androidapp.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.androidapp.data.local.ALL_MIGRATIONS
import com.example.androidapp.data.local.WorkoutDatabase
import com.example.androidapp.data.local.seedMissingExercises
import com.example.androidapp.domain.TimeSource
import com.example.androidapp.domain.nowEpochMillis
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    // Room takes migrations as a vararg, so the array must be spread — the
    // suppressed copy is a handful of Migration objects, built once per process.
    @Suppress("SpreadOperator")
    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
        timeSource: TimeSource,
    ): WorkoutDatabase =
        Room.databaseBuilder(context, WorkoutDatabase::class.java, WorkoutDatabase.NAME)
            // No fallbackToDestructiveMigration: a forgotten migration must fail
            // loudly rather than silently wipe a user's training history.
            .addMigrations(*ALL_MIGRATIONS)
            .addCallback(
                object : RoomDatabase.Callback() {
                    /**
                     * Tops up the seed library on **every** open, not just creation
                     * (ROADMAP F15).
                     *
                     * `onCreate` fires once per database file, so seeding there meant a
                     * later release could never deliver new exercises to an existing
                     * install. `onOpen` runs after `onCreate` on a first open and on every
                     * open after that, so one path covers first launch and upgrade alike.
                     *
                     * Running synchronously on this connection is also the fix for the
                     * first-launch flash: dispatching the insert to a coroutine raced the
                     * first read, so `observeExercises()` emitted an empty library and the
                     * UI cannot tell that apart from "nothing matched your search".
                     */
                    override fun onOpen(db: SupportSQLiteDatabase) {
                        super.onOpen(db)
                        seedMissingExercises(db, timeSource.nowEpochMillis())
                    }
                },
            )
            .build()
}

package com.example.androidapp.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.androidapp.data.SeedExercises
import com.example.androidapp.data.local.ALL_MIGRATIONS
import com.example.androidapp.data.local.WorkoutDatabase
import com.example.androidapp.data.local.toEntity
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

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
        @ApplicationScope scope: CoroutineScope,
    ): WorkoutDatabase {
        lateinit var database: WorkoutDatabase

        database = Room.databaseBuilder(context, WorkoutDatabase::class.java, WorkoutDatabase.NAME)
            // No fallbackToDestructiveMigration: a forgotten migration must fail
            // loudly rather than silently wipe a user's training history.
            .addMigrations(*ALL_MIGRATIONS)
            .addCallback(
                object : RoomDatabase.Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        // onCreate runs on the connection thread while the database
                        // is still being opened, so the write is dispatched rather
                        // than run inline — a Room query on this thread deadlocks.
                        //
                        // Seeding here (instead of shipping a prepackaged .db in
                        // assets) avoids a build step that generates the file and a
                        // second source of truth that goes stale whenever the schema
                        // changes. It costs milliseconds, once, on first launch.
                        //
                        // The timestamp is captured once so every seeded row shares
                        // a createdAt, which makes the seed recognisable as a batch.
                        val seededAt = System.currentTimeMillis()
                        scope.launch {
                            database.exerciseDao()
                                .insertAll(SeedExercises.all.map { it.toEntity(seededAt) })
                        }
                    }
                },
            )
            .build()

        return database
    }
}

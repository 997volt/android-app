package com.example.androidapp.data

import androidx.core.content.edit
import android.content.Context
import android.content.SharedPreferences
import com.example.androidapp.domain.DataError
import com.example.androidapp.domain.DataResult
import com.example.androidapp.domain.RestTimer
import com.example.androidapp.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Settings in `SharedPreferences` (ROADMAP N21).
 *
 * Chosen over DataStore because it needs **no new dependency**: what lives here is a
 * handful of integers and booleans owned by one process, which is the case
 * `SharedPreferences` is still the right tool for. If settings ever need to hold a
 * collection, a schema or a migration, that is the moment to move.
 *
 * Writes are applied with `commit` inside the caller's coroutine rather than `apply`: the
 * caller is a screen that reports success or failure, and a fire-and-forget write would let
 * it claim "saved" about something that did not reach disk (this is `DataResult`, not a
 * hoped-for success).
 */
@Singleton
class PreferencesSettingsRepository @Inject constructor(
    @ApplicationContext context: Context,
) : SettingsRepository {

    private val preferences: SharedPreferences =
        context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    override fun observeDefaultRestSeconds(): Flow<Int> = callbackFlow {
        // The current value first, so a collector never has to wait for a change to render.
        trySend(currentRestSeconds())

        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_DEFAULT_REST_SECONDS) trySend(currentRestSeconds())
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }.conflate().distinctUntilChanged()

    override fun observeRestCueEnabled(): Flow<Boolean> = observeFlag(KEY_REST_CUE_ENABLED, true)

    override suspend fun setRestCueEnabled(enabled: Boolean): DataResult<Unit> =
        writeFlag(KEY_REST_CUE_ENABLED, enabled)

    override fun observeKeepScreenOn(): Flow<Boolean> = observeFlag(KEY_KEEP_SCREEN_ON, true)

    override suspend fun setKeepScreenOn(enabled: Boolean): DataResult<Unit> =
        writeFlag(KEY_KEEP_SCREEN_ON, enabled)

    /** A boolean preference, defaulted rather than null: these flags have always had a meaning. */
    private fun observeFlag(key: String, default: Boolean): Flow<Boolean> = callbackFlow {
        trySend(preferences.getBoolean(key, default))
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, changed ->
            if (changed == key) trySend(preferences.getBoolean(key, default))
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }.conflate().distinctUntilChanged()

    private fun writeFlag(key: String, value: Boolean): DataResult<Unit> {
        preferences.edit(commit = true) { putBoolean(key, value) }
        return if (preferences.getBoolean(key, !value) == value) {
            DataResult.Success(Unit)
        } else {
            DataResult.Failure(DataError.Storage(IllegalStateException("the setting was not stored")))
        }
    }

    override suspend fun setDefaultRestSeconds(seconds: Int): DataResult<Unit> =
        if (seconds !in SettingsRepository.VALID_REST_SECONDS) {
            DataResult.Failure(DataError.Invalid("a rest must be between 5 seconds and an hour"))
        } else {
            // Synchronous on purpose: the caller shows "saved" or an error, and a
            // fire-and-forget write would let it claim a success that never reached disk.
            // Committed, not applied: the caller reports success or failure, and a
            // fire-and-forget write would let it claim a success that never reached disk.
            // The KTX `edit` returns Unit, so the write is confirmed by reading it back.
            preferences.edit(commit = true) { putInt(KEY_DEFAULT_REST_SECONDS, seconds) }
            if (currentRestSeconds() == seconds) {
                DataResult.Success(Unit)
            } else {
                DataResult.Failure(
                    DataError.Storage(IllegalStateException("the setting was not stored")),
                )
            }
        }

    private fun currentRestSeconds(): Int = preferences.getInt(
        KEY_DEFAULT_REST_SECONDS,
        // The value the app shipped with before this screen existed, so an upgrade changes
        // nothing for someone who never opens settings.
        RestTimer.DEFAULT_SECONDS,
    )

    private companion object {
        const val FILE_NAME = "settings"
        const val KEY_DEFAULT_REST_SECONDS = "default_rest_seconds"
        const val KEY_REST_CUE_ENABLED = "rest_cue_enabled"
        const val KEY_KEEP_SCREEN_ON = "keep_screen_on"
    }
}

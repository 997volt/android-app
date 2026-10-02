package com.example.androidapp.domain.repository

import com.example.androidapp.domain.DataResult
import kotlinx.coroutines.flow.Flow

/**
 * The choices that apply to the whole app rather than to one workout (ROADMAP N21).
 *
 * Small on purpose: the app is local-only and has no account, so "settings" here means the
 * handful of preferences that change how it behaves for this person on this phone. The
 * default rest is the first, because it was a hardcoded 90 seconds with no way to change it.
 */
interface SettingsRepository {

    /**
     * The rest the app starts when an exercise prescribes none, in seconds (ROADMAP N5).
     *
     * A `Flow` rather than a value: a change made in settings must reach a workout that is
     * already open, and the workout screen is not restarted to pick it up.
     */
    fun observeDefaultRestSeconds(): Flow<Int>

    /** Stores the default rest for future sets. Rejects a value outside [VALID_REST_SECONDS]. */
    suspend fun setDefaultRestSeconds(seconds: Int): DataResult<Unit>

    /**
     * Whether a finished rest should be **heard** as well as seen (ROADMAP N27).
     *
     * With the background alert gone this is the cue that replaces it, so it is on by default: the
     * app would otherwise be quieter than it was, which is a regression dressed as a preference.
     * It plays a tone in-process and uses view-level haptics — neither asks for a permission, which
     * is what N26 bought and DECISIONS.md records.
     */
    fun observeRestCueEnabled(): Flow<Boolean>

    suspend fun setRestCueEnabled(enabled: Boolean): DataResult<Unit>

    /**
     * Whether a workout keeps the screen awake (ROADMAP N27).
     *
     * A window flag rather than a wake lock: the screen stays on while the app is in front, and
     * nothing is held once it is not.
     */
    fun observeKeepScreenOn(): Flow<Boolean>

    suspend fun setKeepScreenOn(enabled: Boolean): DataResult<Unit>

    companion object {
        /**
         * What the setting accepts, in seconds.
         *
         * Bounded rather than free-form: zero or a negative rest is not a setting, it is a bug waiting
         * to be entered, and a rest longer than the session itself is a mistake rather than a
         * preference. (This used to argue from the value becoming an alarm; the alert is gone
         * (ROADMAP N26), and the bounds are still right — they are about what a rest means.)
         */
        val VALID_REST_SECONDS = 5..3600
    }
}

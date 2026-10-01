package com.example.androidapp.data

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.androidapp.domain.DataError
import com.example.androidapp.domain.DataResult
import com.example.androidapp.domain.RestTimer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The settings store (ROADMAP B23).
 *
 * None of this was covered: N21 shipped the store, the screen and its ViewModel with no test in
 * either source set, so the round trip through `SharedPreferences` — the part that decides whether
 * a preference survives the app closing — had never been exercised.
 *
 * Robolectric rather than a device: preferences are a file and a map, and the interesting failures
 * are about what is written, not about Android.
 */
@RunWith(AndroidJUnit4::class)
class PreferencesSettingsRepositoryTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun repository() = PreferencesSettingsRepository(context)

    @Test
    fun anUnsetPreference_isTheValueTheAppShippedWith() = runTest {
        // An upgrade must not change the rest for someone who never opens settings.
        context.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE)
            .edit().clear().commit()

        assertEquals(RestTimer.DEFAULT_SECONDS, repository().observeDefaultRestSeconds().first())
    }

    @Test
    fun aStoredValue_comesBack() = runTest {
        val repository = repository()

        assertTrue(repository.setDefaultRestSeconds(45) is DataResult.Success)

        assertEquals(45, repository.observeDefaultRestSeconds().first())
    }

    @Test
    fun aValueOutsideWhatIsSensible_isRefused_andNothingIsWritten() = runTest {
        // The value becomes an alarm: a typed zero would fire instantly, and a negative rest is
        // not a setting at all. Refusing must also mean *not writing*, or the screen would report
        // a failure while the store quietly held it.
        val repository = repository()
        repository.setDefaultRestSeconds(60)

        val refused = repository.setDefaultRestSeconds(0)

        assertTrue(refused is DataResult.Failure)
        assertTrue((refused as DataResult.Failure).error is DataError.Invalid)
        assertEquals("the stored value is untouched", 60, repository.observeDefaultRestSeconds().first())
    }

    @Test
    fun anotherInstanceSeesWhatWasWritten() = runTest {
        // The point of a preference over in-memory state: it survives the object that wrote it.
        repository().setDefaultRestSeconds(120)

        assertEquals(120, repository().observeDefaultRestSeconds().first())
    }
}

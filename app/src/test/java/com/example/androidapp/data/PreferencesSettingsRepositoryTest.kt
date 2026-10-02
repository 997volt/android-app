package com.example.androidapp.data

import org.junit.Assert.assertNull
import java.time.LocalDate
import com.example.androidapp.domain.model.StatisticsRange
import com.example.androidapp.domain.model.RangeKind
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
    fun anUnsetStatisticsRange_isSevenDays() = runTest {
        // A preference that was never written must not make the screen blank on first open.
        context.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE)
            .edit().clear().commit()

        assertEquals(StatisticsRange(RangeKind.LAST_7_DAYS), repository().observeStatisticsRange().first())
    }

    @Test
    fun aStoredRange_comesBack() = runTest {
        val repository = repository()
        val custom = StatisticsRange(
            kind = RangeKind.CUSTOM,
            from = LocalDate.of(2026, 8, 1),
            to = LocalDate.of(2026, 8, 31),
        )

        assertTrue(repository.setStatisticsRange(custom) is DataResult.Success)

        assertEquals(custom, repository.observeStatisticsRange().first())
    }

    @Test
    fun aRangeWhoseDatesAreNotBothChosen_comesBackThatWay() = runTest {
        val repository = repository()
        val half = StatisticsRange(kind = RangeKind.CUSTOM, from = LocalDate.of(2026, 8, 1), to = null)

        assertTrue(repository.setStatisticsRange(half) is DataResult.Success)

        // The window treats a missing end as "nothing excluded"; the store must not invent one.
        assertEquals(half, repository.observeStatisticsRange().first())
    }

    @Test
    fun aRollingRange_keepsNoDates() = runTest {
        // What is stored is the kind. A From–To left behind on "last 7 days" would be a second source of
        // truth waiting to disagree with it — and would come back the moment the kind changed again.
        val repository = repository()
        repository.setStatisticsRange(
            StatisticsRange(RangeKind.CUSTOM, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31)),
        )

        assertTrue(repository.setStatisticsRange(StatisticsRange(RangeKind.LAST_7_DAYS)) is DataResult.Success)

        val stored = repository.observeStatisticsRange().first()
        assertEquals(RangeKind.LAST_7_DAYS, stored.kind)
        assertNull(stored.from)
        assertNull(stored.to)
    }

    @Test
    fun aKindThisBuildDoesNotKnow_fallsBackToTheDefault() = runTest {
        // Enums are stored by name, so a name from a future build is readable rather than a number that
        // means something else. It resolves to the default instead of throwing at a screen nobody can use.
        context.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE)
            .edit().clear().putString("statistics_range_kind", "LAST_DECADE").commit()

        assertEquals(RangeKind.LAST_7_DAYS, repository().observeStatisticsRange().first().kind)
    }

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

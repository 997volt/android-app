package com.example.androidapp.data

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
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

        assertThat(repository().observeStatisticsRange().first()).isEqualTo(StatisticsRange(RangeKind.LAST_7_DAYS))
    }

    @Test
    fun aStoredRange_comesBack() = runTest {
        val repository = repository()
        val custom = StatisticsRange(
            kind = RangeKind.CUSTOM,
            from = LocalDate.of(2026, 8, 1),
            to = LocalDate.of(2026, 8, 31),
        )

        assertThat(repository.setStatisticsRange(custom) is DataResult.Success).isTrue()

        assertThat(repository.observeStatisticsRange().first()).isEqualTo(custom)
    }

    @Test
    fun aRangeWhoseDatesAreNotBothChosen_comesBackThatWay() = runTest {
        val repository = repository()
        val half = StatisticsRange(kind = RangeKind.CUSTOM, from = LocalDate.of(2026, 8, 1), to = null)

        assertThat(repository.setStatisticsRange(half) is DataResult.Success).isTrue()

        // The window treats a missing end as "nothing excluded"; the store must not invent one.
        assertThat(repository.observeStatisticsRange().first()).isEqualTo(half)
    }

    @Test
    fun aRollingRange_keepsNoDates() = runTest {
        // What is stored is the kind. A From–To left behind on "last 7 days" would be a second source of
        // truth waiting to disagree with it — and would come back the moment the kind changed again.
        val repository = repository()
        repository.setStatisticsRange(
            StatisticsRange(RangeKind.CUSTOM, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31)),
        )

        assertThat(repository.setStatisticsRange(StatisticsRange(RangeKind.LAST_7_DAYS)) is DataResult.Success).isTrue()

        val stored = repository.observeStatisticsRange().first()
        assertThat(stored.kind).isEqualTo(RangeKind.LAST_7_DAYS)
        assertThat(stored.from).isNull()
        assertThat(stored.to).isNull()
    }

    @Test
    fun aKindThisBuildDoesNotKnow_fallsBackToTheDefault() = runTest {
        // Enums are stored by name, so a name from a future build is readable rather than a number that
        // means something else. It resolves to the default instead of throwing at a screen nobody can use.
        context.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE)
            .edit().clear().putString("statistics_range_kind", "LAST_DECADE").commit()

        assertThat(repository().observeStatisticsRange().first().kind).isEqualTo(RangeKind.LAST_7_DAYS)
    }

    @Test
    fun anUnsetPreference_isTheValueTheAppShippedWith() = runTest {
        // An upgrade must not change the rest for someone who never opens settings.
        context.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE)
            .edit().clear().commit()

        assertThat(repository().observeDefaultRestSeconds().first()).isEqualTo(RestTimer.DEFAULT_SECONDS)
    }

    @Test
    fun aStoredValue_comesBack() = runTest {
        val repository = repository()

        assertThat(repository.setDefaultRestSeconds(45) is DataResult.Success).isTrue()

        assertThat(repository.observeDefaultRestSeconds().first()).isEqualTo(45)
    }

    @Test
    fun aValueOutsideWhatIsSensible_isRefused_andNothingIsWritten() = runTest {
        // The value becomes an alarm: a typed zero would fire instantly, and a negative rest is
        // not a setting at all. Refusing must also mean *not writing*, or the screen would report
        // a failure while the store quietly held it.
        val repository = repository()
        repository.setDefaultRestSeconds(60)

        val refused = repository.setDefaultRestSeconds(0)

        assertThat(refused is DataResult.Failure).isTrue()
        assertThat((refused as DataResult.Failure).error is DataError.Invalid).isTrue()
        assertWithMessage("the stored value is untouched").that(repository.observeDefaultRestSeconds().first())
            .isEqualTo(60)
    }

    @Test
    fun anotherInstanceSeesWhatWasWritten() = runTest {
        // The point of a preference over in-memory state: it survives the object that wrote it.
        repository().setDefaultRestSeconds(120)

        assertThat(repository().observeDefaultRestSeconds().first()).isEqualTo(120)
    }
}

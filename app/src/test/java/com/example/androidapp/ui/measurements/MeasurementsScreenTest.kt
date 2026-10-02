package com.example.androidapp.ui.measurements

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.androidapp.domain.model.BodyMeasurement
import com.example.androidapp.domain.model.TapeSite
import com.example.androidapp.ui.components.TestTags
import com.example.androidapp.ui.theme.AndroidAppTheme
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The measurements screen's rules (ROADMAP N32).
 *
 * Matched by tag rather than by English, so a translation cannot break them.
 */
@RunWith(AndroidJUnit4::class)
class MeasurementsScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val today = BodyMeasurement(
        id = "m1",
        measuredAt = Instant.parse("2026-10-02T08:00:00Z"),
        weightGrams = 82_400L,
        bodyFatTenths = 183,
        tape = mapOf(TapeSite.WAIST to 864L),
    )

    private fun setScreen(
        state: MeasurementsUiState = MeasurementsUiState(isLoading = false, entries = listOf(today)),
        onSave: (BodyMeasurement) -> Unit = {},
        onDelete: (String) -> Unit = {},
    ) {
        composeTestRule.setContent {
            AndroidAppTheme {
                MeasurementsScreen(
                    state = state,
                    onSave = onSave,
                    onDelete = onDelete,
                    onBack = {},
                )
            }
        }
    }

    @Test
    fun withNothingRecorded_itSaysSo() {
        setScreen(state = MeasurementsUiState(isLoading = false))

        // The empty state is the whole point of the list being empty rather than blank.
        composeTestRule.onNodeWithTag(TestTags.Measurements.ADD).assertIsEnabled()
    }

    @Test
    fun anEntryCanBeDeleted_byItsOwnId() {
        var deleted: String? = null
        setScreen(onDelete = { deleted = it })

        composeTestRule.onNodeWithTag(TestTags.Measurements.delete("m1")).performClick()

        assertEquals("m1", deleted)
    }

    @Test
    fun theSaveIsGatedOnAWeight() {
        setScreen()
        composeTestRule.onNodeWithTag(TestTags.Measurements.ADD).performClick()

        // The one thing an entry cannot be without.
        composeTestRule.onNodeWithTag(TestTags.Measurements.CONFIRM).assertIsNotEnabled()
        composeTestRule.onNodeWithTag(TestTags.Measurements.WEIGHT).performTextInput("82.4")
        composeTestRule.onNodeWithTag(TestTags.Measurements.CONFIRM).assertIsEnabled()
    }

    @Test
    fun theOptionalNumbers_stayOptional() {
        var saved: BodyMeasurement? = null
        setScreen(onSave = { saved = it })
        composeTestRule.onNodeWithTag(TestTags.Measurements.ADD).performClick()
        composeTestRule.onNodeWithTag(TestTags.Measurements.WEIGHT).performTextInput("82.4")

        composeTestRule.onNodeWithTag(TestTags.Measurements.CONFIRM).performClick()

        val entry = saved
        assertTrue("the entry reached the screen's callback", entry != null)
        assertEquals(82_400L, entry!!.weightGrams)
        // Nothing else was asked for, so nothing else is claimed: null, not zero.
        assertNull("body fat was not measured", entry.bodyFatTenths)
        assertNull("muscle was not measured", entry.muscleTenths)
        assertTrue("no tape site was measured", entry.tape.isEmpty())
    }

    @Test
    fun aTapeSiteIsRecorded_whenItIsFilledIn() {
        var saved: BodyMeasurement? = null
        setScreen(onSave = { saved = it })
        composeTestRule.onNodeWithTag(TestTags.Measurements.ADD).performClick()
        composeTestRule.onNodeWithTag(TestTags.Measurements.WEIGHT).performTextInput("82.4")
        composeTestRule.onNodeWithTag(TestTags.Measurements.tape(TapeSite.WAIST)).performTextInput("86.4")

        composeTestRule.onNodeWithTag(TestTags.Measurements.CONFIRM).performClick()

        assertEquals(864L, saved?.tape?.get(TapeSite.WAIST))
    }

    @Test
    fun aFieldThatIsNotARealNumber_isNotStored() {
        // `toDoubleOrNull` accepts "NaN" and "Infinity", and `Math.round(NaN * 10)` is 0 — so a typed "NaN"
        // became a real 0.0% body-fat reading, a measurement nobody took, in a series that then draws it.
        var saved: BodyMeasurement? = null
        setScreen(onSave = { saved = it })
        composeTestRule.onNodeWithTag(TestTags.Measurements.ADD).performClick()
        composeTestRule.onNodeWithTag(TestTags.Measurements.WEIGHT).performTextInput("82.4")
        composeTestRule.onNodeWithTag(TestTags.Measurements.BODY_FAT).performTextInput("NaN")
        composeTestRule.onNodeWithTag(TestTags.Measurements.tape(TapeSite.WAIST)).performTextInput("Infinity")

        composeTestRule.onNodeWithTag(TestTags.Measurements.CONFIRM).performClick()

        assertNull("a NaN body fat is not a measurement", saved?.bodyFatTenths)
        assertTrue("an infinite tape reading is not one either", saved?.tape?.isEmpty() != false)
    }

    @Test
    fun aNegativeMeasurement_isNotStored() {
        // Nothing this screen collects is below zero, and a negative would draw as a real reading.
        var saved: BodyMeasurement? = null
        setScreen(onSave = { saved = it })
        composeTestRule.onNodeWithTag(TestTags.Measurements.ADD).performClick()
        composeTestRule.onNodeWithTag(TestTags.Measurements.WEIGHT).performTextInput("82.4")
        composeTestRule.onNodeWithTag(TestTags.Measurements.BODY_FAT).performTextInput("-5")

        composeTestRule.onNodeWithTag(TestTags.Measurements.CONFIRM).performClick()

        assertNull("no body fat below zero", saved?.bodyFatTenths)
    }
}

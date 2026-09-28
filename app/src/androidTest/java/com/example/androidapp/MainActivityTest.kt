package com.example.androidapp

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented UI test. Requires a connected device or a running emulator:
 *   ./gradlew connectedDebugAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class MainActivityTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun incrementButton_updatesCounter() {
        composeTestRule.onNodeWithText("0").assertIsDisplayed()

        composeTestRule.onNodeWithText("Increment").performClick()

        composeTestRule.onNodeWithText("1").assertIsDisplayed()
    }
}

package com.example.androidapp.ui

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.androidapp.ui.theme.AndroidAppTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Proves Compose UI tests run on the JVM here (ROADMAP P1.17).
 *
 * `AndroidJUnit4` delegates to Robolectric on the JVM classpath, so the same test
 * body can run on a device or locally — which is the point: a UI assertion should
 * cost seconds, not a 26-minute CI emulator cycle.
 *
 * Kept deliberately trivial. If this fails, the setup is wrong; if a real test
 * fails, the test is wrong. Those are different problems and this keeps them apart.
 */
@RunWith(AndroidJUnit4::class)
class RobolectricSmokeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun composeRendersOnTheJvm() {
        composeTestRule.setContent {
            AndroidAppTheme { Text("hello from the jvm") }
        }

        composeTestRule.onNodeWithText("hello from the jvm").assertIsDisplayed()
    }
}

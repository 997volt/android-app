package com.example.androidapp

import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Box
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.androidapp.ui.navigation.AppNavHost
import com.example.androidapp.ui.theme.AndroidAppTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * The app's single activity.
 *
 * It owns no UI state of its own: the navigation graph (F2) decides which
 * screen is showing and each screen's ViewModel (F3) owns its state. Keeping
 * the activity this thin is what lets every screen be tested without it.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AndroidAppTheme {
                // Test tags as resource ids, so a device-side tool can address a control by *identity* rather
                // than by coordinates: a tap then either lands on the control or fails, instead of silently
                // hitting its neighbour. It exposes the tags to accessibility tooling, which is the point —
                // without it a Compose app is an opaque tree of anonymous Views to everything outside the
                // process, and the last two rounds were spent guessing offsets because of it.
                Box(modifier = Modifier.semantics { testTagsAsResourceId = true }) {
                    AppNavHost()
                }
            }
        }
    }
}

package com.example.androidapp

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
                AppNavHost()
            }
        }
    }
}

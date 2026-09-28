package com.example.androidapp

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Application entry point. [HiltAndroidApp] triggers Hilt's code generation and
 * creates the application-level dependency container that `@AndroidEntryPoint`
 * activities and `@HiltViewModel` ViewModels are injected from (ROADMAP F4).
 */
@HiltAndroidApp
class AndroidApp : Application()

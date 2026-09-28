package com.example.androidapp.di

import javax.inject.Qualifier

/**
 * A [kotlinx.coroutines.CoroutineScope] that lives as long as the process, for
 * work that must outlive any screen — seeding the database, for example.
 *
 * Distinct from a ViewModel's `viewModelScope`, which is cancelled when the
 * screen goes away.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

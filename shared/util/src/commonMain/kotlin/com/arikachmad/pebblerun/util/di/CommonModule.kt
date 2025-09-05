package com.arikachmad.pebblerun.util.di

import org.koin.core.module.Module
import org.koin.dsl.module
import com.arikachmad.pebblerun.storage.DatabaseDriverFactory
import com.arikachmad.pebblerun.storage.WorkoutDatabase

/**
 * Common DI module for shared dependencies
 * Supports PAT-004 (Dependency injection with platform-specific modules)
 */
val commonModule = module {
    
    // Database
    single<WorkoutDatabase> {
        WorkoutDatabase(get<DatabaseDriverFactory>().createDriver())
    }
    
}

/**
 * Expected platform-specific module that provides platform dependencies
 */
expect val platformModule: Module

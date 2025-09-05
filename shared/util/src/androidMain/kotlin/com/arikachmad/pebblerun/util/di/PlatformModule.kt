package com.arikachmad.pebblerun.util.di

import org.koin.core.module.Module
import org.koin.dsl.module
import com.arikachmad.pebblerun.storage.DatabaseDriverFactory

/**
 * Android-specific DI module
 */
actual val platformModule: Module = module {
    
    // Android Context is provided by the application
    single<DatabaseDriverFactory> {
        DatabaseDriverFactory(get())
    }
    
}

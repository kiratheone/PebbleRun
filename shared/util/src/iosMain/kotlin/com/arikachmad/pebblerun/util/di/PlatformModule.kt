package com.arikachmad.pebblerun.util.di

import org.koin.core.module.Module
import org.koin.dsl.module
import com.arikachmad.pebblerun.storage.DatabaseDriverFactory

/**
 * iOS-specific DI module
 */
actual val platformModule: Module = module {
    
    single<DatabaseDriverFactory> {
        DatabaseDriverFactory()
    }
    
}

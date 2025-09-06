package com.arikachmad.pebblerun.android

import android.app.Application
import com.arikachmad.pebblerun.android.di.androidModule
import com.arikachmad.pebblerun.shared.di.sharedAppModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

/**
 * Android Application class
 * Sets up dependency injection for the Android app
 * Integrates shared KMP modules with Android-specific dependencies
 */
class PebbleRunApplication : Application() {
    
    override fun onCreate() {
        super.onCreate()
        
        startKoin {
            androidLogger()
            androidContext(this@PebbleRunApplication)
            modules(
                sharedAppModule,  // Shared ViewModels and business logic
                androidModule     // Android-specific implementations
            )
        }
    }
}

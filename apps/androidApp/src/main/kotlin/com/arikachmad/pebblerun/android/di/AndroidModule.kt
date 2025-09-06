package com.arikachmad.pebblerun.android.di

import com.arikachmad.pebblerun.shared.ui.PlatformActions
import com.arikachmad.pebblerun.shared.ui.UIBridge
import org.koin.dsl.module

/**
 * Android-specific dependency injection module
 * Provides platform-specific implementations
 */
val androidModule = module {
    
    // Platform-specific UI bridge implementation
    single<UIBridge> { AndroidUIBridge(get()) }
    
    // Platform-specific actions implementation
    single<PlatformActions> { AndroidPlatformActions(get()) }
    
    // TODO: Add other Android-specific dependencies
    // single<WorkoutTrackingService> { WorkoutTrackingService() }
    // single<AndroidPebbleTransport> { AndroidPebbleTransport(get()) }
    // single<AndroidLocationProvider> { AndroidLocationProvider(get()) }
}

/**
 * Android implementation of UIBridge
 */
class AndroidUIBridge(
    private val context: android.content.Context
) : UIBridge {
    
    override fun showNotification(title: String, message: String) {
        // TODO: Implement Android notification
    }
    
    override suspend fun requestPermissions(): Boolean {
        // TODO: Implement Android permission request
        return false
    }
    
    override fun startBackgroundService() {
        // TODO: Start Android Foreground Service
    }
    
    override fun stopBackgroundService() {
        // TODO: Stop Android Foreground Service
    }
    
    override fun handleError(error: Throwable) {
        // TODO: Implement Android error handling
    }
}

/**
 * Android implementation of PlatformActions
 */
class AndroidPlatformActions(
    private val context: android.content.Context
) : PlatformActions {
    
    override suspend fun launchPebbleApp(): Boolean {
        // TODO: Implement Android PebbleKit app launch
        return false
    }
    
    override suspend fun closePebbleApp(): Boolean {
        // TODO: Implement Android PebbleKit app close
        return false
    }
    
    override suspend fun sendToPebble(key: Int, value: String): Boolean {
        // TODO: Implement Android PebbleKit message sending
        return false
    }
    
    override suspend fun isPebbleConnected(): Boolean {
        // TODO: Implement Android PebbleKit connection check
        return false
    }
}

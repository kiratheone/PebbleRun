package com.arikachmad.pebblerun.shared.ui

/**
 * Common interface for platform-specific UI integration
 * Provides abstraction for platform-specific behaviors
 */
interface UIBridge {
    
    /**
     * Show platform-specific notification
     */
    fun showNotification(title: String, message: String)
    
    /**
     * Request platform-specific permissions
     */
    suspend fun requestPermissions(): Boolean
    
    /**
     * Start platform-specific background service
     */
    fun startBackgroundService()
    
    /**
     * Stop platform-specific background service
     */
    fun stopBackgroundService()
    
    /**
     * Handle platform-specific error
     */
    fun handleError(error: Throwable)
}

/**
 * Platform-specific actions that can be triggered from shared ViewModels
 */
interface PlatformActions {
    
    /**
     * Launch Pebble app
     */
    suspend fun launchPebbleApp(): Boolean
    
    /**
     * Close Pebble app
     */
    suspend fun closePebbleApp(): Boolean
    
    /**
     * Send data to Pebble
     */
    suspend fun sendToPebble(key: Int, value: String): Boolean
    
    /**
     * Check if Pebble is connected
     */
    suspend fun isPebbleConnected(): Boolean
}

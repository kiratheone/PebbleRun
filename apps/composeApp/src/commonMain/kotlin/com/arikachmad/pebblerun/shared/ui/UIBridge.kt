package com.arikachmad.pebblerun.shared.ui

/**
 * Platform abstraction for UI bridge functionality
 * Implemented by platform-specific modules
 */
interface UIBridge {
    fun showError(message: String)
    fun showSuccess(message: String)
}

/**
 * Platform abstraction for platform-specific actions
 * Implemented by platform-specific modules
 */
interface PlatformActions {
    fun startForegroundService()
    fun stopForegroundService()
    fun requestPermissions()
}

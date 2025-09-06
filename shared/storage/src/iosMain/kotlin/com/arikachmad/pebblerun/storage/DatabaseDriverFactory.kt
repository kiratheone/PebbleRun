package com.arikachmad.pebblerun.storage

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver

/**
 * iOS implementation of the database driver factory
 * Supports TASK-042: iOS-specific database driver with encryption
 * Uses native SQLite with basic encryption placeholder
 */
actual class DatabaseDriverFactory {
    actual fun createDriver(): SqlDriver {
        return createEncryptedDriver()
    }
    
    /**
     * Creates a database driver with basic SQLite setup
     * TODO: Add proper encryption using iOS Keychain in future implementation
     */
    private fun createEncryptedDriver(): SqlDriver {
        // For now, use standard SQLite driver
        // In a production app, this would be enhanced with proper encryption
        return NativeSqliteDriver(
            schema = WorkoutDatabase.Schema,
            name = "workout_encrypted.db"
        )
    }
    
    /**
     * Creates a non-encrypted driver for testing or debug builds
     */
    fun createDebugDriver(): SqlDriver {
        return NativeSqliteDriver(WorkoutDatabase.Schema, "workout_debug.db")
    }
}

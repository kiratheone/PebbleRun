package com.arikachmad.pebblerun.storage

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import net.sqlcipher.database.SupportFactory

/**
 * Android implementation of the database driver factory
 * Supports TASK-039: Android-specific database driver with encryption
 * Uses SQLCipher for database encryption with Android KeyStore integration
 */
actual class DatabaseDriverFactory(
    private val context: Context,
    private val securityManager: AndroidSecurityManager
) {
    actual fun createDriver(): SqlDriver {
        return createEncryptedDriver()
    }
    
    /**
     * Creates an encrypted SQLite driver using SQLCipher
     * Encryption key is managed by AndroidSecurityManager using Android KeyStore
     */
    private fun createEncryptedDriver(): SqlDriver {
        val passphrase = securityManager.getDatabaseEncryptionKey()
        val factory = SupportFactory(passphrase)
        
        return AndroidSqliteDriver(
            schema = WorkoutDatabase.Schema,
            context = context,
            name = "workout_encrypted.db",
            factory = factory
        )
    }
    
    /**
     * Creates a non-encrypted driver for testing or debug builds
     */
    fun createDebugDriver(): SqlDriver {
        return AndroidSqliteDriver(
            schema = WorkoutDatabase.Schema,
            context = context,
            name = "workout_debug.db"
        )
    }
}

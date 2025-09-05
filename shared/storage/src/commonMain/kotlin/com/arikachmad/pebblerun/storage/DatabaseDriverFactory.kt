package com.arikachmad.pebblerun.storage

import app.cash.sqldelight.db.SqlDriver

/**
 * Platform-specific database driver factory for SQLDelight
 * Supports TEC-004 (SQLDelight for cross-platform local data storage)
 */
expect class DatabaseDriverFactory {
    fun createDriver(): SqlDriver
}

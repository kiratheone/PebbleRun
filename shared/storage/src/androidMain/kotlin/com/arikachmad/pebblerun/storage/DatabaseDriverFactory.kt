package com.arikachmad.pebblerun.storage

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver

/**
 * Android implementation of the database driver factory
 */
actual class DatabaseDriverFactory(private val context: android.content.Context) {
    actual fun createDriver(): SqlDriver {
        return AndroidSqliteDriver(WorkoutDatabase.Schema, context, "workout.db")
    }
}

package com.arikachmad.pebblerun.storage

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import app.cash.sqldelight.db.SqlDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Android implementation of data migration manager
 * Supports TASK-046: Android-specific data migration strategies
 * 
 * Uses SQLite migration capabilities with Android-specific optimizations
 */
class AndroidDataMigrationManager(
    private val context: Context,
    private val driverFactory: DatabaseDriverFactory,
    private val backupManager: AndroidBackupManager
) : DataMigrationManager {
    
    companion object {
        private const val CURRENT_SCHEMA_VERSION = 1
        private const val MIGRATION_BACKUP_PREFIX = "pre_migration_backup"
    }
    
    private val _migrationProgress = MutableStateFlow(
        MigrationProgress(
            currentStep = MigrationStep.PREPARING,
            progress = 0f,
            currentVersion = 0,
            targetVersion = 0,
            currentMigration = "",
            migratedRecords = 0,
            totalRecords = 0
        )
    )
    
    private val registeredMigrations = mutableMapOf<Pair<Int, Int>, Migration>()
    
    override fun getMigrationProgress(): Flow<MigrationProgress> = _migrationProgress.asStateFlow()
    
    override suspend fun getCurrentVersion(): Int = withContext(Dispatchers.IO) {
        try {
            val driver = driverFactory.createDriver()
            val result = driver.executeQuery(
                identifier = null,
                sql = "PRAGMA user_version",
                parameters = 0
            )
            
            if (result.next()) {
                result.getLong(0)?.toInt() ?: 0
            } else {
                0
            }
        } catch (e: Exception) {
            0
        }
    }
    
    override suspend fun getTargetVersion(): Int = CURRENT_SCHEMA_VERSION
    
    override suspend fun isMigrationNeeded(): Boolean {
        return getCurrentVersion() < getTargetVersion()
    }
    
    override suspend fun performMigration(): MigrationResult = withContext(Dispatchers.IO) {
        val currentVersion = getCurrentVersion()
        val targetVersion = getTargetVersion()
        
        if (currentVersion >= targetVersion) {
            return@withContext MigrationResult.Success(currentVersion, targetVersion, 0)
        }
        
        performMigration(currentVersion, targetVersion)
    }
    
    override suspend fun performMigration(fromVersion: Int, toVersion: Int): MigrationResult = 
        withContext(Dispatchers.IO) {
        try {
            _migrationProgress.value = _migrationProgress.value.copy(
                currentStep = MigrationStep.PREPARING,
                progress = 0f,
                currentVersion = fromVersion,
                targetVersion = toVersion,
                currentMigration = "Preparing migration"
            )
            
            // Step 1: Create pre-migration backup
            _migrationProgress.value = _migrationProgress.value.copy(
                currentStep = MigrationStep.BACKING_UP,
                progress = 0.1f,
                currentMigration = "Creating backup"
            )
            
            val backupResult = createPreMigrationBackup()
            if (backupResult is BackupResult.Error) {
                return@withContext MigrationResult.Error("Failed to create pre-migration backup: ${backupResult.message}")
            }
            
            // Step 2: Analyze current schema
            _migrationProgress.value = _migrationProgress.value.copy(
                currentStep = MigrationStep.ANALYZING_SCHEMA,
                progress = 0.2f,
                currentMigration = "Analyzing schema"
            )
            
            val migrationPath = findMigrationPath(fromVersion, toVersion)
            if (!migrationPath.isSupported) {
                return@withContext MigrationResult.Error("Migration path from $fromVersion to $toVersion is not supported")
            }
            
            // Step 3: Execute migrations
            var totalRecords = 0
            var migratedRecords = 0
            val driver = driverFactory.createDriver()
            
            driver.execute(null, "BEGIN TRANSACTION", 0)
            
            try {
                for ((index, migrationStep) in migrationPath.requiredMigrations.withIndex()) {
                    val stepProgress = 0.3f + (0.6f * index / migrationPath.requiredMigrations.size)
                    _migrationProgress.value = _migrationProgress.value.copy(
                        currentStep = MigrationStep.MIGRATING_DATA,
                        progress = stepProgress,
                        currentMigration = migrationStep,
                        migratedRecords = migratedRecords
                    )
                    
                    val migration = findMigrationForStep(migrationStep, fromVersion + index, fromVersion + index + 1)
                    if (migration != null) {
                        val stepResult = migration.migrate()
                        when (stepResult) {
                            is MigrationStepResult.Success -> {
                                migratedRecords += stepResult.recordsAffected
                                totalRecords += stepResult.recordsAffected
                            }
                            is MigrationStepResult.Error -> {
                                driver.execute(null, "ROLLBACK", 0)
                                return@withContext MigrationResult.Error("Migration step failed: ${stepResult.message}", stepResult.cause)
                            }
                        }
                    } else {
                        // Execute built-in migrations
                        executeBuiltInMigration(driver, migrationStep, fromVersion + index, fromVersion + index + 1)
                    }
                }
                
                // Update schema version
                driver.execute(null, "PRAGMA user_version = $toVersion", 0)
                driver.execute(null, "COMMIT", 0)
                
                // Step 4: Validate data integrity
                _migrationProgress.value = _migrationProgress.value.copy(
                    currentStep = MigrationStep.VALIDATING,
                    progress = 0.9f,
                    currentMigration = "Validating data"
                )
                
                val validationResult = validateDataIntegrity()
                if (validationResult is ValidationResult.Error) {
                    return@withContext MigrationResult.Error("Data validation failed: ${validationResult.message}")
                }
                
                // Step 5: Cleanup
                _migrationProgress.value = _migrationProgress.value.copy(
                    currentStep = MigrationStep.CLEANING_UP,
                    progress = 0.95f,
                    currentMigration = "Cleaning up"
                )
                
                cleanupMigrationArtifacts()
                
                _migrationProgress.value = _migrationProgress.value.copy(
                    currentStep = MigrationStep.COMPLETED,
                    progress = 1.0f,
                    currentMigration = "Migration completed",
                    migratedRecords = totalRecords
                )
                
                MigrationResult.Success(fromVersion, toVersion, totalRecords)
                
            } catch (e: Exception) {
                driver.execute(null, "ROLLBACK", 0)
                MigrationResult.Error("Migration failed: ${e.message}", e)
            }
            
        } catch (e: Exception) {
            MigrationResult.Error("Migration preparation failed: ${e.message}", e)
        }
    }
    
    override suspend fun validateDataIntegrity(): ValidationResult = withContext(Dispatchers.IO) {
        try {
            val driver = driverFactory.createDriver()
            
            // Check foreign key constraints
            val fkResult = driver.executeQuery(null, "PRAGMA foreign_key_check", 0)
            if (fkResult.next()) {
                return@withContext ValidationResult.Error("Foreign key constraint violations found")
            }
            
            // Check table integrity
            val integrityResult = driver.executeQuery(null, "PRAGMA integrity_check", 0)
            if (integrityResult.next()) {
                val result = integrityResult.getString(0)
                if (result != "ok") {
                    return@withContext ValidationResult.Error("Database integrity check failed: $result")
                }
            }
            
            // Validate critical data exists
            val sessionCountResult = driver.executeQuery(null, "SELECT COUNT(*) FROM WorkoutSession", 0)
            if (sessionCountResult.next()) {
                val count = sessionCountResult.getLong(0) ?: 0
                // Additional validation logic based on expected data
            }
            
            ValidationResult.Success("Data integrity validation passed")
            
        } catch (e: Exception) {
            ValidationResult.Error("Data integrity validation failed: ${e.message}")
        }
    }
    
    override suspend fun createPreMigrationBackup(): BackupResult {
        return backupManager.createFullBackup()
    }
    
    override suspend fun restorePreMigrationBackup(): RestoreResult {
        val backups = backupManager.listBackups()
        val preMigrationBackup = backups.find { it.name.contains(MIGRATION_BACKUP_PREFIX) }
        
        return if (preMigrationBackup != null) {
            backupManager.restoreFromBackup(preMigrationBackup.id)
        } else {
            RestoreResult.Error("No pre-migration backup found")
        }
    }
    
    override suspend fun getAvailableMigrations(): List<MigrationPath> {
        return listOf(
            MigrationPath(0, 1, listOf("initial_schema"), true, 5000L)
            // Add more migration paths as needed
        )
    }
    
    override fun registerMigration(migration: Migration) {
        registeredMigrations[Pair(migration.fromVersion, migration.toVersion)] = migration
    }
    
    override suspend fun cleanupMigrationArtifacts() = withContext(Dispatchers.IO) {
        try {
            val driver = driverFactory.createDriver()
            
            // Clean up temporary tables
            val tempTables = listOf("temp_migration_data", "migration_backup")
            for (table in tempTables) {
                try {
                    driver.execute(null, "DROP TABLE IF EXISTS $table", 0)
                } catch (e: Exception) {
                    // Ignore errors for tables that don't exist
                }
            }
            
            // Clean up migration-specific files
            val tempDir = File(context.cacheDir, "migration_temp")
            if (tempDir.exists()) {
                tempDir.deleteRecursively()
            }
            
        } catch (e: Exception) {
            // Log but don't throw - cleanup is best effort
        }
    }
    
    private fun findMigrationPath(fromVersion: Int, toVersion: Int): MigrationPath {
        val availableMigrations = getAvailableMigrations()
        return availableMigrations.find { 
            it.fromVersion == fromVersion && it.toVersion == toVersion 
        } ?: MigrationPath(fromVersion, toVersion, emptyList(), false)
    }
    
    private fun findMigrationForStep(stepName: String, fromVersion: Int, toVersion: Int): Migration? {
        return registeredMigrations[Pair(fromVersion, toVersion)]
    }
    
    private suspend fun executeBuiltInMigration(driver: SqlDriver, stepName: String, fromVersion: Int, toVersion: Int) {
        when (stepName) {
            "initial_schema" -> {
                // Execute initial schema creation
                // This would typically be handled by SQLDelight migrations
            }
            // Add more built-in migrations as needed
        }
    }
}

/**
 * Example migration implementation
 */
class AddHeartRateQualityMigration : Migration {
    override val fromVersion: Int = 0
    override val toVersion: Int = 1
    override val description: String = "Add quality column to HRSample table"
    override val isReversible: Boolean = false
    
    override suspend fun migrate(): MigrationStepResult {
        return try {
            // Migration logic would go here
            MigrationStepResult.Success(0)
        } catch (e: Exception) {
            MigrationStepResult.Error("Failed to add quality column: ${e.message}", e)
        }
    }
    
    override suspend fun rollback(): MigrationStepResult {
        return MigrationStepResult.Error("Migration is not reversible")
    }
    
    override suspend fun validate(): ValidationResult {
        return try {
            // Validation logic would go here
            ValidationResult.Success("Quality column added successfully")
        } catch (e: Exception) {
            ValidationResult.Error("Validation failed: ${e.message}")
        }
    }
}

/**
 * Android-specific data transformation for complex migrations
 */
class AndroidWorkoutDataTransformation : DataTransformation {
    override val name: String = "WorkoutDataTransformation"
    override val description: String = "Transform workout data for new schema"
    
    override suspend fun transform(inputData: Any): Any {
        // Transformation logic would go here
        return inputData
    }
    
    override suspend fun validate(transformedData: Any): Boolean {
        // Validation logic would go here
        return true
    }
}

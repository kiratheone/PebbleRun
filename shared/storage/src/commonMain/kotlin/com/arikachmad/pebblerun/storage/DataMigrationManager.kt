package com.arikachmad.pebblerun.storage

import com.arikachmad.pebblerun.domain.entity.ValidationResult
import kotlinx.coroutines.flow.Flow

/**
 * Common interface for data migration functionality
 * Supports TASK-046: Platform-specific data migration strategies
 * 
 * Provides unified API for handling database schema migrations and data transformations
 */
interface DataMigrationManager {
    
    /**
     * Gets the current database schema version
     */
    suspend fun getCurrentVersion(): Int
    
    /**
     * Gets the target schema version
     */
    suspend fun getTargetVersion(): Int
    
    /**
     * Checks if migration is needed
     */
    suspend fun isMigrationNeeded(): Boolean
    
    /**
     * Performs database migration from current to target version
     */
    suspend fun performMigration(): MigrationResult
    
    /**
     * Performs incremental migration from one version to another
     */
    suspend fun performMigration(fromVersion: Int, toVersion: Int): MigrationResult
    
    /**
     * Gets migration progress as a flow
     */
    fun getMigrationProgress(): Flow<MigrationProgress>
    
    /**
     * Validates data integrity after migration
     */
    suspend fun validateDataIntegrity(): ValidationResult
    
    /**
     * Creates a backup before migration
     */
    suspend fun createPreMigrationBackup(): BackupResult
    
    /**
     * Restores from pre-migration backup in case of failure
     */
    suspend fun restorePreMigrationBackup(): RestoreResult
    
    /**
     * Gets available migration paths
     */
    suspend fun getAvailableMigrations(): List<MigrationPath>
    
    /**
     * Registers a custom migration step
     */
    fun registerMigration(migration: Migration)
    
    /**
     * Cleans up migration artifacts and temporary data
     */
    suspend fun cleanupMigrationArtifacts()
}

/**
 * Migration result sealed class
 */
sealed class MigrationResult {
    data class Success(val fromVersion: Int, val toVersion: Int, val migratedRecords: Int) : MigrationResult()
    data class Error(val message: String, val cause: Throwable? = null) : MigrationResult()
    data class Cancelled(val reason: String) : MigrationResult()
}

/**
 * Migration progress data class
 */
data class MigrationProgress(
    val currentStep: MigrationStep,
    val progress: Float, // 0.0 to 1.0
    val currentVersion: Int,
    val targetVersion: Int,
    val currentMigration: String,
    val migratedRecords: Int,
    val totalRecords: Int
)

/**
 * Migration step enumeration
 */
enum class MigrationStep {
    PREPARING,
    BACKING_UP,
    ANALYZING_SCHEMA,
    CREATING_TEMP_TABLES,
    MIGRATING_DATA,
    UPDATING_SCHEMA,
    VALIDATING,
    CLEANING_UP,
    COMPLETED
}

/**
 * Migration path data class
 */
data class MigrationPath(
    val fromVersion: Int,
    val toVersion: Int,
    val requiredMigrations: List<String>,
    val isSupported: Boolean,
    val estimatedTime: Long? = null
)

/**
 * Migration interface for individual migration steps
 */
interface Migration {
    val fromVersion: Int
    val toVersion: Int
    val description: String
    val isReversible: Boolean
    
    suspend fun migrate(): MigrationStepResult
    suspend fun rollback(): MigrationStepResult
    suspend fun validate(): ValidationResult
}

/**
 * Migration step result sealed class
 */
sealed class MigrationStepResult {
    data class Success(val recordsAffected: Int) : MigrationStepResult()
    data class Error(val message: String, val cause: Throwable? = null) : MigrationStepResult()
}

/**
 * Data transformation interface for complex migrations
 */
interface DataTransformation {
    val name: String
    val description: String
    
    suspend fun transform(inputData: Any): Any
    suspend fun validate(transformedData: Any): Boolean
}

/**
 * Schema change types
 */
enum class SchemaChangeType {
    ADD_TABLE,
    DROP_TABLE,
    ADD_COLUMN,
    DROP_COLUMN,
    MODIFY_COLUMN,
    ADD_INDEX,
    DROP_INDEX,
    ADD_CONSTRAINT,
    DROP_CONSTRAINT
}

/**
 * Schema change data class
 */
data class SchemaChange(
    val type: SchemaChangeType,
    val tableName: String,
    val columnName: String? = null,
    val definition: String,
    val isRequired: Boolean = true
)

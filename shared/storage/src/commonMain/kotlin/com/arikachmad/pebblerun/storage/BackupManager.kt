package com.arikachmad.pebblerun.storage

import com.arikachmad.pebblerun.domain.entity.ValidationResult
import kotlinx.coroutines.flow.Flow

/**
 * Common interface for backup and restore functionality
 * Supports TASK-045: Platform-specific backup and restore mechanisms
 * 
 * Provides unified API for both Android and iOS backup systems
 */
interface BackupManager {
    
    /**
     * Creates a full backup of all workout data
     * Returns the backup identifier/path
     */
    suspend fun createFullBackup(): BackupResult
    
    /**
     * Creates an incremental backup since the last backup
     * Returns the backup identifier/path
     */
    suspend fun createIncrementalBackup(lastBackupTime: Long): BackupResult
    
    /**
     * Restores data from a backup
     */
    suspend fun restoreFromBackup(backupId: String): RestoreResult
    
    /**
     * Lists all available backups
     */
    suspend fun listBackups(): List<BackupInfo>
    
    /**
     * Deletes a specific backup
     */
    suspend fun deleteBackup(backupId: String): Boolean
    
    /**
     * Gets backup progress as a flow
     */
    fun getBackupProgress(): Flow<BackupProgress>
    
    /**
     * Gets restore progress as a flow
     */
    fun getRestoreProgress(): Flow<RestoreProgress>
    
    /**
     * Validates a backup file/data
     */
    suspend fun validateBackup(backupId: String): ValidationResult
    
    /**
     * Configures automatic backup settings
     */
    suspend fun configureAutoBackup(settings: AutoBackupSettings)
    
    /**
     * Triggers automatic backup if conditions are met
     */
    suspend fun triggerAutoBackupIfNeeded(): BackupResult?
}

/**
 * Backup result sealed class
 */
sealed class BackupResult {
    data class Success(val backupId: String, val size: Long) : BackupResult()
    data class Error(val message: String, val cause: Throwable? = null) : BackupResult()
}

/**
 * Restore result sealed class
 */
sealed class RestoreResult {
    data class Success(val restoredSessions: Int, val restoredSize: Long) : RestoreResult()
    data class Error(val message: String, val cause: Throwable? = null) : RestoreResult()
}

/**
 * Backup information data class
 */
data class BackupInfo(
    val id: String,
    val name: String,
    val createdAt: Long,
    val size: Long,
    val type: BackupType,
    val sessionCount: Int,
    val isValidated: Boolean
)

/**
 * Backup type enumeration
 */
enum class BackupType {
    FULL, INCREMENTAL, MANUAL, AUTOMATIC
}

/**
 * Backup progress data class
 */
data class BackupProgress(
    val currentStep: BackupStep,
    val progress: Float, // 0.0 to 1.0
    val currentItem: String,
    val totalItems: Int,
    val completedItems: Int
)

/**
 * Restore progress data class
 */
data class RestoreProgress(
    val currentStep: RestoreStep,
    val progress: Float, // 0.0 to 1.0
    val currentItem: String,
    val totalItems: Int,
    val completedItems: Int
)

/**
 * Backup step enumeration
 */
enum class BackupStep {
    PREPARING,
    BACKING_UP_DATABASE,
    BACKING_UP_FILES,
    COMPRESSING,
    FINALIZING,
    COMPLETED
}

/**
 * Restore step enumeration
 */
enum class RestoreStep {
    PREPARING,
    VALIDATING,
    EXTRACTING,
    RESTORING_DATABASE,
    RESTORING_FILES,
    FINALIZING,
    COMPLETED
}

/**
 * Auto backup settings data class
 */
data class AutoBackupSettings(
    val enabled: Boolean,
    val frequency: BackupFrequency,
    val requireWifi: Boolean,
    val requireCharging: Boolean,
    val maxBackupCount: Int,
    val backupOnWorkoutComplete: Boolean
)

/**
 * Backup frequency enumeration
 */
enum class BackupFrequency {
    DAILY, WEEKLY, MONTHLY, NEVER
}

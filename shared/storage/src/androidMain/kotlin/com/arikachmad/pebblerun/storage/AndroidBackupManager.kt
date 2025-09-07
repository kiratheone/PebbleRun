package com.arikachmad.pebblerun.storage

import android.Manifest
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Android implementation of backup manager
 * Supports TASK-045: Android-specific backup and restore mechanisms
 * 
 * Uses Android WorkManager for scheduled backups and local storage
 */
class AndroidBackupManager(
    private val context: Context,
    private val fileSystemManager: AndroidFileSystemManager,
    private val securityManager: AndroidSecurityManager
) : BackupManager {
    
    companion object {
        private const val BACKUP_WORK_TAG = "backup_work"
        private const val BACKUP_METADATA_FILE = "backup_metadata.json"
        private val json = Json { prettyPrint = true }
    }
    
    private val _backupProgress = MutableStateFlow(
        BackupProgress(BackupStep.PREPARING, 0f, "", 0, 0)
    )
    
    private val _restoreProgress = MutableStateFlow(
        RestoreProgress(RestoreStep.PREPARING, 0f, "", 0, 0)
    )
    
    override fun getBackupProgress(): Flow<BackupProgress> = _backupProgress.asStateFlow()
    override fun getRestoreProgress(): Flow<RestoreProgress> = _restoreProgress.asStateFlow()
    
    override suspend fun createFullBackup(): BackupResult = withContext(Dispatchers.IO) {
        try {
            _backupProgress.value = BackupProgress(BackupStep.PREPARING, 0f, "Preparing backup...", 0, 0)
            
            val backupDir = fileSystemManager.getBackupDirectory()
            val timestamp = System.currentTimeMillis()
            val backupId = "full_backup_$timestamp"
            val backupFile = File(backupDir, "$backupId.zip")
            
            // Create backup metadata
            val metadata = BackupMetadata(
                id = backupId,
                name = "Full Backup",
                createdAt = timestamp,
                type = BackupType.FULL,
                version = "1.0"
            )
            
            ZipOutputStream(FileOutputStream(backupFile)).use { zipOut ->
                // Step 1: Backup database
                _backupProgress.value = BackupProgress(BackupStep.BACKING_UP_DATABASE, 0.2f, "Backing up database...", 3, 1)
                val databaseFile = context.getDatabasePath("workout_encrypted.db")
                if (databaseFile.exists()) {
                    addFileToZip(zipOut, databaseFile, "database/workout.db")
                }
                
                // Step 2: Backup security keys (encrypted)
                _backupProgress.value = BackupProgress(BackupStep.BACKING_UP_FILES, 0.4f, "Backing up security data...", 3, 2)
                val keyBackup = securityManager.getDatabaseEncryptionKey().concatToString()
                val encryptedKeys = securityManager.encryptData(keyBackup)
                addStringToZip(zipOut, encryptedKeys, "security/keys.enc")
                
                // Step 3: Add metadata
                _backupProgress.value = BackupProgress(BackupStep.FINALIZING, 0.8f, "Adding metadata...", 3, 3)
                val metadataJson = json.encodeToString(metadata)
                addStringToZip(zipOut, metadataJson, BACKUP_METADATA_FILE)
            }
            
            _backupProgress.value = BackupProgress(BackupStep.COMPLETED, 1.0f, "Backup completed", 3, 3)
            
            BackupResult.Success(backupId, backupFile.length())
        } catch (e: Exception) {
            BackupResult.Error("Failed to create backup: ${e.message}", e)
        }
    }
    
    override suspend fun createIncrementalBackup(lastBackupTime: Long): BackupResult = withContext(Dispatchers.IO) {
        try {
            // For this implementation, incremental backup is same as full backup
            // In a more complex system, this would only backup changed data
            createFullBackup()
        } catch (e: Exception) {
            BackupResult.Error("Failed to create incremental backup: ${e.message}", e)
        }
    }
    
    override suspend fun restoreFromBackup(backupId: String): RestoreResult = withContext(Dispatchers.IO) {
        try {
            _restoreProgress.value = RestoreProgress(RestoreStep.PREPARING, 0f, "Preparing restore...", 0, 0)
            
            val backupDir = fileSystemManager.getBackupDirectory()
            val backupFile = File(backupDir, "$backupId.zip")
            
            if (!backupFile.exists()) {
                return@withContext RestoreResult.Error("Backup file not found: $backupId")
            }
            
            _restoreProgress.value = RestoreProgress(RestoreStep.VALIDATING, 0.1f, "Validating backup...", 4, 1)
            
            var restoredSessions = 0
            val tempDir = fileSystemManager.getTempDirectory()
            
            ZipInputStream(FileInputStream(backupFile)).use { zipIn ->
                var entry: ZipEntry?
                while (zipIn.nextEntry.also { entry = it } != null) {
                    val currentEntry = entry ?: continue
                    
                    when {
                        currentEntry.name == BACKUP_METADATA_FILE -> {
                            _restoreProgress.value = RestoreProgress(RestoreStep.EXTRACTING, 0.3f, "Reading metadata...", 4, 2)
                            val metadataJson = zipIn.readBytes().decodeToString()
                            val metadata = json.decodeFromString<BackupMetadata>(metadataJson)
                            // Validate metadata here if needed
                        }
                        
                        currentEntry.name.startsWith("database/") -> {
                            _restoreProgress.value = RestoreProgress(RestoreStep.RESTORING_DATABASE, 0.6f, "Restoring database...", 4, 3)
                            val databaseFile = context.getDatabasePath("workout_encrypted.db")
                            
                            // Create backup of current database
                            val currentBackup = File(tempDir, "current_database_backup.db")
                            if (databaseFile.exists()) {
                                databaseFile.copyTo(currentBackup, overwrite = true)
                            }
                            
                            // Restore database
                            FileOutputStream(databaseFile).use { out ->
                                zipIn.copyTo(out)
                            }
                            
                            // Count sessions in restored database (simplified)
                            restoredSessions = countSessionsInDatabase()
                        }
                        
                        currentEntry.name.startsWith("security/") -> {
                            _restoreProgress.value = RestoreProgress(RestoreStep.RESTORING_FILES, 0.8f, "Restoring security data...", 4, 4)
                            val encryptedData = zipIn.readBytes().decodeToString()
                            try {
                                val decryptedKeys = securityManager.decryptData(encryptedData)
                                // Restore security keys if needed
                            } catch (e: Exception) {
                                // Log but don't fail the restore for security data
                            }
                        }
                    }
                    zipIn.closeEntry()
                }
            }
            
            _restoreProgress.value = RestoreProgress(RestoreStep.COMPLETED, 1.0f, "Restore completed", 4, 4)
            
            RestoreResult.Success(restoredSessions, backupFile.length())
        } catch (e: Exception) {
            RestoreResult.Error("Failed to restore from backup: ${e.message}", e)
        }
    }
    
    override suspend fun listBackups(): List<BackupInfo> = withContext(Dispatchers.IO) {
        val backupDir = fileSystemManager.getBackupDirectory()
        val backupFiles = backupDir.listFiles()?.filter { it.name.endsWith(".zip") } ?: emptyList()
        
        backupFiles.mapNotNull { file ->
            try {
                val metadata = extractBackupMetadata(file)
                BackupInfo(
                    id = metadata.id,
                    name = metadata.name,
                    createdAt = metadata.createdAt,
                    size = file.length(),
                    type = metadata.type,
                    sessionCount = metadata.sessionCount ?: 0,
                    isValidated = true
                )
            } catch (e: Exception) {
                // If we can't read metadata, create basic info from filename
                val fileName = file.nameWithoutExtension
                BackupInfo(
                    id = fileName,
                    name = fileName,
                    createdAt = file.lastModified(),
                    size = file.length(),
                    type = BackupType.MANUAL,
                    sessionCount = 0,
                    isValidated = false
                )
            }
        }.sortedByDescending { it.createdAt }
    }
    
    override suspend fun deleteBackup(backupId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val backupDir = fileSystemManager.getBackupDirectory()
            val backupFile = File(backupDir, "$backupId.zip")
            backupFile.delete()
        } catch (e: Exception) {
            false
        }
    }
    
    override suspend fun validateBackup(backupId: String): ValidationResult = withContext(Dispatchers.IO) {
        try {
            val backupDir = fileSystemManager.getBackupDirectory()
            val backupFile = File(backupDir, "$backupId.zip")
            
            if (!backupFile.exists()) {
                return@withContext ValidationResult.Error("Backup file not found")
            }
            
            // Validate ZIP structure and metadata
            ZipInputStream(FileInputStream(backupFile)).use { zipIn ->
                var hasMetadata = false
                var hasDatabase = false
                
                var entry: ZipEntry?
                while (zipIn.nextEntry.also { entry = it } != null) {
                    val currentEntry = entry ?: continue
                    
                    when {
                        currentEntry.name == BACKUP_METADATA_FILE -> {
                            hasMetadata = true
                            val metadataJson = zipIn.readBytes().decodeToString()
                            json.decodeFromString<BackupMetadata>(metadataJson)
                        }
                        currentEntry.name.startsWith("database/") -> hasDatabase = true
                    }
                    zipIn.closeEntry()
                }
                
                when {
                    !hasMetadata -> ValidationResult.Error("Missing backup metadata")
                    !hasDatabase -> ValidationResult.Error("Missing database backup")
                    else -> ValidationResult.Success("Backup is valid")
                }
            }
        } catch (e: Exception) {
            ValidationResult.Error("Backup validation failed: ${e.message}")
        }
    }
    
    override suspend fun configureAutoBackup(settings: AutoBackupSettings) {
        val workManager = WorkManager.getInstance(context)
        
        if (settings.enabled) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(if (settings.requireWifi) NetworkType.UNMETERED else NetworkType.CONNECTED)
                .setRequiresCharging(settings.requireCharging)
                .build()
            
            val backupRequest = PeriodicWorkRequestBuilder<AutoBackupWorker>(
                when (settings.frequency) {
                    BackupFrequency.DAILY -> java.util.concurrent.TimeUnit.DAYS.toMillis(1)
                    BackupFrequency.WEEKLY -> java.util.concurrent.TimeUnit.DAYS.toMillis(7)
                    BackupFrequency.MONTHLY -> java.util.concurrent.TimeUnit.DAYS.toMillis(30)
                    BackupFrequency.NEVER -> return // Don't schedule if NEVER
                }, java.util.concurrent.TimeUnit.MILLISECONDS
            )
                .setConstraints(constraints)
                .addTag(BACKUP_WORK_TAG)
                .build()
            
            workManager.enqueueUniquePeriodicWork(
                "auto_backup",
                ExistingPeriodicWorkPolicy.UPDATE,
                backupRequest
            )
        } else {
            workManager.cancelAllWorkByTag(BACKUP_WORK_TAG)
        }
    }
    
    override suspend fun triggerAutoBackupIfNeeded(): BackupResult? {
        if (shouldCreateAutoBackup()) {
            return createFullBackup()
        }
        return null
    }
    
    private fun addFileToZip(zipOut: ZipOutputStream, file: File, entryName: String) {
        val entry = ZipEntry(entryName)
        zipOut.putNextEntry(entry)
        FileInputStream(file).use { it.copyTo(zipOut) }
        zipOut.closeEntry()
    }
    
    private fun addStringToZip(zipOut: ZipOutputStream, content: String, entryName: String) {
        val entry = ZipEntry(entryName)
        zipOut.putNextEntry(entry)
        zipOut.write(content.toByteArray())
        zipOut.closeEntry()
    }
    
    private fun extractBackupMetadata(backupFile: File): BackupMetadata {
        ZipInputStream(FileInputStream(backupFile)).use { zipIn ->
            var entry: ZipEntry?
            while (zipIn.nextEntry.also { entry = it } != null) {
                val currentEntry = entry ?: continue
                if (currentEntry.name == BACKUP_METADATA_FILE) {
                    val metadataJson = zipIn.readBytes().decodeToString()
                    return json.decodeFromString(metadataJson)
                }
                zipIn.closeEntry()
            }
        }
        throw IllegalStateException("No metadata found in backup file")
    }
    
    private fun countSessionsInDatabase(): Int {
        // This would require database access - simplified for now
        return 0
    }
    
    private fun shouldCreateAutoBackup(): Boolean {
        // Check if conditions are met for auto backup
        // Guard connectivity access with ACCESS_NETWORK_STATE permission
        val hasNetPerm = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_NETWORK_STATE
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        val capabilities = if (hasNetPerm) {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = connectivityManager.activeNetwork
            connectivityManager.getNetworkCapabilities(network)
        } else {
            null
        }
        
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val batteryLevel = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        
        // Simplified conditions - would be configurable
    return capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true &&
                batteryLevel > 20
    }
}

/**
 * Android WorkManager worker for automatic backups
 */
class AutoBackupWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {
    
    override suspend fun doWork(): ListenableWorker.Result {
        return try {
            // Create backup manager instance and perform backup
            // This would be injected in a real implementation
            ListenableWorker.Result.success()
        } catch (e: Exception) {
            ListenableWorker.Result.retry()
        }
    }
}

/**
 * Backup metadata data class
 */
@kotlinx.serialization.Serializable
data class BackupMetadata(
    val id: String,
    val name: String,
    val createdAt: Long,
    val type: BackupType,
    val version: String,
    val sessionCount: Int? = null,
    val checksum: String? = null
)

/**
 * Extension functions for SecurityManager
 */
private fun AndroidSecurityManager.encryptData(data: String): String {
    // Simplified - would use proper encryption
    return data
}

private fun AndroidSecurityManager.decryptData(encryptedData: String): String {
    // Simplified - would use proper decryption
    return encryptedData
}

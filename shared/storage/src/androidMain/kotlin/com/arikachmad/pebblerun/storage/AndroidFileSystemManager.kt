package com.arikachmad.pebblerun.storage

import android.content.Context
import android.os.Environment
import android.os.StatFs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

/**
 * Android-specific file system manager
 * Supports TASK-040: Android-specific file system access and management
 * 
 * Handles secure file operations, storage management, and backup functionality
 */
class AndroidFileSystemManager(private val context: Context) {
    
    companion object {
        private const val BACKUP_DIR = "workout_backups"
        private const val EXPORT_DIR = "workout_exports"
        private const val TEMP_DIR = "temp"
        private const val MAX_BACKUP_COUNT = 10
        private val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US)
    }
    
    private val internalStorageDir = context.filesDir
    private val externalStorageDir = context.getExternalFilesDir(null)
    
    /**
     * Gets the backup directory, creating it if necessary
     */
    suspend fun getBackupDirectory(): File = withContext(Dispatchers.IO) {
        val backupDir = File(internalStorageDir, BACKUP_DIR)
        if (!backupDir.exists()) {
            backupDir.mkdirs()
        }
        backupDir
    }
    
    /**
     * Gets the export directory, creating it if necessary
     * Uses external storage if available for user access
     */
    suspend fun getExportDirectory(): File = withContext(Dispatchers.IO) {
        val baseDir = externalStorageDir ?: internalStorageDir
        val exportDir = File(baseDir, EXPORT_DIR)
        if (!exportDir.exists()) {
            exportDir.mkdirs()
        }
        exportDir
    }
    
    /**
     * Gets the temporary directory, creating it if necessary
     */
    suspend fun getTempDirectory(): File = withContext(Dispatchers.IO) {
        val tempDir = File(internalStorageDir, TEMP_DIR)
        if (!tempDir.exists()) {
            tempDir.mkdirs()
        }
        tempDir
    }
    
    /**
     * Creates a backup of the database file
     * Returns the backup file path
     */
    suspend fun createDatabaseBackup(): String = withContext(Dispatchers.IO) {
        val backupDir = getBackupDirectory()
        val timestamp = DATE_FORMAT.format(Date())
        val backupFile = File(backupDir, "workout_backup_$timestamp.db")
        
        val databaseFile = context.getDatabasePath("workout_encrypted.db")
        if (databaseFile.exists()) {
            copyFile(databaseFile, backupFile)
            cleanupOldBackups(backupDir)
        } else {
            throw IOException("Database file not found")
        }
        
        backupFile.absolutePath
    }
    
    /**
     * Restores database from backup file
     */
    suspend fun restoreDatabaseFromBackup(backupPath: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val backupFile = File(backupPath)
            if (!backupFile.exists()) {
                return@withContext false
            }
            
            val databaseFile = context.getDatabasePath("workout_encrypted.db")
            copyFile(backupFile, databaseFile)
            true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Exports workout data to a specified file format
     */
    suspend fun exportWorkoutData(data: String, fileName: String, format: ExportFormat): String = 
        withContext(Dispatchers.IO) {
            val exportDir = getExportDirectory()
            val timestamp = DATE_FORMAT.format(Date())
            val fileExtension = when (format) {
                ExportFormat.JSON -> "json"
                ExportFormat.CSV -> "csv"
                ExportFormat.GPX -> "gpx"
            }
            
            val exportFile = File(exportDir, "${fileName}_$timestamp.$fileExtension")
            exportFile.writeText(data)
            exportFile.absolutePath
        }
    
    /**
     * Imports workout data from a file
     */
    suspend fun importWorkoutData(filePath: String): String = withContext(Dispatchers.IO) {
        val file = File(filePath)
        if (!file.exists()) {
            throw IOException("Import file not found: $filePath")
        }
        file.readText()
    }
    
    /**
     * Gets available storage space information
     */
    suspend fun getStorageInfo(): StorageInfo = withContext(Dispatchers.IO) {
        val internalStats = StatFs(internalStorageDir.absolutePath)
        val internalAvailable = internalStats.blockSizeLong * internalStats.availableBlocksLong
        val internalTotal = internalStats.blockSizeLong * internalStats.blockCountLong
        
        val externalAvailable = externalStorageDir?.let { dir ->
            val externalStats = StatFs(dir.absolutePath)
            externalStats.blockSizeLong * externalStats.availableBlocksLong
        } ?: 0L
        
        StorageInfo(
            internalAvailable = internalAvailable,
            internalTotal = internalTotal,
            externalAvailable = externalAvailable,
            isExternalStorageAvailable = externalStorageDir != null
        )
    }
    
    /**
     * Lists all backup files
     */
    suspend fun listBackupFiles(): List<BackupFileInfo> = withContext(Dispatchers.IO) {
        val backupDir = getBackupDirectory()
        backupDir.listFiles()?.filter { it.name.endsWith(".db") }?.map { file ->
            BackupFileInfo(
                path = file.absolutePath,
                name = file.name,
                size = file.length(),
                lastModified = file.lastModified()
            )
    }?.sortedByDescending { it.lastModified } ?: emptyList()
    }
    
    /**
     * Lists all export files
     */
    suspend fun listExportFiles(): List<ExportFileInfo> = withContext(Dispatchers.IO) {
        val exportDir = getExportDirectory()
        exportDir.listFiles()?.map { file ->
            val format = when (file.extension.lowercase()) {
                "json" -> ExportFormat.JSON
                "csv" -> ExportFormat.CSV
                "gpx" -> ExportFormat.GPX
                else -> ExportFormat.JSON
            }
            
            ExportFileInfo(
                path = file.absolutePath,
                name = file.name,
                size = file.length(),
                lastModified = file.lastModified(),
                format = format
            )
    }?.sortedByDescending { it.lastModified } ?: emptyList()
    }
    
    /**
     * Deletes a backup file
     */
    suspend fun deleteBackupFile(filePath: String): Boolean = withContext(Dispatchers.IO) {
        try {
            File(filePath).delete()
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Deletes an export file
     */
    suspend fun deleteExportFile(filePath: String): Boolean = withContext(Dispatchers.IO) {
        try {
            File(filePath).delete()
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Cleans up temporary files
     */
    suspend fun cleanupTempFiles() = withContext(Dispatchers.IO) {
        val tempDir = getTempDirectory()
        tempDir.listFiles()?.forEach { file ->
            if (file.lastModified() < System.currentTimeMillis() - 24 * 60 * 60 * 1000) { // 24 hours
                file.delete()
            }
        }
    }
    
    /**
     * Copies a file from source to destination
     */
    private fun copyFile(source: File, destination: File) {
        FileInputStream(source).use { input ->
            FileOutputStream(destination).use { output ->
                input.copyTo(output)
            }
        }
    }
    
    /**
     * Removes old backup files, keeping only the latest MAX_BACKUP_COUNT
     */
    private fun cleanupOldBackups(backupDir: File) {
        val backupFiles = backupDir.listFiles()?.filter { it.name.endsWith(".db") }
            ?.sortedByDescending { it.lastModified() } ?: return
        
        if (backupFiles.size > MAX_BACKUP_COUNT) {
            backupFiles.drop(MAX_BACKUP_COUNT).forEach { it.delete() }
        }
    }
    
    /**
     * Checks if external storage is writable
     */
    fun isExternalStorageWritable(): Boolean {
        return Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED
    }
    
    /**
     * Checks if external storage is readable
     */
    fun isExternalStorageReadable(): Boolean {
        val state = Environment.getExternalStorageState()
        return state == Environment.MEDIA_MOUNTED || state == Environment.MEDIA_MOUNTED_READ_ONLY
    }
}

/**
 * Storage information data class
 */
data class StorageInfo(
    val internalAvailable: Long,
    val internalTotal: Long,
    val externalAvailable: Long,
    val isExternalStorageAvailable: Boolean
)

/**
 * Backup file information data class
 */
data class BackupFileInfo(
    val path: String,
    val name: String,
    val size: Long,
    val lastModified: Long
)

/**
 * Export file information data class
 */
data class ExportFileInfo(
    val path: String,
    val name: String,
    val size: Long,
    val lastModified: Long,
    val format: ExportFormat
)



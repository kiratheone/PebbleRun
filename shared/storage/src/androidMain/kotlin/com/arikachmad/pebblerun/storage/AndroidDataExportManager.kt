package com.arikachmad.pebblerun.storage

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Android-specific data export/import manager
 * Supports TASK-041: Android-specific data export/import functionality
 * 
 * Handles various export formats and sharing capabilities
 */
class AndroidDataExportManager(
    private val context: Context,
    private val fileSystemManager: AndroidFileSystemManager
) {
    
    companion object {
        private val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US)
        private const val PROVIDER_AUTHORITY = "com.arikachmad.pebblerun.fileprovider"
    }
    
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }
    
    /**
     * Exports workout sessions to JSON format
     */
    suspend fun exportToJson(
        sessions: List<WorkoutSessionExport>,
        fileName: String? = null
    ): ExportResult = withContext(Dispatchers.IO) {
        try {
            val exportData = WorkoutExportData(
                exportDate = System.currentTimeMillis(),
                version = "1.0",
                sessions = sessions
            )
            
            val jsonString = json.encodeToString(exportData)
            val finalFileName = fileName ?: "pebble_run_workouts"
            val filePath = fileSystemManager.exportWorkoutData(jsonString, finalFileName, ExportFormat.JSON)
            
            ExportResult.Success(filePath, ExportFormat.JSON)
        } catch (e: Exception) {
            ExportResult.Error("Failed to export to JSON: ${e.message}")
        }
    }
    
    /**
     * Exports workout sessions to CSV format
     */
    suspend fun exportToCsv(
        sessions: List<WorkoutSessionExport>,
        fileName: String? = null
    ): ExportResult = withContext(Dispatchers.IO) {
        try {
            val csvBuilder = StringBuilder()
            
            // CSV Header
            csvBuilder.appendLine("Session ID,Start Time,End Time,Duration (seconds),Distance (meters),Average Pace,Average HR,Max HR,Min HR,GPS Points,HR Samples")
            
            // CSV Data
            sessions.forEach { session ->
                csvBuilder.appendLine(
                    "${session.id}," +
                    "${session.startTime}," +
                    "${session.endTime ?: ""}," +
                    "${session.duration}," +
                    "${session.distance}," +
                    "${session.averagePace ?: ""}," +
                    "${session.averageHeartRate ?: ""}," +
                    "${session.maxHeartRate ?: ""}," +
                    "${session.minHeartRate ?: ""}," +
                    "${session.gpsPoints.size}," +
                    "${session.hrSamples.size}"
                )
            }
            
            val finalFileName = fileName ?: "pebble_run_workouts"
            val filePath = fileSystemManager.exportWorkoutData(csvBuilder.toString(), finalFileName, ExportFormat.CSV)
            
            ExportResult.Success(filePath, ExportFormat.CSV)
        } catch (e: Exception) {
            ExportResult.Error("Failed to export to CSV: ${e.message}")
        }
    }
    
    /**
     * Exports a single workout session to GPX format
     */
    suspend fun exportToGpx(
        session: WorkoutSessionExport,
        fileName: String? = null
    ): ExportResult = withContext(Dispatchers.IO) {
        try {
            val gpxBuilder = StringBuilder()
            
            // GPX Header
            gpxBuilder.appendLine("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
            gpxBuilder.appendLine("<gpx version=\"1.1\" creator=\"PebbleRun\"")
            gpxBuilder.appendLine("     xmlns=\"http://www.topografix.com/GPX/1/1\"")
            gpxBuilder.appendLine("     xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"")
            gpxBuilder.appendLine("     xsi:schemaLocation=\"http://www.topografix.com/GPX/1/1 http://www.topografix.com/GPX/1/1/gpx.xsd\">")
            
            // Metadata
            gpxBuilder.appendLine("  <metadata>")
            gpxBuilder.appendLine("    <name>PebbleRun Workout - ${Date(session.startTime)}</name>")
            gpxBuilder.appendLine("    <desc>Workout session exported from PebbleRun</desc>")
            gpxBuilder.appendLine("    <time>${formatGpxTime(session.startTime)}</time>")
            gpxBuilder.appendLine("  </metadata>")
            
            // Track
            gpxBuilder.appendLine("  <trk>")
            gpxBuilder.appendLine("    <name>Workout Track</name>")
            gpxBuilder.appendLine("    <type>running</type>")
            gpxBuilder.appendLine("    <trkseg>")
            
            // Track points with heart rate data
            session.gpsPoints.forEach { point ->
                gpxBuilder.appendLine("      <trkpt lat=\"${point.latitude}\" lon=\"${point.longitude}\">")
                if (point.altitude != null) {
                    gpxBuilder.appendLine("        <ele>${point.altitude}</ele>")
                }
                gpxBuilder.appendLine("        <time>${formatGpxTime(point.timestamp)}</time>")
                
                // Add heart rate if available for this timestamp
                val hrSample = session.hrSamples.find { 
                    Math.abs(it.timestamp - point.timestamp) < 5000 // Within 5 seconds
                }
                if (hrSample != null) {
                    gpxBuilder.appendLine("        <extensions>")
                    gpxBuilder.appendLine("          <hr>${hrSample.heartRate}</hr>")
                    gpxBuilder.appendLine("        </extensions>")
                }
                
                gpxBuilder.appendLine("      </trkpt>")
            }
            
            gpxBuilder.appendLine("    </trkseg>")
            gpxBuilder.appendLine("  </trk>")
            gpxBuilder.appendLine("</gpx>")
            
            val finalFileName = fileName ?: "pebble_run_workout_${session.id}"
            val filePath = fileSystemManager.exportWorkoutData(gpxBuilder.toString(), finalFileName, ExportFormat.GPX)
            
            ExportResult.Success(filePath, ExportFormat.GPX)
        } catch (e: Exception) {
            ExportResult.Error("Failed to export to GPX: ${e.message}")
        }
    }
    
    /**
     * Imports workout data from JSON file
     */
    suspend fun importFromJson(filePath: String): ImportResult = withContext(Dispatchers.IO) {
        try {
            val jsonString = fileSystemManager.importWorkoutData(filePath)
            val exportData = json.decodeFromString<WorkoutExportData>(jsonString)
            
            ImportResult.Success(exportData.sessions, exportData.version)
        } catch (e: Exception) {
            ImportResult.Error("Failed to import from JSON: ${e.message}")
        }
    }
    
    /**
     * Creates a share intent for the exported file
     */
    suspend fun shareExportedFile(filePath: String, format: ExportFormat): Intent = withContext(Dispatchers.IO) {
        val file = File(filePath)
        val uri = FileProvider.getUriForFile(context, PROVIDER_AUTHORITY, file)
        
        val mimeType = when (format) {
            ExportFormat.JSON -> "application/json"
            ExportFormat.CSV -> "text/csv"
            ExportFormat.GPX -> "application/gpx+xml"
        }
        
        Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "PebbleRun Workout Data")
            putExtra(Intent.EXTRA_TEXT, "Workout data exported from PebbleRun")
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
    }
    
    /**
     * Validates import file format and content
     */
    suspend fun validateImportFile(filePath: String): ValidationResult = withContext(Dispatchers.IO) {
        try {
            val file = File(filePath)
            if (!file.exists()) {
                return@withContext ValidationResult.Error("File does not exist")
            }
            
            val extension = file.extension.lowercase()
            when (extension) {
                "json" -> validateJsonFile(filePath)
                "csv" -> validateCsvFile(filePath)
                "gpx" -> validateGpxFile(filePath)
                else -> ValidationResult.Error("Unsupported file format: $extension")
            }
        } catch (e: Exception) {
            ValidationResult.Error("Validation failed: ${e.message}")
        }
    }
    
    /**
     * Gets file size in human-readable format
     */
    fun getFileSizeString(filePath: String): String {
        val file = File(filePath)
        val bytes = file.length()
        
        return when {
            bytes >= 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
            bytes >= 1024 -> "${bytes / 1024} KB"
            else -> "$bytes bytes"
        }
    }
    
    private fun validateJsonFile(filePath: String): ValidationResult {
        return try {
            val jsonString = File(filePath).readText()
            json.decodeFromString<WorkoutExportData>(jsonString)
            ValidationResult.Success("Valid JSON export file")
        } catch (e: Exception) {
            ValidationResult.Error("Invalid JSON format: ${e.message}")
        }
    }
    
    private fun validateCsvFile(filePath: String): ValidationResult {
        return try {
            val lines = File(filePath).readLines()
            if (lines.isEmpty()) {
                return ValidationResult.Error("CSV file is empty")
            }
            
            val header = lines.first()
            val expectedColumns = listOf("Session ID", "Start Time", "End Time", "Duration")
            val hasRequiredColumns = expectedColumns.any { header.contains(it) }
            
            if (hasRequiredColumns) {
                ValidationResult.Success("Valid CSV export file")
            } else {
                ValidationResult.Error("CSV file missing required columns")
            }
        } catch (e: Exception) {
            ValidationResult.Error("Invalid CSV format: ${e.message}")
        }
    }
    
    private fun validateGpxFile(filePath: String): ValidationResult {
        return try {
            val content = File(filePath).readText()
            if (content.contains("<gpx") && content.contains("</gpx>")) {
                ValidationResult.Success("Valid GPX file")
            } else {
                ValidationResult.Error("Invalid GPX format")
            }
        } catch (e: Exception) {
            ValidationResult.Error("Invalid GPX format: ${e.message}")
        }
    }
    
    private fun formatGpxTime(timestamp: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date(timestamp))
    }
}

/**
 * Import result sealed class
 */
sealed class ImportResult {
    data class Success(val sessions: List<WorkoutSessionExport>, val version: String) : ImportResult()
    data class Error(val message: String) : ImportResult()
}

package com.arikachmad.pebblerun.storage

import kotlinx.serialization.Serializable

/**
 * Export format enumeration for workout data export
 * Supports TASK-041 and TASK-045 (Export/Import functionality)
 */
enum class ExportFormat {
    JSON,
    CSV,
    GPX
}

/**
 * Export result sealed class for export operations
 */
sealed class ExportResult {
    data class Success(val filePath: String, val format: ExportFormat) : ExportResult()
    data class Error(val message: String, val cause: Throwable? = null) : ExportResult()
}

/**
 * Data class representing an exported file
 */
data class ExportedFile(
    val name: String,
    val path: String,
    val size: Long,
    val lastModified: Long,
    val format: ExportFormat
)

/**
 * Export data models for workout sessions
 */
@Serializable
data class WorkoutSessionExport(
    val id: String,
    val startTime: Long,
    val endTime: Long?,
    val duration: Long,
    val distance: Double,
    val averagePace: String,
    val averageHeartRate: Int,
    val maxHeartRate: Int,
    val minHeartRate: Int,
    val gpsPoints: List<GeoPointExport>,
    val hrSamples: List<HRSampleExport>
)

@Serializable
data class GeoPointExport(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double,
    val timestamp: Long
)

@Serializable
data class HRSampleExport(
    val heartRate: Int,
    val timestamp: Long
)

@Serializable
data class WorkoutExportData(
    val exportedAt: Long,
    val version: String,
    val sessions: List<WorkoutSessionExport>
)

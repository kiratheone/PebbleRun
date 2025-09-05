package com.arikachmad.pebblerun.data.backup

import com.arikachmad.pebblerun.domain.entity.WorkoutSession
import com.arikachmad.pebblerun.domain.repository.WorkoutRepository
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

/**
 * Service for backing up and restoring workout data.
 * Satisfies TASK-019: Create backup and restore functionality for workout data.
 * Supports data portability and recovery scenarios.
 */
interface BackupService {
    
    /**
     * Creates a backup of all workout sessions
     * @return Backup data as JSON string
     */
    suspend fun createFullBackup(): Result<BackupData>
    
    /**
     * Creates a backup of specific workout sessions
     * @param sessionIds List of session IDs to backup
     * @return Backup data as JSON string
     */
    suspend fun createPartialBackup(sessionIds: List<String>): Result<BackupData>
    
    /**
     * Restores workout data from backup
     * @param backupData The backup data to restore
     * @param overwriteExisting Whether to overwrite existing sessions
     * @return List of restored sessions
     */
    suspend fun restoreFromBackup(
        backupData: BackupData,
        overwriteExisting: Boolean = false
    ): Result<BackupRestoreResult>
    
    /**
     * Validates backup data integrity
     */
    suspend fun validateBackup(backupData: BackupData): Result<BackupValidation>
    
    /**
     * Gets backup metadata without full restoration
     */
    suspend fun getBackupInfo(backupData: BackupData): Result<BackupInfo>
}

/**
 * Implementation of BackupService using WorkoutRepository
 */
class WorkoutBackupService(
    private val repository: WorkoutRepository
) : BackupService {

    override suspend fun createFullBackup(): Result<BackupData> {
        return try {
            val sessions = repository.getAllSessions().getOrThrow()
            val backupData = BackupData(
                version = BACKUP_VERSION,
                createdAt = Clock.System.now().epochSeconds,
                sessionCount = sessions.size,
                sessions = sessions.map { it.toBackupSession() }
            )
            Result.success(backupData)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun createPartialBackup(sessionIds: List<String>): Result<BackupData> {
        return try {
            val sessions = mutableListOf<WorkoutSession>()
            for (sessionId in sessionIds) {
                val session = repository.getSessionById(sessionId).getOrNull()
                if (session != null) {
                    sessions.add(session)
                }
            }
            
            val backupData = BackupData(
                version = BACKUP_VERSION,
                createdAt = Clock.System.now().epochSeconds,
                sessionCount = sessions.size,
                sessions = sessions.map { it.toBackupSession() }
            )
            Result.success(backupData)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun restoreFromBackup(
        backupData: BackupData,
        overwriteExisting: Boolean
    ): Result<BackupRestoreResult> = coroutineScope {
        try {
            val validationResult = validateBackup(backupData).getOrThrow()
            if (!validationResult.isValid) {
                return@coroutineScope Result.failure(
                    IllegalArgumentException("Invalid backup data: ${validationResult.errors.joinToString()}")
                )
            }

            val restoredSessions = mutableListOf<WorkoutSession>()
            val skippedSessions = mutableListOf<String>()
            val failedSessions = mutableListOf<Pair<String, String>>()

            for (backupSession in backupData.sessions) {
                try {
                    val session = backupSession.toWorkoutSession()
                    
                    // Check if session already exists
                    val existingSession = repository.getSessionById(session.id).getOrNull()
                    if (existingSession != null && !overwriteExisting) {
                        skippedSessions.add(session.id)
                        continue
                    }

                    // Create or update the session
                    val result = if (existingSession != null) {
                        repository.updateSession(session)
                    } else {
                        repository.createSession(session)
                    }

                    if (result.isSuccess) {
                        restoredSessions.add(session)
                    } else {
                        failedSessions.add(session.id to (result.exceptionOrNull()?.message ?: "Unknown error"))
                    }
                } catch (e: Exception) {
                    failedSessions.add(backupSession.id to e.message.orEmpty())
                }
            }

            val restoreResult = BackupRestoreResult(
                totalSessions = backupData.sessionCount,
                restoredSessions = restoredSessions.size,
                skippedSessions = skippedSessions.size,
                failedSessions = failedSessions.size,
                restoredSessionIds = restoredSessions.map { it.id },
                skippedSessionIds = skippedSessions,
                failedSessionDetails = failedSessions.toMap()
            )

            Result.success(restoreResult)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun validateBackup(backupData: BackupData): Result<BackupValidation> {
        return try {
            val errors = mutableListOf<String>()
            val warnings = mutableListOf<String>()

            // Check backup version compatibility
            if (backupData.version != BACKUP_VERSION) {
                if (isVersionCompatible(backupData.version)) {
                    warnings.add("Backup version ${backupData.version} is older than current version $BACKUP_VERSION")
                } else {
                    errors.add("Backup version ${backupData.version} is not compatible with current version $BACKUP_VERSION")
                }
            }

            // Validate session count
            if (backupData.sessionCount != backupData.sessions.size) {
                errors.add("Session count mismatch: expected ${backupData.sessionCount}, found ${backupData.sessions.size}")
            }

            // Validate individual sessions
            for ((index, session) in backupData.sessions.withIndex()) {
                try {
                    session.toWorkoutSession() // This will throw if conversion fails
                } catch (e: Exception) {
                    errors.add("Invalid session at index $index: ${e.message}")
                }
            }

            val validation = BackupValidation(
                isValid = errors.isEmpty(),
                errors = errors,
                warnings = warnings,
                sessionCount = backupData.sessions.size,
                backupVersion = backupData.version,
                backupDate = backupData.createdAt
            )

            Result.success(validation)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getBackupInfo(backupData: BackupData): Result<BackupInfo> {
        return try {
            val info = BackupInfo(
                version = backupData.version,
                createdAt = backupData.createdAt,
                sessionCount = backupData.sessionCount,
                totalDistance = backupData.sessions.sumOf { it.totalDistance },
                totalDuration = backupData.sessions.sumOf { it.totalDuration },
                dateRange = if (backupData.sessions.isNotEmpty()) {
                    val startTimes = backupData.sessions.map { it.startTime }
                    Pair(startTimes.minOrNull() ?: 0L, startTimes.maxOrNull() ?: 0L)
                } else {
                    Pair(0L, 0L)
                }
            )
            Result.success(info)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun isVersionCompatible(version: String): Boolean {
        // Simple version compatibility check
        // In production, implement proper semantic versioning comparison
        return version.startsWith("1.")
    }

    companion object {
        private const val BACKUP_VERSION = "1.0"
    }
}

/**
 * Data class representing backup data structure
 */
data class BackupData(
    val version: String,
    val createdAt: Long, // Epoch seconds
    val sessionCount: Int,
    val sessions: List<BackupSession>
)

/**
 * Simplified session representation for backup
 */
data class BackupSession(
    val id: String,
    val startTime: Long,
    val endTime: Long?,
    val status: String,
    val totalDistance: Double,
    val totalDuration: Long,
    val averagePace: Double,
    val averageHeartRate: Int,
    val maxHeartRate: Int,
    val minHeartRate: Int,
    val geoPointCount: Int,
    val hrSampleCount: Int,
    // Simplified data - full GPS and HR data would be very large
    // In production, consider separate backup files for detailed data
    val summaryData: Map<String, String> = emptyMap()
)

/**
 * Result of backup restoration operation
 */
data class BackupRestoreResult(
    val totalSessions: Int,
    val restoredSessions: Int,
    val skippedSessions: Int,
    val failedSessions: Int,
    val restoredSessionIds: List<String>,
    val skippedSessionIds: List<String>,
    val failedSessionDetails: Map<String, String>
) {
    val isSuccess: Boolean get() = failedSessions == 0
    val hasPartialSuccess: Boolean get() = restoredSessions > 0 && failedSessions > 0
}

/**
 * Validation result for backup data
 */
data class BackupValidation(
    val isValid: Boolean,
    val errors: List<String>,
    val warnings: List<String>,
    val sessionCount: Int,
    val backupVersion: String,
    val backupDate: Long
)

/**
 * Information about backup contents
 */
data class BackupInfo(
    val version: String,
    val createdAt: Long,
    val sessionCount: Int,
    val totalDistance: Double,
    val totalDuration: Long,
    val dateRange: Pair<Long, Long>
)

/**
 * Extension functions for converting between domain and backup models
 */
private fun WorkoutSession.toBackupSession(): BackupSession {
    return BackupSession(
        id = id,
        startTime = startTime.epochSeconds,
        endTime = endTime?.epochSeconds,
        status = status.name,
        totalDistance = totalDistance,
        totalDuration = totalDuration,
        averagePace = averagePace,
        averageHeartRate = averageHeartRate,
        maxHeartRate = maxHeartRate,
        minHeartRate = minHeartRate,
        geoPointCount = geoPoints.size,
        hrSampleCount = hrSamples.size
    )
}

private fun BackupSession.toWorkoutSession(): WorkoutSession {
    return WorkoutSession(
        id = id,
        startTime = kotlinx.datetime.Instant.fromEpochSeconds(startTime),
        endTime = endTime?.let { kotlinx.datetime.Instant.fromEpochSeconds(it) },
        status = com.arikachmad.pebblerun.domain.entity.WorkoutStatus.valueOf(status),
        totalDistance = totalDistance,
        totalDuration = totalDuration,
        averagePace = averagePace,
        averageHeartRate = averageHeartRate,
        maxHeartRate = maxHeartRate,
        minHeartRate = minHeartRate,
        geoPoints = emptyList(), // Simplified - would need to restore from detailed backup
        hrSamples = emptyList() // Simplified - would need to restore from detailed backup
    )
}

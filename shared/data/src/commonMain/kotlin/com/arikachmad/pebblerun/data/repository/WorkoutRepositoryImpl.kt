package com.arikachmad.pebblerun.data.repository

import com.arikachmad.pebblerun.data.mapper.WorkoutDataMapper
import com.arikachmad.pebblerun.domain.entity.WorkoutSession
import com.arikachmad.pebblerun.domain.entity.WorkoutStatus
import com.arikachmad.pebblerun.domain.repository.WorkoutRepository
import com.arikachmad.pebblerun.domain.repository.WorkoutSessionStats
import com.arikachmad.pebblerun.storage.WorkoutDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/**
 * Implementation of WorkoutRepository that uses SQLDelight for local data storage.
 * Satisfies REQ-005 (Local storage of workout data) and PAT-002 (Repository pattern).
 * Supports TASK-016: Implement WorkoutRepository with CRUD operations.
 */
class WorkoutRepositoryImpl(
    private val database: WorkoutDatabase,
    private val mapper: WorkoutDataMapper
) : WorkoutRepository {

    override suspend fun createSession(session: WorkoutSession): Result<WorkoutSession> {
        return try {
            database.transaction {
                // Insert the main session
                database.workoutDatabaseQueries.insertWorkoutSession(
                    id = session.id,
                    startTime = session.startTime.epochSeconds,
                    endTime = session.endTime?.epochSeconds,
                    status = session.status.name,
                    totalDistance = session.totalDistance,
                    totalDuration = session.totalDuration,
                    averagePace = session.averagePace,
                    averageHeartRate = if (session.averageHeartRate == 0) null else session.averageHeartRate.toLong(),
                    maxHeartRate = if (session.maxHeartRate == 0) null else session.maxHeartRate.toLong(),
                    minHeartRate = if (session.minHeartRate == 0) null else session.minHeartRate.toLong(),
                    createdAt = session.startTime.epochSeconds,
                    updatedAt = session.startTime.epochSeconds
                )

                // Insert geo points
                session.geoPoints.forEach { geoPoint ->
                    val pointData = mapper.mapGeoPointToData(geoPoint, session.id)
                    database.workoutDatabaseQueries.insertGeoPoint(
                        id = pointData.id,
                        sessionId = pointData.sessionId,
                        latitude = pointData.latitude,
                        longitude = pointData.longitude,
                        altitude = pointData.altitude,
                        accuracy = pointData.accuracy,
                        timestamp = pointData.timestamp,
                        speed = pointData.speed,
                        bearing = pointData.bearing
                    )
                }

                // Insert HR samples
                session.hrSamples.forEach { hrSample ->
                    val sampleData = mapper.mapHRSampleToData(hrSample)
                    database.workoutDatabaseQueries.insertHRSample(
                        id = sampleData.id,
                        sessionId = sampleData.sessionId,
                        heartRate = sampleData.heartRate,
                        timestamp = sampleData.timestamp,
                        quality = sampleData.quality,
                        source = sampleData.source
                    )
                }
            }
            Result.success(session)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateSession(session: WorkoutSession): Result<WorkoutSession> {
        return try {
            database.transaction {
                database.workoutDatabaseQueries.updateWorkoutSession(
                    endTime = session.endTime?.epochSeconds,
                    status = session.status.name,
                    totalDistance = session.totalDistance,
                    totalDuration = session.totalDuration,
                    averagePace = session.averagePace,
                    averageHeartRate = if (session.averageHeartRate == 0) null else session.averageHeartRate.toLong(),
                    maxHeartRate = if (session.maxHeartRate == 0) null else session.maxHeartRate.toLong(),
                    minHeartRate = if (session.minHeartRate == 0) null else session.minHeartRate.toLong(),
                    updatedAt = Clock.System.now().epochSeconds,
                    id = session.id
                )
            }
            Result.success(session)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getSessionById(id: String): Result<WorkoutSession?> {
        return try {
            val sessionData = database.workoutDatabaseQueries.selectById(id).executeAsOneOrNull()
            if (sessionData == null) {
                return Result.success(null)
            }

            val geoPoints = database.workoutDatabaseQueries.selectGeoPointsBySession(id).executeAsList()
            val hrSamples = database.workoutDatabaseQueries.selectHRSamplesBySession(id).executeAsList()

            val session = mapper.mapSessionDataToDomain(sessionData, geoPoints, hrSamples)
            Result.success(session)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getAllSessions(
        limit: Int?,
        offset: Int,
        status: WorkoutStatus?,
        startDate: Instant?,
        endDate: Instant?
    ): Result<List<WorkoutSession>> {
        return try {
            val sessions = if (status != null) {
                database.workoutDatabaseQueries.selectByStatus(status.name).executeAsList()
            } else {
                database.workoutDatabaseQueries.selectAll().executeAsList()
            }

            val domainSessions = sessions.map { sessionData ->
                val geoPoints = database.workoutDatabaseQueries.selectGeoPointsBySession(sessionData.id).executeAsList()
                val hrSamples = database.workoutDatabaseQueries.selectHRSamplesBySession(sessionData.id).executeAsList()
                mapper.mapSessionDataToDomain(sessionData, geoPoints, hrSamples)
            }

            Result.success(domainSessions)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun observeSessions(): Flow<List<WorkoutSession>> {
        return flow {
            try {
                val sessions = database.workoutDatabaseQueries.selectAll().executeAsList()
                val domainSessions = sessions.map { sessionData ->
                    val geoPoints = database.workoutDatabaseQueries.selectGeoPointsBySession(sessionData.id).executeAsList()
                    val hrSamples = database.workoutDatabaseQueries.selectHRSamplesBySession(sessionData.id).executeAsList()
                    mapper.mapSessionDataToDomain(sessionData, geoPoints, hrSamples)
                }
                emit(domainSessions)
            } catch (e: Exception) {
                throw e
            }
        }.catch { emit(emptyList()) }
    }

    override fun observeSession(id: String): Flow<WorkoutSession?> {
        return flow {
            try {
                val sessionData = database.workoutDatabaseQueries.selectById(id).executeAsOneOrNull()
                if (sessionData != null) {
                    val geoPoints = database.workoutDatabaseQueries.selectGeoPointsBySession(id).executeAsList()
                    val hrSamples = database.workoutDatabaseQueries.selectHRSamplesBySession(id).executeAsList()
                    val session = mapper.mapSessionDataToDomain(sessionData, geoPoints, hrSamples)
                    emit(session)
                } else {
                    emit(null)
                }
            } catch (e: Exception) {
                emit(null)
            }
        }
    }

    override suspend fun deleteSession(id: String): Result<Unit> {
        return try {
            database.transaction {
                database.workoutDatabaseQueries.deleteGeoPointsBySession(id)
                database.workoutDatabaseQueries.deleteHRSamplesBySession(id)
                database.workoutDatabaseQueries.deleteWorkoutSession(id)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getActiveSession(): Result<WorkoutSession?> {
        return try {
            val activeSessions = database.workoutDatabaseQueries.selectActive().executeAsList()
            if (activeSessions.isEmpty()) {
                return Result.success(null)
            }

            val sessionData = activeSessions.first()
            val geoPoints = database.workoutDatabaseQueries.selectGeoPointsBySession(sessionData.id).executeAsList()
            val hrSamples = database.workoutDatabaseQueries.selectHRSamplesBySession(sessionData.id).executeAsList()

            val session = mapper.mapSessionDataToDomain(sessionData, geoPoints, hrSamples)
            Result.success(session)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun observeActiveSession(): Flow<WorkoutSession?> {
        return flow {
            try {
                val activeSessions = database.workoutDatabaseQueries.selectActive().executeAsList()
                if (activeSessions.isNotEmpty()) {
                    val sessionData = activeSessions.first()
                    val geoPoints = database.workoutDatabaseQueries.selectGeoPointsBySession(sessionData.id).executeAsList()
                    val hrSamples = database.workoutDatabaseQueries.selectHRSamplesBySession(sessionData.id).executeAsList()
                    val session = mapper.mapSessionDataToDomain(sessionData, geoPoints, hrSamples)
                    emit(session)
                } else {
                    emit(null)
                }
            } catch (e: Exception) {
                emit(null)
            }
        }
    }

    override suspend fun completeSession(
        id: String,
        endTime: Instant,
        finalStats: WorkoutSessionStats
    ): Result<WorkoutSession> {
        return try {
            database.transaction {
                database.workoutDatabaseQueries.updateWorkoutSession(
                    endTime = endTime.epochSeconds,
                    status = WorkoutStatus.COMPLETED.name,
                    totalDistance = finalStats.totalDistance,
                    totalDuration = finalStats.totalDuration,
                    averagePace = finalStats.averagePace,
                    averageHeartRate = if (finalStats.averageHeartRate == 0) null else finalStats.averageHeartRate.toLong(),
                    maxHeartRate = if (finalStats.maxHeartRate == 0) null else finalStats.maxHeartRate.toLong(),
                    minHeartRate = if (finalStats.minHeartRate == 0) null else finalStats.minHeartRate.toLong(),
                    updatedAt = Clock.System.now().epochSeconds,
                    id = id
                )
            }
            
            // Return the updated session
            getSessionById(id).getOrThrow()!!.let { Result.success(it) }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getSessionStats(id: String): Result<WorkoutSessionStats?> {
        return try {
            val sessionData = database.workoutDatabaseQueries.selectById(id).executeAsOneOrNull()
                ?: return Result.success(null)

            val stats = WorkoutSessionStats(
                sessionId = sessionData.id,
                totalDuration = sessionData.totalDuration,
                totalDistance = sessionData.totalDistance,
                averagePace = sessionData.averagePace ?: 0.0,
                averageHeartRate = sessionData.averageHeartRate?.toInt() ?: 0,
                maxHeartRate = sessionData.maxHeartRate?.toInt() ?: 0,
                minHeartRate = sessionData.minHeartRate?.toInt() ?: 0,
                caloriesBurned = 0 // TODO: Calculate based on HR and duration
            )
            
            Result.success(stats)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun exportSessions(sessionIds: List<String>): Result<String> {
        return try {
            // TODO: Implement JSON export functionality
            // This is a placeholder for TASK-019 backup functionality
            val sessions = sessionIds.mapNotNull { id ->
                getSessionById(id).getOrNull()
            }
            Result.success("Exported ${sessions.size} sessions") // Placeholder
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun importSessions(data: String): Result<List<WorkoutSession>> {
        return try {
            // TODO: Implement JSON import functionality
            // This is a placeholder for TASK-019 backup functionality
            Result.success(emptyList()) // Placeholder
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

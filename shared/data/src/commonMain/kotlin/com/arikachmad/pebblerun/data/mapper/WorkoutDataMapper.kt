package com.arikachmad.pebblerun.data.mapper

import com.arikachmad.pebblerun.domain.entity.GeoPoint as DomainGeoPoint
import com.arikachmad.pebblerun.domain.entity.HRQuality
import com.arikachmad.pebblerun.domain.entity.HRSample as DomainHRSample
import com.arikachmad.pebblerun.domain.entity.WorkoutSession as DomainWorkoutSession
import com.arikachmad.pebblerun.domain.entity.WorkoutStatus
import com.arikachmad.pebblerun.storage.GeoPoint as DataGeoPoint
import com.arikachmad.pebblerun.storage.HRSample as DataHRSample
import com.arikachmad.pebblerun.storage.WorkoutSession as DataWorkoutSession
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.random.Random

/**
 * Data mapper for converting between domain entities and data layer models.
 * Satisfies TASK-017: Create data mappers between domain and data layer.
 * Supports clean architecture separation between domain and data layers.
 */
class WorkoutDataMapper {

    /**
     * Maps domain WorkoutSession to data WorkoutSession for SQLDelight storage
     */
    fun mapSessionDomainToData(session: DomainWorkoutSession): DataWorkoutSession {
        return DataWorkoutSession(
            id = session.id,
            startTime = session.startTime.epochSeconds,
            endTime = session.endTime?.epochSeconds,
            status = session.status.name,
            totalDistance = session.totalDistance,
            totalDuration = session.totalDuration,
            averagePace = if (session.averagePace == 0.0) null else session.averagePace,
            averageHeartRate = if (session.averageHeartRate == 0) null else session.averageHeartRate.toLong(),
            maxHeartRate = if (session.maxHeartRate == 0) null else session.maxHeartRate.toLong(),
            minHeartRate = if (session.minHeartRate == 0) null else session.minHeartRate.toLong(),
            createdAt = session.startTime.epochSeconds,
            updatedAt = Clock.System.now().epochSeconds
        )
    }

    /**
     * Maps data WorkoutSession to domain WorkoutSession
     */
    fun mapSessionDataToDomain(
        sessionData: DataWorkoutSession,
        geoPoints: List<DataGeoPoint> = emptyList(),
        hrSamples: List<DataHRSample> = emptyList()
    ): DomainWorkoutSession {
        return DomainWorkoutSession(
            id = sessionData.id,
            startTime = Instant.fromEpochSeconds(sessionData.startTime),
            endTime = sessionData.endTime?.let { Instant.fromEpochSeconds(it) },
            status = WorkoutStatus.valueOf(sessionData.status),
            totalDuration = sessionData.totalDuration,
            totalDistance = sessionData.totalDistance,
            averagePace = sessionData.averagePace ?: 0.0,
            averageHeartRate = sessionData.averageHeartRate?.toInt() ?: 0,
            maxHeartRate = sessionData.maxHeartRate?.toInt() ?: 0,
            minHeartRate = sessionData.minHeartRate?.toInt() ?: 0,
            geoPoints = geoPoints.map { mapGeoPointDataToDomain(it) },
            hrSamples = hrSamples.map { mapHRSampleDataToDomain(it) }
        )
    }

    /**
     * Maps domain GeoPoint to data GeoPoint for storage
     */
    fun mapGeoPointToData(geoPoint: DomainGeoPoint, sessionId: String): DataGeoPoint {
        return DataGeoPoint(
            id = generateId(), // Generate unique ID for database
            sessionId = sessionId,
            latitude = geoPoint.latitude,
            longitude = geoPoint.longitude,
            altitude = geoPoint.altitude,
            accuracy = geoPoint.accuracy.toDouble(),
            timestamp = geoPoint.timestamp.epochSeconds,
            speed = geoPoint.speed?.toDouble(),
            bearing = geoPoint.bearing?.toDouble()
        )
    }

    /**
     * Maps data GeoPoint to domain GeoPoint
     */
    fun mapGeoPointDataToDomain(geoPointData: DataGeoPoint): DomainGeoPoint {
        return DomainGeoPoint(
            latitude = geoPointData.latitude,
            longitude = geoPointData.longitude,
            altitude = geoPointData.altitude,
            accuracy = geoPointData.accuracy.toFloat(),
            timestamp = Instant.fromEpochSeconds(geoPointData.timestamp),
            speed = geoPointData.speed?.toFloat(),
            bearing = geoPointData.bearing?.toFloat()
        )
    }

    /**
     * Maps domain HRSample to data HRSample for storage
     */
    fun mapHRSampleToData(hrSample: DomainHRSample): DataHRSample {
        return DataHRSample(
            id = generateId(), // Generate unique ID for database
            sessionId = hrSample.sessionId,
            heartRate = hrSample.heartRate.toLong(),
            timestamp = hrSample.timestamp.epochSeconds,
            quality = hrSample.quality.name,
            source = "PEBBLE" // Default source for HR samples from Pebble
        )
    }

    /**
     * Maps data HRSample to domain HRSample
     */
    fun mapHRSampleDataToDomain(hrSampleData: DataHRSample): DomainHRSample {
        return DomainHRSample(
            heartRate = hrSampleData.heartRate.toInt(),
            timestamp = Instant.fromEpochSeconds(hrSampleData.timestamp),
            quality = try {
                HRQuality.valueOf(hrSampleData.quality)
            } catch (e: IllegalArgumentException) {
                HRQuality.FAIR // Default to FAIR if unknown quality
            },
            sessionId = hrSampleData.sessionId
        )
    }

    /**
     * Generates a unique ID for database entities
     * Uses timestamp + random suffix for uniqueness
     */
    private fun generateId(): String {
        val timestamp = Clock.System.now().epochSeconds
        val random = Random.nextInt(1000, 9999)
        return "${timestamp}_$random"
    }
}

/**
 * Data class for mapping GeoPoint data to include session reference
 * Used internally for database operations
 */
data class GeoPointData(
    val id: String,
    val sessionId: String,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double?,
    val accuracy: Double,
    val timestamp: Long,
    val speed: Double?,
    val bearing: Double?
)

/**
 * Data class for mapping HRSample data to include metadata
 * Used internally for database operations
 */
data class HRSampleData(
    val id: String,
    val sessionId: String,
    val heartRate: Int,
    val timestamp: Long,
    val quality: String,
    val source: String
)

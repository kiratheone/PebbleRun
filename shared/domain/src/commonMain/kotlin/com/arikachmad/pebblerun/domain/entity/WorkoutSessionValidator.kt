package com.arikachmad.pebblerun.domain.entity

import kotlinx.datetime.Instant

/**
 * Session state transition validator for workout sessions.
 * Satisfies TASK-014 (session state transition validation) and ensures data integrity.
 * Follows PAT-001 (Domain-driven design) by keeping validation logic in domain layer.
 */
object WorkoutSessionValidator {
    
    /**
     * Validates if a state transition is allowed based on business rules
     */
    fun canTransitionTo(
        currentStatus: WorkoutStatus,
        newStatus: WorkoutStatus,
        session: WorkoutSession? = null
    ): ValidationResult {
        return when (currentStatus) {
            WorkoutStatus.PENDING -> validateFromPending(newStatus, session)
            WorkoutStatus.ACTIVE -> validateFromActive(newStatus, session)
            WorkoutStatus.PAUSED -> validateFromPaused(newStatus, session)
            WorkoutStatus.COMPLETED -> validateFromCompleted(newStatus, session)
        }
    }
    
    /**
     * Validates transitions from PENDING status
     */
    private fun validateFromPending(
        newStatus: WorkoutStatus,
        session: WorkoutSession?
    ): ValidationResult {
        return when (newStatus) {
            WorkoutStatus.PENDING -> ValidationResult.Valid
            WorkoutStatus.ACTIVE -> {
                // Can start if no validation issues
                ValidationResult.Valid
            }
            WorkoutStatus.PAUSED -> ValidationResult.Invalid(
                "Cannot pause a session that hasn't been started"
            )
            WorkoutStatus.COMPLETED -> {
                // Can complete/cancel without starting
                ValidationResult.Valid
            }
        }
    }
    
    /**
     * Validates transitions from ACTIVE status
     */
    private fun validateFromActive(
        newStatus: WorkoutStatus,
        session: WorkoutSession?
    ): ValidationResult {
        return when (newStatus) {
            WorkoutStatus.PENDING -> ValidationResult.Invalid(
                "Cannot revert active session to pending status"
            )
            WorkoutStatus.ACTIVE -> ValidationResult.Valid
            WorkoutStatus.PAUSED -> {
                // Additional validation for pause conditions
                session?.let { validatePauseConditions(it) } ?: ValidationResult.Valid
            }
            WorkoutStatus.COMPLETED -> {
                // Additional validation for completion
                session?.let { validateCompletionConditions(it) } ?: ValidationResult.Valid
            }
        }
    }
    
    /**
     * Validates transitions from PAUSED status
     */
    private fun validateFromPaused(
        newStatus: WorkoutStatus,
        session: WorkoutSession?
    ): ValidationResult {
        return when (newStatus) {
            WorkoutStatus.PENDING -> ValidationResult.Invalid(
                "Cannot revert paused session to pending status"
            )
            WorkoutStatus.ACTIVE -> {
                // Can resume if not too much time has passed
                session?.let { validateResumeConditions(it) } ?: ValidationResult.Valid
            }
            WorkoutStatus.PAUSED -> ValidationResult.Valid
            WorkoutStatus.COMPLETED -> {
                // Can complete from paused state
                session?.let { validateCompletionConditions(it) } ?: ValidationResult.Valid
            }
        }
    }
    
    /**
     * Validates transitions from COMPLETED status
     */
    private fun validateFromCompleted(
        newStatus: WorkoutStatus,
        session: WorkoutSession?
    ): ValidationResult {
        return when (newStatus) {
            WorkoutStatus.COMPLETED -> ValidationResult.Valid
            else -> ValidationResult.Invalid(
                "Cannot change status of completed session to ${newStatus.name}"
            )
        }
    }
    
    /**
     * Validates specific conditions for pausing a session
     */
    private fun validatePauseConditions(session: WorkoutSession): ValidationResult {
        // Check minimum active duration before allowing pause
        val currentTime = kotlinx.datetime.Clock.System.now()
        val activeDuration = currentTime.epochSeconds - session.startTime.epochSeconds
        
        if (activeDuration < 10) { // Minimum 10 seconds active
            return ValidationResult.Invalid(
                "Session must be active for at least 10 seconds before pausing"
            )
        }
        
        return ValidationResult.Valid
    }
    
    /**
     * Validates conditions for resuming from paused state
     */
    private fun validateResumeConditions(session: WorkoutSession): ValidationResult {
        // Check if session has been paused for too long
        val currentTime = kotlinx.datetime.Clock.System.now()
        val lastUpdateTime = session.hrSamples.maxByOrNull { it.timestamp }?.timestamp 
            ?: session.geoPoints.maxByOrNull { it.timestamp }?.timestamp
            ?: session.startTime
            
        val pausedDuration = currentTime.epochSeconds - lastUpdateTime.epochSeconds
        
        if (pausedDuration > 3600) { // More than 1 hour paused
            return ValidationResult.Warning(
                "Session has been paused for over 1 hour. Data continuity may be affected."
            )
        }
        
        return ValidationResult.Valid
    }
    
    /**
     * Validates conditions for completing a session
     */
    private fun validateCompletionConditions(session: WorkoutSession): ValidationResult {
        val currentTime = kotlinx.datetime.Clock.System.now()
        val totalDuration = currentTime.epochSeconds - session.startTime.epochSeconds
        
        // Check minimum session duration
        if (totalDuration < 30) { // Less than 30 seconds
            return ValidationResult.Warning(
                "Session duration is very short (${totalDuration}s). Continue anyway?"
            )
        }
        
        // Check if session has meaningful data
        if (session.geoPoints.isEmpty() && session.hrSamples.isEmpty()) {
            return ValidationResult.Warning(
                "Session contains no tracking data. Save anyway?"
            )
        }
        
        return ValidationResult.Valid
    }
    
    /**
     * Validates session data integrity
     */
    fun validateSessionIntegrity(session: WorkoutSession): ValidationResult {
        val issues = mutableListOf<String>()
        
        // Validate timestamps
        if (session.endTime != null && session.endTime!! <= session.startTime) {
            issues.add("End time must be after start time")
        }
        
        // Validate GPS data continuity
        if (session.geoPoints.size > 1) {
            val geoIssues = validateGeoPointContinuity(session.geoPoints)
            issues.addAll(geoIssues)
        }
        
        // Validate HR data continuity
        if (session.hrSamples.size > 1) {
            val hrIssues = validateHRSampleContinuity(session.hrSamples)
            issues.addAll(hrIssues)
        }
        
        // Validate calculated statistics
        if (session.totalDistance < 0) {
            issues.add("Total distance cannot be negative")
        }
        
        if (session.totalDuration < 0) {
            issues.add("Total duration cannot be negative")
        }
        
        return if (issues.isEmpty()) {
            ValidationResult.Valid
        } else {
            ValidationResult.Invalid(issues.joinToString("; "))
        }
    }
    
    /**
     * Validates GPS point data continuity
     */
    private fun validateGeoPointContinuity(geoPoints: List<GeoPoint>): List<String> {
        val issues = mutableListOf<String>()
        
        for (i in 1 until geoPoints.size) {
            val prev = geoPoints[i - 1]
            val curr = geoPoints[i]
            
            // Check timestamp order
            if (curr.timestamp <= prev.timestamp) {
                issues.add("GPS points not in chronological order at index $i")
            }
            
            // Check for unrealistic speeds (> 50 km/h for running)
            val timeDiff = curr.timestamp.epochSeconds - prev.timestamp.epochSeconds
            if (timeDiff > 0) {
                val distance = calculateDistance(prev, curr)
                val speed = (distance / timeDiff) * 3.6 // Convert m/s to km/h
                
                if (speed > 50.0) {
                    issues.add("Unrealistic speed detected: ${(speed * 10).toInt() / 10.0} km/h at index $i")
                }
            }
        }
        
        return issues
    }
    
    /**
     * Validates HR sample data continuity
     */
    private fun validateHRSampleContinuity(hrSamples: List<HRSample>): List<String> {
        val issues = mutableListOf<String>()
        
        for (i in 1 until hrSamples.size) {
            val prev = hrSamples[i - 1]
            val curr = hrSamples[i]
            
            // Check timestamp order
            if (curr.timestamp <= prev.timestamp) {
                issues.add("HR samples not in chronological order at index $i")
            }
            
            // Check for unrealistic HR changes
            val timeDiff = curr.timestamp.epochSeconds - prev.timestamp.epochSeconds
            if (timeDiff > 0 && timeDiff <= 60) { // Within 1 minute
                val hrChange = kotlin.math.abs(curr.heartRate - prev.heartRate)
                val maxExpectedChange = 10 * timeDiff // 10 BPM per second max
                
                if (hrChange > maxExpectedChange) {
                    issues.add("Large HR change detected: ${hrChange} BPM in ${timeDiff}s at index $i")
                }
            }
        }
        
        return issues
    }
    
    /**
     * Simple distance calculation for validation (using approximation for speed)
     */
    private fun calculateDistance(point1: GeoPoint, point2: GeoPoint): Double {
        val latDiff = point2.latitude - point1.latitude
        val lonDiff = point2.longitude - point1.longitude
        
        // Approximate distance calculation (good enough for validation)
        val avgLat = (point1.latitude + point2.latitude) / 2
        val latDist = latDiff * 111000 // Approximate meters per degree latitude
        val lonDist = lonDiff * 111000 * kotlin.math.cos(avgLat * kotlin.math.PI / 180.0)
        
        return kotlin.math.sqrt(latDist * latDist + lonDist * lonDist)
    }
}

/**
 * Validation result for session state transitions
 */
sealed class ValidationResult {
    object Valid : ValidationResult()
    data class Invalid(val reason: String) : ValidationResult()
    data class Warning(val message: String) : ValidationResult()
    
    val isValid: Boolean get() = this is Valid || this is Warning
    val hasWarning: Boolean get() = this is Warning
    val errorMessage: String? get() = when (this) {
        is Invalid -> reason
        is Warning -> message
        Valid -> null
    }
}

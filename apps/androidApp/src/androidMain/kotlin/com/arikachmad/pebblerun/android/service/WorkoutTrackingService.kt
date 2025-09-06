package com.arikachmad.pebblerun.android.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.arikachmad.pebblerun.domain.entity.WorkoutSession
import com.arikachmad.pebblerun.domain.entity.WorkoutStatus
import com.arikachmad.pebblerun.domain.usecase.StartWorkoutUseCase
import com.arikachmad.pebblerun.domain.usecase.StopWorkoutUseCase
import com.arikachmad.pebblerun.domain.usecase.UpdateWorkoutDataUseCase
import com.arikachmad.pebblerun.bridge.location.LocationProvider
import com.arikachmad.pebblerun.bridge.pebble.PebbleTransport
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.datetime.Clock
import org.koin.android.ext.android.inject
import kotlin.time.Duration.Companion.seconds

/**
 * Android Foreground Service for continuous workout tracking.
 * Satisfies REQ-004 (Background tracking capability) and GUD-001 (Android Foreground Service best practices).
 * Implements TASK-028: Create Android Foreground Service for workout tracking.
 */
class WorkoutTrackingService : LifecycleService() {
    
    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "workout_tracking"
        private const val WAKE_LOCK_TAG = "PebbleRun::WorkoutWakeLock"
        
        // Service actions
        const val ACTION_START_WORKOUT = "START_WORKOUT"
        const val ACTION_STOP_WORKOUT = "STOP_WORKOUT"
        const val ACTION_PAUSE_WORKOUT = "PAUSE_WORKOUT"
        const val ACTION_RESUME_WORKOUT = "RESUME_WORKOUT"
        
        // Intent extras
        const val EXTRA_WORKOUT_ID = "workout_id"
        const val EXTRA_NOTES = "notes"
        
        /**
         * Starts the workout tracking service
         */
        fun startWorkout(context: Context, workoutId: String? = null, notes: String = "") {
            val intent = Intent(context, WorkoutTrackingService::class.java).apply {
                action = ACTION_START_WORKOUT
                putExtra(EXTRA_WORKOUT_ID, workoutId)
                putExtra(EXTRA_NOTES, notes)
            }
            context.startForegroundService(intent)
        }
        
        /**
         * Stops the workout tracking service
         */
        fun stopWorkout(context: Context) {
            val intent = Intent(context, WorkoutTrackingService::class.java).apply {
                action = ACTION_STOP_WORKOUT
            }
            context.startService(intent)
        }
        
        /**
         * Pauses the current workout
         */
        fun pauseWorkout(context: Context) {
            val intent = Intent(context, WorkoutTrackingService::class.java).apply {
                action = ACTION_PAUSE_WORKOUT
            }
            context.startService(intent)
        }
        
        /**
         * Resumes a paused workout
         */
        fun resumeWorkout(context: Context) {
            val intent = Intent(context, WorkoutTrackingService::class.java).apply {
                action = ACTION_RESUME_WORKOUT
            }
            context.startService(intent)
        }
    }
    
    // Injected dependencies
    private val startWorkoutUseCase: StartWorkoutUseCase by inject()
    private val stopWorkoutUseCase: StopWorkoutUseCase by inject()
    private val updateWorkoutDataUseCase: UpdateWorkoutDataUseCase by inject()
    private val locationProvider: LocationProvider by inject()
    private val pebbleTransport: PebbleTransport by inject()
    
    // Service state
    private var currentSession: WorkoutSession? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var trackingJob: Job? = null
    private val _serviceState = MutableStateFlow(ServiceState.IDLE)
    val serviceState: StateFlow<ServiceState> = _serviceState.asStateFlow()
    
    // Update intervals
    private val locationUpdateInterval = 1.seconds // REQ-003: 1-second update frequency
    private val hrSampleInterval = 1.seconds // REQ-001: Real-time HR data collection
    
    enum class ServiceState {
        IDLE, TRACKING, PAUSED, ERROR
    }
    
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        acquireWakeLock()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        
        when (intent?.action) {
            ACTION_START_WORKOUT -> {
                val workoutId = intent.getStringExtra(EXTRA_WORKOUT_ID)
                val notes = intent.getStringExtra(EXTRA_NOTES) ?: ""
                startWorkoutTracking(workoutId, notes)
            }
            ACTION_STOP_WORKOUT -> stopWorkoutTracking()
            ACTION_PAUSE_WORKOUT -> pauseWorkoutTracking()
            ACTION_RESUME_WORKOUT -> resumeWorkoutTracking()
        }
        
        return START_STICKY // Service should be restarted if killed by system
    }
    
    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null // This is a started service, not bound
    }
    
    override fun onDestroy() {
        super.onDestroy()
        releaseWakeLock()
        stopTracking()
    }
    
    /**
     * Starts workout tracking session
     * Implements TASK-028 requirements for background tracking
     */
    private fun startWorkoutTracking(workoutId: String?, notes: String) {
        lifecycleScope.launch {
            try {
                _serviceState.value = ServiceState.TRACKING
                
                // Start workout session through use case
                val params = StartWorkoutUseCase.Params(
                    sessionId = workoutId,
                    startTime = Clock.System.now(),
                    notes = notes
                )
                
                val result = startWorkoutUseCase(params)
                result.fold(
                    onSuccess = { session ->
                        currentSession = session
                        startForegroundNotification(session)
                        startLocationTracking()
                        startHRMonitoring()
                        startDataSynchronization()
                    },
                    onFailure = { error ->
                        _serviceState.value = ServiceState.ERROR
                        handleError("Failed to start workout", error)
                        stopSelf()
                    }
                )
            } catch (e: Exception) {
                _serviceState.value = ServiceState.ERROR
                handleError("Unexpected error starting workout", e)
                stopSelf()
            }
        }
    }
    
    /**
     * Stops workout tracking and saves session
     */
    private fun stopWorkoutTracking() {
        lifecycleScope.launch {
            try {
                currentSession?.let { session ->
                    val params = StopWorkoutUseCase.Params(
                        sessionId = session.id,
                        endTime = Clock.System.now()
                    )
                    
                    stopWorkoutUseCase(params)
                    currentSession = null
                }
                
                stopTracking()
                _serviceState.value = ServiceState.IDLE
                stopSelf()
            } catch (e: Exception) {
                handleError("Error stopping workout", e)
                stopSelf()
            }
        }
    }
    
    /**
     * Pauses current workout tracking
     */
    private fun pauseWorkoutTracking() {
        lifecycleScope.launch {
            try {
                currentSession?.let { session ->
                    if (session.canTransitionTo(WorkoutStatus.PAUSED)) {
                        val pausedSession = session.withStatus(WorkoutStatus.PAUSED, Clock.System.now())
                        currentSession = pausedSession
                        
                        stopLocationTracking()
                        _serviceState.value = ServiceState.PAUSED
                        updateNotification(pausedSession)
                    }
                }
            } catch (e: Exception) {
                handleError("Error pausing workout", e)
            }
        }
    }
    
    /**
     * Resumes paused workout tracking
     */
    private fun resumeWorkoutTracking() {
        lifecycleScope.launch {
            try {
                currentSession?.let { session ->
                    if (session.canTransitionTo(WorkoutStatus.ACTIVE)) {
                        val activeSession = session.withStatus(WorkoutStatus.ACTIVE, Clock.System.now())
                        currentSession = activeSession
                        
                        startLocationTracking()
                        _serviceState.value = ServiceState.TRACKING
                        updateNotification(activeSession)
                    }
                }
            } catch (e: Exception) {
                handleError("Error resuming workout", e)
            }
        }
    }
    
    /**
     * Starts continuous location tracking
     * Satisfies REQ-002 (GPS-based pace and distance calculation)
     */
    private fun startLocationTracking() {
        trackingJob?.cancel()
        trackingJob = lifecycleScope.launch {
            locationProvider.getCurrentLocation()
                .flowOn(Dispatchers.IO)
                .catch { error ->
                    handleError("Location tracking error", error)
                }
                .collect { location ->
                    updateWorkoutWithLocation(location)
                }
        }
    }
    
    /**
     * Stops location tracking
     */
    private fun stopLocationTracking() {
        trackingJob?.cancel()
        trackingJob = null
    }
    
    /**
     * Starts HR monitoring from Pebble device
     * Satisfies REQ-001 (Real-time HR data collection)
     */
    private fun startHRMonitoring() {
        lifecycleScope.launch {
            pebbleTransport.connectToPebble()
                .flowOn(Dispatchers.IO)
                .catch { error ->
                    handleError("Pebble connection error", error)
                }
                .collect { hrData ->
                    updateWorkoutWithHR(hrData.heartRate)
                }
        }
    }
    
    /**
     * Starts real-time data synchronization between mobile and Pebble
     * Satisfies REQ-006 (Real-time data synchronization)
     */
    private fun startDataSynchronization() {
        lifecycleScope.launch {
            while (currentSession?.status == WorkoutStatus.ACTIVE) {
                currentSession?.let { session ->
                    // Send current pace and duration to Pebble
                    pebbleTransport.sendWorkoutData(
                        pace = session.averagePace,
                        duration = session.totalDuration,
                        distance = session.totalDistance
                    )
                }
                delay(locationUpdateInterval)
            }
        }
    }
    
    /**
     * Updates workout session with new location data
     */
    private suspend fun updateWorkoutWithLocation(location: com.arikachmad.pebblerun.bridge.location.model.Location) {
        currentSession?.let { session ->
            val params = UpdateWorkoutDataUseCase.Params(
                sessionId = session.id,
                newLocation = com.arikachmad.pebblerun.domain.entity.GeoPoint(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    altitude = location.altitude,
                    timestamp = Clock.System.now()
                )
            )
            
            updateWorkoutDataUseCase(params).fold(
                onSuccess = { updatedSession ->
                    currentSession = updatedSession
                    updateNotification(updatedSession)
                },
                onFailure = { error ->
                    handleError("Failed to update workout with location", error)
                }
            )
        }
    }
    
    /**
     * Updates workout session with new HR data
     */
    private suspend fun updateWorkoutWithHR(heartRate: Int) {
        currentSession?.let { session ->
            val params = UpdateWorkoutDataUseCase.Params(
                sessionId = session.id,
                newHRSample = com.arikachmad.pebblerun.domain.entity.HRSample(
                    heartRate = heartRate,
                    timestamp = Clock.System.now()
                )
            )
            
            updateWorkoutDataUseCase(params).fold(
                onSuccess = { updatedSession ->
                    currentSession = updatedSession
                    updateNotification(updatedSession)
                },
                onFailure = { error ->
                    handleError("Failed to update workout with HR", error)
                }
            )
        }
    }
    
    /**
     * Stops all tracking activities
     */
    private fun stopTracking() {
        stopLocationTracking()
        lifecycleScope.launch {
            pebbleTransport.disconnect()
        }
    }
    
    /**
     * Creates notification channel for foreground service
     * Satisfies GUD-001 (Android Foreground Service best practices)
     */
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Workout Tracking",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Notifications for active workout tracking sessions"
            setShowBadge(false)
        }
        
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }
    
    /**
     * Starts foreground service with notification
     */
    private fun startForegroundNotification(session: WorkoutSession) {
        val notification = createNotification(session)
        startForeground(NOTIFICATION_ID, notification)
    }
    
    /**
     * Updates notification with current workout data
     */
    private fun updateNotification(session: WorkoutSession) {
        val notification = createNotification(session)
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
    
    /**
     * Creates notification for workout tracking
     */
    private fun createNotification(session: WorkoutSession): Notification {
        val stopIntent = Intent(this, WorkoutTrackingService::class.java).apply {
            action = ACTION_STOP_WORKOUT
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE
        )
        
        val pauseResumeAction = if (session.status == WorkoutStatus.ACTIVE) {
            NotificationCompat.Action.Builder(
                android.R.drawable.ic_media_pause,
                "Pause",
                PendingIntent.getService(
                    this, 1,
                    Intent(this, WorkoutTrackingService::class.java).apply {
                        action = ACTION_PAUSE_WORKOUT
                    },
                    PendingIntent.FLAG_IMMUTABLE
                )
            ).build()
        } else {
            NotificationCompat.Action.Builder(
                android.R.drawable.ic_media_play,
                "Resume",
                PendingIntent.getService(
                    this, 2,
                    Intent(this, WorkoutTrackingService::class.java).apply {
                        action = ACTION_RESUME_WORKOUT
                    },
                    PendingIntent.FLAG_IMMUTABLE
                )
            ).build()
        }
        
        val contentTitle = when (session.status) {
            WorkoutStatus.ACTIVE -> "Workout Active"
            WorkoutStatus.PAUSED -> "Workout Paused"
            else -> "Workout Tracking"
        }
        
        val contentText = buildString {
            append("Duration: ${formatDuration(session.totalDuration)}")
            if (session.totalDistance > 0) {
                append(" • Distance: ${formatDistance(session.totalDistance)}")
            }
            if (session.averageHeartRate > 0) {
                append(" • HR: ${session.averageHeartRate} bpm")
            }
        }
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(contentTitle)
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(true)
            .addAction(pauseResumeAction)
            .addAction(
                NotificationCompat.Action.Builder(
                    android.R.drawable.ic_menu_close_clear_cancel,
                    "Stop",
                    stopPendingIntent
                ).build()
            )
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }
    
    /**
     * Acquires wake lock to prevent device sleep during workout
     * Satisfies CON-001 (Battery optimization)
     */
    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            WAKE_LOCK_TAG
        ).apply {
            acquire(10 * 60 * 1000L) // 10 minutes timeout for safety
        }
    }
    
    /**
     * Releases wake lock
     */
    private fun releaseWakeLock() {
        wakeLock?.release()
        wakeLock = null
    }
    
    /**
     * Handles errors during service operation
     * Satisfies TASK-033 (Error recovery mechanisms)
     */
    private fun handleError(message: String, error: Throwable) {
        // Log error (implementation depends on logging framework)
        println("WorkoutTrackingService Error: $message - ${error.message}")
        
        // Update service state
        _serviceState.value = ServiceState.ERROR
        
        // Show error notification
        showErrorNotification(message)
        
        // Attempt recovery for recoverable errors
        when (error) {
            is SecurityException -> {
                // Permission-related error - cannot recover
                stopSelf()
            }
            is kotlinx.coroutines.CancellationException -> {
                // Coroutine cancelled - normal during shutdown
                return
            }
            else -> {
                // Other errors - try to continue with limited functionality
                // Could implement retry logic here
            }
        }
    }
    
    /**
     * Shows error notification to user
     */
    private fun showErrorNotification(message: String) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Workout Tracking Error")
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setAutoCancel(true)
            .build()
        
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID + 1, notification)
    }
    
    /**
     * Formats duration in seconds to human-readable string
     */
    private fun formatDuration(seconds: Long): String {
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60
        
        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, secs)
        } else {
            String.format("%d:%02d", minutes, secs)
        }
    }
    
    /**
     * Formats distance in meters to human-readable string
     */
    private fun formatDistance(meters: Double): String {
        return if (meters >= 1000) {
            String.format("%.2f km", meters / 1000)
        } else {
            String.format("%.0f m", meters)
        }
    }
}

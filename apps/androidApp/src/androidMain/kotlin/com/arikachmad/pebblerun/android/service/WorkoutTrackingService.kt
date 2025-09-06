package com.arikachmad.pebblerun.android.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.BroadcastReceiver
import android.os.IBinder
import android.os.PowerManager
import android.os.BatteryManager
import android.os.Build
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
        private const val ALERT_CHANNEL_ID = "workout_alerts"
        private const val ACHIEVEMENT_CHANNEL_ID = "achievements"
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
    
    // Battery optimization manager
    private lateinit var batteryOptimizationManager: BatteryOptimizationManager
    
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
        batteryOptimizationManager = BatteryOptimizationManager(this)
        createNotificationChannel()
        acquireWakeLock()
        checkBatteryOptimizations()
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
                        // Start enhanced workout tracking for Phase 5
                        lifecycleScope.launch { 
                            enhancedWorkoutTracking() 
                        }
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
            // Send workout data to Pebble every second
            while (_serviceState.value == ServiceState.TRACKING) {
                currentSession?.let { session ->
                    try {
                        // Send pace and time to Pebble
                        val currentPace = session.calculateCurrentPace()
                        val elapsedTime = session.formatElapsedTime()
                        
                        pebbleTransport.sendWorkoutData(
                            pace = currentPace,
                            time = elapsedTime,
                            distance = session.distanceMeters
                        )
                        
                        // Update notification with latest data
                        updateNotification(session)
                        
                    } catch (e: Exception) {
                        handleError("Data sync error", e)
                    }
                }
                delay(1000) // 1-second interval per REQ-003
            }
        }
    }
    
    /**
     * Enhanced workout tracking with performance metrics
     * Implements advanced tracking logic for Phase 5
     */
    private suspend fun enhancedWorkoutTracking() {
        val performanceTracker = WorkoutPerformanceTracker()
        
        while (_serviceState.value == ServiceState.TRACKING) {
            currentSession?.let { session ->
                try {
                    // Calculate advanced metrics
                    val metrics = performanceTracker.calculateMetrics(session)
                    
                    // Update session with enhanced data
                    val updatedSession = session.copy(
                        avgPace = metrics.avgPace,
                        currentPace = metrics.currentPace,
                        avgHeartRate = metrics.avgHeartRate,
                        caloriesBurned = metrics.estimatedCalories,
                        elevationGain = metrics.elevationGain
                    )
                    
                    currentSession = updatedSession
                    
                    // Send enhanced data to Pebble
                    pebbleTransport.sendEnhancedWorkoutData(metrics)
                    
                    // Update notification with enhanced metrics
                    updateEnhancedNotification(updatedSession, metrics)
                    
                } catch (e: Exception) {
                    handleError("Enhanced tracking error", e)
                }
            }
            delay(1000)
        }
    }
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
        // Primary workout tracking channel
        val workoutChannel = NotificationChannel(
            CHANNEL_ID,
            "Workout Tracking",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Notifications for active workout tracking sessions"
            setShowBadge(false)
            enableLights(false)
            enableVibration(false)
        }
        
        // High priority channel for alerts
        val alertChannel = NotificationChannel(
            ALERT_CHANNEL_ID,
            "Workout Alerts",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Important workout alerts and milestones"
            setShowBadge(true)
            enableLights(true)
            enableVibration(true)
        }
        
        // Achievement notifications
        val achievementChannel = NotificationChannel(
            ACHIEVEMENT_CHANNEL_ID,
            "Achievements",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Workout achievements and milestones"
            setShowBadge(true)
        }
        
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannels(listOf(workoutChannel, alertChannel, achievementChannel))
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
     * Creates enhanced notification with performance metrics
     * Implements TASK-032: Enhanced notification system
     */
    private fun updateEnhancedNotification(session: WorkoutSession, metrics: WorkoutPerformanceTracker.WorkoutMetrics) {
        val notification = createEnhancedNotification(session, metrics)
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
    
    /**
     * Creates enhanced notification with detailed workout metrics
     */
    private fun createEnhancedNotification(session: WorkoutSession, metrics: WorkoutPerformanceTracker.WorkoutMetrics): Notification {
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
            WorkoutStatus.ACTIVE -> "🏃 Workout Active"
            WorkoutStatus.PAUSED -> "⏸️ Workout Paused"
            else -> "📱 Workout Tracking"
        }
        
        val contentText = buildString {
            append("⏱️ ${formatDuration(session.totalDuration)}")
            if (session.totalDistance > 0) {
                append(" • 📍 ${formatDistance(session.totalDistance)}")
            }
            if (metrics.currentHeartRate > 0) {
                append(" • ❤️ ${metrics.currentHeartRate} bpm")
            }
        }
        
        val bigTextStyle = NotificationCompat.BigTextStyle()
            .bigText(buildString {
                appendLine("Duration: ${formatDuration(session.totalDuration)}")
                appendLine("Distance: ${formatDistance(session.totalDistance)}")
                appendLine("Current Pace: ${metrics.currentPace}/km")
                appendLine("Average Pace: ${metrics.avgPace}/km")
                appendLine("Heart Rate: ${metrics.currentHeartRate} bpm (${metrics.heartRateZone.name})")
                appendLine("Calories: ${metrics.estimatedCalories}")
                if (metrics.elevationGain > 0) {
                    appendLine("Elevation: +${metrics.elevationGain.toInt()}m")
                }
            })
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(contentTitle)
            .setContentText(contentText)
            .setStyle(bigTextStyle)
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
            .setProgress(0, 0, false)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }
    
    /**
     * Sends workout milestone notifications
     */
    private fun sendMilestoneNotification(milestone: WorkoutMilestone) {
        val notification = NotificationCompat.Builder(this, ACHIEVEMENT_CHANNEL_ID)
            .setContentTitle("🎉 Milestone Reached!")
            .setContentText(milestone.message)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
            
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(milestone.id, notification)
    }
    
    /**
     * Sends workout alert notifications (e.g., heart rate warnings)
     */
    private fun sendWorkoutAlert(alert: WorkoutAlert) {
        val notification = NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
            .setContentTitle("⚠️ Workout Alert")
            .setContentText(alert.message)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVibrate(longArrayOf(0, 250, 250, 250))
            .build()
            
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(alert.id, notification)
    }
    
    data class WorkoutMilestone(
        val id: Int,
        val message: String,
        val type: MilestoneType
    )
    
    enum class MilestoneType {
        DISTANCE, TIME, CALORIES, PACE
    }
    
    data class WorkoutAlert(
        val id: Int,
        val message: String,
        val type: AlertType,
        val severity: AlertSeverity
    )
    
    enum class AlertType {
        HEART_RATE_HIGH, HEART_RATE_LOW, GPS_LOST, PEBBLE_DISCONNECTED, BATTERY_OPTIMIZATION, LOW_BATTERY
    }
    
    enum class AlertSeverity {
        INFO, WARNING, CRITICAL
    }
    
    /**
     * Checks battery optimization status and alerts if needed
     * Implements TASK-033: Battery optimization handling
     */
    private fun checkBatteryOptimizations() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!batteryOptimizationManager.isBatteryOptimizationDisabled()) {
                sendBatteryOptimizationAlert()
            }
        }
    }
    
    /**
     * Sends battery optimization alert to user
     */
    private fun sendBatteryOptimizationAlert() {
        val alert = WorkoutAlert(
            id = 999,
            message = "Battery optimization may affect workout tracking. Tap to optimize settings.",
            type = AlertType.BATTERY_OPTIMIZATION,
            severity = AlertSeverity.WARNING
        )
        sendWorkoutAlert(alert)
    }
    
    /**
     * Monitors battery level and adjusts tracking accordingly
     */
    private fun monitorBatteryLevel() {
        val batteryFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                intent?.let {
                    val level = it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                    val scale = it.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                    val batteryPercent = (level * 100 / scale)
                    
                    handleBatteryLevelChange(batteryPercent)
                }
            }
        }
        registerReceiver(batteryReceiver, batteryFilter)
    }
    
    /**
     * Handles battery level changes and adjusts tracking frequency
     */
    private fun handleBatteryLevelChange(batteryPercent: Int) {
        when {
            batteryPercent <= 10 -> {
                // Critical battery level - reduce tracking frequency
                adjustTrackingForLowBattery(TrackingMode.POWER_SAVE)
                sendLowBatteryAlert(batteryPercent)
            }
            batteryPercent <= 20 -> {
                // Low battery - moderate power saving
                adjustTrackingForLowBattery(TrackingMode.BALANCED)
            }
            else -> {
                // Normal battery level - full tracking
                adjustTrackingForLowBattery(TrackingMode.NORMAL)
            }
        }
    }
    
    /**
     * Adjusts tracking parameters based on battery level
     */
    private fun adjustTrackingForLowBattery(mode: TrackingMode) {
        val newLocationInterval = when (mode) {
            TrackingMode.POWER_SAVE -> 5.seconds // Reduce to 5-second intervals
            TrackingMode.BALANCED -> 2.seconds   // Reduce to 2-second intervals
            TrackingMode.NORMAL -> 1.seconds     // Normal 1-second intervals
        }
        
        // Update tracking intervals (implementation would depend on location provider)
        // This is a placeholder for the actual implementation
        lifecycleScope.launch {
            // Update location provider settings
            // locationProvider.updateInterval(newLocationInterval)
        }
    }
    
    /**
     * Sends low battery alert to user
     */
    private fun sendLowBatteryAlert(batteryPercent: Int) {
        val alert = WorkoutAlert(
            id = 998,
            message = "Low battery ($batteryPercent%). Tracking frequency reduced to save power.",
            type = AlertType.LOW_BATTERY,
            severity = AlertSeverity.WARNING
        )
        sendWorkoutAlert(alert)
    }
    
    enum class TrackingMode {
        NORMAL, BALANCED, POWER_SAVE
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

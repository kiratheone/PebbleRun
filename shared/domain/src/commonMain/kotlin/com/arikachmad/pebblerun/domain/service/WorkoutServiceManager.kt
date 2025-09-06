package com.arikachmad.pebblerun.domain.service

import com.arikachmad.pebblerun.domain.entity.WorkoutSession
import com.arikachmad.pebblerun.domain.entity.WorkoutStatus
import kotlinx.coroutines.flow.StateFlow

/**
 * Service lifecycle states for workout tracking services.
 * Supports TASK-030 (Service lifecycle management) and CON-004 (Graceful handling of disconnections).
 */
enum class ServiceLifecycleState {
    /** Service is not running */
    STOPPED,
    /** Service is initializing */
    STARTING,
    /** Service is running and tracking workout */
    RUNNING,
    /** Service is pausing due to user action */
    PAUSING,
    /** Service is paused */
    PAUSED,
    /** Service is resuming from pause */
    RESUMING,
    /** Service is stopping gracefully */
    STOPPING,
    /** Service encountered an error */
    ERROR,
    /** Service is cleaning up resources */
    CLEANING_UP
}

/**
 * Service lifecycle event types for monitoring and logging.
 */
sealed class ServiceLifecycleEvent {
    data class StateChanged(
        val fromState: ServiceLifecycleState,
        val toState: ServiceLifecycleState,
        val timestamp: kotlinx.datetime.Instant
    ) : ServiceLifecycleEvent()
    
    data class ErrorOccurred(
        val error: Throwable,
        val state: ServiceLifecycleState,
        val timestamp: kotlinx.datetime.Instant
    ) : ServiceLifecycleEvent()
    
    data class ResourceAcquired(
        val resourceType: String,
        val resourceId: String,
        val timestamp: kotlinx.datetime.Instant
    ) : ServiceLifecycleEvent()
    
    data class ResourceReleased(
        val resourceType: String,
        val resourceId: String,
        val timestamp: kotlinx.datetime.Instant
    ) : ServiceLifecycleEvent()
    
    data class CleanupCompleted(
        val cleanupType: String,
        val success: Boolean,
        val timestamp: kotlinx.datetime.Instant
    ) : ServiceLifecycleEvent()
}

/**
 * Interface for workout tracking service lifecycle management.
 * Satisfies TASK-030 (Service lifecycle management and cleanup).
 * Follows PAT-002 (Repository pattern) for service abstractions.
 */
interface WorkoutServiceManager {
    
    /** Current service lifecycle state */
    val lifecycleState: StateFlow<ServiceLifecycleState>
    
    /** Current workout session if any */
    val currentSession: StateFlow<WorkoutSession?>
    
    /** Service lifecycle events for monitoring */
    val lifecycleEvents: StateFlow<List<ServiceLifecycleEvent>>
    
    /** Health status of service components */
    val serviceHealth: StateFlow<ServiceHealth>
    
    /**
     * Starts workout tracking service
     * @param workoutId Optional custom workout ID
     * @param notes Optional workout notes
     * @return Result indicating success or failure
     */
    suspend fun startService(workoutId: String? = null, notes: String = ""): Result<Unit>
    
    /**
     * Stops workout tracking service gracefully
     * @param forceStop Whether to force stop even if cleanup fails
     * @return Result indicating success or failure
     */
    suspend fun stopService(forceStop: Boolean = false): Result<Unit>
    
    /**
     * Pauses the current workout session
     * @return Result indicating success or failure
     */
    suspend fun pauseService(): Result<Unit>
    
    /**
     * Resumes a paused workout session
     * @return Result indicating success or failure
     */
    suspend fun resumeService(): Result<Unit>
    
    /**
     * Performs health check on service components
     * @return Health status of all components
     */
    suspend fun performHealthCheck(): ServiceHealth
    
    /**
     * Cleans up all resources and prepares for shutdown
     * @param timeout Maximum time to wait for cleanup
     * @return Result indicating cleanup success
     */
    suspend fun cleanup(timeout: kotlin.time.Duration = kotlin.time.Duration.INFINITE): Result<Unit>
    
    /**
     * Recovers from error state by attempting to restart components
     * @return Result indicating recovery success
     */
    suspend fun recoverFromError(): Result<Unit>
    
    /**
     * Gets service metrics and statistics
     * @return Current service metrics
     */
    fun getServiceMetrics(): ServiceMetrics
}

/**
 * Health status of service components.
 * Supports TASK-033 (Error recovery mechanisms).
 */
data class ServiceHealth(
    val overallHealth: HealthStatus,
    val locationProviderHealth: HealthStatus,
    val pebbleTransportHealth: HealthStatus,
    val databaseHealth: HealthStatus,
    val batteryOptimizationHealth: HealthStatus,
    val lastHealthCheck: kotlinx.datetime.Instant,
    val healthIssues: List<HealthIssue> = emptyList()
) {
    val isHealthy: Boolean
        get() = overallHealth == HealthStatus.HEALTHY && healthIssues.isEmpty()
}

enum class HealthStatus {
    HEALTHY,
    WARNING,
    ERROR,
    UNKNOWN
}

data class HealthIssue(
    val component: String,
    val severity: HealthStatus,
    val message: String,
    val timestamp: kotlinx.datetime.Instant,
    val isRecoverable: Boolean = true
)

/**
 * Service performance and usage metrics.
 * Supports CON-001 (Battery optimization) and CON-002 (Memory efficiency).
 */
data class ServiceMetrics(
    val uptime: kotlin.time.Duration,
    val memoryUsage: MemoryMetrics,
    val batteryUsage: BatteryMetrics,
    val locationUpdates: LocationMetrics,
    val pebbleConnectivity: ConnectivityMetrics,
    val errorCount: Int,
    val lastMetricsUpdate: kotlinx.datetime.Instant
)

data class MemoryMetrics(
    val currentUsageMB: Double,
    val peakUsageMB: Double,
    val leakCount: Int
)

data class BatteryMetrics(
    val estimatedBatteryDrainPercentPerHour: Double,
    val currentBatteryLevel: Double,
    val isOptimized: Boolean
)

data class LocationMetrics(
    val totalUpdates: Long,
    val averageAccuracy: Double,
    val lastUpdateTimestamp: kotlinx.datetime.Instant?
)

data class ConnectivityMetrics(
    val connectionAttempts: Long,
    val successfulConnections: Long,
    val currentConnectionStatus: ConnectionStatus,
    val averageReconnectionTime: kotlin.time.Duration,
    val lastConnectionTimestamp: kotlinx.datetime.Instant?
)

enum class ConnectionStatus {
    CONNECTED,
    CONNECTING,
    DISCONNECTED,
    ERROR
}

/**
 * Resource management interface for tracking and cleaning up service resources.
 * Supports proper resource lifecycle management and leak prevention.
 */
interface ServiceResourceManager {
    
    /**
     * Registers a resource for lifecycle management
     */
    fun registerResource(type: String, id: String, resource: Any)
    
    /**
     * Unregisters and cleans up a resource
     */
    suspend fun unregisterResource(type: String, id: String): Boolean
    
    /**
     * Gets all registered resources by type
     */
    fun getResourcesByType(type: String): List<Pair<String, Any>>
    
    /**
     * Cleans up all resources of a specific type
     */
    suspend fun cleanupResourcesOfType(type: String): Result<Unit>
    
    /**
     * Cleans up all registered resources
     */
    suspend fun cleanupAllResources(): Result<Unit>
    
    /**
     * Gets resource usage statistics
     */
    fun getResourceStatistics(): Map<String, Int>
}

/**
 * Exception types for service lifecycle management.
 */
sealed class ServiceLifecycleException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class ServiceAlreadyRunning : ServiceLifecycleException("Service is already running")
    class ServiceNotRunning : ServiceLifecycleException("Service is not running")
    class InvalidStateTransition(from: ServiceLifecycleState, to: ServiceLifecycleState) : 
        ServiceLifecycleException("Invalid state transition from $from to $to")
    class CleanupFailed(component: String, cause: Throwable) : 
        ServiceLifecycleException("Failed to cleanup component: $component", cause)
    class ResourceAcquisitionFailed(resource: String, cause: Throwable) : 
        ServiceLifecycleException("Failed to acquire resource: $resource", cause)
    class HealthCheckFailed(cause: Throwable) : 
        ServiceLifecycleException("Health check failed", cause)
}

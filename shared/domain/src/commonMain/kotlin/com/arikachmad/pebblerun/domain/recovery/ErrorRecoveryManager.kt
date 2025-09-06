package com.arikachmad.pebblerun.domain.recovery

import kotlinx.coroutines.flow.StateFlow
import kotlinx.datetime.Instant
import kotlin.time.Duration

/**
 * Error recovery system for handling service interruptions and failures.
 * Satisfies TASK-033 (Error recovery mechanisms for service interruptions).
 * Implements CON-004 (Graceful handling of disconnections) and robust error handling.
 */
interface ErrorRecoveryManager {
    
    /** Current recovery state */
    val recoveryState: StateFlow<RecoveryState>
    
    /** Error history and statistics */
    val errorHistory: StateFlow<List<ErrorEvent>>
    
    /** Recovery configuration */
    val recoveryConfig: StateFlow<RecoveryConfiguration>
    
    /**
     * Registers an error and attempts recovery
     * @param error The error that occurred
     * @param context Additional context about the error
     * @return Recovery attempt result
     */
    suspend fun handleError(error: Throwable, context: ErrorContext): RecoveryResult
    
    /**
     * Manually triggers recovery for a specific component
     * @param component The component to recover
     * @param strategy The recovery strategy to use
     * @return Recovery result
     */
    suspend fun triggerRecovery(component: String, strategy: RecoveryStrategy): RecoveryResult
    
    /**
     * Checks the health of all monitored components
     * @return Health check results
     */
    suspend fun performHealthCheck(): HealthCheckResult
    
    /**
     * Updates recovery configuration
     * @param config New recovery configuration
     */
    suspend fun updateRecoveryConfig(config: RecoveryConfiguration)
    
    /**
     * Registers a recoverable component
     * @param component The component to monitor
     */
    fun registerComponent(component: RecoverableComponent)
    
    /**
     * Unregisters a component from monitoring
     * @param componentName The component name
     */
    fun unregisterComponent(componentName: String)
    
    /**
     * Gets recovery statistics
     * @return Current recovery statistics
     */
    fun getRecoveryStatistics(): RecoveryStatistics
    
    /**
     * Enables or disables automatic recovery
     * @param enabled Whether automatic recovery should be enabled
     */
    suspend fun setAutoRecoveryEnabled(enabled: Boolean)
    
    /**
     * Resets error counters and recovery state
     */
    suspend fun resetRecoveryState()
}

/**
 * Current state of the recovery system.
 */
enum class RecoveryState {
    IDLE,           // No active recovery operations
    MONITORING,     // Actively monitoring for errors
    RECOVERING,     // Currently attempting recovery
    DEGRADED,       // Operating with reduced functionality
    FAILED,         // Recovery failed, manual intervention needed
    DISABLED        // Recovery system disabled
}

/**
 * Error event information.
 */
data class ErrorEvent(
    val id: String,
    val error: Throwable,
    val context: ErrorContext,
    val timestamp: Instant,
    val severity: ErrorSeverity,
    val component: String,
    val recoveryAttempts: Int = 0,
    val recovered: Boolean = false,
    val recoveryDuration: Duration? = null
)

/**
 * Context information about an error.
 */
data class ErrorContext(
    val component: String,
    val operation: String,
    val userFacing: Boolean = false,
    val workoutActive: Boolean = false,
    val batteryLevel: Double? = null,
    val networkAvailable: Boolean = true,
    val additionalData: Map<String, String> = emptyMap()
)

/**
 * Error severity levels.
 */
enum class ErrorSeverity {
    LOW,        // Minor issues, service continues normally
    MEDIUM,     // Some functionality impacted, recovery recommended
    HIGH,       // Major functionality impacted, recovery required
    CRITICAL    // Service cannot continue, immediate recovery needed
}

/**
 * Recovery attempt result.
 */
data class RecoveryResult(
    val success: Boolean,
    val strategy: RecoveryStrategy,
    val duration: Duration,
    val message: String,
    val degradedFunctionality: Set<String> = emptySet(),
    val nextAttemptDelay: Duration? = null
) {
    val isPartialRecovery: Boolean get() = success && degradedFunctionality.isNotEmpty()
}

/**
 * Recovery strategies for different types of errors.
 */
sealed class RecoveryStrategy(
    val name: String,
    val description: String,
    val estimatedDuration: Duration,
    val riskLevel: RiskLevel
) {
    object Restart : RecoveryStrategy(
        "Restart", 
        "Restart the failed component", 
        Duration.parse("5s"),
        RiskLevel.LOW
    )
    
    object Reconnect : RecoveryStrategy(
        "Reconnect", 
        "Attempt to reconnect to external service", 
        Duration.parse("10s"),
        RiskLevel.LOW
    )
    
    object Fallback : RecoveryStrategy(
        "Fallback", 
        "Switch to fallback functionality", 
        Duration.parse("2s"),
        RiskLevel.MEDIUM
    )
    
    object Reset : RecoveryStrategy(
        "Reset", 
        "Reset component to initial state", 
        Duration.parse("15s"),
        RiskLevel.MEDIUM
    )
    
    object Reinitialize : RecoveryStrategy(
        "Reinitialize", 
        "Completely reinitialize the component", 
        Duration.parse("30s"),
        RiskLevel.HIGH
    )
    
    object GracefulDegradation : RecoveryStrategy(
        "GracefulDegradation", 
        "Continue with reduced functionality", 
        Duration.parse("1s"),
        RiskLevel.LOW
    )
    
    object UserIntervention : RecoveryStrategy(
        "UserIntervention", 
        "Requires user action to resolve", 
        Duration.INFINITE,
        RiskLevel.HIGH
    )
}

enum class RiskLevel {
    LOW,    // Safe to attempt automatically
    MEDIUM, // Can be attempted with caution
    HIGH    // Should require confirmation or be limited
}

/**
 * Recovery configuration and policies.
 */
data class RecoveryConfiguration(
    val autoRecoveryEnabled: Boolean = true,
    val maxRetryAttempts: Int = 3,
    val retryDelay: Duration = Duration.parse("30s"),
    val exponentialBackoff: Boolean = true,
    val maxBackoffDelay: Duration = Duration.parse("5m"),
    val enableDegradedMode: Boolean = true,
    val userNotificationThreshold: ErrorSeverity = ErrorSeverity.HIGH,
    val criticalErrorEscalation: Boolean = true,
    val componentSpecificConfig: Map<String, ComponentRecoveryConfig> = emptyMap()
)

/**
 * Component-specific recovery configuration.
 */
data class ComponentRecoveryConfig(
    val enabled: Boolean = true,
    val maxRetryAttempts: Int = 3,
    val retryDelay: Duration = Duration.parse("30s"),
    val allowedStrategies: Set<RecoveryStrategy> = setOf(
        RecoveryStrategy.Restart,
        RecoveryStrategy.Reconnect,
        RecoveryStrategy.Fallback
    ),
    val criticalComponent: Boolean = false,
    val degradedModeAllowed: Boolean = true
)

/**
 * Health check result for all components.
 */
data class HealthCheckResult(
    val overallHealth: ComponentHealth,
    val componentHealth: Map<String, ComponentHealth>,
    val timestamp: Instant,
    val issues: List<HealthIssue> = emptyList(),
    val recommendations: List<String> = emptyList()
)

/**
 * Component health status.
 */
enum class ComponentHealth {
    HEALTHY,        // Component is functioning normally
    WARNING,        // Component has minor issues
    DEGRADED,       // Component is functioning with reduced capability
    ERROR,          // Component has errors but is still operational
    FAILED,         // Component is not operational
    UNKNOWN         // Health status cannot be determined
}

/**
 * Health issue description.
 */
data class HealthIssue(
    val component: String,
    val severity: ErrorSeverity,
    val description: String,
    val recommendation: String,
    val autoRecoverable: Boolean = true
)

/**
 * Recovery statistics and metrics.
 */
data class RecoveryStatistics(
    val totalErrors: Long,
    val totalRecoveries: Long,
    val successfulRecoveries: Long,
    val failedRecoveries: Long,
    val averageRecoveryTime: Duration,
    val componentStats: Map<String, ComponentRecoveryStats>,
    val uptime: Duration,
    val lastReset: Instant
) {
    val successRate: Double get() = if (totalRecoveries > 0) successfulRecoveries.toDouble() / totalRecoveries else 0.0
}

/**
 * Recovery statistics for a specific component.
 */
data class ComponentRecoveryStats(
    val componentName: String,
    val errors: Long,
    val recoveries: Long,
    val successRate: Double,
    val averageRecoveryTime: Duration,
    val lastError: Instant?,
    val lastRecovery: Instant?,
    val currentHealth: ComponentHealth
)

/**
 * Interface for components that can be recovered.
 */
interface RecoverableComponent {
    /** Component name for identification */
    val componentName: String
    
    /** Whether this component is critical for operation */
    val isCritical: Boolean
    
    /**
     * Checks if the component is healthy
     * @return Component health status
     */
    suspend fun checkHealth(): ComponentHealth
    
    /**
     * Attempts to recover the component using the specified strategy
     * @param strategy The recovery strategy to use
     * @return Recovery result
     */
    suspend fun recover(strategy: RecoveryStrategy): RecoveryResult
    
    /**
     * Gets available recovery strategies for this component
     * @return List of supported recovery strategies
     */
    fun getSupportedRecoveryStrategies(): List<RecoveryStrategy>
    
    /**
     * Enables degraded mode for this component
     * @return Whether degraded mode was successfully enabled
     */
    suspend fun enableDegradedMode(): Boolean
    
    /**
     * Disables degraded mode and attempts to restore full functionality
     * @return Whether full functionality was restored
     */
    suspend fun disableDegradedMode(): Boolean
    
    /**
     * Gets current functionality status
     * @return Set of currently disabled/degraded functions
     */
    fun getDegradedFunctionality(): Set<String>
    
    /**
     * Called when an error occurs in this component
     * @param error The error that occurred
     * @param context Error context
     */
    suspend fun onError(error: Throwable, context: ErrorContext)
}

/**
 * Error recovery event listener interface.
 */
interface RecoveryEventListener {
    /** Called when an error occurs */
    suspend fun onErrorOccurred(event: ErrorEvent)
    
    /** Called when recovery starts */
    suspend fun onRecoveryStarted(component: String, strategy: RecoveryStrategy)
    
    /** Called when recovery completes */
    suspend fun onRecoveryCompleted(component: String, result: RecoveryResult)
    
    /** Called when recovery fails */
    suspend fun onRecoveryFailed(component: String, error: Throwable)
    
    /** Called when degraded mode is enabled */
    suspend fun onDegradedModeEnabled(component: String, degradedFunctions: Set<String>)
    
    /** Called when degraded mode is disabled */
    suspend fun onDegradedModeDisabled(component: String)
}

/**
 * Exception types for error recovery system.
 */
sealed class RecoveryException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class ComponentNotRegistered(component: String) : 
        RecoveryException("Component not registered: $component")
    class RecoveryStrategyNotSupported(strategy: RecoveryStrategy, component: String) : 
        RecoveryException("Recovery strategy ${strategy.name} not supported by component $component")
    class RecoveryTimeout(component: String, duration: Duration) : 
        RecoveryException("Recovery timeout for component $component after $duration")
    class RecoveryDisabled : RecoveryException("Recovery system is disabled")
    class MaxRetriesExceeded(component: String, attempts: Int) : 
        RecoveryException("Maximum retry attempts ($attempts) exceeded for component $component")
}

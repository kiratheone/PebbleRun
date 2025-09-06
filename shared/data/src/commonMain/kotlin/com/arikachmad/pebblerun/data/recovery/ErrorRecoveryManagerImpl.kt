package com.arikachmad.pebblerun.data.recovery

import com.arikachmad.pebblerun.domain.recovery.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.math.min
import kotlin.math.pow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Implementation of ErrorRecoveryManager for handling service interruptions.
 * Satisfies TASK-033 (Error recovery mechanisms for service interruptions).
 * Implements CON-004 (Graceful handling of disconnections) and robust error handling.
 */
class ErrorRecoveryManagerImpl(
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) : ErrorRecoveryManager {

    // State flows
    private val _recoveryState = MutableStateFlow(RecoveryState.IDLE)
    override val recoveryState: StateFlow<RecoveryState> = _recoveryState.asStateFlow()

    private val _errorHistory = MutableStateFlow<List<ErrorEvent>>(emptyList())
    override val errorHistory: StateFlow<List<ErrorEvent>> = _errorHistory.asStateFlow()

    private val _recoveryConfig = MutableStateFlow(RecoveryConfiguration())
    override val recoveryConfig: StateFlow<RecoveryConfiguration> = _recoveryConfig.asStateFlow()

    // Internal state
    private val registeredComponents = mutableMapOf<String, RecoverableComponent>()
    private val recoveryJobs = mutableMapOf<String, Job>()
    private val retryCounters = mutableMapOf<String, Int>()
    private val lastRecoveryAttempts = mutableMapOf<String, Instant>()
    private val eventListeners = mutableListOf<RecoveryEventListener>()
    private val componentStats = mutableMapOf<String, ComponentRecoveryStats>()
    
    // Statistics
    private var totalErrors = 0L
    private var totalRecoveries = 0L
    private var successfulRecoveries = 0L
    private var failedRecoveries = 0L
    private var systemStartTime = Clock.System.now()

    // Constants
    private val maxHistorySize = 1000
    private val healthCheckInterval = 1.minutes

    init {
        startHealthMonitoring()
    }

    override suspend fun handleError(error: Throwable, context: ErrorContext): RecoveryResult {
        return try {
            totalErrors++
            
            val errorEvent = createErrorEvent(error, context)
            addErrorToHistory(errorEvent)
            
            // Notify listeners
            notifyListeners { it.onErrorOccurred(errorEvent) }
            
            // Determine if recovery should be attempted
            if (!shouldAttemptRecovery(context.component, errorEvent.severity)) {
                return RecoveryResult(
                    success = false,
                    strategy = RecoveryStrategy.UserIntervention,
                    duration = Duration.ZERO,
                    message = "Recovery not attempted due to configuration or exceeded retry limits"
                )
            }
            
            // Attempt recovery
            val component = registeredComponents[context.component]
            if (component == null) {
                return RecoveryResult(
                    success = false,
                    strategy = RecoveryStrategy.UserIntervention,
                    duration = Duration.ZERO,
                    message = "Component not registered for recovery: ${context.component}"
                )
            }
            
            performRecovery(component, errorEvent)
            
        } catch (e: Exception) {
            RecoveryResult(
                success = false,
                strategy = RecoveryStrategy.UserIntervention,
                duration = Duration.ZERO,
                message = "Recovery attempt failed: ${e.message}"
            )
        }
    }

    override suspend fun triggerRecovery(component: String, strategy: RecoveryStrategy): RecoveryResult {
        return try {
            val recoverableComponent = registeredComponents[component]
                ?: throw RecoveryException.ComponentNotRegistered(component)
            
            if (strategy !in recoverableComponent.getSupportedRecoveryStrategies()) {
                throw RecoveryException.RecoveryStrategyNotSupported(strategy, component)
            }
            
            executeRecoveryStrategy(recoverableComponent, strategy)
            
        } catch (e: Exception) {
            RecoveryResult(
                success = false,
                strategy = strategy,
                duration = Duration.ZERO,
                message = "Manual recovery failed: ${e.message}"
            )
        }
    }

    override suspend fun performHealthCheck(): HealthCheckResult {
        val componentHealth = mutableMapOf<String, ComponentHealth>()
        val issues = mutableListOf<HealthIssue>()
        val recommendations = mutableListOf<String>()
        
        for ((name, component) in registeredComponents) {
            try {
                val health = component.checkHealth()
                componentHealth[name] = health
                
                // Update component stats
                updateComponentStats(name, health)
                
                // Generate issues and recommendations
                when (health) {
                    ComponentHealth.WARNING -> {
                        issues.add(
                            HealthIssue(
                                component = name,
                                severity = ErrorSeverity.LOW,
                                description = "Component $name has minor issues",
                                recommendation = "Monitor closely or restart component",
                                autoRecoverable = true
                            )
                        )
                    }
                    ComponentHealth.DEGRADED -> {
                        issues.add(
                            HealthIssue(
                                component = name,
                                severity = ErrorSeverity.MEDIUM,
                                description = "Component $name is running in degraded mode",
                                recommendation = "Attempt recovery to restore full functionality",
                                autoRecoverable = true
                            )
                        )
                    }
                    ComponentHealth.ERROR -> {
                        issues.add(
                            HealthIssue(
                                component = name,
                                severity = ErrorSeverity.HIGH,
                                description = "Component $name has errors",
                                recommendation = "Immediate recovery required",
                                autoRecoverable = true
                            )
                        )
                    }
                    ComponentHealth.FAILED -> {
                        issues.add(
                            HealthIssue(
                                component = name,
                                severity = ErrorSeverity.CRITICAL,
                                description = "Component $name has failed",
                                recommendation = "Critical recovery needed - may require user intervention",
                                autoRecoverable = component.isCritical
                            )
                        )
                    }
                    else -> {
                        // Healthy or unknown - no issues
                    }
                }
                
            } catch (e: Exception) {
                componentHealth[name] = ComponentHealth.UNKNOWN
                issues.add(
                    HealthIssue(
                        component = name,
                        severity = ErrorSeverity.HIGH,
                        description = "Unable to check health of component $name: ${e.message}",
                        recommendation = "Investigate component health check failure",
                        autoRecoverable = false
                    )
                )
            }
        }
        
        // Determine overall health
        val overallHealth = when {
            componentHealth.values.any { it == ComponentHealth.FAILED } -> ComponentHealth.FAILED
            componentHealth.values.any { it == ComponentHealth.ERROR } -> ComponentHealth.ERROR
            componentHealth.values.any { it == ComponentHealth.DEGRADED } -> ComponentHealth.DEGRADED
            componentHealth.values.any { it == ComponentHealth.WARNING } -> ComponentHealth.WARNING
            componentHealth.values.all { it == ComponentHealth.HEALTHY } -> ComponentHealth.HEALTHY
            else -> ComponentHealth.UNKNOWN
        }
        
        // Generate general recommendations
        if (issues.isNotEmpty()) {
            recommendations.add("Review component health issues and consider recovery actions")
        }
        if (overallHealth == ComponentHealth.DEGRADED) {
            recommendations.add("System is operating with reduced functionality")
        }
        
        return HealthCheckResult(
            overallHealth = overallHealth,
            componentHealth = componentHealth,
            timestamp = Clock.System.now(),
            issues = issues,
            recommendations = recommendations
        )
    }

    override suspend fun updateRecoveryConfig(config: RecoveryConfiguration) {
        _recoveryConfig.value = config
        
        // If auto-recovery was disabled, cancel any ongoing recovery operations
        if (!config.autoRecoveryEnabled) {
            cancelAllRecoveryJobs()
        }
    }

    override fun registerComponent(component: RecoverableComponent) {
        registeredComponents[component.componentName] = component
        
        // Initialize component stats
        componentStats[component.componentName] = ComponentRecoveryStats(
            componentName = component.componentName,
            errors = 0,
            recoveries = 0,
            successRate = 0.0,
            averageRecoveryTime = Duration.ZERO,
            lastError = null,
            lastRecovery = null,
            currentHealth = ComponentHealth.UNKNOWN
        )
    }

    override fun unregisterComponent(componentName: String) {
        registeredComponents.remove(componentName)
        componentStats.remove(componentName)
        retryCounters.remove(componentName)
        lastRecoveryAttempts.remove(componentName)
        
        // Cancel any ongoing recovery for this component
        recoveryJobs[componentName]?.cancel()
        recoveryJobs.remove(componentName)
    }

    override fun getRecoveryStatistics(): RecoveryStatistics {
        val currentTime = Clock.System.now()
        val uptime = currentTime.minus(systemStartTime)
        
        val averageRecoveryTime = if (totalRecoveries > 0) {
            // Calculate from error history
            val recoveryDurations = _errorHistory.value
                .filter { it.recovered && it.recoveryDuration != null }
                .mapNotNull { it.recoveryDuration }
            
            if (recoveryDurations.isNotEmpty()) {
                recoveryDurations.fold(Duration.ZERO) { acc, duration -> acc + duration } / recoveryDurations.size
            } else {
                Duration.ZERO
            }
        } else {
            Duration.ZERO
        }
        
        return RecoveryStatistics(
            totalErrors = totalErrors,
            totalRecoveries = totalRecoveries,
            successfulRecoveries = successfulRecoveries,
            failedRecoveries = failedRecoveries,
            averageRecoveryTime = averageRecoveryTime,
            componentStats = componentStats.toMap(),
            uptime = uptime,
            lastReset = systemStartTime
        )
    }

    override suspend fun setAutoRecoveryEnabled(enabled: Boolean) {
        val currentConfig = _recoveryConfig.value
        updateRecoveryConfig(currentConfig.copy(autoRecoveryEnabled = enabled))
        
        if (enabled) {
            _recoveryState.value = RecoveryState.MONITORING
        } else {
            _recoveryState.value = RecoveryState.DISABLED
        }
    }

    override suspend fun resetRecoveryState() {
        // Cancel all ongoing recovery operations
        cancelAllRecoveryJobs()
        
        // Reset counters and state
        retryCounters.clear()
        lastRecoveryAttempts.clear()
        totalErrors = 0
        totalRecoveries = 0
        successfulRecoveries = 0
        failedRecoveries = 0
        systemStartTime = Clock.System.now()
        
        // Clear error history
        _errorHistory.value = emptyList()
        
        // Reset component stats
        componentStats.values.forEach { stats ->
            componentStats[stats.componentName] = stats.copy(
                errors = 0,
                recoveries = 0,
                successRate = 0.0,
                averageRecoveryTime = Duration.ZERO,
                lastError = null,
                lastRecovery = null
            )
        }
        
        _recoveryState.value = if (_recoveryConfig.value.autoRecoveryEnabled) {
            RecoveryState.MONITORING
        } else {
            RecoveryState.DISABLED
        }
    }

    // Event listener management
    fun addRecoveryEventListener(listener: RecoveryEventListener) {
        eventListeners.add(listener)
    }

    fun removeRecoveryEventListener(listener: RecoveryEventListener) {
        eventListeners.remove(listener)
    }

    // Private helper methods

    private fun createErrorEvent(error: Throwable, context: ErrorContext): ErrorEvent {
        val severity = determineSeverity(error, context)
        val errorId = generateErrorId()
        
        return ErrorEvent(
            id = errorId,
            error = error,
            context = context,
            timestamp = Clock.System.now(),
            severity = severity,
            component = context.component,
            recoveryAttempts = retryCounters[context.component] ?: 0
        )
    }

    private fun determineSeverity(error: Throwable, context: ErrorContext): ErrorSeverity {
        return when {
            // Critical errors that stop the workout
            error is OutOfMemoryError || 
            error is SecurityException ||
            (context.workoutActive && context.component in listOf("workout_service", "location_provider")) -> 
                ErrorSeverity.CRITICAL
            
            // High severity for core components
            error is IllegalStateException ||
            context.component in listOf("pebble_transport", "database") -> 
                ErrorSeverity.HIGH
            
            // Medium severity for recoverable issues
            error is kotlinx.coroutines.TimeoutCancellationException ||
            error.message?.contains("connection", ignoreCase = true) == true -> 
                ErrorSeverity.MEDIUM
            
            // Low severity for minor issues
            else -> ErrorSeverity.LOW
        }
    }

    private fun shouldAttemptRecovery(component: String, severity: ErrorSeverity): Boolean {
        val config = _recoveryConfig.value
        
        // Check if auto-recovery is enabled
        if (!config.autoRecoveryEnabled) return false
        
        // Check retry limits
        val currentRetries = retryCounters[component] ?: 0
        val maxRetries = config.componentSpecificConfig[component]?.maxRetryAttempts ?: config.maxRetryAttempts
        
        if (currentRetries >= maxRetries) return false
        
        // Check severity threshold
        if (severity < config.userNotificationThreshold && severity != ErrorSeverity.CRITICAL) return true
        
        // Allow recovery for critical errors if escalation is enabled
        return severity == ErrorSeverity.CRITICAL && config.criticalErrorEscalation
    }

    private suspend fun performRecovery(component: RecoverableComponent, errorEvent: ErrorEvent): RecoveryResult {
        val componentName = component.componentName
        
        // Update state
        _recoveryState.value = RecoveryState.RECOVERING
        totalRecoveries++
        
        // Increment retry counter
        retryCounters[componentName] = (retryCounters[componentName] ?: 0) + 1
        lastRecoveryAttempts[componentName] = Clock.System.now()
        
        // Notify listeners
        val strategies = component.getSupportedRecoveryStrategies()
        val strategy = selectRecoveryStrategy(strategies, errorEvent.severity, retryCounters[componentName] ?: 1)
        
        notifyListeners { it.onRecoveryStarted(componentName, strategy) }
        
        try {
            val result = executeRecoveryStrategy(component, strategy)
            
            if (result.success) {
                successfulRecoveries++
                retryCounters.remove(componentName) // Reset counter on success
                updateErrorEvent(errorEvent.id, recovered = true, recoveryDuration = result.duration)
                
                // Update stats
                updateComponentRecoveryStats(componentName, true, result.duration)
                
                notifyListeners { it.onRecoveryCompleted(componentName, result) }
                
                // Check if degraded mode was enabled
                if (result.isPartialRecovery) {
                    notifyListeners { 
                        it.onDegradedModeEnabled(componentName, result.degradedFunctionality) 
                    }
                }
            } else {
                failedRecoveries++
                updateComponentRecoveryStats(componentName, false, result.duration)
                notifyListeners { it.onRecoveryFailed(componentName, Exception(result.message)) }
            }
            
            return result
            
        } catch (e: Exception) {
            failedRecoveries++
            updateComponentRecoveryStats(componentName, false, Duration.ZERO)
            notifyListeners { it.onRecoveryFailed(componentName, e) }
            
            return RecoveryResult(
                success = false,
                strategy = strategy,
                duration = Duration.ZERO,
                message = "Recovery failed with exception: ${e.message}"
            )
        } finally {
            _recoveryState.value = RecoveryState.MONITORING
        }
    }

    private suspend fun executeRecoveryStrategy(
        component: RecoverableComponent, 
        strategy: RecoveryStrategy
    ): RecoveryResult {
        val startTime = Clock.System.now()
        
        return when (strategy) {
            RecoveryStrategy.GracefulDegradation -> {
                val degraded = component.enableDegradedMode()
                val duration = Clock.System.now().minus(startTime)
                RecoveryResult(
                    success = degraded,
                    strategy = strategy,
                    duration = duration,
                    message = if (degraded) "Degraded mode enabled" else "Failed to enable degraded mode",
                    degradedFunctionality = if (degraded) component.getDegradedFunctionality() else emptySet()
                )
            }
            else -> {
                component.recover(strategy)
            }
        }
    }

    private fun selectRecoveryStrategy(
        availableStrategies: List<RecoveryStrategy>, 
        severity: ErrorSeverity, 
        attemptNumber: Int
    ): RecoveryStrategy {
        val config = _recoveryConfig.value
        
        // For critical errors, try more aggressive strategies first
        val prioritizedStrategies = when (severity) {
            ErrorSeverity.CRITICAL -> availableStrategies.sortedByDescending { it.riskLevel }
            ErrorSeverity.HIGH -> availableStrategies.sortedBy { it.riskLevel }
            else -> availableStrategies.filter { it.riskLevel <= RiskLevel.MEDIUM }
        }
        
        // Use different strategies based on attempt number
        return when (attemptNumber) {
            1 -> prioritizedStrategies.find { it.riskLevel == RiskLevel.LOW } 
                ?: RecoveryStrategy.Restart
            2 -> prioritizedStrategies.find { it.riskLevel == RiskLevel.MEDIUM } 
                ?: RecoveryStrategy.Reset
            3 -> prioritizedStrategies.find { it.riskLevel == RiskLevel.HIGH } 
                ?: RecoveryStrategy.Reinitialize
            else -> if (config.enableDegradedMode) {
                RecoveryStrategy.GracefulDegradation
            } else {
                RecoveryStrategy.UserIntervention
            }
        }
    }

    private suspend fun addErrorToHistory(errorEvent: ErrorEvent) {
        val currentHistory = _errorHistory.value
        val updatedHistory = (currentHistory + errorEvent).takeLast(maxHistorySize)
        _errorHistory.value = updatedHistory
    }

    private suspend fun updateErrorEvent(eventId: String, recovered: Boolean, recoveryDuration: Duration?) {
        val currentHistory = _errorHistory.value
        val updatedHistory = currentHistory.map { event ->
            if (event.id == eventId) {
                event.copy(recovered = recovered, recoveryDuration = recoveryDuration)
            } else {
                event
            }
        }
        _errorHistory.value = updatedHistory
    }

    private fun updateComponentStats(componentName: String, health: ComponentHealth) {
        val currentStats = componentStats[componentName] ?: return
        componentStats[componentName] = currentStats.copy(currentHealth = health)
    }

    private fun updateComponentRecoveryStats(componentName: String, success: Boolean, duration: Duration) {
        val currentStats = componentStats[componentName] ?: return
        val newRecoveries = currentStats.recoveries + 1
        val newSuccessRate = if (success) {
            (currentStats.successRate * currentStats.recoveries + 1.0) / newRecoveries
        } else {
            (currentStats.successRate * currentStats.recoveries) / newRecoveries
        }
        
        val newAverageTime = if (currentStats.recoveries > 0) {
            (currentStats.averageRecoveryTime * currentStats.recoveries.toInt() + duration) / (currentStats.recoveries + 1).toInt()
        } else {
            duration
        }
        
        componentStats[componentName] = currentStats.copy(
            recoveries = newRecoveries,
            successRate = newSuccessRate,
            averageRecoveryTime = newAverageTime,
            lastRecovery = Clock.System.now()
        )
    }

    private fun startHealthMonitoring() {
        scope.launch {
            _recoveryState.value = if (_recoveryConfig.value.autoRecoveryEnabled) {
                RecoveryState.MONITORING
            } else {
                RecoveryState.DISABLED
            }
            
            while (isActive) {
                try {
                    if (_recoveryState.value == RecoveryState.MONITORING) {
                        performHealthCheck()
                    }
                    delay(healthCheckInterval)
                } catch (e: CancellationException) {
                    break
                } catch (e: Exception) {
                    println("Health monitoring error: ${e.message}")
                    delay(healthCheckInterval)
                }
            }
        }
    }

    private fun cancelAllRecoveryJobs() {
        recoveryJobs.values.forEach { it.cancel() }
        recoveryJobs.clear()
    }

    private suspend fun notifyListeners(action: suspend (RecoveryEventListener) -> Unit) {
        eventListeners.forEach { listener ->
            try {
                action(listener)
            } catch (e: Exception) {
                println("Error notifying recovery event listener: ${e.message}")
            }
        }
    }

    private fun generateErrorId(): String {
        return "error_${Clock.System.now().toEpochMilliseconds()}_${kotlin.random.Random.nextInt(1000, 9999)}"
    }
}

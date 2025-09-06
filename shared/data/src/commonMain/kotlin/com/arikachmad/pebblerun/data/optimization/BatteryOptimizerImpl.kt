package com.arikachmad.pebblerun.data.optimization

import com.arikachmad.pebblerun.domain.optimization.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.math.max
import kotlin.math.min
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Implementation of BatteryOptimizer for workout tracking power management.
 * Satisfies CON-001 (Battery optimization) and TASK-031 (Battery optimization strategies).
 */
class BatteryOptimizerImpl(
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) : BatteryOptimizer {

    // State flows
    private val _optimizationLevel = MutableStateFlow(OptimizationLevel.BALANCED)
    override val optimizationLevel: StateFlow<OptimizationLevel> = _optimizationLevel.asStateFlow()

    private val _batteryStatus = MutableStateFlow(
        BatteryStatus(
            level = 100.0,
            isCharging = false,
            isLowPowerMode = false,
            health = BatteryHealth.GOOD,
            temperature = null,
            voltage = null,
            estimatedTimeRemaining = null,
            lastUpdated = Clock.System.now()
        )
    )
    override val batteryStatus: StateFlow<BatteryStatus> = _batteryStatus.asStateFlow()

    private val _powerConsumption = MutableStateFlow(
        PowerConsumption(
            currentDrainRate = 0.0,
            averageDrainRate = 0.0,
            peakDrainRate = 0.0,
            componentBreakdown = emptyMap(),
            estimatedLifetime = Duration.INFINITE,
            lastMeasurement = Clock.System.now()
        )
    )
    override val powerConsumption: StateFlow<PowerConsumption> = _powerConsumption.asStateFlow()

    // Power-aware components
    private val powerAwareComponents = mutableMapOf<String, PowerAwareComponent>()
    
    // Monitoring
    private var monitoringJob: Job? = null
    private val batteryHistory = mutableListOf<BatteryMeasurement>()
    private val maxHistorySize = 100
    
    // Power consumption tracking
    private var lastBatteryLevel = 100.0
    private var lastMeasurementTime = Clock.System.now()
    
    // Configuration cache
    private var cachedConfigurations = mutableMapOf<OptimizationLevel, TrackingConfiguration>()

    data class BatteryMeasurement(
        val level: Double,
        val timestamp: Instant,
        val drainRate: Double
    )

    override suspend fun setOptimizationLevel(level: OptimizationLevel) {
        try {
            val previousLevel = _optimizationLevel.value
            _optimizationLevel.value = level
            
            // Apply optimization immediately
            applyOptimizationLevel(level)
            
            println("Battery optimization level changed from $previousLevel to $level")
        } catch (e: Exception) {
            // Revert on failure
            println("Failed to set optimization level: ${e.message}")
            throw BatteryOptimizationException.OptimizationFailed(e)
        }
    }

    override suspend fun getRecommendedOptimizationLevel(): OptimizationLevel {
        val status = _batteryStatus.value
        val consumption = _powerConsumption.value
        
        return when {
            // Emergency mode for critical battery
            status.level <= OptimizationLevel.EMERGENCY.batteryThreshold -> OptimizationLevel.EMERGENCY
            
            // Power saver for low battery or high consumption
            status.level <= OptimizationLevel.POWER_SAVER.batteryThreshold || 
            consumption.isHighConsumption -> OptimizationLevel.POWER_SAVER
            
            // Balanced for moderate battery levels
            status.level <= OptimizationLevel.BALANCED.batteryThreshold -> OptimizationLevel.BALANCED
            
            // Maximum performance for high battery and charging
            status.isCharging || status.level >= OptimizationLevel.MAXIMUM_PERFORMANCE.batteryThreshold -> 
                OptimizationLevel.MAXIMUM_PERFORMANCE
            
            else -> OptimizationLevel.BALANCED
        }
    }

    override suspend fun optimizeTrackingParameters(currentBatteryLevel: Double): TrackingConfiguration {
        val recommendedLevel = determineOptimizationLevel(currentBatteryLevel)
        
        // Check cache first
        cachedConfigurations[recommendedLevel]?.let { return it }
        
        // Create configuration for the level
        val baseConfig = TrackingConfiguration.forOptimizationLevel(recommendedLevel)
        
        // Apply additional optimizations based on current conditions
        val optimizedConfig = when {
            currentBatteryLevel <= 15.0 -> baseConfig.copy(
                locationUpdateInterval = baseConfig.locationUpdateInterval * 2,
                hrSampleInterval = baseConfig.hrSampleInterval * 2,
                backgroundProcessingEnabled = false,
                wakeLockEnabled = false
            )
            currentBatteryLevel <= 30.0 -> baseConfig.copy(
                locationUpdateInterval = baseConfig.locationUpdateInterval * 1.5,
                hrSampleInterval = baseConfig.hrSampleInterval * 1.5,
                highAccuracyLocationEnabled = false
            )
            else -> baseConfig
        }
        
        // Cache the configuration
        cachedConfigurations[recommendedLevel] = optimizedConfig
        
        return optimizedConfig
    }

    override fun estimateRemainingWorkoutTime(currentUsage: Double, batteryLevel: Double): Duration {
        if (currentUsage <= 0 || batteryLevel <= 0) return Duration.ZERO
        
        // Calculate remaining battery capacity in mAh (assuming typical phone battery ~3000mAh)
        val totalCapacity = 3000.0 // mAh - should be device-specific
        val remainingCapacity = (batteryLevel / 100.0) * totalCapacity
        
        // Calculate hours remaining based on current drain rate
        val hoursRemaining = remainingCapacity / currentUsage
        
        // Add safety margin (reduce by 20% for real-world conditions)
        val safeHoursRemaining = hoursRemaining * 0.8
        
        return max(0.0, safeHoursRemaining).hours
    }

    override suspend fun applyPowerSavingMeasures(emergencyLevel: Boolean) {
        try {
            val targetLevel = if (emergencyLevel) OptimizationLevel.EMERGENCY else OptimizationLevel.POWER_SAVER
            
            // Apply optimization level
            setOptimizationLevel(targetLevel)
            
            // Apply emergency measures if needed
            if (emergencyLevel) {
                applyEmergencyPowerSaving()
            }
            
            // Notify components to reduce power consumption
            powerAwareComponents.values.forEach { component ->
                try {
                    val powerLevel = if (emergencyLevel) 0.1 else 0.3
                    component.setPowerLevel(powerLevel)
                } catch (e: Exception) {
                    println("Failed to apply power saving to component: ${e.message}")
                }
            }
            
            println("Applied power saving measures (emergency: $emergencyLevel)")
        } catch (e: Exception) {
            throw BatteryOptimizationException.OptimizationFailed(e)
        }
    }

    override suspend fun startBatteryMonitoring() {
        stopBatteryMonitoring() // Stop any existing monitoring
        
        monitoringJob = scope.launch {
            while (isActive) {
                try {
                    updateBatteryStatus()
                    updatePowerConsumption()
                    checkForOptimizationNeeds()
                    delay(30.seconds) // Monitor every 30 seconds
                } catch (e: CancellationException) {
                    break
                } catch (e: Exception) {
                    println("Battery monitoring error: ${e.message}")
                    delay(60.seconds) // Retry after 1 minute on error
                }
            }
        }
        
        println("Battery monitoring started")
    }

    override suspend fun stopBatteryMonitoring() {
        monitoringJob?.cancel()
        monitoringJob = null
        println("Battery monitoring stopped")
    }

    // Registration for power-aware components
    fun registerPowerAwareComponent(name: String, component: PowerAwareComponent) {
        powerAwareComponents[name] = component
    }

    fun unregisterPowerAwareComponent(name: String) {
        powerAwareComponents.remove(name)
    }

    // Private helper methods

    private suspend fun applyOptimizationLevel(level: OptimizationLevel) {
        val config = TrackingConfiguration.forOptimizationLevel(level)
        
        // Apply configuration to power-aware components
        powerAwareComponents.values.forEach { component ->
            try {
                val powerLevel = when (level) {
                    OptimizationLevel.MAXIMUM_PERFORMANCE -> 1.0
                    OptimizationLevel.BALANCED -> 0.7
                    OptimizationLevel.POWER_SAVER -> 0.4
                    OptimizationLevel.EMERGENCY -> 0.1
                }
                component.setPowerLevel(powerLevel)
            } catch (e: Exception) {
                println("Failed to apply optimization level to component: ${e.message}")
            }
        }
    }

    private suspend fun applyEmergencyPowerSaving() {
        // Disable all non-essential features
        powerAwareComponents.values.forEach { component ->
            try {
                // Find and apply the most power-efficient mode
                val powerModes = component.getAvailablePowerModes()
                val emergencyMode = powerModes.minByOrNull { it.powerLevel }
                emergencyMode?.let { component.applyPowerMode(it) }
            } catch (e: Exception) {
                println("Failed to apply emergency power saving: ${e.message}")
            }
        }
    }

    private fun determineOptimizationLevel(batteryLevel: Double): OptimizationLevel {
        return OptimizationLevel.values().firstOrNull { level ->
            batteryLevel <= level.batteryThreshold
        } ?: OptimizationLevel.MAXIMUM_PERFORMANCE
    }

    private suspend fun updateBatteryStatus() {
        // This would be implemented with platform-specific battery APIs
        val currentTime = Clock.System.now()
        
        // Simulate battery status update (in real implementation, this would call platform APIs)
        val currentLevel = simulateBatteryLevel()
        val isCharging = false // Platform-specific implementation needed
        val isLowPowerMode = false // Platform-specific implementation needed
        
        val status = BatteryStatus(
            level = currentLevel,
            isCharging = isCharging,
            isLowPowerMode = isLowPowerMode,
            health = BatteryHealth.GOOD,
            temperature = null, // Platform-specific
            voltage = null, // Platform-specific
            estimatedTimeRemaining = calculateEstimatedTimeRemaining(currentLevel),
            lastUpdated = currentTime
        )
        
        _batteryStatus.value = status
        
        // Record measurement for history
        recordBatteryMeasurement(currentLevel, currentTime)
    }

    private suspend fun updatePowerConsumption() {
        val currentTime = Clock.System.now()
        val currentLevel = _batteryStatus.value.level
        
        // Calculate drain rate based on battery level change
        val timeDiff = currentTime.minus(lastMeasurementTime).inWholeSeconds.toDouble()
        val levelDiff = lastBatteryLevel - currentLevel
        
        val currentDrainRate = if (timeDiff > 0) {
            // Estimate drain rate in mA/h based on percentage change
            (levelDiff / timeDiff) * 3600 * 30 // Rough estimation
        } else {
            _powerConsumption.value.currentDrainRate
        }
        
        // Calculate average from history
        val averageDrainRate = batteryHistory.takeLast(10).map { it.drainRate }.average()
        val peakDrainRate = batteryHistory.maxOfOrNull { it.drainRate } ?: currentDrainRate
        
        // Get component breakdown
        val componentBreakdown = powerAwareComponents.mapValues { (_, component) ->
            component.getPowerContribution().currentConsumption
        }
        
        val estimatedLifetime = estimateRemainingWorkoutTime(currentDrainRate, currentLevel)
        
        val consumption = PowerConsumption(
            currentDrainRate = max(0.0, currentDrainRate),
            averageDrainRate = max(0.0, averageDrainRate),
            peakDrainRate = max(0.0, peakDrainRate),
            componentBreakdown = componentBreakdown,
            estimatedLifetime = estimatedLifetime,
            lastMeasurement = currentTime
        )
        
        _powerConsumption.value = consumption
        
        // Update tracking variables
        lastBatteryLevel = currentLevel
        lastMeasurementTime = currentTime
    }

    private suspend fun checkForOptimizationNeeds() {
        val status = _batteryStatus.value
        val consumption = _powerConsumption.value
        val currentLevel = _optimizationLevel.value
        
        val recommendedLevel = getRecommendedOptimizationLevel()
        
        // Auto-adjust optimization level if needed
        if (recommendedLevel != currentLevel) {
            println("Auto-adjusting optimization level from $currentLevel to $recommendedLevel")
            setOptimizationLevel(recommendedLevel)
        }
        
        // Apply emergency measures if critical
        if (status.isCritical && !status.isCharging) {
            applyPowerSavingMeasures(emergencyLevel = true)
        }
    }

    private fun recordBatteryMeasurement(level: Double, timestamp: Instant) {
        val timeDiff = if (batteryHistory.isNotEmpty()) {
            timestamp.minus(batteryHistory.last().timestamp).inWholeSeconds.toDouble()
        } else {
            0.0
        }
        
        val levelDiff = if (batteryHistory.isNotEmpty()) {
            batteryHistory.last().level - level
        } else {
            0.0
        }
        
        val drainRate = if (timeDiff > 0) {
            (levelDiff / timeDiff) * 3600 * 30 // Rough mA/h estimation
        } else {
            0.0
        }
        
        val measurement = BatteryMeasurement(level, timestamp, max(0.0, drainRate))
        
        batteryHistory.add(measurement)
        
        // Keep only recent measurements
        if (batteryHistory.size > maxHistorySize) {
            batteryHistory.removeAt(0)
        }
    }

    private fun calculateEstimatedTimeRemaining(currentLevel: Double): Duration? {
        if (batteryHistory.size < 2) return null
        
        val averageDrainRate = batteryHistory.takeLast(5).map { it.drainRate }.average()
        if (averageDrainRate <= 0) return null
        
        return estimateRemainingWorkoutTime(averageDrainRate, currentLevel)
    }

    private fun simulateBatteryLevel(): Double {
        // Simulate gradual battery drain for testing
        val current = _batteryStatus.value.level
        return max(0.0, current - 0.1) // Lose 0.1% per update cycle
    }
}

/**
 * Example implementation of a power-aware location component.
 */
class PowerAwareLocationComponent : PowerAwareComponent {
    private var currentPowerLevel = 1.0
    
    override fun getCurrentPowerLevel(): Double = currentPowerLevel
    
    override suspend fun setPowerLevel(level: Double) {
        currentPowerLevel = level.coerceIn(0.0, 1.0)
        // Adjust location update frequency, accuracy, etc. based on power level
    }
    
    override fun getAvailablePowerModes(): List<PowerMode> {
        return listOf(
            PowerMode("high_accuracy", "High Accuracy", "GPS + Network + Sensors", 1.0, setOf("gps", "network", "sensors")),
            PowerMode("balanced", "Balanced", "GPS + Network", 0.6, setOf("gps", "network")),
            PowerMode("power_save", "Power Save", "Network only", 0.3, setOf("network")),
            PowerMode("minimal", "Minimal", "Significant changes only", 0.1, setOf("significant_changes"))
        )
    }
    
    override suspend fun applyPowerMode(mode: PowerMode) {
        currentPowerLevel = mode.powerLevel
        // Apply the specific power mode configuration
    }
    
    override fun getPowerContribution(): PowerContribution {
        return PowerContribution(
            componentName = "Location Provider",
            currentConsumption = currentPowerLevel * 500, // mA/h
            baselineConsumption = 50.0, // mA/h
            maxConsumption = 500.0, // mA/h
            optimizationPotential = (1.0 - currentPowerLevel) * 100
        )
    }
}

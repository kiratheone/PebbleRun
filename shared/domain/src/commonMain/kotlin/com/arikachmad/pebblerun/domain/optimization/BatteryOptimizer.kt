package com.arikachmad.pebblerun.domain.optimization

import kotlinx.coroutines.flow.StateFlow
import kotlinx.datetime.Instant
import kotlin.time.Duration

/**
 * Battery optimization strategies for workout tracking.
 * Satisfies CON-001 (Battery optimization) and TASK-031 (Battery optimization strategies).
 * Implements power-aware tracking to maximize battery life during workouts.
 */
interface BatteryOptimizer {
    
    /** Current battery optimization level */
    val optimizationLevel: StateFlow<OptimizationLevel>
    
    /** Current battery status and metrics */
    val batteryStatus: StateFlow<BatteryStatus>
    
    /** Power consumption estimates */
    val powerConsumption: StateFlow<PowerConsumption>
    
    /**
     * Sets the battery optimization level
     * @param level The optimization level to apply
     */
    suspend fun setOptimizationLevel(level: OptimizationLevel)
    
    /**
     * Gets recommended optimization level based on current battery status
     * @return Recommended optimization level
     */
    suspend fun getRecommendedOptimizationLevel(): OptimizationLevel
    
    /**
     * Optimizes tracking parameters based on battery level
     * @param currentBatteryLevel Battery level percentage (0-100)
     * @return Optimized tracking configuration
     */
    suspend fun optimizeTrackingParameters(currentBatteryLevel: Double): TrackingConfiguration
    
    /**
     * Estimates remaining workout time based on current battery usage
     * @param currentUsage Current power consumption rate
     * @param batteryLevel Current battery level percentage
     * @return Estimated remaining workout duration
     */
    fun estimateRemainingWorkoutTime(currentUsage: Double, batteryLevel: Double): Duration
    
    /**
     * Applies power-saving measures immediately
     * @param emergencyLevel Whether this is an emergency power-saving situation
     */
    suspend fun applyPowerSavingMeasures(emergencyLevel: Boolean = false)
    
    /**
     * Monitors battery drain and adjusts settings automatically
     */
    suspend fun startBatteryMonitoring()
    
    /**
     * Stops battery monitoring
     */
    suspend fun stopBatteryMonitoring()
}

/**
 * Battery optimization levels with different power-saving strategies.
 */
enum class OptimizationLevel(
    val displayName: String,
    val description: String,
    val batteryThreshold: Double
) {
    MAXIMUM_PERFORMANCE(
        "Maximum Performance",
        "Highest accuracy, maximum battery usage",
        batteryThreshold = 80.0
    ),
    BALANCED(
        "Balanced",
        "Good balance between accuracy and battery life",
        batteryThreshold = 50.0
    ),
    POWER_SAVER(
        "Power Saver",
        "Reduced accuracy, extended battery life",
        batteryThreshold = 30.0
    ),
    EMERGENCY(
        "Emergency Mode",
        "Minimal features, maximum battery conservation",
        batteryThreshold = 15.0
    )
}

/**
 * Current battery status and health information.
 */
data class BatteryStatus(
    val level: Double, // Percentage 0-100
    val isCharging: Boolean,
    val isLowPowerMode: Boolean,
    val health: BatteryHealth,
    val temperature: Double?, // Celsius
    val voltage: Double?, // Volts
    val estimatedTimeRemaining: Duration?,
    val lastUpdated: Instant
) {
    val isLow: Boolean get() = level <= 20.0
    val isCritical: Boolean get() = level <= 10.0
    val shouldOptimize: Boolean get() = level <= 50.0 && !isCharging
}

enum class BatteryHealth {
    GOOD, FAIR, POOR, UNKNOWN
}

/**
 * Power consumption metrics and tracking.
 */
data class PowerConsumption(
    val currentDrainRate: Double, // mA/h
    val averageDrainRate: Double, // mA/h
    val peakDrainRate: Double, // mA/h
    val componentBreakdown: Map<String, Double>, // Component -> drain rate
    val estimatedLifetime: Duration,
    val lastMeasurement: Instant
) {
    val isHighConsumption: Boolean get() = currentDrainRate > averageDrainRate * 1.5
}

/**
 * Optimized tracking configuration based on battery level.
 */
data class TrackingConfiguration(
    val locationUpdateInterval: Duration,
    val hrSampleInterval: Duration,
    val pebbleSyncInterval: Duration,
    val backgroundProcessingEnabled: Boolean,
    val highAccuracyLocationEnabled: Boolean,
    val wakeLockEnabled: Boolean,
    val notificationFrequency: Duration,
    val dataCompressionEnabled: Boolean,
    val optimizationLevel: OptimizationLevel
) {
    companion object {
        fun forOptimizationLevel(level: OptimizationLevel): TrackingConfiguration {
            return when (level) {
                OptimizationLevel.MAXIMUM_PERFORMANCE -> TrackingConfiguration(
                    locationUpdateInterval = Duration.parse("1s"),
                    hrSampleInterval = Duration.parse("1s"),
                    pebbleSyncInterval = Duration.parse("2s"),
                    backgroundProcessingEnabled = true,
                    highAccuracyLocationEnabled = true,
                    wakeLockEnabled = true,
                    notificationFrequency = Duration.parse("5s"),
                    dataCompressionEnabled = false,
                    optimizationLevel = level
                )
                OptimizationLevel.BALANCED -> TrackingConfiguration(
                    locationUpdateInterval = Duration.parse("2s"),
                    hrSampleInterval = Duration.parse("2s"),
                    pebbleSyncInterval = Duration.parse("5s"),
                    backgroundProcessingEnabled = true,
                    highAccuracyLocationEnabled = true,
                    wakeLockEnabled = true,
                    notificationFrequency = Duration.parse("10s"),
                    dataCompressionEnabled = true,
                    optimizationLevel = level
                )
                OptimizationLevel.POWER_SAVER -> TrackingConfiguration(
                    locationUpdateInterval = Duration.parse("5s"),
                    hrSampleInterval = Duration.parse("5s"),
                    pebbleSyncInterval = Duration.parse("10s"),
                    backgroundProcessingEnabled = true,
                    highAccuracyLocationEnabled = false,
                    wakeLockEnabled = false,
                    notificationFrequency = Duration.parse("30s"),
                    dataCompressionEnabled = true,
                    optimizationLevel = level
                )
                OptimizationLevel.EMERGENCY -> TrackingConfiguration(
                    locationUpdateInterval = Duration.parse("30s"),
                    hrSampleInterval = Duration.parse("30s"),
                    pebbleSyncInterval = Duration.parse("60s"),
                    backgroundProcessingEnabled = false,
                    highAccuracyLocationEnabled = false,
                    wakeLockEnabled = false,
                    notificationFrequency = Duration.parse("60s"),
                    dataCompressionEnabled = true,
                    optimizationLevel = level
                )
            }
        }
    }
}

/**
 * Battery optimization strategies and recommendations.
 */
data class OptimizationStrategy(
    val name: String,
    val description: String,
    val estimatedSavings: Double, // Percentage of battery saved
    val impactOnAccuracy: AccuracyImpact,
    val apply: suspend () -> Unit
)

enum class AccuracyImpact {
    NONE, LOW, MEDIUM, HIGH
}

/**
 * Power-aware component interface for components that can adjust their power consumption.
 */
interface PowerAwareComponent {
    /**
     * Gets current power consumption level (0.0 to 1.0)
     */
    fun getCurrentPowerLevel(): Double
    
    /**
     * Sets power consumption level (0.0 = minimum, 1.0 = maximum)
     */
    suspend fun setPowerLevel(level: Double)
    
    /**
     * Gets available power saving modes
     */
    fun getAvailablePowerModes(): List<PowerMode>
    
    /**
     * Applies a specific power mode
     */
    suspend fun applyPowerMode(mode: PowerMode)
    
    /**
     * Gets component's contribution to total power consumption
     */
    fun getPowerContribution(): PowerContribution
}

data class PowerMode(
    val id: String,
    val name: String,
    val description: String,
    val powerLevel: Double,
    val features: Set<String>
)

data class PowerContribution(
    val componentName: String,
    val currentConsumption: Double, // mA/h
    val baselineConsumption: Double, // mA/h minimum
    val maxConsumption: Double, // mA/h maximum
    val optimizationPotential: Double // Percentage that can be saved
)

/**
 * Exception types for battery optimization.
 */
sealed class BatteryOptimizationException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class OptimizationFailed(cause: Throwable) : BatteryOptimizationException("Battery optimization failed", cause)
    class UnsupportedOptimizationLevel(level: OptimizationLevel) : 
        BatteryOptimizationException("Optimization level not supported: $level")
    class BatteryMonitoringUnavailable : BatteryOptimizationException("Battery monitoring is not available on this device")
    class PowerModeNotSupported(mode: String) : BatteryOptimizationException("Power mode not supported: $mode")
}

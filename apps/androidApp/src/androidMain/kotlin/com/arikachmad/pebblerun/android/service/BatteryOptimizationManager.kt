package com.arikachmad.pebblerun.android.service

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.annotation.RequiresApi

/**
 * Android battery optimization manager for PebbleRun.
 * Implements TASK-033: Android battery optimization handling.
 * Ensures reliable background operation for workout tracking.
 */
class BatteryOptimizationManager(private val context: Context) {
    
    private val powerManager: PowerManager by lazy {
        context.getSystemService(Context.POWER_SERVICE) as PowerManager
    }
    
    /**
     * Checks if battery optimization is disabled for the app
     */
    @RequiresApi(Build.VERSION_CODES.M)
    fun isBatteryOptimizationDisabled(): Boolean {
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }
    
    /**
     * Requests to disable battery optimization for the app
     */
    @RequiresApi(Build.VERSION_CODES.M)
    fun requestDisableBatteryOptimization(): Intent? {
        return if (!isBatteryOptimizationDisabled()) {
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
        } else null
    }
    
    /**
     * Opens battery optimization settings for the app
     */
    fun openBatteryOptimizationSettings(): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
        }
    }
    
    /**
     * Gets battery optimization recommendations for the user
     */
    fun getBatteryOptimizationRecommendations(): List<BatteryRecommendation> {
        val recommendations = mutableListOf<BatteryRecommendation>()
        
        // Check battery optimization status
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !isBatteryOptimizationDisabled()) {
            recommendations.add(
                BatteryRecommendation(
                    title = "Disable Battery Optimization",
                    description = "Allow PebbleRun to run in background for accurate workout tracking",
                    priority = RecommendationPriority.HIGH,
                    action = BatteryAction.DISABLE_OPTIMIZATION
                )
            )
        }
        
        // Check auto-start permissions (varies by manufacturer)
        recommendations.add(
            BatteryRecommendation(
                title = "Enable Auto-Start",
                description = "Allow PebbleRun to start automatically for reliable tracking",
                priority = RecommendationPriority.MEDIUM,
                action = BatteryAction.ENABLE_AUTOSTART
            )
        )
        
        // Background app management
        recommendations.add(
            BatteryRecommendation(
                title = "Background App Management",
                description = "Ensure PebbleRun is not restricted in background app settings",
                priority = RecommendationPriority.MEDIUM,
                action = BatteryAction.BACKGROUND_MANAGEMENT
            )
        )
        
        return recommendations
    }
    
    /**
     * Gets device-specific battery optimization instructions
     */
    fun getDeviceSpecificInstructions(): List<DeviceInstruction> {
        val manufacturer = Build.MANUFACTURER.lowercase()
        
        return when {
            manufacturer.contains("xiaomi") -> getXiaomiInstructions()
            manufacturer.contains("huawei") -> getHuaweiInstructions()
            manufacturer.contains("oppo") -> getOppoInstructions()
            manufacturer.contains("vivo") -> getVivoInstructions()
            manufacturer.contains("oneplus") -> getOnePlusInstructions()
            manufacturer.contains("samsung") -> getSamsungInstructions()
            else -> getGenericInstructions()
        }
    }
    
    private fun getXiaomiInstructions(): List<DeviceInstruction> {
        return listOf(
            DeviceInstruction(
                step = 1,
                title = "Open Security App",
                description = "Find and open the 'Security' app on your device"
            ),
            DeviceInstruction(
                step = 2,
                title = "Battery & Performance",
                description = "Tap on 'Battery & performance' option"
            ),
            DeviceInstruction(
                step = 3,
                title = "App Battery Saver",
                description = "Go to 'App battery saver' settings"
            ),
            DeviceInstruction(
                step = 4,
                title = "Find PebbleRun",
                description = "Search for PebbleRun in the app list"
            ),
            DeviceInstruction(
                step = 5,
                title = "Set to 'No restrictions'",
                description = "Change the setting to 'No restrictions' for background activity"
            )
        )
    }
    
    private fun getHuaweiInstructions(): List<DeviceInstruction> {
        return listOf(
            DeviceInstruction(
                step = 1,
                title = "Open Settings",
                description = "Go to device Settings"
            ),
            DeviceInstruction(
                step = 2,
                title = "Battery",
                description = "Find and tap on 'Battery' option"
            ),
            DeviceInstruction(
                step = 3,
                title = "App Launch",
                description = "Look for 'App launch' or 'Startup manager'"
            ),
            DeviceInstruction(
                step = 4,
                title = "Find PebbleRun",
                description = "Locate PebbleRun in the app list"
            ),
            DeviceInstruction(
                step = 5,
                title = "Enable Manual Management",
                description = "Toggle on 'Manage manually' and enable all options"
            )
        )
    }
    
    private fun getOppoInstructions(): List<DeviceInstruction> {
        return listOf(
            DeviceInstruction(
                step = 1,
                title = "Open Settings",
                description = "Navigate to Settings"
            ),
            DeviceInstruction(
                step = 2,
                title = "Battery",
                description = "Go to 'Battery' settings"
            ),
            DeviceInstruction(
                step = 3,
                title = "App Battery Management",
                description = "Find 'App Battery Management' or 'Power Saving'"
            ),
            DeviceInstruction(
                step = 4,
                title = "Background App Management",
                description = "Look for background app management options"
            ),
            DeviceInstruction(
                step = 5,
                title = "Allow PebbleRun",
                description = "Ensure PebbleRun is allowed to run in background"
            )
        )
    }
    
    private fun getVivoInstructions(): List<DeviceInstruction> {
        return listOf(
            DeviceInstruction(
                step = 1,
                title = "Open iManager",
                description = "Find and open the 'iManager' app"
            ),
            DeviceInstruction(
                step = 2,
                title = "App Manager",
                description = "Go to 'App manager' section"
            ),
            DeviceInstruction(
                step = 3,
                title = "Autostart Manager",
                description = "Open 'Autostart manager'"
            ),
            DeviceInstruction(
                step = 4,
                title = "Enable PebbleRun",
                description = "Find PebbleRun and enable autostart"
            ),
            DeviceInstruction(
                step = 5,
                title = "Background App Control",
                description = "Also check 'Background app control' and allow PebbleRun"
            )
        )
    }
    
    private fun getOnePlusInstructions(): List<DeviceInstruction> {
        return listOf(
            DeviceInstruction(
                step = 1,
                title = "Open Settings",
                description = "Go to device Settings"
            ),
            DeviceInstruction(
                step = 2,
                title = "Battery",
                description = "Navigate to 'Battery' settings"
            ),
            DeviceInstruction(
                step = 3,
                title = "Battery Optimization",
                description = "Find 'Battery optimization' option"
            ),
            DeviceInstruction(
                step = 4,
                title = "Don't Optimize",
                description = "Set PebbleRun to 'Don't optimize'"
            ),
            DeviceInstruction(
                step = 5,
                title = "Recent Apps Lock",
                description = "In recent apps, lock PebbleRun to prevent killing"
            )
        )
    }
    
    private fun getSamsungInstructions(): List<DeviceInstruction> {
        return listOf(
            DeviceInstruction(
                step = 1,
                title = "Open Settings",
                description = "Navigate to Settings"
            ),
            DeviceInstruction(
                step = 2,
                title = "Device Care",
                description = "Find 'Device care' or 'Battery and device care'"
            ),
            DeviceInstruction(
                step = 3,
                title = "Battery",
                description = "Go to 'Battery' section"
            ),
            DeviceInstruction(
                step = 4,
                title = "Background App Limits",
                description = "Look for 'Background app limits' or 'App power management'"
            ),
            DeviceInstruction(
                step = 5,
                title = "Never Sleeping Apps",
                description = "Add PebbleRun to 'Never sleeping apps' list"
            )
        )
    }
    
    private fun getGenericInstructions(): List<DeviceInstruction> {
        return listOf(
            DeviceInstruction(
                step = 1,
                title = "Open Settings",
                description = "Go to your device Settings"
            ),
            DeviceInstruction(
                step = 2,
                title = "Battery Settings",
                description = "Find Battery or Power management settings"
            ),
            DeviceInstruction(
                step = 3,
                title = "App Management",
                description = "Look for app-specific battery or background settings"
            ),
            DeviceInstruction(
                step = 4,
                title = "Find PebbleRun",
                description = "Locate PebbleRun in the app list"
            ),
            DeviceInstruction(
                step = 5,
                title = "Allow Background Activity",
                description = "Enable unrestricted background activity for PebbleRun"
            )
        )
    }
    
    data class BatteryRecommendation(
        val title: String,
        val description: String,
        val priority: RecommendationPriority,
        val action: BatteryAction
    )
    
    enum class RecommendationPriority {
        LOW, MEDIUM, HIGH, CRITICAL
    }
    
    enum class BatteryAction {
        DISABLE_OPTIMIZATION,
        ENABLE_AUTOSTART,
        BACKGROUND_MANAGEMENT,
        LOCK_IN_RECENTS,
        WHITELIST_APP
    }
    
    data class DeviceInstruction(
        val step: Int,
        val title: String,
        val description: String
    )
}

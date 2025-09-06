package com.arikachmad.pebblerun.android.di

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.arikachmad.pebblerun.android.service.WorkoutTrackingService
import com.arikachmad.pebblerun.android.viewmodel.WorkoutViewModel
import com.arikachmad.pebblerun.android.viewmodel.HistoryViewModel
import com.arikachmad.pebblerun.android.viewmodel.SettingsViewModel
import com.arikachmad.pebblerun.android.security.AndroidSecureStorage
import com.arikachmad.pebblerun.android.error.AndroidErrorHandler
import com.arikachmad.pebblerun.shared.ui.PlatformActions
import com.arikachmad.pebblerun.shared.ui.UIBridge
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

/**
 * Android-specific dependency injection module
 * Provides platform-specific implementations
 * Satisfies REQ-012 (Add Android-specific dependency injection with Koin or Hilt)
 * Satisfies PAT-003 (Platform-specific dependency injection containers)
 */
val androidModule = module {
    
    // Platform-specific UI bridge implementation
    single<UIBridge> { AndroidUIBridge(get()) }
    
    // Platform-specific actions implementation
    single<PlatformActions> { AndroidPlatformActions(get()) }
    
    // Android-specific ViewModels
    viewModel { WorkoutViewModel(get(), get(), get(), get(), get()) }
    viewModel { HistoryViewModel(get()) }
    viewModel { SettingsViewModel(get(), get()) }
    
    // Android-specific security
    single { AndroidSecureStorage(get()) }
    
    // Android-specific error handling
    single { AndroidErrorHandler(get(), get()) }
    
    // TODO: Add other Android-specific dependencies when bridge modules are ready
    // single<WorkoutTrackingService> { WorkoutTrackingService() }
    // single<AndroidPebbleTransport> { AndroidPebbleTransport(get()) }
    // single<AndroidLocationProvider> { AndroidLocationProvider(get()) }
}

/**
 * Android implementation of UIBridge
 * Handles platform-specific UI operations and system integrations
 */
class AndroidUIBridge(
    private val context: Context
) : UIBridge {
    
    // Lazy initialization to avoid circular dependency
    private val errorHandler by lazy { 
        AndroidErrorHandler(context, this)
    }
    
    override fun showNotification(title: String, message: String) {
        // Delegate to WorkoutTrackingService which handles notifications
        val intent = Intent(context, WorkoutTrackingService::class.java).apply {
            action = "SHOW_NOTIFICATION"
            putExtra("title", title)
            putExtra("message", message)
        }
        try {
            context.startService(intent)
        } catch (e: Exception) {
            android.util.Log.e("AndroidUIBridge", "Failed to show notification", e)
        }
    }
    
    override suspend fun requestPermissions(): Boolean {
        // Permission request is handled by MainActivity through platformActions
        return checkRequiredPermissions()
    }
    
    override fun startBackgroundService() {
        try {
            WorkoutTrackingService.startWorkout(context)
        } catch (e: Exception) {
            errorHandler.handleError(e, com.arikachmad.pebblerun.android.error.ErrorType.GENERAL)
        }
    }
    
    override fun stopBackgroundService() {
        try {
            WorkoutTrackingService.stopWorkout(context)
        } catch (e: Exception) {
            errorHandler.handleError(e, com.arikachmad.pebblerun.android.error.ErrorType.GENERAL)
        }
    }
    
    override fun handleError(error: Throwable) {
        errorHandler.handleError(error)
    }
    
    /**
     * Check if all required permissions are granted
     */
    private fun checkRequiredPermissions(): Boolean {
        val requiredPermissions = arrayOf(
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_COARSE_LOCATION,
            android.Manifest.permission.BLUETOOTH_CONNECT,
            android.Manifest.permission.BLUETOOTH_SCAN,
            android.Manifest.permission.POST_NOTIFICATIONS
        )
        
        return requiredPermissions.all { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
    }
}

/**
 * Android implementation of PlatformActions
 * Handles platform-specific actions and lifecycle events
 * Satisfies REQ-009 (Add Android-specific permission handling)
 */
class AndroidPlatformActions(
    private val context: Context
) : PlatformActions {
    
    private val _permissionResults = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val permissionResults: StateFlow<Map<String, Boolean>> = _permissionResults.asStateFlow()
    
    private val _isActivityResumed = MutableStateFlow(false)
    val isActivityResumed: StateFlow<Boolean> = _isActivityResumed.asStateFlow()
    
    override suspend fun launchPebbleApp(): Boolean {
        // TODO: Implement Android PebbleKit app launch when bridge-pebble module is ready
        return false
    }
    
    override suspend fun closePebbleApp(): Boolean {
        // TODO: Implement Android PebbleKit app close when bridge-pebble module is ready
        return false
    }
    
    override suspend fun sendToPebble(key: Int, value: String): Boolean {
        // TODO: Implement Android PebbleKit message sending when bridge-pebble module is ready
        return false
    }
    
    override suspend fun isPebbleConnected(): Boolean {
        // TODO: Implement Android PebbleKit connection check when bridge-pebble module is ready
        return false
    }
    
    /**
     * Handle permission request results from MainActivity
     * Satisfies REQ-009 (Android-specific permission handling)
     */
    suspend fun handlePermissionResults(permissions: Map<String, Boolean>) {
        _permissionResults.value = permissions
        
        // Check if all critical permissions are granted
        val criticalPermissions = setOf(
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.BLUETOOTH_CONNECT
        )
        
        val criticalPermissionsGranted = criticalPermissions.all { permission ->
            permissions[permission] == true
        }
        
        if (!criticalPermissionsGranted) {
            // Show user guidance for enabling permissions
            android.util.Log.w("PebbleRun", "Critical permissions not granted: $permissions")
        }
    }
    
    /**
     * Handle activity lifecycle events
     */
    suspend fun onActivityResumed() {
        _isActivityResumed.value = true
    }
    
    suspend fun onActivityPaused() {
        _isActivityResumed.value = false
    }
}

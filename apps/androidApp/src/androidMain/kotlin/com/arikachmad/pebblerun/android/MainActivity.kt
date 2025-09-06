package com.arikachmad.pebblerun.android

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.lifecycleScope
import com.arikachmad.pebblerun.android.service.WorkoutTrackingService
import com.arikachmad.pebblerun.android.ui.navigation.PebbleRunNavigation
import com.arikachmad.pebblerun.android.ui.theme.PebbleRunTheme
import com.arikachmad.pebblerun.shared.ui.PlatformActions
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.androidx.compose.KoinAndroidContext
import org.koin.core.annotation.KoinExperimentalAPI

/**
 * Main Android activity implementing platform-specific UI with proper lifecycle management
 * Uses native Android Jetpack Compose with Material Design 3
 * Satisfies REQ-005 (Android app with native Compose UI and proper Foreground Service integration)
 * Satisfies GUD-001 (Android Material Design 3 with native Compose implementation)
 * Satisfies PAT-001 (MVVM pattern with platform-specific ViewModels for Android)
 */
class MainActivity : ComponentActivity() {
    
    private val platformActions: PlatformActions by inject()
    
    // Permission request launcher for runtime permissions
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Handle permission results through platform actions
        lifecycleScope.launch {
            platformActions.handlePermissionResults(permissions)
        }
    }
    
    @OptIn(KoinExperimentalAPI::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Request necessary permissions on startup
        requestRequiredPermissions()

        setContent {
            KoinAndroidContext {
                PebbleRunTheme {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        PebbleRunApp()
                    }
                }
            }
        }
    }
    
    override fun onResume() {
        super.onResume()
        // Notify platform actions that activity is resumed
        lifecycleScope.launch {
            platformActions.onActivityResumed()
        }
    }
    
    override fun onPause() {
        super.onPause()
        // Notify platform actions that activity is paused
        lifecycleScope.launch {
            platformActions.onActivityPaused()
        }
    }
    
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        // Handle intents from notifications or external sources
        intent?.let {
            handleServiceIntent(it)
        }
    }
    
    /**
     * Request required permissions for the app
     * Satisfies REQ-009 (Android-specific permission handling)
     */
    private fun requestRequiredPermissions() {
        val permissions = arrayOf(
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_COARSE_LOCATION,
            android.Manifest.permission.BLUETOOTH_CONNECT,
            android.Manifest.permission.BLUETOOTH_SCAN,
            android.Manifest.permission.POST_NOTIFICATIONS
        )
        
        permissionLauncher.launch(permissions)
    }
    
    /**
     * Handle intents from service notifications or external sources
     */
    private fun handleServiceIntent(intent: Intent) {
        when (intent.action) {
            WorkoutTrackingService.ACTION_START_WORKOUT -> {
                // Navigation will be handled by the UI layer
            }
            WorkoutTrackingService.ACTION_STOP_WORKOUT -> {
                // Navigation will be handled by the UI layer
            }
            // Add other action handling as needed
        }
    }
}

@Composable
fun PebbleRunApp() {
    Scaffold(
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        PebbleRunNavigation(
            modifier = Modifier.padding(innerPadding)
        )
    }
}

@Preview(showBackground = true)
@Composable
fun PebbleRunAppPreview() {
    PebbleRunTheme {
        PebbleRunApp()
    }
}

package com.arikachmad.pebblerun.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.arikachmad.pebblerun.android.ui.navigation.PebbleRunNavigation
import com.arikachmad.pebblerun.android.ui.theme.PebbleRunTheme
import org.koin.androidx.compose.KoinAndroidContext
import org.koin.core.annotation.KoinExperimentalAPI

/**
 * Main Android activity implementing platform-specific UI
 * Uses native Android Jetpack Compose with Material Design 3
 * Satisfies GUD-001 (Android Material Design 3 with native Compose implementation)
 */
class MainActivity : ComponentActivity() {
    
    @OptIn(KoinExperimentalAPI::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

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

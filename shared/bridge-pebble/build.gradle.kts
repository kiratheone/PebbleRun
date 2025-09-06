plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    kotlin("native.cocoapods")
}

kotlin {
    androidTarget()
    
    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "BridgePebble"
        }
    }

    cocoapods {
        summary = "Pebble Bridge for KMP - Interface to communicate with Pebble smartwatch"
        homepage = "https://github.com/kiratheone/PebbleRun"
        version = "1.0.0"
        ios.deploymentTarget = "15.3"
        
        framework {
            baseName = "BridgePebble"
            isStatic = false
        }
        
        // Create conditional pod configuration based on project properties
        val enablePebbleKit = project.findProperty("pebblekit.enabled")?.toString()?.toBoolean() 
            ?: true // Default to enabled
        
        if (enablePebbleKit) {
            pod("PebbleKit") {
                version = "~> 4.0"
                // Enable weak linking - framework will be optional at runtime
                linkOnly = true
            }
        }
        
        podfile = project.file("../apps/iosApp/Podfile")
    }
    
    sourceSets {
        commonMain.dependencies {
            implementation(projects.shared.domain)
            implementation(projects.shared.proto)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
        }
        androidMain.dependencies {
            implementation("com.getpebble:pebblekit:4.0.1")
            implementation("androidx.core:core-ktx:1.12.0")
            implementation("androidx.lifecycle:lifecycle-service:2.7.0")
        }
        iosMain.dependencies {
            // PebbleKit conditionally available
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

android {
    namespace = "com.arikachmad.pebblerun.bridge.pebble"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    
    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

// Task configuration to disable PebbleKit for simulator builds
tasks.configureEach {
    val isSimulatorTask = name.contains("Simulator", ignoreCase = true)
    
    if (isSimulatorTask && name.startsWith("linkPod") || name.contains("linkDebug")) {
        // Disable PebbleKit pod linking for simulator tasks
        enabled = false
    }
}

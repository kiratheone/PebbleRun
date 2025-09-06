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
            isStatic = false // Use dynamic framework for CocoaPods compatibility
        }
        
        // Configure PebbleKit pod
        pod("PebbleKit") {
            version = "~> 4.0" // Match Android version 4.0.1
            // If you need to specify a custom source or configuration
            // source = "https://github.com/pebble/pebblekit-ios"
        }
        
        // Optional: Add other iOS dependencies if needed
        // pod("Alamofire") { version = "~> 5.0" }
        
        // Specify podfile location if non-standard
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
            // https://mvnrepository.com/artifact/com.getpebble/pebblekit
            implementation("com.getpebble:pebblekit:4.0.1")
            implementation("androidx.core:core-ktx:1.12.0")
            implementation("androidx.lifecycle:lifecycle-service:2.7.0")
        }
        iosMain.dependencies {
            // PebbleKit iOS will be linked through XCFramework or CocoaPods
            // The framework should be available in the iOS project
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

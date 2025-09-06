plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        }
    }
    
    sourceSets {
        androidMain.dependencies {
            // Compose dependencies  
            implementation("androidx.compose.ui:ui:1.7.7")
            implementation("androidx.compose.ui:ui-graphics:1.7.7")
            implementation("androidx.compose.ui:ui-tooling-preview:1.7.7")
            implementation("androidx.compose.material3:material3:1.3.1")
            implementation("androidx.compose.material:material-icons-extended:1.7.7")
            
            // Activity Compose
            implementation(libs.androidx.activity.compose)
            
            // Lifecycle
            implementation("androidx.lifecycle:lifecycle-viewmodel-compose")
            implementation("androidx.lifecycle:lifecycle-runtime-compose")
            
            // Navigation
            implementation("androidx.navigation:navigation-compose:2.8.5")
            
            // Dependency Injection
            implementation(libs.koin.android)
            implementation(libs.koin.compose)
            
            // Security - Jetpack Security
            implementation("androidx.security:security-crypto:1.1.0-alpha06")
            
            // WorkManager for background tasks
            implementation("androidx.work:work-runtime-ktx:2.9.0")
            
            // Coroutines
            implementation(libs.kotlinx.coroutines.android)
            
            // Shared modules
            implementation(project(":apps:composeApp"))
            implementation(project(":shared:domain"))
            implementation(project(":shared:data"))
            implementation(project(":shared:bridge-pebble"))
            implementation(project(":shared:bridge-location"))
            implementation(project(":shared:util"))
        }
    }
}

android {
    namespace = "com.arikachmad.pebblerun.android"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.arikachmad.pebblerun"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    
    buildFeatures {
        compose = true
    }
    
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    debugImplementation("androidx.compose.ui:ui-tooling:1.7.7")
    debugImplementation("androidx.compose.ui:ui-test-manifest:1.7.7")
}

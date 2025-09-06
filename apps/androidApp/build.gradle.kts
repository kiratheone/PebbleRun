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
            // Compose BOM
            implementation(platform("androidx.compose:compose-bom:2024.12.01"))
            
            // Compose dependencies
            implementation("androidx.compose.ui:ui")
            implementation("androidx.compose.ui:ui-graphics")
            implementation("androidx.compose.ui:ui-tooling-preview")
            implementation("androidx.compose.material3:material3")
            implementation("androidx.compose.material:material-icons-extended")
            
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
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

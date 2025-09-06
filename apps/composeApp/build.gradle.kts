import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }

    sourceSets {
        androidMain.dependencies {
            // Android-specific dependencies for ViewModels
            implementation(libs.androidx.lifecycle.viewmodel.ktx)
            implementation(libs.kotlinx.coroutines.android)
        }
        commonMain.dependencies {
            // Shared dependencies for business logic only
            implementation(libs.kotlinx.coroutines.core)
            
            // Dependency injection
            implementation(libs.koin.core)
            
            // Shared modules - temporarily commented out due to compilation errors
            implementation(project(":shared:domain"))
            // implementation(project(":shared:data"))
            implementation(project(":shared:util"))
        }
        iosMain.dependencies {
            // iOS-specific dependencies for Flow integration
            implementation(libs.kmp.nativecoroutines)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

android {
    namespace = "com.arikachmad.pebblerun.shared"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}


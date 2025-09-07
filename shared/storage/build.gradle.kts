plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.sqldelight)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    androidTarget()
    
    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Storage"
            // Link against system SQLite3 library for SQLDelight
            linkerOpts("-lsqlite3")
        }
    }
    
    sourceSets {
        commonMain.dependencies {
            implementation(libs.sqldelight.runtime)
            implementation(libs.sqldelight.coroutines)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.serialization.json)
        }
        
        androidMain.dependencies {
            implementation(libs.sqldelight.android.driver)
            // SQLCipher for database encryption
            implementation("net.zetetic:android-database-sqlcipher:4.5.4")
            // Android Security library for secure preferences
            implementation("androidx.security:security-crypto:1.1.0-alpha06")
            // AndroidX core + WorkManager for scheduled work
            implementation(libs.androidx.core.ktx)
            implementation(libs.androidx.work.ktx)
        }
        
        iosMain.dependencies {
            implementation(libs.sqldelight.native.driver)
        }
        
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

android {
    namespace = "com.arikachmad.pebblerun.storage"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    
    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

sqldelight {
    databases {
        create("WorkoutDatabase") {
            packageName.set("com.arikachmad.pebblerun.storage")
        }
    }
}

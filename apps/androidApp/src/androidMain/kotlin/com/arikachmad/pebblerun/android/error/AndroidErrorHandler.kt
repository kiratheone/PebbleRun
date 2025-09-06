package com.arikachmad.pebblerun.android.error

import android.content.Context
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Data
import com.arikachmad.pebblerun.shared.ui.UIBridge
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Android-specific error handling and crash reporting
 * Satisfies REQ-014 (Add Android-specific error handling and crash reporting)
 * Satisfies TEC-004 (Platform-specific error handling and user feedback)
 */
class AndroidErrorHandler(
    private val context: Context,
    private val uiBridge: UIBridge
) {
    
    companion object {
        private const val TAG = "PebbleRunErrorHandler"
        private const val CRASH_LOG_DIR = "crash_logs"
        private const val MAX_LOG_FILES = 10
    }
    
    private val errorScope = CoroutineScope(SupervisorJob())
    
    // Global exception handler for coroutines
    val coroutineExceptionHandler = CoroutineExceptionHandler { _, exception ->
        handleError(exception, ErrorType.COROUTINE_EXCEPTION)
    }
    
    init {
        setupUncaughtExceptionHandler()
        createCrashLogDirectory()
    }
    
    /**
     * Handle various types of errors
     */
    fun handleError(throwable: Throwable, errorType: ErrorType = ErrorType.GENERAL) {
        errorScope.launch {
            try {
                // Log the error
                logError(throwable, errorType)
                
                // Write crash log to file
                writeCrashLog(throwable, errorType)
                
                // Show user-friendly notification
                showUserErrorNotification(throwable, errorType)
                
                // Report error to analytics (if available)
                reportErrorToAnalytics(throwable, errorType)
                
            } catch (e: Exception) {
                // If error handling itself fails, log to system
                Log.e(TAG, "Failed to handle error", e)
            }
        }
    }
    
    /**
     * Handle network-related errors
     */
    fun handleNetworkError(throwable: Throwable, operation: String) {
        handleError(NetworkException(operation, throwable), ErrorType.NETWORK)
    }
    
    /**
     * Handle Pebble communication errors
     */
    fun handlePebbleError(throwable: Throwable, operation: String) {
        handleError(PebbleException(operation, throwable), ErrorType.PEBBLE_COMMUNICATION)
    }
    
    /**
     * Handle location/GPS errors
     */
    fun handleLocationError(throwable: Throwable, operation: String) {
        handleError(LocationException(operation, throwable), ErrorType.LOCATION)
    }
    
    /**
     * Handle storage/database errors
     */
    fun handleStorageError(throwable: Throwable, operation: String) {
        handleError(StorageException(operation, throwable), ErrorType.STORAGE)
    }
    
    /**
     * Log error with appropriate level
     */
    private fun logError(throwable: Throwable, errorType: ErrorType) {
        val message = buildErrorMessage(throwable, errorType)
        
        when (errorType.severity) {
            ErrorSeverity.LOW -> Log.i(TAG, message, throwable)
            ErrorSeverity.MEDIUM -> Log.w(TAG, message, throwable)
            ErrorSeverity.HIGH -> Log.e(TAG, message, throwable)
            ErrorSeverity.CRITICAL -> {
                Log.e(TAG, "CRITICAL ERROR: $message", throwable)
                // Additional critical error handling
            }
        }
    }
    
    /**
     * Write crash log to file for debugging
     */
    private fun writeCrashLog(throwable: Throwable, errorType: ErrorType) {
        try {
            val crashLogDir = File(context.filesDir, CRASH_LOG_DIR)
            val timestamp = Clock.System.now().toEpochMilliseconds()
            val logFile = File(crashLogDir, "crash_$timestamp.log")
            
            FileWriter(logFile).use { writer ->
                writer.write("Timestamp: $timestamp\n")
                writer.write("Error Type: ${errorType.name}\n")
                writer.write("Severity: ${errorType.severity}\n")
                writer.write("Message: ${throwable.message}\n")
                writer.write("Stack Trace:\n")
                
                val stringWriter = StringWriter()
                throwable.printStackTrace(PrintWriter(stringWriter))
                writer.write(stringWriter.toString())
            }
            
            // Clean up old log files
            cleanupOldLogFiles(crashLogDir)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write crash log", e)
        }
    }
    
    /**
     * Show user-friendly error notification
     */
    private fun showUserErrorNotification(throwable: Throwable, errorType: ErrorType) {
        val userMessage = when (errorType) {
            ErrorType.NETWORK -> "Network connection issue. Please check your internet connection."
            ErrorType.PEBBLE_COMMUNICATION -> "Pebble communication error. Please check your Pebble connection."
            ErrorType.LOCATION -> "Location access error. Please check location permissions."
            ErrorType.STORAGE -> "Data storage error. Please check available storage space."
            ErrorType.PERMISSION -> "Permission required. Please grant necessary permissions."
            ErrorType.COROUTINE_EXCEPTION -> "An unexpected error occurred. The app will continue running."
            else -> "An error occurred: ${throwable.message ?: "Unknown error"}"
        }
        
        // Show notification through UI bridge
        uiBridge.showNotification(
            title = "PebbleRun Error",
            message = userMessage
        )
    }
    
    /**
     * Report error to analytics (placeholder for future implementation)
     */
    private fun reportErrorToAnalytics(throwable: Throwable, errorType: ErrorType) {
        // TODO: Implement analytics reporting when available
        // This could be Firebase Crashlytics, Sentry, or another crash reporting service
        Log.d(TAG, "Error reported to analytics: ${errorType.name}")
    }
    
    /**
     * Build detailed error message
     */
    private fun buildErrorMessage(throwable: Throwable, errorType: ErrorType): String {
        return "ErrorType: ${errorType.name}, " +
                "Severity: ${errorType.severity}, " +
                "Message: ${throwable.message}, " +
                "Cause: ${throwable.cause?.message ?: "None"}"
    }
    
    /**
     * Setup uncaught exception handler for the entire app
     */
    private fun setupUncaughtExceptionHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        
        Thread.setDefaultUncaughtExceptionHandler { thread, exception ->
            try {
                handleError(exception, ErrorType.UNCAUGHT_EXCEPTION)
                
                // Also call the default handler to ensure proper app termination
                defaultHandler?.uncaughtException(thread, exception)
            } catch (e: Exception) {
                // If our error handling fails, fall back to default handler
                Log.e(TAG, "Failed to handle uncaught exception", e)
                defaultHandler?.uncaughtException(thread, exception)
            }
        }
    }
    
    /**
     * Create crash log directory
     */
    private fun createCrashLogDirectory() {
        val crashLogDir = File(context.filesDir, CRASH_LOG_DIR)
        if (!crashLogDir.exists()) {
            crashLogDir.mkdirs()
        }
    }
    
    /**
     * Clean up old log files to prevent storage bloat
     */
    private fun cleanupOldLogFiles(crashLogDir: File) {
        val logFiles = crashLogDir.listFiles()?.sortedByDescending { it.lastModified() }
        
        if (logFiles != null && logFiles.size > MAX_LOG_FILES) {
            logFiles.drop(MAX_LOG_FILES).forEach { file ->
                file.delete()
            }
        }
    }
}

/**
 * Error types with severity levels
 */
enum class ErrorType(val severity: ErrorSeverity) {
    GENERAL(ErrorSeverity.MEDIUM),
    NETWORK(ErrorSeverity.MEDIUM),
    PEBBLE_COMMUNICATION(ErrorSeverity.HIGH),
    LOCATION(ErrorSeverity.HIGH),
    STORAGE(ErrorSeverity.HIGH),
    PERMISSION(ErrorSeverity.MEDIUM),
    COROUTINE_EXCEPTION(ErrorSeverity.HIGH),
    UNCAUGHT_EXCEPTION(ErrorSeverity.CRITICAL)
}

/**
 * Error severity levels
 */
enum class ErrorSeverity {
    LOW, MEDIUM, HIGH, CRITICAL
}

/**
 * Custom exception classes for better error categorization
 */
class NetworkException(operation: String, cause: Throwable) : 
    Exception("Network error during $operation", cause)

class PebbleException(operation: String, cause: Throwable) : 
    Exception("Pebble communication error during $operation", cause)

class LocationException(operation: String, cause: Throwable) : 
    Exception("Location error during $operation", cause)

class StorageException(operation: String, cause: Throwable) : 
    Exception("Storage error during $operation", cause)

/**
 * Worker for background error reporting
 */
class ErrorReportingWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {
    
    override fun doWork(): Result {
        // TODO: Implement background error reporting logic
        return Result.success()
    }
}

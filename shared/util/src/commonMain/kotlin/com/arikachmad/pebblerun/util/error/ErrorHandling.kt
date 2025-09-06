package com.arikachmad.pebblerun.util.error

/**
 * Domain-specific error types for PebbleRun
 * Supports TASK-006 (Set up logging framework and error handling utilities)
 */
sealed class PebbleRunError : Exception() {
    
    // Pebble Communication Errors
    data class PebbleConnectionError(override val message: String) : PebbleRunError()
    data class PebbleMessageError(override val message: String) : PebbleRunError()
    data class PebbleNotFoundError(override val message: String = "Pebble device not found") : PebbleRunError()
    
    // GPS/Location Errors
    data class LocationPermissionError(override val message: String = "Location permission denied") : PebbleRunError()
    data class LocationServiceError(override val message: String) : PebbleRunError()
    data class GpsAccuracyError(override val message: String) : PebbleRunError()
    
    // Workout Session Errors
    data class SessionAlreadyActiveError(override val message: String = "Workout session already active") : PebbleRunError()
    data class SessionNotFoundError(override val message: String = "Workout session not found") : PebbleRunError()
    data class InvalidSessionStateError(override val message: String) : PebbleRunError()
    
    // Data Storage Errors
    data class DatabaseError(override val message: String, override val cause: Throwable? = null) : PebbleRunError()
    data class DataCorruptionError(override val message: String) : PebbleRunError()
    
    // Heart Rate Errors
    data class HeartRateError(override val message: String) : PebbleRunError()
    data class HrSensorError(override val message: String = "Heart rate sensor unavailable") : PebbleRunError()
    
    // Network/Sync Errors
    data class NetworkError(override val message: String, override val cause: Throwable? = null) : PebbleRunError()
    data class SyncError(override val message: String) : PebbleRunError()
    
    // System/Integration Errors
    data class SystemNotReadyError(override val message: String) : PebbleRunError()
    data class ComponentUnavailableError(override val message: String) : PebbleRunError()
    data class IntegrationError(override val message: String, override val cause: Throwable? = null) : PebbleRunError()
    
    // Generic Errors
    data class ValidationError(override val message: String) : PebbleRunError()
    data class UnknownError(override val message: String, override val cause: Throwable? = null) : PebbleRunError()
}

/**
 * Result wrapper for handling errors in a functional way
 */
sealed class Result<out T> {
    data class Success<T>(val data: T) : Result<T>()
    data class Error(val exception: PebbleRunError) : Result<Nothing>()
    
    fun isSuccess(): Boolean = this is Success
    fun isError(): Boolean = this is Error
    
    fun getOrNull(): T? = when (this) {
        is Success -> data
        is Error -> null
    }
    
    fun exceptionOrNull(): PebbleRunError? = when (this) {
        is Success -> null
        is Error -> exception
    }
    
    inline fun onSuccess(action: (T) -> Unit): Result<T> {
        if (this is Success) action(data)
        return this
    }
    
    inline fun onError(action: (PebbleRunError) -> Unit): Result<T> {
        if (this is Error) action(exception)
        return this
    }
    
    inline fun <R> map(transform: (T) -> R): Result<R> = when (this) {
        is Success -> Success(transform(data))
        is Error -> this
    }
    
    inline fun <R> flatMap(transform: (T) -> Result<R>): Result<R> = when (this) {
        is Success -> transform(data)
        is Error -> this
    }
}

/**
 * Extension functions for safe execution
 */
inline fun <T> safeCall(action: () -> T): Result<T> {
    return try {
        Result.Success(action())
    } catch (e: PebbleRunError) {
        Result.Error(e)
    } catch (e: Exception) {
        Result.Error(PebbleRunError.UnknownError("Unexpected error: ${e.message}", e))
    }
}

suspend inline fun <T> safeSuspendCall(crossinline action: suspend () -> T): Result<T> {
    return try {
        Result.Success(action())
    } catch (e: PebbleRunError) {
        Result.Error(e)
    } catch (e: Exception) {
        Result.Error(PebbleRunError.UnknownError("Unexpected error: ${e.message}", e))
    }
}

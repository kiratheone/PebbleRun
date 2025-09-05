package com.arikachmad.pebblerun.util.logging

/**
 * Common logging interface for cross-platform logging
 * Supports TASK-006 (Set up logging framework and error handling utilities)
 */
interface Logger {
    fun d(tag: String, message: String)
    fun i(tag: String, message: String)
    fun w(tag: String, message: String)
    fun e(tag: String, message: String)
    fun e(tag: String, message: String, throwable: Throwable)
}

/**
 * Platform-specific logger implementation
 */
expect class PlatformLogger() {
    fun d(tag: String, message: String)
    fun i(tag: String, message: String)
    fun w(tag: String, message: String)
    fun e(tag: String, message: String)
    fun e(tag: String, message: String, throwable: Throwable)
}

/**
 * Global logger instance
 */
object Log {
    private val logger = PlatformLogger()
    
    fun d(tag: String, message: String) = logger.d(tag, message)
    fun i(tag: String, message: String) = logger.i(tag, message)
    fun w(tag: String, message: String) = logger.w(tag, message)
    fun e(tag: String, message: String) = logger.e(tag, message)
    fun e(tag: String, message: String, throwable: Throwable) = logger.e(tag, message, throwable)
}

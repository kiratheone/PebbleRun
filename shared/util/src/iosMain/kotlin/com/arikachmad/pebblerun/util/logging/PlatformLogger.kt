package com.arikachmad.pebblerun.util.logging

import platform.Foundation.NSLog

/**
 * iOS implementation of logger using NSLog
 */
actual class PlatformLogger {
    actual fun d(tag: String, message: String) {
        NSLog("[$tag] DEBUG: $message")
    }
    
    actual fun i(tag: String, message: String) {
        NSLog("[$tag] INFO: $message")
    }
    
    actual fun w(tag: String, message: String) {
        NSLog("[$tag] WARN: $message")
    }
    
    actual fun e(tag: String, message: String) {
        NSLog("[$tag] ERROR: $message")
    }
    
    actual fun e(tag: String, message: String, throwable: Throwable) {
        NSLog("[$tag] ERROR: $message - ${throwable.message}")
    }
}

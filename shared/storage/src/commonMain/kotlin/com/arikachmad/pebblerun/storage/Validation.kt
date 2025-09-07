package com.arikachmad.pebblerun.storage

/**
 * Lightweight validation result for storage operations.
 * Keep independent from domain layer per dependency rules.
 */
sealed class ValidationResult {
    data class Success(val message: String = "OK") : ValidationResult()
    data class Error(val message: String) : ValidationResult()
}

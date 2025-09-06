package com.arikachmad.pebblerun.domain.error

/**
 * Domain-specific Result type for clean architecture compliance.
 * No external dependencies allowed in domain layer.
 */
sealed class DomainResult<out T> {
    data class Success<T>(val data: T) : DomainResult<T>()
    data class Error(val exception: DomainError) : DomainResult<Nothing>()
}

/**
 * Domain-specific error types
 */
sealed class DomainError(override val message: String) : Throwable(message) {
    data class ValidationError(val field: String, val reason: String) : 
        DomainError("Validation failed for $field: $reason")
    
    data class BusinessRuleViolation(val rule: String) : 
        DomainError("Business rule violation: $rule")
    
    data class EntityNotFound(val entityType: String, val id: String) : 
        DomainError("$entityType not found with id: $id")
    
    data class InvalidOperation(val operation: String, val context: String) : 
        DomainError("Invalid operation '$operation' in context: $context")
}

/**
 * Extension functions for easier Result handling
 */
inline fun <T> DomainResult<T>.onSuccess(action: (value: T) -> Unit): DomainResult<T> {
    if (this is DomainResult.Success) action(data)
    return this
}

inline fun <T> DomainResult<T>.onError(action: (error: DomainError) -> Unit): DomainResult<T> {
    if (this is DomainResult.Error) action(exception)
    return this
}

fun <T> T.toDomainSuccess(): DomainResult<T> = DomainResult.Success(this)

fun DomainError.toDomainError(): DomainResult<Nothing> = DomainResult.Error(this)

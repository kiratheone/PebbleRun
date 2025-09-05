package com.arikachmad.pebblerun.domain.usecase

/**
 * Base use case interface for domain operations.
 * Satisfies PAT-001 (Domain-driven design with use cases) and TEC-001 (Clean Architecture).
 */
interface UseCase<in P, out R> {
    suspend operator fun invoke(params: P): R
}

/**
 * Base use case for operations without parameters
 */
interface NoParamsUseCase<out R> {
    suspend operator fun invoke(): R
}

package com.areadiscovery.domain.model

sealed class DomainError {
    data class NetworkError(val message: String) : DomainError()
    data class ApiError(val code: Int, val message: String) : DomainError()
    data class CacheError(val message: String) : DomainError()
    data class LocationError(val message: String) : DomainError()
}

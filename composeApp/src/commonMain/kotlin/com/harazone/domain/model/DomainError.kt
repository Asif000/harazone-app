package com.harazone.domain.model

sealed class DomainError {
    data class NetworkError(val message: String) : DomainError()
    data class ApiError(val code: Int, val message: String) : DomainError()
    data class CacheError(val message: String) : DomainError()
    data class LocationError(val message: String) : DomainError()
}

class DomainErrorException(val domainError: DomainError) : Exception(
    when (domainError) {
        is DomainError.NetworkError -> domainError.message
        is DomainError.ApiError -> domainError.message
        is DomainError.CacheError -> domainError.message
        is DomainError.LocationError -> domainError.message
    }
)

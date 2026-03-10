package com.harazone.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class Confidence {
    HIGH,
    MEDIUM,
    LOW
}

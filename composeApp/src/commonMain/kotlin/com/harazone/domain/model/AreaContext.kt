package com.harazone.domain.model

data class AreaContext(
    val timeOfDay: String,
    val dayOfWeek: String,
    val visitCount: Int,
    val preferredLanguage: String,
    val isNewUser: Boolean = false,
    val isRtl: Boolean = false,
    val homeCurrencyCode: String = "USD",
    val tasteProfile: List<String> = emptyList(),
    val skipCache: Boolean = false,
)

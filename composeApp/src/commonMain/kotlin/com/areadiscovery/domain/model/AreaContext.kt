package com.areadiscovery.domain.model

data class AreaContext(
    val timeOfDay: String,
    val dayOfWeek: String,
    val visitCount: Int,
    val preferredLanguage: String
)

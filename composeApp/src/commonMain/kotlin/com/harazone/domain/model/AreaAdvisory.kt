package com.harazone.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class AdvisoryLevel {
    @SerialName("safe") SAFE,
    @SerialName("caution") CAUTION,
    @SerialName("reconsider") RECONSIDER,
    @SerialName("do_not_travel") DO_NOT_TRAVEL,
    @SerialName("unknown") UNKNOWN;

    fun isAtLeast(level: AdvisoryLevel): Boolean = this.ordinal >= level.ordinal && this != UNKNOWN
}

@Serializable
data class AreaAdvisory(
    val level: AdvisoryLevel,
    val countryName: String,
    val countryCode: String,
    val summary: String,
    val details: List<String>,
    val subNationalZones: List<SubNationalAdvisory>,
    val sourceUrl: String,
    val lastUpdated: Long,
    val cachedAt: Long,
)

@Serializable
data class SubNationalAdvisory(
    val regionName: String,
    val level: AdvisoryLevel,
    val summary: String,
)

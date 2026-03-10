package com.harazone.domain.model

data class TasteProfile(
    val strongAffinities: List<String>,
    val emergingInterests: List<String>,
    val notableAbsences: List<String>,
    val diningStyle: String?,
    val totalSaves: Int,
)

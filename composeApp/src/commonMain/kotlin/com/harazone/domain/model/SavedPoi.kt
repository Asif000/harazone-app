package com.harazone.domain.model

data class SavedPoi(
    val id: String,
    val name: String,
    val type: String,
    val areaName: String,
    val lat: Double,
    val lng: Double,
    val whySpecial: String,
    val savedAt: Long,
    val userNote: String? = null,
)

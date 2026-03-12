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
    val imageUrl: String? = null,
    val description: String? = null,
    val rating: Float? = null,
    val vibe: String = "",
)

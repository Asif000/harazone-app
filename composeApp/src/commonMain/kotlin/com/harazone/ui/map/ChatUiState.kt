package com.harazone.ui.map

import com.harazone.domain.model.ContextualPill
import com.harazone.domain.model.MessageRole
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ChatResponse(
    val prose: String = "",
    val pois: List<ChatPoiCard> = emptyList(),
)

@Serializable
data class ChatPoiCard(
    @SerialName("n") val name: String,
    @SerialName("t") val type: String,
    val lat: Double,
    val lng: Double,
    @SerialName("w") val whySpecial: String,
    @SerialName("img") val imageUrl: String? = null,
    // TODO(BACKLOG-MEDIUM): render these fields in ChatPoiMiniCard UI
    @SerialName("insight") val insight: String? = null,
    @SerialName("rating") val rating: Float? = null,
    @SerialName("priceRange") val priceRange: String? = null,
    @SerialName("status") val status: String? = null,
    @SerialName("hours") val hours: String? = null,
) {
    val id: String get() = "$name|$lat|$lng"
}

data class ChatBubble(
    val id: String,
    val role: MessageRole,
    val content: String,
    val isStreaming: Boolean = false,
    val isError: Boolean = false,
)

data class ChatUiState(
    val isOpen: Boolean = false,
    val areaName: String = "",
    val vibeName: String? = null,
    val bubbles: List<ChatBubble> = emptyList(),
    val isStreaming: Boolean = false,
    val inputText: String = "",
    val lastUserQuery: String = "",
    val poiCards: List<ChatPoiCard> = emptyList(),
    val bubblePoiCards: Map<String, List<ChatPoiCard>> = emptyMap(),
    val showSkeletons: Boolean = false,
    val savedPoiIds: Set<String> = emptySet(),
    val contextBanner: String? = null,
    val depthLevel: Int = 0,
    val persistentPills: List<ContextualPill> = emptyList(),
    val showReturnDialog: Boolean = false,
    val contextBlurb: String? = null,
    val whyNow: String? = null,
    val localTip: String? = null,
    val isContextLoading: Boolean = false,
    val isTipRefreshing: Boolean = false,
    val isResidentMode: Boolean = false,
)

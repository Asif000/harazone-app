package com.harazone.ui.map

import com.harazone.domain.model.ChatIntent
import com.harazone.domain.model.MessageRole
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ChatPoiCard(
    @SerialName("n") val name: String,
    @SerialName("t") val type: String,
    val lat: Double,
    val lng: Double,
    @SerialName("w") val whySpecial: String,
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
    val followUpChips: List<String> = emptyList(),
    val inputText: String = "",
    val lastUserQuery: String = "",
    val poiCards: List<ChatPoiCard> = emptyList(),
    val showSkeletons: Boolean = false,
    val savedPoiIds: Set<String> = emptySet(),
    val intentPills: List<ChatIntent> = emptyList(),
)

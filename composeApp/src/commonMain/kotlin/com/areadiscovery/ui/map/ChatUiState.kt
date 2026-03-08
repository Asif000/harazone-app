package com.areadiscovery.ui.map

import com.areadiscovery.domain.model.MessageRole

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
)

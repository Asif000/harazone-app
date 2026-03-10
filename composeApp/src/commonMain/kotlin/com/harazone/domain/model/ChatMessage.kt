package com.harazone.domain.model

enum class MessageRole {
    USER,
    AI
}

data class ChatMessage(
    val id: String,
    val role: MessageRole,
    val content: String,
    val timestamp: Long,
    val sources: List<Source>
)

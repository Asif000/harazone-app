package com.harazone.domain.model

data class ContextualPill(
    val label: String,
    val message: String,
    val intent: ChatIntent = ChatIntent.DISCOVER,
    val emoji: String = "✨",
)

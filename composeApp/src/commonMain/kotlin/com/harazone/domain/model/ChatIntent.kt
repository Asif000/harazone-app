package com.harazone.domain.model

enum class ChatIntent(val displayLabel: String, val openingMessage: String) {
    TONIGHT("Tonight", "What should I do tonight?"),
    DISCOVER("Discover", "What makes this place special?"),
    HUNGRY("Hungry", "Where should I eat right now?"),
    OUTSIDE("Outside", "Get me outside."),
    SURPRISE("Surprise me", "Surprise me."),
}

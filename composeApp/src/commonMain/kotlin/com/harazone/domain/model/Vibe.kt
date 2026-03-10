package com.harazone.domain.model

enum class Vibe(
    val displayName: String,
    val accentColorHex: String,
    // TODO(BACKLOG-LOW): orbIconName is a dead field — unmapped in UI, overridden by toImageVector()
    val orbIconName: String,
) {
    CHARACTER("Character", "#2BBCB3", "palette"),
    HISTORY("History", "#C4935A", "history"),
    WHATS_ON("What's On", "#9B6ED8", "event"),
    SAFETY("Safety", "#E8A735", "shield"),
    NEARBY("Nearby", "#5B9BD5", "explore"),
    COST("Cost", "#5CAD6F", "payments");

    companion object {
        val DEFAULT = CHARACTER
    }
}

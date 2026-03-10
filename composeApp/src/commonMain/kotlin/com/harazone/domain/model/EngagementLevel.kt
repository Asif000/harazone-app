package com.harazone.domain.model

enum class EngagementLevel {
    FRESH, LIGHT, REGULAR, POWER, DORMANT;

    companion object {
        private const val DAY_MS = 24 * 60 * 60 * 1000L

        fun from(saves: List<SavedPoi>, nowMs: Long): EngagementLevel {
            if (saves.isEmpty()) return FRESH
            val mostRecentSave = saves.maxOf { it.savedAt }
            if (nowMs - mostRecentSave > 14 * DAY_MS) return DORMANT
            return when (saves.size) {
                in 1..5 -> LIGHT
                in 6..29 -> REGULAR
                else -> POWER
            }
        }
    }
}

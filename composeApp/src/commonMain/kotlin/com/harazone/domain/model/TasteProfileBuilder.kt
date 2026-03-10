package com.harazone.domain.model

object TasteProfileBuilder {

    // Must match Gemini output schema: Valid t values in buildAreaPortraitPrompt / outputFormatBlock
    private val COMMON_TYPES = listOf(
        "food", "entertainment", "park", "historic", "shopping",
        "arts", "beach", "district",
    )
    private val FOOD_TYPES = listOf("food")
    private const val THIRTY_DAY_MS = 30L * 24 * 60 * 60 * 1000

    fun build(saves: List<SavedPoi>, nowMs: Long): TasteProfile {
        val countByType = saves.groupBy { it.type }.mapValues { it.value.size }

        val strongAffinities = countByType
            .filter { it.value >= 3 }
            .entries
            .sortedByDescending { it.value }
            .map { it.key }

        val recentSaves = saves.filter { it.savedAt > nowMs - THIRTY_DAY_MS }
        val recentCountByType = recentSaves.groupBy { it.type }.mapValues { it.value.size }

        val emergingInterests = recentCountByType
            .filter { it.value in 1..2 && it.key !in strongAffinities }
            .map { it.key }

        val notableAbsences = COMMON_TYPES.filter { countByType[it] == null }

        val diningStyle = if (saves.count { it.type in FOOD_TYPES } >= 2) "food lover" else null

        return TasteProfile(
            strongAffinities = strongAffinities,
            emergingInterests = emergingInterests,
            notableAbsences = notableAbsences,
            diningStyle = diningStyle,
            totalSaves = saves.size,
        )
    }
}

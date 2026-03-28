package com.harazone.domain.model

enum class DataConfidence { LOW, MEDIUM, HIGH }
enum class DataClassification { STATIC, DYNAMIC, VOLATILE }

data class ResidentDataPoint(
    val value: String,
    val detail: String,
    val confidence: DataConfidence,
    val classification: DataClassification,
    val sourceLabel: String,
    val verifyUrl: String? = null,
)

data class ResidentCategory(
    val id: String,
    val label: String,
    val icon: String,
    val points: List<ResidentDataPoint>,
)

data class ResidentData(
    val areaName: String,
    val categories: List<ResidentCategory>,
    val originContext: String?,
    val fetchedAt: Long,
) {
    companion object {
        const val CAT_RENTAL = "D1"
        const val CAT_BUY = "D2"
        const val CAT_COL = "D3"
        const val CAT_SAFETY = "D4"
        const val CAT_JOBS = "D6"
        const val CAT_COMMUNITY = "D9"
        const val CAT_WEATHER = "D10"
        const val CAT_VISA = "D12"
        const val CAT_CULTURE = "D21"

        val ALL_IDS = listOf(CAT_RENTAL, CAT_BUY, CAT_COL, CAT_SAFETY, CAT_JOBS, CAT_COMMUNITY, CAT_WEATHER, CAT_VISA, CAT_CULTURE)
    }
}

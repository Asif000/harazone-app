package com.harazone.domain.model

/**
 * Represents a single line in the Discovery Header's rotating meta ticker.
 * Priority determines display order — lower number = higher priority.
 * Safety warnings (priority 1) never rotate; they stay fixed.
 */
sealed class MetaLine(val priority: Int) {
    /** Priority 1 — fixed, never rotates. Amber text. */
    data class SafetyWarning(val text: String) : MetaLine(1)

    /** Priority 2 — teleported/remote area context. Teal text. */
    data class RemoteContext(val fromCity: String, val distance: String) : MetaLine(2) {
        val text: String get() = "From $fromCity \u00b7 $distance"
    }

    /** Priority 2 — local currency + exchange rate. Teal text. Remote areas only. */
    data class CurrencyContext(val text: String) : MetaLine(2)

    /** Priority 2 — primary local language. Teal text. Remote areas only. */
    data class LanguageContext(val text: String) : MetaLine(2)

    /** Priority 3 — active vibe filter summary. Purple text. */
    data class VibeFilter(val matchCount: Int, val totalCount: Int, val vibeNames: String) : MetaLine(3) {
        val text: String get() = "\uD83C\uDFAD $matchCount/$totalCount $vibeNames"
    }

    /** Priority 4 — companion nudge teaser. Purple text. */
    data class CompanionNudge(val text: String) : MetaLine(4)

    /** Priority 5 — featured POI nearby. Teal text. */
    data class PoiHighlight(val text: String) : MetaLine(5)

    /** Priority 6 — default weather/time/visit. White/muted text. */
    data class Default(val text: String) : MetaLine(6)

    /** GPS acquiring state — static, no rotation. */
    data object GpsAcquiring : MetaLine(99)

    /** Location denied state — static. */
    data object LocationDenied : MetaLine(99)

    /** Discovery spinner active — pauses rotation. */
    data class Discovering(val areaName: String, val isSurprise: Boolean = false) : MetaLine(99)
}

val MetaLine.text: String
    get() = when (this) {
        is MetaLine.SafetyWarning -> text
        is MetaLine.RemoteContext -> text
        is MetaLine.CurrencyContext -> text
        is MetaLine.LanguageContext -> text
        is MetaLine.VibeFilter -> text
        is MetaLine.CompanionNudge -> text
        is MetaLine.PoiHighlight -> text
        is MetaLine.Default -> text
        is MetaLine.GpsAcquiring -> "\uD83D\uDEF0 Getting your location..."
        is MetaLine.LocationDenied -> "\uD83D\uDD0D Search any city, place, or area"
        is MetaLine.Discovering -> if (isSurprise) "Surprises in $areaName..." else "Discovering $areaName..."
    }

/** Whether this line should stay fixed (no rotation). */
fun MetaLine.isFixed(): Boolean = this is MetaLine.SafetyWarning

/**
 * Build the priority-sorted list of meta lines from current state.
 * Safety warning at priority 1 is fixed; others rotate every 4s.
 */
fun buildMetaLines(
    advisoryLevel: AdvisoryLevel?,
    advisoryCountryName: String? = null,
    isRemote: Boolean = false,
    homeCity: String? = null,
    remoteDistance: String? = null,
    activeVibeFilters: Set<String> = emptySet(),
    vibeMatchCount: Int = 0,
    totalPoiCount: Int = 0,
    companionNudgeText: String? = null,
    poiHighlights: List<String> = emptyList(),
    weatherText: String? = null,
    timeText: String? = null,
    visitTag: String = "First visit",
    isSearching: Boolean = false,
    areaName: String = "",
    currencyText: String? = null,
    languageText: String? = null,
    isSurprise: Boolean = false,
): List<MetaLine> {
    if (isSearching) {
        return listOf(MetaLine.Discovering(areaName, isSurprise))
    }

    val lines = mutableListOf<MetaLine>()

    // Priority 1: Safety warning (fixed, no rotation)
    if (advisoryLevel != null && advisoryLevel != AdvisoryLevel.SAFE && advisoryLevel != AdvisoryLevel.UNKNOWN) {
        val warningText = when (advisoryLevel) {
            AdvisoryLevel.CAUTION -> "\u26A0\uFE0F Exercise caution \u00b7 Check advisory"
            AdvisoryLevel.RECONSIDER -> "\u26A0\uFE0F Reconsider travel \u00b7 Check advisory"
            AdvisoryLevel.DO_NOT_TRAVEL -> "\u26A0\uFE0F Do not travel \u00b7 Check advisory"
            else -> null
        }
        if (warningText != null) {
            lines.add(MetaLine.SafetyWarning(warningText))
        }
    }

    // Priority 2: Remote/teleported area
    if (isRemote && homeCity != null && remoteDistance != null) {
        lines.add(MetaLine.RemoteContext(homeCity, remoteDistance))
    }
    if (isRemote && currencyText != null) {
        lines.add(MetaLine.CurrencyContext(currencyText))
    }
    if (isRemote && languageText != null) {
        lines.add(MetaLine.LanguageContext(languageText))
    }

    // Priority 3: Active vibe filter
    if (activeVibeFilters.isNotEmpty()) {
        val names = activeVibeFilters.joinToString(" \u00b7 ")
        lines.add(MetaLine.VibeFilter(vibeMatchCount, totalPoiCount, names))
    }

    // Priority 4: Companion nudge
    if (companionNudgeText != null) {
        lines.add(MetaLine.CompanionNudge(companionNudgeText))
    }

    // Priority 5: POI highlights
    for (highlight in poiHighlights) {
        if (highlight.isNotBlank()) {
            lines.add(MetaLine.PoiHighlight(highlight))
        }
    }

    // Priority 6: Default (always present as fallback)
    val defaultText = buildString {
        if (weatherText != null) append(weatherText)
        if (timeText != null) {
            if (isNotEmpty()) append(" \u00b7 ")
            append(timeText)
        }
        if (isNotEmpty()) append(" \u00b7 ")
        append(visitTag)
    }
    lines.add(MetaLine.Default(defaultText))

    return lines.sortedBy { it.priority }
}

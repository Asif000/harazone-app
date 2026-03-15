package com.harazone.ui.map

import androidx.compose.ui.graphics.Color
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import com.harazone.ui.components.currentHour
import com.harazone.ui.components.currentMinute
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

enum class PinStatusColor { GREEN, ORANGE, RED, GREY }

enum class PinBadge { CLOSING_SOON, TRENDING, EVENT }

fun liveStatusToColor(liveStatus: String?): PinStatusColor = when {
    liveStatus == null -> PinStatusColor.GREY
    liveStatus.contains("clos", ignoreCase = true) && liveStatus.contains("soon", ignoreCase = true) -> PinStatusColor.ORANGE
    liveStatus.contains("closed", ignoreCase = true) -> PinStatusColor.RED
    liveStatus.contains("open", ignoreCase = true) && !liveStatus.contains("closed", ignoreCase = true) -> PinStatusColor.GREEN
    else -> PinStatusColor.GREY
}

/**
 * Derive open/closed status from hours string + device local clock.
 * Parses formats like "9am-10pm", "9:00am-10:00pm", "11:30am - 10pm", "24 hours", "Open 24 hours".
 * Returns "open", "closing soon" (within 60 min), "closed", or null if unparseable.
 */
fun deriveStatusFromHours(hours: String?, nowMinutesOverride: Int? = null): String? {
    if (hours == null) return null
    val h = hours.trim().lowercase()
    if (h.contains("24 hour") || h == "24h") return "open"
    // Extract open-close times from pattern like "9am-10pm" or "9:00 am - 10:00 pm"
    val timePattern = Regex("""(\d{1,2})(?::(\d{2}))?\s*(am|pm)\s*[-–]\s*(\d{1,2})(?::(\d{2}))?\s*(am|pm)""", RegexOption.IGNORE_CASE)
    val match = timePattern.find(h) ?: return null
    val groups = match.groupValues
    val openMinutes = to24Minutes(groups[1].toInt(), groups[2].ifEmpty { "0" }.toInt(), groups[3])
    val closeMinutes = to24Minutes(groups[4].toInt(), groups[5].ifEmpty { "0" }.toInt(), groups[6])

    val nowMinutes = nowMinutesOverride ?: (currentHour() * 60 + currentMinute())

    // Handle overnight ranges (e.g., 6pm-2am)
    val isOvernight = closeMinutes <= openMinutes
    val isOpen = if (isOvernight) {
        nowMinutes >= openMinutes || nowMinutes < closeMinutes
    } else {
        nowMinutes in openMinutes until closeMinutes
    }

    if (!isOpen) return "closed"

    // Check closing soon (within 60 min)
    val minutesToClose = if (isOvernight && nowMinutes >= openMinutes) {
        (1440 - nowMinutes) + closeMinutes
    } else {
        closeMinutes - nowMinutes
    }
    if (minutesToClose in 1..60) return "closing soon"
    return "open"
}

private fun to24Minutes(hr: Int, min: Int, amPm: String): Int {
    val h = when {
        amPm.lowercase() == "am" && hr == 12 -> 0
        amPm.lowercase() == "pm" && hr != 12 -> hr + 12
        else -> hr
    }
    return h * 60 + min
}

/**
 * Resolve effective live status: prefer hours-derived status over Gemini's guess.
 */
fun resolveStatus(liveStatus: String?, hours: String?, nowMinutesOverride: Int? = null): String? {
    return deriveStatusFromHours(hours, nowMinutesOverride) ?: liveStatus
}

fun isClosed(liveStatus: String?): Boolean = liveStatusToColor(liveStatus) == PinStatusColor.RED

fun deriveBadge(liveStatus: String?): PinBadge? = when {
    liveStatus == null -> null
    liveStatus.contains("clos", ignoreCase = true) && liveStatus.contains("soon", ignoreCase = true) -> PinBadge.CLOSING_SOON
    liveStatus.contains("trend", ignoreCase = true) -> PinBadge.TRENDING
    liveStatus.contains("event", ignoreCase = true) -> PinBadge.EVENT
    else -> null
}

fun PinStatusColor.toComposeColor(): Color = when (this) {
    PinStatusColor.GREEN -> Color(0xFF4CAF50)
    PinStatusColor.ORANGE -> Color(0xFFFF9800)
    PinStatusColor.RED -> Color(0xFFf44336)
    PinStatusColor.GREY -> Color(0xFF9E9E9E)
}

private fun toRad(deg: Double): Double = deg * PI / 180.0
private fun toDeg(rad: Double): Double = rad * 180.0 / PI

/**
 * NOAA Solar Calculator — shared solar geometry for sunrise/sunset.
 */
private data class SolarGeometry(
    val cosOmega: Double,
    val jTransit: Double,
    val currentMs: Double,
)

@OptIn(ExperimentalTime::class)
private fun solarGeometry(lat: Double, lng: Double): SolarGeometry {
    val currentMs = Clock.System.now().toEpochMilliseconds().toDouble()
    val daysSinceEpoch = floor(currentMs / 86400000.0).toLong()
    val jd = daysSinceEpoch + 2440587.5
    val n = jd - 2451545.0
    val meanAnomaly = (357.5291 + 0.98560028 * n) % 360.0
    val mRad = toRad(meanAnomaly)
    val equationOfCenter = 1.9148 * sin(mRad) + 0.0200 * sin(2 * mRad) + 0.0003 * sin(3 * mRad)
    val eclipticLong = (meanAnomaly + equationOfCenter + 180.0 + 102.9372) % 360.0
    val eclRad = toRad(eclipticLong)
    val declination = toDeg(asin(sin(eclRad) * sin(toRad(23.4393))))
    val decRad = toRad(declination)
    val latRad = toRad(lat)
    val cosOmega = (sin(toRad(-0.833)) - sin(latRad) * sin(decRad)) /
        (cos(latRad) * cos(decRad))
    val jTransit = 2451545.0 + n + 0.0009 + ((-lng) / 360.0)
    return SolarGeometry(cosOmega, jTransit, currentMs)
}

/**
 * NOAA Solar Calculator — minutes until sunset for given lat/lng.
 * Returns Int.MAX_VALUE for polar summer (never sets), -1 for polar winter (never rises).
 */
internal fun calculateSunsetMinutes(lat: Double, lng: Double): Int {
    val sg = solarGeometry(lat, lng)
    if (sg.cosOmega > 1.0) return -1     // polar winter
    if (sg.cosOmega < -1.0) return Int.MAX_VALUE // polar summer
    val omega = toDeg(acos(sg.cosOmega))
    val jSet = sg.jTransit + omega / 360.0
    val sunsetMs = (jSet - 2440587.5) * 86400000.0
    return ((sunsetMs - sg.currentMs) / 60000.0).toInt()
}

/**
 * NOAA Solar Calculator — minutes until sunrise for given lat/lng.
 * Returns Int.MAX_VALUE for polar winter (never rises), -1 for polar summer (never sets / already risen).
 */
internal fun calculateSunriseMinutes(lat: Double, lng: Double): Int {
    val sg = solarGeometry(lat, lng)
    if (sg.cosOmega > 1.0) return Int.MAX_VALUE // polar winter — never rises
    if (sg.cosOmega < -1.0) return -1            // polar summer — already risen
    val omega = toDeg(acos(sg.cosOmega))
    val jRise = sg.jTransit - omega / 360.0
    val sunriseMs = (jRise - 2440587.5) * 86400000.0
    return ((sunriseMs - sg.currentMs) / 60000.0).toInt()
}

/**
 * Parse earliest opening hour from a POI hours string.
 * Returns the opening hour (0-23) or null if unparseable.
 */
internal fun parseOpeningHour(hours: String?): Int? {
    if (hours == null) return null
    val h = hours.trim().lowercase()
    if (h.contains("24 hour") || h == "24h") return 0
    val timePattern = Regex("""(\d{1,2})(?::(\d{2}))?\s*(am|pm)""", RegexOption.IGNORE_CASE)
    val match = timePattern.find(h) ?: return null
    val hr = match.groupValues[1].toInt()
    val amPm = match.groupValues[3].lowercase()
    return when {
        amPm == "am" && hr == 12 -> 0
        amPm == "pm" && hr != 12 -> hr + 12
        else -> hr
    }
}

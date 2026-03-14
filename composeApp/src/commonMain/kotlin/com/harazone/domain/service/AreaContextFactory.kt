package com.harazone.domain.service

import com.harazone.domain.model.AreaContext
import com.harazone.domain.provider.LocaleProvider
import com.harazone.util.AppClock

open class AreaContextFactory(
    private val clock: AppClock,
    private val localeProvider: LocaleProvider,
) {

    open fun create(): AreaContext {
        val nowMs = clock.nowMs()
        return AreaContext(
            timeOfDay = resolveTimeOfDay(nowMs),
            dayOfWeek = resolveDayOfWeek(nowMs),
            visitCount = 0,
            preferredLanguage = localeProvider.languageTag,
            isRtl = localeProvider.isRtl,
            homeCurrencyCode = localeProvider.homeCurrencyCode,
        )
    }

    private fun resolveTimeOfDay(epochMs: Long): String {
        // Uses UTC — kotlinx-datetime not in deps. Platform TZ support deferred to Epic 8 (adaptive intelligence).
        val totalSeconds = epochMs / 1000
        val secondsInDay = totalSeconds % 86400
        val hour = (secondsInDay / 3600).toInt()
        return when (hour) {
            in 5..11 -> "morning"
            in 12..16 -> "afternoon"
            in 17..20 -> "evening"
            else -> "night"
        }
    }

    private fun resolveDayOfWeek(epochMs: Long): String {
        // Unix epoch (Jan 1, 1970) was a Thursday. Uses UTC (see resolveTimeOfDay comment).
        val daysSinceEpoch = epochMs / 86_400_000
        val dayIndex = ((daysSinceEpoch % 7) + 7) % 7 // Handle negative values
        return when (dayIndex.toInt()) {
            0 -> "Thursday"
            1 -> "Friday"
            2 -> "Saturday"
            3 -> "Sunday"
            4 -> "Monday"
            5 -> "Tuesday"
            6 -> "Wednesday"
            else -> "Monday" // Unreachable
        }
    }
}

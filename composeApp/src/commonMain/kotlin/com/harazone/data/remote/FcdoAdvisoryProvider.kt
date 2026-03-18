package com.harazone.data.remote

import co.touchlab.kermit.Logger
import com.harazone.data.repository.UserPreferencesRepository
import com.harazone.domain.model.AdvisoryLevel
import com.harazone.domain.model.AreaAdvisory
import com.harazone.domain.model.SubNationalAdvisory
import com.harazone.domain.provider.AdvisoryProvider
import com.harazone.util.AppClock
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class FcdoAdvisoryProvider(
    private val httpClient: HttpClient,
    private val prefs: UserPreferencesRepository,
    private val clock: AppClock,
) : AdvisoryProvider {

    private val json = Json { ignoreUnknownKeys = true }
    private val log = Logger.withTag("FcdoAdvisoryProvider")

    override suspend fun getAdvisory(countryCode: String, regionName: String?): Result<AreaAdvisory> {
        return try {
            val code = countryCode.uppercase()

            // Check cache first
            val cached = prefs.getAdvisoryCache(code)
            if (cached != null) {
                val advisory = json.decodeFromString<AreaAdvisory>(cached)
                val ageMs = clock.nowMs() - advisory.cachedAt
                if (ageMs < CACHE_TTL_MS) {
                    val resolved = resolveSubNational(advisory, regionName)
                    return Result.success(resolved)
                }
            }

            // Fetch from FCDO
            val countryName = prefs.getAdvisoryCachedCountryName(code) ?: code
            val slug = FcdoCountrySlugs.getSlug(code, countryName)
            val url = "$FCDO_BASE_URL/$slug"

            val responseText = httpClient.get(url).bodyAsText()
            val advisory = parseFcdoResponse(responseText, code, regionName)

            // Cache on success — never cache UNKNOWN
            if (advisory.level != AdvisoryLevel.UNKNOWN) {
                prefs.setAdvisoryCache(code, json.encodeToString(AreaAdvisory.serializer(), advisory))
                prefs.setAdvisoryCachedCountryName(code, advisory.countryName)
            }

            Result.success(advisory)
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (e: Exception) {
            log.w(e) { "Failed to fetch advisory for $countryCode" }
            Result.success(makeUnknownAdvisory(countryCode))
        }
    }

    internal fun parseFcdoResponse(
        responseText: String,
        countryCode: String,
        regionName: String?,
    ): AreaAdvisory {
        val root = json.parseToJsonElement(responseText).jsonObject
        val title = root["title"]?.jsonPrimitive?.content ?: ""
        val countryName = title.replace(" travel advice", "").trim()

        val details = root["details"]?.jsonObject

        val lastUpdatedStr = root["public_updated_at"]?.jsonPrimitive?.content
        val lastUpdated = parseIso8601(lastUpdatedStr)

        // Primary: use alert_status array (e.g. ["avoid_all_travel_to_whole_country"])
        val alertStatus = details?.get("alert_status")?.jsonArray
            ?.mapNotNull { it.jsonPrimitive.content }
            ?: emptyList()

        // Parts are under details, not root
        val parts = details?.get("parts")?.jsonArray ?: emptyList()

        // Summary comes from warnings-and-insurance part, not details.summary
        val warningsPart = parts.firstOrNull { part ->
            part.jsonObject["slug"]?.jsonPrimitive?.content == "warnings-and-insurance"
        }
        val warningsBody = warningsPart?.jsonObject?.get("body")?.jsonPrimitive?.content ?: ""
        val summary = stripHtml(warningsBody).take(300)

        // Classify level: alert_status first, fall back to keyword matching
        val level = classifyAlertStatus(alertStatus)
            ?: classifyAdvisoryLevel(summary)

        val safetyPart = parts.firstOrNull { part ->
            part.jsonObject["slug"]?.jsonPrimitive?.content == "safety-and-security"
        }

        val safetyBody = safetyPart?.jsonObject?.get("body")?.jsonPrimitive?.content ?: ""
        val detailsList = parseSafetyDetails(safetyBody, summary)
        val subNationalZones = parseSubNationalZones(safetyBody)

        val baseAdvisory = AreaAdvisory(
            level = level,
            countryName = countryName,
            countryCode = countryCode,
            summary = summary,
            details = detailsList,
            subNationalZones = subNationalZones,
            sourceUrl = "https://www.gov.uk/foreign-travel-advice/${FcdoCountrySlugs.getSlug(countryCode, countryName)}",
            lastUpdated = lastUpdated,
            cachedAt = clock.nowMs(),
        )

        return resolveSubNational(baseAdvisory, regionName)
    }

    internal fun classifyAlertStatus(alertStatus: List<String>): AdvisoryLevel? {
        if (alertStatus.isEmpty()) return null
        return when {
            alertStatus.any { "avoid_all_travel_to_whole_country" in it } -> AdvisoryLevel.DO_NOT_TRAVEL
            alertStatus.any { "avoid_all_travel_to_parts" in it } -> AdvisoryLevel.DO_NOT_TRAVEL
            alertStatus.any { "avoid_all_but_essential_travel_to_whole_country" in it } -> AdvisoryLevel.RECONSIDER
            alertStatus.any { "avoid_all_but_essential_travel_to_parts" in it } -> AdvisoryLevel.RECONSIDER
            else -> null // Unknown alert_status — fall through to keyword matching
        }
    }

    internal fun classifyAdvisoryLevel(summary: String): AdvisoryLevel {
        val lower = summary.lowercase()
        return when {
            "advises against all travel to" in lower ||
                ("advises against all travel" in lower && "but essential" !in lower) -> AdvisoryLevel.DO_NOT_TRAVEL
            "advises against all but essential travel" in lower -> AdvisoryLevel.RECONSIDER
            "exercise increased caution" in lower ||
                "advises caution" in lower ||
                "high degree of caution" in lower -> AdvisoryLevel.CAUTION
            summary.isBlank() -> AdvisoryLevel.SAFE
            else -> {
                if (summary.isNotBlank()) {
                    log.w { "Unmatched FCDO summary phrasing: ${summary.take(120)}" }
                }
                AdvisoryLevel.SAFE
            }
        }
    }

    private fun resolveSubNational(advisory: AreaAdvisory, regionName: String?): AreaAdvisory {
        if (regionName.isNullOrBlank() || advisory.subNationalZones.isEmpty()) return advisory

        val match = advisory.subNationalZones.firstOrNull { zone ->
            regionName.contains(zone.regionName, ignoreCase = true) ||
                zone.regionName.contains(regionName, ignoreCase = true)
        }

        return if (match != null && match.level.ordinal > advisory.level.ordinal) {
            advisory.copy(level = match.level, summary = match.summary)
        } else {
            advisory
        }
    }

    private fun parseSafetyDetails(safetyBodyHtml: String, fallbackSummary: String): List<String> {
        if (safetyBodyHtml.isBlank()) {
            return if (fallbackSummary.isNotBlank()) listOf(fallbackSummary) else emptyList()
        }

        // Split by h2/h3 sections and strip HTML
        val sections = safetyBodyHtml.split(Regex("<h[23][^>]*>"))
            .map { stripHtml(it).trim() }
            .filter { it.isNotBlank() && it.length > 10 }

        return if (sections.isNotEmpty()) sections else listOf(stripHtml(safetyBodyHtml).trim())
    }

    private fun parseSubNationalZones(safetyBodyHtml: String): List<SubNationalAdvisory> {
        if (safetyBodyHtml.isBlank()) return emptyList()

        val zones = mutableListOf<SubNationalAdvisory>()
        val lower = safetyBodyHtml.lowercase()

        // Look for region-specific "advises against" patterns
        val regionPattern = Regex(
            """advises against (?:all )?(?:but essential )?travel to ([^.<]+)""",
            RegexOption.IGNORE_CASE,
        )
        for (match in regionPattern.findAll(safetyBodyHtml)) {
            val regionText = match.groupValues[1].trim()
            val surroundingText = match.value
            val level = classifyAdvisoryLevel(surroundingText)
            if (level != AdvisoryLevel.SAFE) {
                zones.add(
                    SubNationalAdvisory(
                        regionName = stripHtml(regionText).trim(),
                        level = level,
                        summary = stripHtml(surroundingText).trim(),
                    )
                )
            }
        }

        return zones
    }

    private fun makeUnknownAdvisory(countryCode: String): AreaAdvisory {
        return AreaAdvisory(
            level = AdvisoryLevel.UNKNOWN,
            countryName = "",
            countryCode = countryCode,
            summary = "",
            details = emptyList(),
            subNationalZones = emptyList(),
            sourceUrl = "",
            lastUpdated = 0L,
            cachedAt = 0L, // Don't set real time — UNKNOWN is never cached
        )
    }

    private fun stripHtml(html: String): String {
        return html.replace(Regex("<[^>]+>"), " ")
            .replace(Regex("&amp;"), "&")
            .replace(Regex("&lt;"), "<")
            .replace(Regex("&gt;"), ">")
            .replace(Regex("&quot;"), "\"")
            .replace(Regex("&#39;"), "'")
            .replace(Regex("&nbsp;"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    // TODO(BACKLOG-LOW): Replace with kotlinx-datetime Instant.parse() when dependency is added
    private fun parseIso8601(isoString: String?): Long {
        if (isoString.isNullOrBlank()) return 0L
        return try {
            // Simple regex-based parsing for ISO 8601 (assumes UTC — sufficient for display label)
            val pattern = Regex("""(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2}):(\d{2})""")
            val match = pattern.find(isoString) ?: return 0L
            val (year, month, day, hour, minute, second) = match.destructured

            // Approximate epoch calculation (sufficient for display "last updated" label)
            val y = year.toInt()
            val m = month.toInt()
            val d = day.toInt()
            val h = hour.toInt()
            val min = minute.toInt()
            val s = second.toInt()

            // Days from epoch (1970-01-01) — simplified, not accounting for leap seconds
            val daysInYear = if (y % 4 == 0 && (y % 100 != 0 || y % 400 == 0)) 366 else 365
            val monthDays = intArrayOf(0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334)
            var days = (y - 1970) * 365L + ((y - 1969) / 4) - ((y - 1901) / 100) + ((y - 1601) / 400)
            days += monthDays[m - 1] + d - 1
            if (m > 2 && daysInYear == 366) days += 1

            (days * 86400L + h * 3600L + min * 60L + s) * 1000L
        } catch (e: Exception) {
            log.w(e) { "Failed to parse ISO 8601 timestamp: $isoString" }
            0L
        }
    }

    companion object {
        private const val FCDO_BASE_URL = "https://www.gov.uk/api/content/foreign-travel-advice"
        private const val CACHE_TTL_MS = 24 * 60 * 60 * 1000L // 24 hours
    }
}

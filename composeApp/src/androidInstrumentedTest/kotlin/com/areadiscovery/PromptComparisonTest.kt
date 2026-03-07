package com.areadiscovery

import android.os.Environment
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.areadiscovery.data.remote.BuildKonfigApiKeyProvider
import com.areadiscovery.data.remote.GeminiAreaIntelligenceProvider
import com.areadiscovery.data.remote.GeminiPromptBuilder
import com.areadiscovery.data.remote.GeminiResponseParser
import com.areadiscovery.data.remote.HttpClientFactory
import com.areadiscovery.domain.model.AreaContext
import com.areadiscovery.domain.model.BucketUpdate
import com.areadiscovery.domain.model.POI
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Integration test that calls real Gemini API for each test location
 * and writes a comparison report. Run manually:
 *
 *   ./gradlew :composeApp:connectedDebugAndroidTest --tests "*.PromptComparisonTest"
 *
 * Results saved to device Downloads folder. Pull with:
 *   adb pull /sdcard/Download/prompt-test-results-<timestamp>.md ~/Desktop/
 */
@RunWith(AndroidJUnit4::class)
class PromptComparisonTest {

    private data class TestLocation(
        val name: String,
        val lat: Double,
        val lng: Double,
        val why: String,
    )

    private val testLocations = listOf(
        TestLocation("Times Square, New York", 40.7580, -73.9855, "Dense/touristy — chain exclusion"),
        TestLocation("Alfama, Lisbon", 38.7139, -9.1394, "Character/history — local persona"),
        TestLocation("Dozier, Alabama", 31.4910, -86.3647, "Rural/sparse — quality filter"),
        TestLocation("Shibuya, Tokyo", 35.6595, 139.7004, "Non-English — food gate"),
        TestLocation("Sao Paulo, Brazil", -23.5505, -46.6333, "Mega-city Latin America"),
        TestLocation("Karachi, Pakistan", 24.8607, 67.0011, "South Asia — cultural context"),
        TestLocation("Doha, Qatar", 25.2854, 51.5310, "Gulf state — modern vs traditional"),
        TestLocation("Dubai, UAE", 25.2048, 55.2708, "Ultra-touristy — uniqueness filter"),
        TestLocation("Isfahan, Iran", 32.6546, 51.6680, "Deep history — heritage"),
        TestLocation("Baghdad, Iraq", 33.3152, 44.3661, "Underrepresented — AI bias"),
        TestLocation("Shanghai, China", 31.2304, 121.4737, "East Asia mega-city"),
        TestLocation("Zurich, Switzerland", 47.3769, 8.5417, "European — cost vibe"),
        TestLocation("Mecca, Saudi Arabia", 21.3891, 39.8579, "Sacred/restricted — sensitivity"),
        TestLocation("Sahara Desert", 23.0000, 12.0000, "Edge case — empty results"),
    )

    private val context = AreaContext(
        timeOfDay = "afternoon",
        dayOfWeek = "Friday",
        visitCount = 0,
        preferredLanguage = "en",
    )

    @Test
    fun capturePromptResults() = runBlocking {
        val provider = GeminiAreaIntelligenceProvider(
            httpClient = HttpClientFactory.create(),
            apiKeyProvider = BuildKonfigApiKeyProvider(),
            promptBuilder = GeminiPromptBuilder(),
            responseParser = GeminiResponseParser(),
        )

        val report = StringBuilder()
        val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.US).format(Date())
        report.appendLine("# Prompt Test Results — $timestamp")
        report.appendLine()
        report.appendLine("Context: ${context.timeOfDay}, ${context.dayOfWeek}, lang=${context.preferredLanguage}")
        report.appendLine()

        var totalPois = 0
        var totalLocations = 0
        var failedLocations = 0

        for (location in testLocations) {
            report.appendLine("---")
            report.appendLine("## ${location.name}")
            report.appendLine("_${location.why}_")
            report.appendLine()

            try {
                val updates = provider.streamAreaPortrait(location.name, context).toList()
                val pois = updates.filterIsInstance<BucketUpdate.PortraitComplete>()
                    .flatMap { it.pois }

                totalPois += pois.size
                totalLocations++

                report.appendLine("**POI count: ${pois.size}**")
                report.appendLine()

                if (pois.isEmpty()) {
                    report.appendLine("_No POIs returned_")
                } else {
                    report.appendLine("| # | Name | Type | Vibe | Confidence | Insight |")
                    report.appendLine("|---|------|------|------|------------|---------|")
                    pois.forEachIndexed { i, poi ->
                        val insight = poi.insight.take(60).replace("|", "/")
                        report.appendLine("| ${i + 1} | ${poi.name} | ${poi.type} | ${poi.vibe} | ${poi.confidence} | $insight |")
                    }
                }

                // Bucket highlights
                val buckets = updates.filterIsInstance<BucketUpdate.BucketComplete>()
                if (buckets.isNotEmpty()) {
                    report.appendLine()
                    report.appendLine("**Buckets:** ${buckets.size}")
                    for (bucket in buckets) {
                        report.appendLine("- ${bucket.content.type}: ${bucket.content.highlight.take(80)}")
                    }
                }
            } catch (e: Exception) {
                failedLocations++
                report.appendLine("**ERROR:** ${e.message?.take(120)}")
            }

            report.appendLine()
        }

        // Summary
        report.insertSummary(totalLocations, totalPois, failedLocations)

        // Write to device Downloads folder
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val file = File(downloadsDir, "prompt-test-results-$timestamp.md")
        file.writeText(report.toString())

        // Also log path for easy finding
        println("PROMPT TEST RESULTS: ${file.absolutePath}")
        println("Pull with: adb pull ${file.absolutePath} ~/Desktop/")
    }

    private fun StringBuilder.insertSummary(locations: Int, pois: Int, failed: Int) {
        val summary = buildString {
            appendLine("## Summary")
            appendLine()
            appendLine("| Metric | Value |")
            appendLine("|--------|-------|")
            appendLine("| Locations tested | $locations |")
            appendLine("| Total POIs | $pois |")
            appendLine("| Avg POIs/location | ${if (locations > 0) "%.1f".format(pois.toFloat() / locations) else "N/A"} |")
            appendLine("| Failed locations | $failed |")
            appendLine()
        }
        val insertPos = this.indexOf("---")
        if (insertPos > 0) {
            this.insert(insertPos, summary)
        } else {
            this.append(summary)
        }
    }
}

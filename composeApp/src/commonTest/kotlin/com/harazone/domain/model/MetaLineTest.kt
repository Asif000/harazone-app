package com.harazone.domain.model

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MetaLineTest {

    @Test
    fun buildMetaLines_includes_currency_and_language_lines_when_remote() {
        val lines = buildMetaLines(
            advisoryLevel = null,
            isRemote = true,
            currencyText = "¥ · ~149 JPY/USD",
            languageText = "日本語 · Japanese",
        )
        assertTrue(lines.any { it is MetaLine.CurrencyContext })
        assertTrue(lines.any { it is MetaLine.LanguageContext })
        val currency = lines.filterIsInstance<MetaLine.CurrencyContext>().first()
        val language = lines.filterIsInstance<MetaLine.LanguageContext>().first()
        assertTrue(currency.text.contains("JPY"))
        assertTrue(language.text.contains("Japanese"))
    }

    @Test
    fun buildMetaLines_excludes_currency_and_language_lines_when_not_remote() {
        val lines = buildMetaLines(
            advisoryLevel = null,
            isRemote = false,
            currencyText = "¥ · ~149 JPY/USD",
            languageText = "日本語 · Japanese",
        )
        assertFalse(lines.any { it is MetaLine.CurrencyContext })
        assertFalse(lines.any { it is MetaLine.LanguageContext })
    }

    @Test
    fun buildMetaLines_excludes_currency_line_when_null_even_if_remote() {
        val lines = buildMetaLines(
            advisoryLevel = null,
            isRemote = true,
            currencyText = null,
            languageText = "日本語 · Japanese",
        )
        assertFalse(lines.any { it is MetaLine.CurrencyContext })
        assertTrue(lines.any { it is MetaLine.LanguageContext })
    }

    @Test
    fun currencyContext_and_languageContext_are_not_fixed() {
        val currency = MetaLine.CurrencyContext("¥ · ~149 JPY/USD")
        val language = MetaLine.LanguageContext("日本語 · Japanese")
        assertFalse(currency.isFixed())
        assertFalse(language.isFixed())
    }

    @Test
    fun currencyContext_and_languageContext_have_priority_2() {
        val currency = MetaLine.CurrencyContext("¥ · ~149 JPY/USD")
        val language = MetaLine.LanguageContext("日本語 · Japanese")
        assertTrue(currency.priority == 2)
        assertTrue(language.priority == 2)
    }

    @Test
    fun buildMetaLines_resident_mode_adds_ResidentHeadline_lines() {
        val residentData = ResidentData(
            areaName = "Lisbon",
            categories = listOf(
                ResidentCategory("D1", "Rental Prices", "\uD83C\uDFE0", listOf(
                    ResidentDataPoint("~\u20AC800/mo", "1-bed center", DataConfidence.MEDIUM, DataClassification.DYNAMIC, "src"),
                )),
                ResidentCategory("D3", "Cost of Living", "\uD83D\uDCCA", listOf(
                    ResidentDataPoint("62/100", "Moderate", DataConfidence.MEDIUM, DataClassification.DYNAMIC, "src"),
                )),
                ResidentCategory("D4", "Safety", "\uD83D\uDEE1\uFE0F", listOf(
                    ResidentDataPoint("Above avg", "Safe area", DataConfidence.MEDIUM, DataClassification.DYNAMIC, "src"),
                )),
            ),
            originContext = null,
            fetchedAt = 1000L,
        )
        val lines = buildMetaLines(
            advisoryLevel = null,
            discoveryMode = DiscoveryMode.RESIDENT,
            residentData = residentData,
        )
        val residentHeadlines = lines.filterIsInstance<MetaLine.ResidentHeadline>()
        assertTrue(residentHeadlines.size == 3, "Expected 3 ResidentHeadline lines, got ${residentHeadlines.size}")
        assertTrue(residentHeadlines.all { it.priority == 2 })
    }

    @Test
    fun buildMetaLines_traveler_mode_no_ResidentHeadline_regression() {
        val lines = buildMetaLines(
            advisoryLevel = null,
            discoveryMode = DiscoveryMode.TRAVELER,
            residentData = null,
        )
        assertFalse(lines.any { it is MetaLine.ResidentHeadline })
    }

    @Test
    fun buildMetaLines_resident_mode_replaces_currency_language() {
        val residentData = ResidentData(
            areaName = "Lisbon",
            categories = listOf(
                ResidentCategory("D1", "Rental Prices", "\uD83C\uDFE0", listOf(
                    ResidentDataPoint("~\u20AC800/mo", "1-bed center", DataConfidence.MEDIUM, DataClassification.DYNAMIC, "src"),
                )),
            ),
            originContext = null,
            fetchedAt = 1000L,
        )
        val lines = buildMetaLines(
            advisoryLevel = null,
            isRemote = true,
            currencyText = "\u00A5 \u00B7 ~149 JPY/USD",
            languageText = "Japanese",
            discoveryMode = DiscoveryMode.RESIDENT,
            residentData = residentData,
        )
        // In resident mode, currency/language lines should NOT appear
        assertFalse(lines.any { it is MetaLine.CurrencyContext })
        assertFalse(lines.any { it is MetaLine.LanguageContext })
    }

    @Test
    fun residentHeadline_text_extension_returns_text() {
        val headline = MetaLine.ResidentHeadline("Test headline")
        assertTrue(headline.text == "Test headline")
    }

    @Test
    fun buildMetaLines_resident_mode_safety_warning_rotates_instead_of_fixed() {
        val residentData = ResidentData(
            areaName = "Uvita",
            categories = listOf(
                ResidentCategory("D1", "Rental Prices", "\uD83C\uDFE0", listOf(
                    ResidentDataPoint("~$900/mo", "1-bed", DataConfidence.MEDIUM, DataClassification.DYNAMIC, "src"),
                )),
                ResidentCategory("D4", "Safety", "\uD83D\uDEE1\uFE0F", listOf(
                    ResidentDataPoint("Generally Safe", "", DataConfidence.MEDIUM, DataClassification.DYNAMIC, "src"),
                )),
            ),
            originContext = null,
            fetchedAt = 1000L,
        )
        val lines = buildMetaLines(
            advisoryLevel = AdvisoryLevel.CAUTION,
            discoveryMode = DiscoveryMode.RESIDENT,
            residentData = residentData,
        )
        // Safety warning should be a ResidentHeadline (rotates), NOT a SafetyWarning (fixed)
        assertFalse(lines.any { it is MetaLine.SafetyWarning }, "SafetyWarning should not be present in resident mode")
        assertTrue(lines.any { it is MetaLine.ResidentHeadline && it.text == "\u26A0\uFE0F Exercise caution \u00b7 Check advisory" },
            "Safety warning should appear as a rotating ResidentHeadline with exact warning text")
        // Should also have the rent and safety headlines
        assertTrue(lines.any { it is MetaLine.ResidentHeadline && it.text.contains("$900") })
        assertTrue(lines.any { it is MetaLine.ResidentHeadline && it.text.contains("Generally Safe") })
    }

    @Test
    fun buildMetaLines_traveler_mode_safety_warning_stays_fixed() {
        val lines = buildMetaLines(
            advisoryLevel = AdvisoryLevel.CAUTION,
            discoveryMode = DiscoveryMode.TRAVELER,
            residentData = null,
        )
        assertTrue(lines.any { it is MetaLine.SafetyWarning }, "SafetyWarning should be present in traveler mode")
        assertTrue(lines.first { it is MetaLine.SafetyWarning }.isFixed())
    }

    @Test
    fun buildMetaLines_resident_mode_without_data_keeps_safety_fixed() {
        // Resident mode toggled but data hasn't loaded yet — safety should stay fixed
        val lines = buildMetaLines(
            advisoryLevel = AdvisoryLevel.DO_NOT_TRAVEL,
            discoveryMode = DiscoveryMode.RESIDENT,
            residentData = null,
        )
        assertTrue(lines.any { it is MetaLine.SafetyWarning }, "SafetyWarning should be fixed when resident data is null")
    }
}

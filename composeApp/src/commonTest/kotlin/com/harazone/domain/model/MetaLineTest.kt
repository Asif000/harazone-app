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
}

package com.harazone.data.remote

import kotlin.test.Test
import kotlin.test.assertEquals

class FcdoCountrySlugsTest {

    @Test
    fun getSlug_AE_returns_united_arab_emirates() {
        assertEquals("united-arab-emirates", FcdoCountrySlugs.getSlug("AE", "United Arab Emirates"))
    }

    @Test
    fun getSlug_JP_returns_japan() {
        assertEquals("japan", FcdoCountrySlugs.getSlug("JP", "Japan"))
    }

    @Test
    fun getSlug_BA_returns_bosnia_and_herzegovina() {
        assertEquals("bosnia-and-herzegovina", FcdoCountrySlugs.getSlug("BA", "Bosnia and Herzegovina"))
    }

    @Test
    fun getSlug_PS_returns_occupied_palestinian_territories() {
        assertEquals("the-occupied-palestinian-territories", FcdoCountrySlugs.getSlug("PS", "Palestine"))
    }

    @Test
    fun getSlug_GB_returns_uk() {
        assertEquals("uk", FcdoCountrySlugs.getSlug("GB", "United Kingdom"))
    }

    @Test
    fun getSlug_unmapped_country_derives_from_name() {
        assertEquals("some-new-country", FcdoCountrySlugs.getSlug("ZZ", "Some New Country"))
    }

    @Test
    fun getSlug_fallback_strips_apostrophes_and_dots() {
        assertEquals("côte-divoire", FcdoCountrySlugs.getSlug("ZZ", "Côte d'Ivoire"))
    }

    @Test
    fun getSlug_fallback_strips_dots() {
        assertEquals("st-somewhere", FcdoCountrySlugs.getSlug("ZZ", "St. Somewhere"))
    }

    @Test
    fun getSlug_case_insensitive_code() {
        assertEquals("japan", FcdoCountrySlugs.getSlug("jp", "Japan"))
        assertEquals("japan", FcdoCountrySlugs.getSlug("Jp", "Japan"))
    }
}

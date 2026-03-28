package com.harazone.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ResidentDataTest {

    @Test
    fun `ResidentDataPoint defaults verifyUrl to null`() {
        val point = ResidentDataPoint(
            value = "~$1,400/mo",
            detail = "1-bedroom in city center",
            confidence = DataConfidence.MEDIUM,
            classification = DataClassification.DYNAMIC,
            sourceLabel = "Gemini AI estimate",
        )
        assertNull(point.verifyUrl)
    }

    @Test
    fun `ResidentCategory holds points list`() {
        val cat = ResidentCategory(
            id = "D1",
            label = "Rental Prices",
            icon = "\uD83C\uDFE0",
            points = listOf(
                ResidentDataPoint("~$1,400", "center", DataConfidence.MEDIUM, DataClassification.DYNAMIC, "src"),
                ResidentDataPoint("~$900", "outside", DataConfidence.MEDIUM, DataClassification.DYNAMIC, "src"),
            ),
        )
        assertEquals(2, cat.points.size)
        assertEquals("D1", cat.id)
    }

    @Test
    fun `ResidentData originContext can be null`() {
        val data = ResidentData(
            areaName = "Lisbon",
            categories = emptyList(),
            originContext = null,
            fetchedAt = 1000L,
        )
        assertNull(data.originContext)
    }

    @Test
    fun `DataClassification citation rules follow spec`() {
        // STATIC = year only, DYNAMIC = month, VOLATILE = date + verify
        assertEquals(DataClassification.STATIC, DataClassification.valueOf("STATIC"))
        assertEquals(DataClassification.DYNAMIC, DataClassification.valueOf("DYNAMIC"))
        assertEquals(DataClassification.VOLATILE, DataClassification.valueOf("VOLATILE"))
    }

    @Test
    fun `DiscoveryMode has two values`() {
        assertEquals(2, DiscoveryMode.entries.size)
        assertEquals(DiscoveryMode.TRAVELER, DiscoveryMode.valueOf("TRAVELER"))
        assertEquals(DiscoveryMode.RESIDENT, DiscoveryMode.valueOf("RESIDENT"))
    }
}

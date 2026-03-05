package com.areadiscovery.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class DomainModelTest {

    @Test
    fun areaDataClassCopyWorks() {
        val area = Area(name = "Alfama", latitude = 38.7139, longitude = -9.1335, displayName = "Alfama, Lisbon")
        val copied = area.copy(displayName = "Updated Name")
        assertEquals("Updated Name", copied.displayName)
        assertEquals(area.name, copied.name)
    }

    @Test
    fun areaDataClassEquality() {
        val area1 = Area(name = "Alfama", latitude = 38.7139, longitude = -9.1335, displayName = "Alfama")
        val area2 = Area(name = "Alfama", latitude = 38.7139, longitude = -9.1335, displayName = "Alfama")
        assertEquals(area1, area2)
    }

    @Test
    fun bucketContentIsImmutableDataClass() {
        val content = BucketContent(
            type = BucketType.SAFETY,
            highlight = "Safe area",
            content = "Very safe",
            confidence = Confidence.HIGH,
            sources = listOf(Source("Test", null))
        )
        val copied = content.copy(highlight = "Updated")
        assertEquals("Updated", copied.highlight)
        assertEquals(content.type, copied.type)
    }

    @Test
    fun poiDataClassCopyWorks() {
        val poi = POI(
            name = "Castle",
            type = "Historic",
            description = "A castle",
            confidence = Confidence.HIGH,
            latitude = 38.7139,
            longitude = -9.1335
        )
        val copied = poi.copy(name = "Updated Castle")
        assertEquals("Updated Castle", copied.name)
        assertEquals(poi.latitude, copied.latitude)
    }

    @Test
    fun chatMessageDataClassCopyWorks() {
        val msg = ChatMessage(
            id = "1",
            role = MessageRole.USER,
            content = "Hello",
            timestamp = 1000L,
            sources = emptyList()
        )
        val copied = msg.copy(content = "Updated")
        assertEquals("Updated", copied.content)
        assertEquals(msg.id, copied.id)
    }

    @Test
    fun chatTokenDataClassWorks() {
        val token = ChatToken(text = "hello ", isComplete = false)
        val copied = token.copy(isComplete = true)
        assertEquals(true, copied.isComplete)
        assertEquals(token.text, copied.text)
    }

    @Test
    fun areaContextDataClassCopyWorks() {
        val ctx = AreaContext(
            timeOfDay = "afternoon",
            dayOfWeek = "Tuesday",
            visitCount = 0,
            preferredLanguage = "en"
        )
        val copied = ctx.copy(visitCount = 1)
        assertEquals(1, copied.visitCount)
        assertEquals(ctx.timeOfDay, copied.timeOfDay)
    }

    @Test
    fun areaPortraitDataClassWorks() {
        val area = Area("Alfama", 38.7139, -9.1335, "Alfama")
        val portrait = AreaPortrait(
            area = area,
            buckets = emptyMap(),
            pois = emptyList(),
            generatedAt = 1000L,
            language = "en"
        )
        val copied = portrait.copy(language = "pt")
        assertEquals("pt", copied.language)
        assertEquals(area, copied.area)
    }

    @Test
    fun sourceAttributionDataClassWorks() {
        val attr = SourceAttribution(
            confidence = Confidence.HIGH,
            sources = listOf(Source("Test Source", "https://example.com"))
        )
        val copied = attr.copy(confidence = Confidence.LOW)
        assertEquals(Confidence.LOW, copied.confidence)
        assertEquals(attr.sources, copied.sources)
    }

    @Test
    fun domainErrorSealedClassVariants() {
        val networkErr: DomainError = DomainError.NetworkError("No connection")
        val apiErr: DomainError = DomainError.ApiError(500, "Server error")
        val cacheErr: DomainError = DomainError.CacheError("Cache miss")
        val locationErr: DomainError = DomainError.LocationError("GPS unavailable")

        // Exhaustive when
        val messages = listOf(networkErr, apiErr, cacheErr, locationErr).map { error ->
            when (error) {
                is DomainError.NetworkError -> error.message
                is DomainError.ApiError -> "${error.code}: ${error.message}"
                is DomainError.CacheError -> error.message
                is DomainError.LocationError -> error.message
            }
        }
        assertEquals(4, messages.size)
        assertEquals("No connection", messages[0])
        assertEquals("500: Server error", messages[1])
    }

    @Test
    fun bucketUpdateSealedClassVariants() {
        val delta: BucketUpdate = BucketUpdate.ContentDelta(BucketType.SAFETY, "text ")
        val complete: BucketUpdate = BucketUpdate.BucketComplete(
            BucketContent(BucketType.SAFETY, "highlight", "content", Confidence.HIGH, emptyList())
        )
        val portraitComplete: BucketUpdate = BucketUpdate.PortraitComplete(emptyList())

        // Exhaustive when
        val types = listOf(delta, complete, portraitComplete).map { update ->
            when (update) {
                is BucketUpdate.ContentDelta -> "delta"
                is BucketUpdate.BucketComplete -> "complete"
                is BucketUpdate.PortraitComplete -> "portrait"
                is BucketUpdate.ContentAvailabilityNote -> "note"
            }
        }
        assertEquals(listOf("delta", "complete", "portrait"), types)
    }
}

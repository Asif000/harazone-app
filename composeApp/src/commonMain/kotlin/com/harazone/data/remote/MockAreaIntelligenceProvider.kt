package com.harazone.data.remote

import com.harazone.domain.model.AreaContext
import com.harazone.domain.model.BucketContent
import com.harazone.domain.model.BucketType
import com.harazone.domain.model.BucketUpdate
import com.harazone.domain.model.ChatMessage
import com.harazone.domain.model.ChatToken
import com.harazone.domain.model.Confidence
import com.harazone.domain.model.EngagementLevel
import com.harazone.domain.model.GeoArea
import com.harazone.domain.model.ProfileIdentity
import com.harazone.domain.model.SavedPoi
import com.harazone.domain.model.Source
import com.harazone.domain.model.TasteProfile
import com.harazone.domain.model.VibeInsight
import com.harazone.domain.provider.AreaIntelligenceProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class MockAreaIntelligenceProvider : AreaIntelligenceProvider {

    override fun streamAreaPortrait(areaName: String, context: AreaContext): Flow<BucketUpdate> = flow {
        for (bucket in mockBuckets) {
            val words = bucket.content.split(" ")
            for (word in words) {
                emit(BucketUpdate.ContentDelta(bucket.type, "$word "))
                delay(200)
            }
            emit(BucketUpdate.BucketComplete(bucket))
        }
        emit(BucketUpdate.PortraitComplete(mockPOIs))
    }

    override fun streamChatResponse(
        query: String,
        areaName: String,
        conversationHistory: List<ChatMessage>
    ): Flow<ChatToken> = flow {
        val response = "$areaName is a vibrant historic neighbourhood. Locals recommend visiting " +
            "in the late afternoon when the light hits the castle walls. Tram 28 is the easiest " +
            "way to reach the upper streets."
        val words = response.split(" ")
        for ((index, word) in words.withIndex()) {
            emit(ChatToken(text = "$word ", isComplete = false))
            delay(80)
        }
        emit(ChatToken(text = "", isComplete = true))
    }

    override suspend fun generatePoiContext(
        poiName: String,
        poiType: String,
        areaName: String,
        timeHint: String,
        languageTag: String,
    ): Triple<String, String, String> = Triple(
        "Mock context blurb for $poiName in $areaName.",
        "Great time to visit.",
        "",
    )

    override suspend fun generateProfileIdentity(
        savedPois: List<SavedPoi>,
        tasteProfile: TasteProfile,
        engagementLevel: EngagementLevel,
        languageTag: String,
    ): ProfileIdentity = ProfileIdentity(
        explorerName = "Urban Wanderer",
        tagline = "Finds magic in the mundane",
        avatarEmoji = "🧭",
        totalVisits = savedPois.size,
        totalAreas = savedPois.map { it.areaName }.distinct().size,
        totalVibes = savedPois.map { it.vibe }.distinct().filter { it.isNotBlank() }.size,
        geoFootprint = savedPois.groupBy { it.areaName }.keys.map { GeoArea(areaName = it, countryCode = "XX") },
        vibeInsights = listOf(VibeInsight(vibeName = "culture", insight = "You're drawn to places with stories.")),
    )

    override fun streamProfileChat(
        query: String,
        savedPois: List<SavedPoi>,
        tasteProfile: TasteProfile,
        conversationHistory: List<ChatMessage>,
        languageTag: String,
    ): Flow<ChatToken> = flow {
        emit(ChatToken(text = "Mock profile chat response for: $query", isComplete = false))
        emit(ChatToken(text = "", isComplete = true))
    }

    companion object {
        val mockPOIs = listOf(
            com.harazone.domain.model.POI(
                name = "Castelo de Sao Jorge",
                type = "historic",
                description = "Hilltop medieval castle with panoramic views over Lisbon and the Tagus River. Originally built by the Moors in the 11th century.",
                confidence = Confidence.HIGH,
                latitude = 38.7139,
                longitude = -9.1335,
                vibe = "history",
                insight = "The castle survived the 1755 earthquake that destroyed most of Lisbon",
                hours = "9am-9pm daily",
                liveStatus = "open",
                rating = 4.6f,
                vibeInsights = mapOf(
                    "history" to "Moorish fortress dating to the 11th century",
                    "character" to "Crown jewel of Alfama's skyline",
                    "cost" to "Entry 10 EUR, free on Sundays before 2pm",
                ),
            ),
            com.harazone.domain.model.POI(
                name = "Feira da Ladra",
                type = "shopping",
                description = "Lisbon's oldest open-air flea market, held every Tuesday and Saturday at Campo de Santa Clara since the 12th century.",
                confidence = Confidence.MEDIUM,
                latitude = 38.7148,
                longitude = -9.1244,
                vibe = "character",
                insight = "One of Europe's oldest continuous flea markets",
                hours = "Tue & Sat 6am-5pm",
                liveStatus = "busy",
                rating = 4.1f,
                vibeInsights = mapOf(
                    "character" to "A chaotic treasure hunt beloved by locals",
                    "cost" to "Free entry, bargaining expected",
                    "history" to "Operating since the 12th century",
                ),
            ),
            com.harazone.domain.model.POI(
                name = "Museu do Fado",
                type = "arts",
                description = "Museum dedicated to Fado music, UNESCO's Intangible Cultural Heritage. Houses instruments, costumes, and interactive exhibits.",
                confidence = Confidence.HIGH,
                latitude = 38.7108,
                longitude = -9.1283,
                vibe = "character",
                insight = "The definitive museum for Portugal's soul music",
                hours = "10am-6pm, closed Mondays",
                liveStatus = "open",
                rating = 4.4f,
                vibeInsights = mapOf(
                    "character" to "Fado is the emotional DNA of Alfama",
                    "history" to "Fado recognized by UNESCO in 2011",
                    "cost" to "5 EUR entry, audio guide included",
                ),
            ),
            com.harazone.domain.model.POI(
                name = "Fado Show at Clube de Fado",
                type = "entertainment",
                description = "Intimate live Fado performances in a restored 14th-century building. One of Alfama's most acclaimed Fado houses.",
                confidence = Confidence.HIGH,
                latitude = 38.7102,
                longitude = -9.1305,
                vibe = "whats_on",
                insight = "Nightly shows start at 9pm with dinner service",
                hours = "7pm-2am daily",
                liveStatus = "open",
                rating = 4.7f,
                vibeInsights = mapOf(
                    "whats_on" to "Live Fado every night from 9pm",
                    "character" to "Authentic atmosphere in a medieval setting",
                    "cost" to "25 EUR minimum, dinner from 35 EUR",
                ),
            ),
            com.harazone.domain.model.POI(
                name = "Police Station Alfama",
                type = "safety",
                description = "Local PSP police station serving the Alfama district. Open 24/7 for emergencies and tourist assistance.",
                confidence = Confidence.MEDIUM,
                latitude = 38.7125,
                longitude = -9.1310,
                vibe = "safety",
                insight = "Tourist police desk available in English",
                hours = "24/7",
                liveStatus = "open",
                rating = 3.8f,
                vibeInsights = mapOf(
                    "safety" to "English-speaking tourist desk available",
                    "nearby" to "Central location in Alfama",
                ),
            ),
            com.harazone.domain.model.POI(
                name = "Santa Apolonia Station",
                type = "transit",
                description = "Major railway terminus on the Tagus riverfront. Connects Alfama to northern Portugal and international routes.",
                confidence = Confidence.HIGH,
                latitude = 38.7145,
                longitude = -9.1222,
                vibe = "nearby",
                insight = "Direct trains to Porto in 2h45m",
                hours = "5am-1am daily",
                liveStatus = "open",
                rating = 3.9f,
                vibeInsights = mapOf(
                    "nearby" to "5-minute walk from Alfama center",
                    "cost" to "Porto train from 15 EUR, Sintra day trip 4.50 EUR",
                ),
            ),
            com.harazone.domain.model.POI(
                name = "Pasteis de Belem",
                type = "food",
                description = "Iconic bakery famous for its original Portuguese custard tarts, made from a secret recipe since 1837.",
                confidence = Confidence.HIGH,
                latitude = 38.6976,
                longitude = -9.2033,
                vibe = "cost",
                insight = "The original custard tart at just 1.30 EUR",
                hours = "8am-11pm daily",
                liveStatus = "busy",
                rating = 4.5f,
                vibeInsights = mapOf(
                    "cost" to "Tarts 1.30 EUR each, coffee 0.80 EUR",
                    "history" to "Secret recipe since 1837",
                    "character" to "Quintessential Lisbon experience",
                ),
            ),
        )

        val mockBuckets = listOf(
            BucketContent(
                type = BucketType.SAFETY,
                highlight = "Alfama has one of the lowest crime rates among Lisbon's historic districts",
                content = "Alfama is generally considered one of the safest neighborhoods in Lisbon for visitors. " +
                    "The tight-knit community and narrow streets create a natural sense of security. " +
                    "Petty theft can occur in crowded tourist areas, especially around the castle, " +
                    "but violent crime is exceptionally rare. The local police presence is consistent.",
                confidence = Confidence.HIGH,
                sources = listOf(
                    Source("Lisbon Safety Report 2025", "https://example.com/safety"),
                    Source("TripAdvisor Community", null)
                )
            ),
            BucketContent(
                type = BucketType.CHARACTER,
                highlight = "Fado music, UNESCO's Intangible Cultural Heritage, was born in Alfama's narrow streets",
                content = "Alfama is the soul of Lisbon — a labyrinth of cobblestone alleys draped with laundry lines " +
                    "and echoing with Fado melodies. This is where Lisbon's oldest traditions live on. " +
                    "The neighborhood retains its Moorish layout with winding streets that predate the 1755 earthquake. " +
                    "Local tascas serve authentic Portuguese cuisine alongside spontaneous Fado performances.",
                confidence = Confidence.MEDIUM,
                sources = listOf(
                    Source("UNESCO Intangible Heritage", "https://example.com/unesco"),
                    Source("Lonely Planet Lisbon Guide", "https://example.com/lp")
                )
            ),
            BucketContent(
                type = BucketType.WHATS_HAPPENING,
                highlight = "The Feira da Ladra flea market runs every Tuesday and Saturday",
                content = "This week features the regular Feira da Ladra flea market at Campo de Santa Clara. " +
                    "Several Fado houses in the area are hosting special performances for the summer season. " +
                    "The Santo Antonio festival preparations are underway, with sardine grills being set up " +
                    "along the main streets for the June celebrations.",
                confidence = Confidence.LOW,
                sources = listOf(
                    Source("Visit Lisboa Events", "https://example.com/events")
                )
            ),
            BucketContent(
                type = BucketType.COST,
                highlight = "Average meal costs 8-15 EUR, significantly below Lisbon's tourist center average",
                content = "Alfama remains one of Lisbon's more affordable neighborhoods for dining. " +
                    "A traditional meal at a local tasca costs 8-15 EUR, compared to 20-35 EUR in Chiado or Baixa. " +
                    "Street food options include bifanas (pork sandwiches) for 3-5 EUR. " +
                    "Accommodation ranges from 60-120 EUR/night for guesthouses, less than half of central hotel prices.",
                confidence = Confidence.MEDIUM,
                sources = listOf(
                    Source("Numbeo Cost of Living", "https://example.com/numbeo"),
                    Source("Local Restaurant Survey", null)
                )
            ),
            BucketContent(
                type = BucketType.HISTORY,
                highlight = "Alfama survived the 1755 earthquake that destroyed most of Lisbon — its Moorish foundations date to 711 AD",
                content = "Alfama is Lisbon's oldest district, with roots stretching back to Moorish rule beginning in 711 AD. " +
                    "The name derives from the Arabic 'Al-hamma' meaning hot fountains or baths. " +
                    "While the catastrophic 1755 earthquake and tsunami destroyed 85% of Lisbon, " +
                    "Alfama's position on solid bedrock preserved most of its medieval street plan. " +
                    "The neighborhood served as the city's most prestigious address until the 16th century.",
                confidence = Confidence.HIGH,
                sources = listOf(
                    Source("Lisbon Historical Archive", "https://example.com/history"),
                    Source("Portuguese Heritage Foundation", "https://example.com/heritage")
                )
            ),
            BucketContent(
                type = BucketType.NEARBY,
                highlight = "Castelo de Sao Jorge is a 5-minute walk uphill with panoramic city views",
                content = "Within walking distance of Alfama's center: Castelo de Sao Jorge (5 min uphill), " +
                    "the National Pantheon (8 min), Miradouro de Santa Luzia viewpoint (3 min), " +
                    "and the iconic Tram 28 route passes directly through. " +
                    "The Feira da Ladra flea market is a 10-minute walk northeast. " +
                    "The riverside area with bars and restaurants is a steep 5-minute walk downhill.",
                confidence = Confidence.HIGH,
                sources = listOf(
                    Source("Google Maps", "https://example.com/maps"),
                    Source("Alfama Walking Tour Guide", null)
                )
            )
        )
    }
}

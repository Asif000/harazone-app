package com.areadiscovery.data.remote

import com.areadiscovery.domain.model.AreaContext
import com.areadiscovery.domain.model.BucketContent
import com.areadiscovery.domain.model.BucketType
import com.areadiscovery.domain.model.BucketUpdate
import com.areadiscovery.domain.model.ChatMessage
import com.areadiscovery.domain.model.ChatToken
import com.areadiscovery.domain.model.Confidence
import com.areadiscovery.domain.model.Source
import com.areadiscovery.domain.provider.AreaIntelligenceProvider
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
        val response = "Alfama is Lisbon's oldest neighborhood, known for its narrow winding streets, " +
            "traditional Fado music houses, and stunning viewpoints overlooking the Tagus River. " +
            "The area has a rich Moorish heritage dating back to 711 AD and famously survived " +
            "the devastating 1755 earthquake that destroyed most of Lisbon."
        val words = response.split(" ")
        for ((index, word) in words.withIndex()) {
            val isLast = index == words.lastIndex
            emit(ChatToken(text = "$word ", isComplete = isLast))
            delay(100)
        }
    }

    companion object {
        val mockPOIs = listOf(
            com.areadiscovery.domain.model.POI(
                name = "Castelo de São Jorge",
                type = "Historic Castle",
                description = "Hilltop medieval castle with panoramic views over Lisbon and the Tagus River. Originally built by the Moors in the 11th century.",
                confidence = Confidence.HIGH,
                latitude = 38.7139,
                longitude = -9.1335
            ),
            com.areadiscovery.domain.model.POI(
                name = "Feira da Ladra",
                type = "Flea Market",
                description = "Lisbon's oldest open-air flea market, held every Tuesday and Saturday at Campo de Santa Clara since the 12th century.",
                confidence = Confidence.MEDIUM,
                latitude = 38.7148,
                longitude = -9.1244
            ),
            com.areadiscovery.domain.model.POI(
                name = "Museu do Fado",
                type = "Museum",
                description = "Museum dedicated to Fado music, UNESCO's Intangible Cultural Heritage. Houses instruments, costumes, and interactive exhibits.",
                confidence = Confidence.HIGH,
                latitude = 38.7108,
                longitude = -9.1283
            )
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
                    "The Santo António festival preparations are underway, with sardine grills being set up " +
                    "along the main streets for the June celebrations.",
                confidence = Confidence.LOW,
                sources = listOf(
                    Source("Visit Lisboa Events", "https://example.com/events")
                )
            ),
            BucketContent(
                type = BucketType.COST,
                highlight = "Average meal costs €8-15, significantly below Lisbon's tourist center average",
                content = "Alfama remains one of Lisbon's more affordable neighborhoods for dining. " +
                    "A traditional meal at a local tasca costs €8-15, compared to €20-35 in Chiado or Baixa. " +
                    "Street food options include bifanas (pork sandwiches) for €3-5. " +
                    "Accommodation ranges from €60-120/night for guesthouses, less than half of central hotel prices.",
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
                highlight = "Castelo de São Jorge is a 5-minute walk uphill with panoramic city views",
                content = "Within walking distance of Alfama's center: Castelo de São Jorge (5 min uphill), " +
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

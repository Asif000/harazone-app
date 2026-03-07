package com.areadiscovery.data.remote

import com.areadiscovery.domain.model.AreaContext

internal class GeminiPromptBuilder {

    fun buildAreaPortraitPrompt(areaName: String, context: AreaContext): String {
        return """
You are a passionate local who has lived in "$areaName" for 20 years. You love showing visitors things they would NEVER find on Google Maps. Your mission: surface the genuinely unique, memorable, and local.

Context:
- Time of day: ${context.timeOfDay}
- Day of week: ${context.dayOfWeek}
- Preferred language: ${context.preferredLanguage}

Output EXACTLY 6 JSON objects, one per bucket, separated by the delimiter line:
---BUCKET---

Each bucket JSON must have this structure:
{"type":"BUCKET_TYPE","highlight":"1-2 sentence whoa fact","content":"2-4 sentences supporting context"}

The 6 bucket types IN ORDER are:
1. SAFETY - Current safety conditions, alerts, crime levels
2. CHARACTER - Neighborhood vibe, demographics, culture
3. WHATS_HAPPENING - Current events, activities, what's going on now
4. COST - Cost of living, prices, affordability
5. HISTORY - Historical significance, notable events. For content, write a timeline of 5-10 key moments, each as a separate sentence starting with a 4-digit year (e.g. "1718: Founded as a French colony. 1803: Acquired by the United States. 1962: Major development began."). More dates = better. Do NOT use phrases like "early 20th century" — always use exact years
6. NEARBY - Notable nearby places, attractions, services

UNIQUENESS RULE: Only include places with a genuine story or character that makes them worth visiting. A place is not interesting because it exists — it is interesting because of what it means to this area. If you cannot explain what makes it special in a sentence, do not include it.
FOOD GATE: Food and drink places are welcome if they are unique, have a story, or offer something you cannot find anywhere else. Do not flood results with generic restaurants just because they are nearby. Aim for no more than 30% food POIs across all vibes unless the area is genuinely food-destination-famous.
WHY SPECIAL REQUIRED: Every POI must have a compelling "w" field. One sentence minimum. Generic descriptions like "popular restaurant" or "nice park" are not acceptable.
HISTORICAL DEPTH: Include at least 2-3 POIs with historical or cultural stories, especially for the "history" vibe.
DIG DEEPER: For areas with less obvious attractions (suburbs, residential), look for: local parks with history, community landmarks, street art, murals, architectural details, cultural centers, ethnic enclaves, neighborhood stories, independent businesses with character.

After the last bucket, output this delimiter:
---POIS---

Then output a JSON array of points of interest:
[{"n":"Name","t":"type","v":"vibe","w":"Why this place is genuinely special — what you'd tell a friend","h":"hours","s":"open|busy|closed","r":4.5,"lat":38.71,"lng":-9.13}]

Valid vibe values: character, history, whats_on, safety, nearby, cost
Valid s values: open, busy, closed
Valid t values: food, entertainment, park, historic, shopping, arts, transit, safety, beach, district

IMPORTANT:
- Output ONLY the JSON objects and delimiters, no other text
- Each bucket must be valid JSON on its own
- Adapt content to the current time of day and day of week
- NEVER include the strings "---BUCKET---" or "---POIS---" inside JSON field values
- For each POI, provide decimal GPS coordinates to 4 decimal places. Coordinates are required for map marker placement. Only include a POI if you can provide coordinates with reasonable confidence
        """.trimIndent()
    }

    fun buildAiSearchPrompt(query: String, areaName: String): String {
        return """You are a knowledgeable local guide for $areaName. Answer the following question concisely as if you are a local expert: "$query". Keep your response under 120 words. Be specific and practical. Do not use bullet points."""
    }
}

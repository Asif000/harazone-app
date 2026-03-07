package com.areadiscovery.data.remote

import com.areadiscovery.domain.model.AreaContext

internal class GeminiPromptBuilder {

    fun buildAreaPortraitPrompt(areaName: String, context: AreaContext): String {
        return """
You are an expert local area intelligence assistant. Generate a comprehensive area portrait for "$areaName".

Context:
- Time of day: ${context.timeOfDay}
- Day of week: ${context.dayOfWeek}
- Preferred language: ${context.preferredLanguage}

Output EXACTLY 6 JSON objects, one per bucket, separated by the delimiter line:
---BUCKET---

Each bucket JSON must have this structure:
{"type":"BUCKET_TYPE","highlight":"1-2 sentence whoa fact","content":"2-4 sentences of supporting context","confidence":"HIGH|MEDIUM|LOW","sources":[{"title":"source name","url":"https://..."}]}

The 6 bucket types IN ORDER are:
1. SAFETY - Current safety conditions, alerts, crime levels
2. CHARACTER - Neighborhood vibe, demographics, culture
3. WHATS_HAPPENING - Current events, activities, what's going on now
4. COST - Cost of living, prices, affordability
5. HISTORY - Historical significance, notable events. For content, write a timeline of 5-10 key moments, each as a separate sentence starting with a 4-digit year (e.g. "1718: Founded as a French colony. 1803: Acquired by the United States. 1962: Major development began."). More dates = better. Do NOT use phrases like "early 20th century" — always use exact years
6. NEARBY - Notable nearby places, attractions, services

After the last bucket, output this delimiter:
---POIS---

Then output a JSON array of points of interest:
[{"poi":"Time Out Market","type":"food","vibe":"character","insight":"Curated food hall with 24 restaurants","hours":"Sun-Wed 10am-12am, Thu-Sat 10am-2am","liveStatus":"open","confidence":"HIGH","rating":4.5,"latitude":38.71,"longitude":-9.13,"vibeInsights":{"character":"A gathering hub for locals","history":"Converted 1892 iron market hall","cost":"Mid-range, ${'$'}10-25"}}]

Valid vibe values: character, history, whats_on, safety, nearby, cost
Valid liveStatus values: open, busy, closed
Valid type values: food, entertainment, park, historic, shopping, arts, transit, safety, beach, district

IMPORTANT:
- Output ONLY the JSON objects and delimiters, no other text
- Each bucket must be valid JSON on its own
- Adapt content to the current time of day and day of week
- Provide honest confidence levels based on data availability
- Include real, verifiable sources where possible
- NEVER include the strings "---BUCKET---" or "---POIS---" inside JSON field values
- For each POI, provide decimal GPS coordinates to 4 decimal places. Coordinates are required for map marker placement. Only include a POI if you can provide coordinates with reasonable confidence
        """.trimIndent()
    }

    fun buildAiSearchPrompt(query: String, areaName: String): String {
        return """You are a knowledgeable local guide for $areaName. Answer the following question concisely as if you are a local expert: "$query". Keep your response under 120 words. Be specific and practical. Do not use bullet points."""
    }
}

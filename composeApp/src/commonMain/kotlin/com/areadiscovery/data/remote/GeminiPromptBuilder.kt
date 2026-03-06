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

Then output a JSON array of notable points of interest:
[{"name":"POI name","type":"category","description":"brief description","confidence":"HIGH|MEDIUM|LOW","latitude":51.5074,"longitude":-0.1278}]

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
}

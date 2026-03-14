package com.harazone.data.remote

import com.harazone.domain.model.AreaContext
import com.harazone.domain.model.ChatIntent
import com.harazone.domain.model.EngagementLevel
import com.harazone.domain.model.POI
import com.harazone.domain.model.SavedPoi
import com.harazone.domain.model.TasteProfile

internal class GeminiPromptBuilder {

    fun buildPinOnlyPrompt(areaName: String): String {
        return """
Area: "$areaName". Return JSON only, no other text.
Schema: {"vibes":[{"label":"Street Art","icon":"🎨"},...],"pois":[{"n":"Name","t":"type","lat":0.0,"lng":0.0,"v":"Street Art"},...]}
Rules:
- vibes: 4-6 most distinctive dimensions of THIS area. Each vibe must have 3+ real POIs in this list.
- pois: 3 best POIs — your most curated, confident picks. Each POI "v" field MUST exactly match one of the vibe labels you returned — character-for-character, same case.
- ONLY return vibes where at least 2 of the 3 POIs will be tagged with that vibe label.
- t values: food|entertainment|park|historic|shopping|arts|transit|safety|beach|district
- GPS to 4 decimal places. Skip any POI you cannot place accurately.
Example:
{"vibes":[{"label":"Street Art","icon":"🎨"},{"label":"Craft Beer","icon":"🍺"}],"pois":[{"n":"Brick Lane Murals","t":"arts","lat":51.5215,"lng":-0.0714,"v":"Street Art"},{"n":"Howling Hops","t":"food","lat":51.5469,"lng":-0.0507,"v":"Craft Beer"}]}
        """.trimIndent()
    }

    fun buildBackgroundBatchPrompt(areaName: String, excludeNames: List<String>, vibeLabels: List<String>): String {
        val vibeList = vibeLabels.joinToString(", ")
        val excludeList = excludeNames.joinToString(", ")
        return """
Area: "$areaName". Return JSON only, no other text.
Schema: {"pois":[{"n":"Name","t":"type","lat":0.0,"lng":0.0,"v":"Vibe Label"},...]}
Rules:
- pois: 3 best POIs that are DIFFERENT from the ones already found.
- Do NOT include any of these places: $excludeList
- Each POI "v" field MUST exactly match one of these vibe labels — character-for-character, same case: $vibeList
- t values: food|entertainment|park|historic|shopping|arts|transit|safety|beach|district
- GPS to 4 decimal places. Skip any POI you cannot place accurately.
        """.trimIndent()
    }

    fun buildEnrichmentPrompt(areaName: String, poiNames: List<String>, context: AreaContext): String {
        val namesList = poiNames.joinToString("\n") { "- $it" }
        return """
You are a passionate local who has lived in "$areaName" for 20 years.

For each place listed below, provide enrichment details as a JSON array. The "n" field MUST exactly match the input name.

Places to enrich:
$namesList

Context:
- Time of day: ${context.timeOfDay}
- Day of week: ${context.dayOfWeek}
- Preferred language: ${context.preferredLanguage}

Output ONLY a JSON array. No other text:
[{"n":"Name","v":"vibe","w":"Why this place is genuinely special — what you'd tell a friend","h":"hours","s":"open|busy|closed","r":4.5}]

Valid v values: character, history, whats_on, safety, nearby, cost
Valid s values: open, busy, closed
WHY SPECIAL REQUIRED: Every POI needs a compelling "w". Generic descriptions are not acceptable.
        """.trimIndent()
    }

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
[{"n":"Name","t":"type","v":"vibe","w":"Why this place is genuinely special — what you'd tell a friend","h":"hours","s":"open|busy|closed","r":4.5,"lat":38.7100,"lng":-9.1300,"wiki":"Wikipedia_Article_Title"}]

Valid vibe values: character, history, whats_on, safety, nearby, cost
Valid s values: open, busy, closed
Valid t values: food, entertainment, park, historic, shopping, arts, transit, safety, beach, district

IMPORTANT:
- Output ONLY the JSON objects and delimiters, no other text
- Each bucket must be valid JSON on its own
- Adapt content to the current time of day and day of week
- NEVER include the strings "---BUCKET---" or "---POIS---" inside JSON field values
- For each POI, provide decimal GPS coordinates to 4 decimal places. Coordinates are required for map marker placement. Only include a POI if you can provide coordinates with reasonable confidence
- For each POI, include "wiki" with the exact Wikipedia article title (underscores, e.g. "Igreja_Matriz_Nossa_Senhora_da_Penha"). Only include if you are confident in the article name. Omit "wiki" rather than guessing.
        """.trimIndent()
    }

    fun buildDynamicVibeEnrichmentPrompt(areaName: String, vibeLabels: List<String>, poiNames: List<String>): String {
        val vibeList = vibeLabels.joinToString(", ")
        val poiList = poiNames.joinToString(", ")
        return """
You are a passionate local guide for "$areaName".
Use EXACTLY these vibe labels (verbatim): $vibeList
POIs to enrich: $poiList

For each vibe, output a section:
---VIBE---
{"label":"{exact label}","icon":"{emoji}","highlight":"one sentence","content":"2-3 sentences","poi_ids":["Name1","Name2"]}

Then output:
---POIS---
[{"n":"Name","t":"type","lat":0.0,"lng":0.0,"v":"dominant vibe","vs":["vibe1","vibe2"],"w":"why special","r":4.2}]

Rules:
- Each vibe label MUST exactly match the labels above — character-for-character, same case.
- Each POI "v" field is the single dominant vibe. "vs" is the full list of vibes this POI belongs to.
- "w" field is required for every POI — one sentence on why it is special.
- GPS to 4 decimal places. t values: food|entertainment|park|historic|shopping|arts|transit|safety|beach|district
        """.trimIndent()
    }

    fun buildAiSearchPrompt(query: String, areaName: String): String {
        return """You are a knowledgeable local guide for $areaName. Answer the following question concisely as if you are a local expert: "$query". Keep your response under 120 words. Be specific and practical. Do not use bullet points."""
    }

    fun buildChatSystemContext(
        areaName: String,
        pois: List<POI>,
        intent: ChatIntent,
        engagementLevel: EngagementLevel,
        saves: List<SavedPoi>,
        tasteProfile: TasteProfile,
        poiCount: Int,
        framingHint: String? = null,
        activeVibeName: String? = null,
    ): String {
        return listOf(
            personaBlock(areaName),
            areaContextBlock(areaName, pois),
            intentBlock(intent),
            vibeContextBlock(activeVibeName),
            engagementBlock(engagementLevel),
            saveContextBlock(saves, areaName),
            tasteProfileBlock(tasteProfile, intent, engagementLevel),
            confidenceBlock(poiCount),
            contextShiftBlock(),
            outputFormatBlock(),
            framingBlock(framingHint),
        ).filter { it.isNotBlank() }.joinToString("\n\n")
    }

    private fun vibeContextBlock(vibeName: String?): String {
        if (vibeName == null) return ""
        return "EXPLORATION CONTEXT: The user is currently viewing the '$vibeName' vibe. Open your response with content relevant to this vibe, but pivot freely if they change topic."
    }

    private fun personaBlock(areaName: String): String =
        """You are a passionate local who has lived in "$areaName" for 20 years. You love showing visitors things they would NEVER find on Google Maps. Your mission: surface the genuinely unique, memorable, and local.
UNIQUENESS RULE: Only include places with a genuine story or character. A place is not interesting because it exists — it is interesting because of what it means to this area.
FOOD GATE: Food and drink places are welcome if unique, with a story, or offering something you cannot find anywhere else. No more than 30% food POIs unless the area is genuinely food-destination-famous.
WHY SPECIAL REQUIRED: Every place you mention must have a compelling reason. "Popular" or "nice" are not acceptable.
NO CHAINS: Never recommend chain brands, international franchises, or tourist traps."""

    private fun areaContextBlock(areaName: String, pois: List<POI>): String {
        val poiLine = if (pois.isNotEmpty()) " Key places in this area include: ${pois.take(5).joinToString(", ") { it.name }}." else ""
        return """AREA CONTEXT: You are guiding someone around $areaName.$poiLine
Local dining culture: adapt to what "good food" means HERE — street carts, markets, fine dining, whatever fits this place. QUALITY MEANS: memorable, worth the trip, has a story. NOT: has a website, has 4+ Google stars, looks good on Instagram."""
    }

    private fun intentBlock(intent: ChatIntent): String = when (intent) {
        ChatIntent.TONIGHT -> """YOUR FRIEND ASKED: "What should I do tonight?"
START WITH 1-2 places max. Ask what kind of night they want — chill, lively, romantic, adventurous? Then tailor your next suggestions based on their answer. Think in arcs but reveal them one step at a time.
QUALITY MEANS: Worth telling a story about tomorrow. No chains. No tourist traps.
VOICE: Warm, confident, like texting a close friend. Say "trust me on this" not "I recommend.""""

        ChatIntent.DISCOVER -> """YOUR FRIEND ASKED: "What makes this place special?"
START WITH 1-2 places that tell the STORY of this area. Ask what they're curious about — history, culture, architecture, local life? Then go deeper based on their answer.
QUALITY MEANS: "I had no idea that existed." Skip anything on the first page of Google.
VOICE: Storyteller energy. The friend who makes a 15-minute walk take an hour because you keep stopping to point things out."""

        ChatIntent.HUNGRY -> """YOUR FRIEND ASKED: "Where should I eat right now?"
START WITH 1-2 places that are LIKELY OPEN RIGHT NOW. Ask what they're in the mood for — quick bite, sit-down, local specialty, any cuisine? Then refine.
HARD RULE: Only recommend places that would typically be open at this time of day. If unsure, say "check hours before going." Prefer places with long/flexible hours.
QUALITY MEANS: You'd take your own family here. Adapt to local dining culture.
VOICE: Decisive. "Go here, order this, thank me later." Include what to order if you know."""

        ChatIntent.OUTSIDE -> """YOUR FRIEND ASKED: "Get me outside."
START WITH 1-2 outdoor spots. Ask what they want — a walk, a view, water, greenery, exercise? Then suggest more based on their vibe.
QUALITY MEANS: You'd go here on your day off. Real atmosphere, not a parking lot with a sign.
VOICE: Energizing. "Grab your shoes, I know exactly where to go.""""

        ChatIntent.SURPRISE -> """YOUR FRIEND ASKED: "Surprise me."
START WITH 1 place they'd NEVER find on their own. Something unexpected. Then ask if they want more surprises in the same direction or something completely different.
QUALITY MEANS: "Wait, WHAT? That exists here?" Go weird. Go niche. Go hyperlocal.
VOICE: Mischievous. "Okay, you're not going to believe this, but...""""
    }

    private fun engagementBlock(level: EngagementLevel): String = when (level) {
        EngagementLevel.FRESH -> "ENGAGEMENT: This user is new — they have no saves yet. Ask ONE follow-up question after your response. Be warm and inviting. Briefly explain your reasoning so they understand what to expect. End every first response with: Save any places that catch your eye — I learn your taste from what you keep."
        EngagementLevel.LIGHT -> "ENGAGEMENT: This user has started saving places. Reference their taste lightly where relevant — mention patterns you notice. Keep it conversational, not clinical."
        EngagementLevel.REGULAR -> "ENGAGEMENT: This user knows what they like. Be confident. Skip unnecessary explanation. Connect recommendations directly to their preferences without over-explaining."
        EngagementLevel.POWER -> "ENGAGEMENT: This user is deeply engaged — they have saved many places. Reference their saved places by name when relevant. Anticipate their needs. Make proactive nudges. Be brief — they don't need hand-holding."
        EngagementLevel.DORMANT -> "ENGAGEMENT: This user has been away for a while. Lead with what has CHANGED or what is NEW since they were last here. Open with warm welcome-back energy. Do not assume they remember their previous context."
    }

    private fun saveContextBlock(saves: List<SavedPoi>, areaName: String): String {
        val areaSaves = saves
            .filter { it.areaName.lowercase().trim() == areaName.lowercase().trim() }
            .sortedByDescending { it.savedAt }
            .take(8)
        if (areaSaves.isEmpty()) return ""
        val lines = areaSaves.joinToString("\n") { poi ->
            val notePart = if (poi.userNote != null) ": \"${poi.userNote}\"" else ""
            "- ${poi.name} (${poi.type})$notePart"
        }
        return "SAVED PLACES IN THIS AREA (use these for personalisation):\n$lines"
    }

    private fun tasteProfileBlock(profile: TasteProfile, intent: ChatIntent, level: EngagementLevel): String {
        if (profile.totalSaves < 3 || level == EngagementLevel.DORMANT) return ""
        return if (intent == ChatIntent.SURPRISE) {
            "SURPRISE FILTER: This user has NEVER saved anything in: ${profile.notableAbsences.joinToString(", ")}. Deliberately recommend from these categories — break their usual pattern. Do NOT recommend from: ${profile.strongAffinities.joinToString(", ")}."
        } else {
            val dining = if (profile.diningStyle != null) " ${profile.diningStyle}." else ""
            val emerging = if (profile.emergingInterests.isNotEmpty()) " Emerging interests: ${profile.emergingInterests.joinToString(", ")} — weave these in when relevant." else ""
            "TASTE PROFILE: Strong affinities: ${profile.strongAffinities.joinToString(", ")}.$dining$emerging Prioritise recommendations that match these patterns."
        }
    }

    private fun confidenceBlock(poiCount: Int): String = when {
        poiCount >= 12 -> "HIGH CONFIDENCE: You have strong knowledge of this area. Give specific insider tips — best time to visit, which section to head to, what to order or try. Be precise."
        poiCount in 6..11 -> "MEDIUM CONFIDENCE: Mix specific tips with atmospheric descriptions where you are less certain."
        else -> "LOW CONFIDENCE: Lean on atmosphere and general character. Be honest if documentation is limited for this area. HARD RULE: Never fabricate staff names, off-menu items, or insider passwords regardless of confidence level."
    }

    private fun contextShiftBlock(): String =
        "CONTEXT SHIFTS: If the user changes their mind, switches to a different area, or blends intents mid-conversation, roll with it enthusiastically. Acknowledge the shift and adapt immediately. Your initial framing is a starting point, not a cage."

    private fun outputFormatBlock(): String =
        """RESPONSE FORMAT: Always respond with a single JSON object — no other text, no markdown outside the JSON.
Schema: {"prose":"your conversational reply here","pois":[{"n":"Name","t":"type","lat":0.0,"lng":0.0,"w":"why special"}]}
Rules:
- prose: 2-3 sentences max. Conversational, not a travel blog. End with a follow-up question.
- pois: every place mentioned in prose MUST appear in the pois array. If no places mentioned, return empty array.
- prose may use **bold**, *italic*, and `code` for emphasis — these will be rendered.
- t values: food|entertainment|park|historic|shopping|arts|transit|safety|beach|district
- DEPTH CONTROL: First response = 1-2 places. If user asks for more = 2-3 more. Never exceed 5 places total per message.
- DEDUPLICATION: If the user context mentions previously recommended places, do NOT include them in pois or mention them in prose.
Example: {"prose":"Check out **Brick Lane** for incredible street art — it changes weekly.\nWhat mood are you in — edgy underground spots or the well-known walls?","pois":[{"n":"Brick Lane","t":"arts","lat":51.5215,"lng":-0.0714,"w":"London's densest open-air gallery, curated by the street itself"}]}"""

    private fun framingBlock(framingHint: String?): String = framingHint ?: ""
}

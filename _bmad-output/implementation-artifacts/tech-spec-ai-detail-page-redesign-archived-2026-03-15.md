---
title: 'AI Detail Page Redesign'
slug: 'ai-detail-page-redesign'
created: '2026-03-15'
status: 'ready-for-dev'
stepsCompleted: [1, 2, 3, 4]
tech_stack: ['Kotlin Multiplatform', 'Compose Multiplatform', 'Koin', 'Gemini API (Ktor SSE)']
files_to_modify:
  - composeApp/src/commonMain/kotlin/com/harazone/ui/theme/Color.kt
  - composeApp/src/commonMain/kotlin/com/harazone/ui/map/ChatUiState.kt
  - composeApp/src/commonMain/kotlin/com/harazone/ui/map/ChatOverlay.kt
  - composeApp/src/commonMain/kotlin/com/harazone/ui/map/ChatViewModel.kt
  - composeApp/src/commonMain/kotlin/com/harazone/ui/map/MapScreen.kt
  - composeApp/src/commonMain/kotlin/com/harazone/ui/map/components/AiDetailPage.kt
  - composeApp/src/commonMain/kotlin/com/harazone/domain/provider/AreaIntelligenceProvider.kt
  - composeApp/src/commonMain/kotlin/com/harazone/data/remote/GeminiPromptBuilder.kt
  - composeApp/src/commonMain/kotlin/com/harazone/data/remote/GeminiAreaIntelligenceProvider.kt
  - composeApp/src/commonMain/kotlin/com/harazone/data/remote/GeminiResponseParser.kt
  - composeApp/src/commonMain/kotlin/com/harazone/data/remote/MockAreaIntelligenceProvider.kt
code_patterns: ['Compose Multiplatform', 'MVVM + Koin', 'Gemini SSE streaming', 'DarkColorScheme override', 'PlatformBackHandler']
test_patterns: ['commonTest JUnit + MockK + Turbine', 'FakeClock', 'FakeAreaRepository pattern']
---

# Tech-Spec: AI Detail Page Redesign

**Created:** 2026-03-15
**Covers brainstorm tickets:** #47 (Nav/UI Overlap), #48 (Prompt Engineering), #49 (Dynamic Context Section), #50 (Light Theme)
**Target release:** Tester release 2026-03-21

---

## Overview

### Problem Statement

`AiDetailPage` has four deficiencies blocking tester release:
1. **Nav/UI overlap** — AmbientTicker, VibeRail, AISearchBar, MapListToggle, MyLocation, SavesNearbyPill all remain visible/tappable over the detail page, creating UI clutter.
2. **Weak prompt contract** — Chat responses sometimes omit the tapped POI from cards, allow back-to-back cards without text, and lack history/culture context. No insight/rating/priceRange/status fields on chat POI cards.
3. **No dynamic context section** — The gap between hero and chat is blank white/dark space. The AI could surface a proactive, time-aware context blurb here instead.
4. **Dark theme throughout** — Hero image looks good dark, but the content area below (name, chat) is jarring dark. Prototypes confirm a warm off-white (#F5F2EF) reads better for text-heavy chat content.

### Solution

Four targeted changes shipped together:
1. Add `state.selectedPoi == null` guards to all bottom-layer UI elements so they disappear when the detail page opens.
2. Extend `outputFormatBlock()` with richer schema + history sentence + POI guarantee + anti-clustering contract.
3. Add `PoiContextBlock` composable between hero and chat; back it with a non-streaming Gemini call that returns `contextBlurb / whyNow / localTip`. Show shimmer while loading, non-blocking (chat is usable immediately).
4. Flip detail page below-hero to `DetailPageLight` (`#F5F2EF`). Hero section stays dark. Chat bubbles get white AI / indigo user on light background.

### Scope

**In Scope:**
- Hide bottom UI elements (ticker, carousel already done, vibe rail, search bar, map/list toggle, my-location, saves-nearby pill) when `selectedPoi != null`
- Extended chat prompt schema: `insight`, `rating`, `priceRange`, `status`, `hours` on pois array; history sentence; POI guarantee; anti-clustering
- `PoiContextBlock` composable with shimmer → content transition; `buildPoiContextPrompt()` + `generatePoiContext()` + `parsePoiContextResponse()`; time-aware contextBlurb/whyNow/localTip; field-level fallbacks
- Light theme for AiDetailPage content area; `ChatBubbleItem` gains `lightMode` param; `DetailPageLight` color constant; hero section stays dark (keep `MaterialTheme(colorScheme = DarkColorScheme)` on `PoiDetailHeader` only)
- `ChatInputBar` placeholder param for contextual hint text
- Unit tests: 10+ covering prompt schema, parser, VM context load, MapScreen visibility conditions

**Out of Scope (defer v1.1):**
- Context block caching / persistent asset
- Dark mode toggle for detail page (ship light-only for tester)
- Weather/season/user history in context block (only time-of-day for v1)
- Prompt regression checklist (10 POIs) — document as manual testing protocol, not automated
- Structured hours API (Gemini still guesses hours)
- Map as ambient strip layout changes beyond current padding behavior (already ~10% peek through scrim at top)

---

## Context for Development

### Codebase Patterns

- **Compose Multiplatform, single `composeApp` module.** `commonMain` for all shared UI. `androidMain`/`iosMain` for platform specifics.
- **AiDetailPage.kt** is in `commonMain/kotlin/com/harazone/ui/map/components/`. It currently wraps its entire content in `MaterialTheme(colorScheme = DarkColorScheme)`. The hero is `PoiDetailHeader` (dark hardcoded colors throughout). Chat bubbles use `AiBubbleColor = Color.White.copy(alpha = 0.07f)` from `ChatOverlay.kt` and `IndigoGradient` for user.
- **MapScreen.kt** uses a single `Box(fillMaxSize)` stack. Z-order matters: items drawn later are visually on top. The scrim + `AiDetailPage` block is drawn at z-position 10 (out of ~22). `MapListToggle`, `AISearchBar`, `MyLocation`, `SavesNearbyPill` are at z-positions 11–15 — they render ON TOP of the scrim. `AmbientTicker`, `VibeRail` are at z-positions 6–8 — they are UNDER the scrim and already visually hidden, but still tick/animate.
- **ChatUiState.kt** defines `ChatPoiCard` (serializable) and `ChatUiState`. `ChatPoiCard` uses short `@SerialName` aliases (`n`, `t`, `w`). Adding new fields with defaults won't break backward compat.
- **GeminiPromptBuilder.kt** `outputFormatBlock()` (line 282) is the only place chat response schema is specified. Changing it changes what Gemini returns for ALL chat sessions.
- **AreaIntelligenceProvider** (interface) has 2 methods: `streamAreaPortrait` and `streamChatResponse`. The new `generatePoiContext` will be a suspend function (non-streaming) since context blocks are short.
- **GeminiAreaIntelligenceProvider** already has a `buildRequestBody()` helper and `extractTextFromGenerateContent` in `GeminiResponseParser`. Use the existing non-SSE `generateContent` endpoint pattern for the context call.
- **`openChatForPoi()`** in `ChatViewModel` (line 142) calls `openChat(forceReset=true)` then `tapIntentPill(...)`. The context fetch should be launched as a sibling coroutine inside `openChatForPoi()` so chat and context load in parallel.
- **MockAreaIntelligenceProvider** (`data/remote/MockAreaIntelligenceProvider.kt`) must implement the new `generatePoiContext` method with a stub return (won't compile otherwise).
- **`ChatBubbleItem`** in `ChatOverlay.kt` (line 535) uses hardcoded `AiBubbleColor` and `IndigoGradient`. No theme-awareness. Adding `lightMode: Boolean = false` param is the cleanest change.
- **`AppClock.nowMs()`** gives epoch ms. Convert to hour-of-day using `(nowMs / 3600000 % 24).toInt()` (UTC hour) — acceptable for tester release, real TZ in v1.1.

### Files to Reference

| File | Purpose |
| ---- | ------- |
| `ui/map/components/AiDetailPage.kt` | Main file — hero, chat, layout |
| `ui/map/MapScreen.kt` | Z-order of all overlay elements |
| `ui/map/ChatUiState.kt` | `ChatPoiCard` and `ChatUiState` data classes |
| `ui/map/ChatOverlay.kt` | `ChatBubbleItem`, `ChatInputBar`, `AiBubbleColor`, `IndigoGradient` |
| `ui/map/ChatViewModel.kt` | `openChatForPoi()`, `fetchPoiContext()` logic |
| `ui/theme/Color.kt` | Add `DetailPageLight` here |
| `domain/provider/AreaIntelligenceProvider.kt` | Add `generatePoiContext` interface method |
| `data/remote/GeminiPromptBuilder.kt` | `outputFormatBlock()` (ln 282), `buildChatSystemContext()` (ln 152) |
| `data/remote/GeminiResponseParser.kt` | Add `parsePoiContextResponse()` |
| `data/remote/GeminiAreaIntelligenceProvider.kt` | Implement `generatePoiContext()` |
| `data/remote/MockAreaIntelligenceProvider.kt` | Stub `generatePoiContext()` to unblock tests |
| `_bmad-output/brainstorming/prototype-detail-page-redesign.html` | Light theme color reference |
| `_bmad-output/brainstorming/prototype-detail-page-inline-pois.html` | POI card anatomy |
| `_bmad-output/brainstorming/prototype-detail-page-states.html` | Shimmer → content states |

### Technical Decisions

- **Non-streaming for context block.** `streamChatResponse` uses SSE for incremental token delivery (needed for chat UX). Context block is a single short JSON object — use non-streaming `generateContent` endpoint (`/models/gemini-2.5-flash:generateContent`). Response is ~100 tokens; latency is acceptable.
- **Parallel context + chat.** Both start immediately in `openChatForPoi()`. Chat seeds itself via `tapIntentPill()` (existing). Context runs as a sibling `viewModelScope.launch`. No dependency between them.
- **Field-level fallbacks.** `contextBlurb` empty → `"${poi.name} is a ${poi.type} in $areaName."`. `whyNow` empty → time-of-day hint string (morning/afternoon/evening). `localTip` empty → leave null (UI hides the tip section entirely).
- **Light theme approach.** Remove the outer `MaterialTheme(colorScheme = DarkColorScheme)` wrapper from `AiDetailPage`. Add `MaterialTheme(colorScheme = DarkColorScheme)` only around the `PoiDetailHeader` item in the LazyColumn. This makes chat bubbles / context block inherit system theme. When system is light (default for tester release): `DetailPageLight` explicit bg + white AI bubbles + indigo user. When system is dark: existing behavior via `DarkColorScheme`.
- **ChatBubbleItem lightMode param.** `lightMode = true`: AI bubble → `Color.White` bg + `BorderStroke(0.5.dp, Color.Gray.copy(alpha = 0.25f))` + dark text `Color(0xFF1A1A1A)`. User bubble: unchanged (`IndigoGradient` + white text works on both light and dark). Error bubble: unchanged.
- **`ChatInputBar` placeholder param.** Add `placeholder: String` param with a default matching current text. `AiDetailPage` passes `"Ask about this place..."`. `ChatOverlay` keeps its existing placeholder.
- **Context block time hint.** Derive from `AppClock.nowMs()`: `val hour = (clock.nowMs() / 3_600_000L % 24).toInt()`. Map to: `"morning"` (5–11), `"afternoon"` (11–17), `"evening"` (17–21), `"night"` (21–5). Pass as `timeHint` to `buildPoiContextPrompt()`.

---

## Implementation Plan

### Tasks

Tasks ordered by dependency (lowest-level first).

---

**T1 — Add `DetailPageLight` color constant**
File: `composeApp/src/commonMain/kotlin/com/harazone/ui/theme/Color.kt`
Action: Add after `MapFloatingUiDark`:
```kotlin
val DetailPageLight = Color(0xFFF5F2EF)
```

---

**T2 — Extend `ChatPoiCard` with rich fields**
File: `composeApp/src/commonMain/kotlin/com/harazone/ui/map/ChatUiState.kt`
Action: Add nullable fields to `ChatPoiCard` (all default null → backward compat with existing JSON):
```kotlin
@Serializable
data class ChatPoiCard(
    @SerialName("n") val name: String,
    @SerialName("t") val type: String,
    val lat: Double,
    val lng: Double,
    @SerialName("w") val whySpecial: String,
    @SerialName("img") val imageUrl: String? = null,
    @SerialName("insight") val insight: String? = null,   // ADD
    @SerialName("rating") val rating: Float? = null,      // ADD
    @SerialName("priceRange") val priceRange: String? = null, // ADD
    @SerialName("status") val status: String? = null,     // ADD
    @SerialName("hours") val hours: String? = null,       // ADD
) {
    val id: String get() = "$name|$lat|$lng"
}
```

---

**T3 — Add context-block fields to `ChatUiState`**
File: `composeApp/src/commonMain/kotlin/com/harazone/ui/map/ChatUiState.kt`
Action: Add four fields to `ChatUiState`:
```kotlin
data class ChatUiState(
    // ... existing fields ...
    val contextBlurb: String? = null,       // ADD
    val whyNow: String? = null,             // ADD
    val localTip: String? = null,           // ADD
    val isContextLoading: Boolean = false,  // ADD
)
```

---

**T4 — Extend `outputFormatBlock()` in `GeminiPromptBuilder`**
File: `composeApp/src/commonMain/kotlin/com/harazone/data/remote/GeminiPromptBuilder.kt`
Action: Replace the existing `outputFormatBlock()` method (line 282) with the extended version below.

New schema adds `insight`, `rating`, `priceRange`, `status`, `hours` to pois entries. New rules: POI guarantee, history sentence, anti-clustering.

```kotlin
private fun outputFormatBlock(): String =
    """RESPONSE FORMAT: Always respond with a single JSON object — no other text, no markdown outside the JSON.
Schema: {"prose":"your conversational reply here","pois":[{"n":"Name","t":"type","lat":0.0,"lng":0.0,"w":"why special","insight":"1-2 line AI hook","rating":4.5,"priceRange":"$$","status":"open|closing|closed|unknown","hours":"Mon-Fri 9am-10pm"}]}
Rules:
- prose: 2-3 sentences max. Conversational, not a travel blog. End with a follow-up question.
- pois: every place mentioned in prose MUST appear in the pois array. If no places mentioned, return empty array.
- POI GUARANTEE: If you were given a focused POI (the place the user tapped), it MUST appear in the pois array in every response — even if not mentioned in prose.
- HISTORY: Include one sentence about the place's history, origin story, or the neighborhood's cultural significance somewhere in your prose or the focused POI's insight field.
- ANTI-CLUSTERING: When mentioning multiple places, include at least 1 sentence of prose/transition between consecutive POI cards. No back-to-back pois entries without connecting prose context. Exception: if user explicitly asks for a list → max 3 consecutive entries with a brief text intro and summary.
- insight: 1-2 sentence AI hook specific to this place (different from w/why-special — insight is the memorable hook, w is the factual reason).
- rating, priceRange, status, hours: include when you know them; omit fields you are uncertain about.
- prose may use **bold**, *italic*, and `code` for emphasis — these will be rendered.
- t values: food|entertainment|park|historic|shopping|arts|transit|safety|beach|district
- DEPTH CONTROL: First response = 1-2 places. If user asks for more = 2-3 more. Never exceed 5 places total per message.
- DEDUPLICATION: If the user context mentions previously recommended places, do NOT include them in pois or mention them in prose.
- LAND COORDINATES ONLY: Coordinates must correspond to a road, building, or walkable area — not open water. Waterfront venues (piers, marinas, riverside restaurants) are fine. If unsure, use the city center coordinates as fallback.
Example: {"prose":"Check out **Brick Lane** for incredible street art — it changes weekly.\nWhat mood are you in — edgy underground spots or the well-known walls?","pois":[{"n":"Brick Lane","t":"arts","lat":51.5215,"lng":-0.0714,"w":"London's densest open-air gallery, curated by the street itself","insight":"Every Sunday morning the whole street transforms — murals painted overnight, artists you'll never find on Google","rating":4.7,"status":"open"}]}"""
```

---

**T5 — Add `buildPoiContextPrompt()` to `GeminiPromptBuilder`**
File: `composeApp/src/commonMain/kotlin/com/harazone/data/remote/GeminiPromptBuilder.kt`
Action: Add new public method after `buildChatSystemContext()`:

```kotlin
fun buildPoiContextPrompt(poiName: String, poiType: String, areaName: String, timeHint: String): String =
    """You are a passionate local in "$areaName". Return ONLY a JSON object — no markdown, no other text.

POI: $poiName ($poiType) in $areaName
Time of day: $timeHint

Schema: {"contextBlurb":"2-3 sentences: time-aware intro, with one sentence about this place's history, origin story, or the neighborhood's cultural significance","whyNow":"1 sentence: why this is a good moment to visit (time of day, mood, opportunity)","localTip":"insider tip a tourist would never find (empty string if you have nothing genuine)"}

Rules:
- contextBlurb MUST mention the time of day ($timeHint) naturally, not as a label.
- contextBlurb MUST include at least one sentence about history, origin, or cultural significance.
- whyNow should be specific to $timeHint — not generic advice.
- localTip: only include if you have a genuinely specific insider tip. If not, return "".
- All fields must be present. Respond with valid JSON only."""
```

---

**T6 — Add `parsePoiContextResponse()` to `GeminiResponseParser`**
File: `composeApp/src/commonMain/kotlin/com/harazone/data/remote/GeminiResponseParser.kt`
Action: Add serializable class + parse method. Insert after `PortraitPoisJson` definition (before `GeminiResponseParser` class body):

```kotlin
@Serializable
internal data class PoiContextJson(
    val contextBlurb: String = "",
    val whyNow: String = "",
    val localTip: String = "",
)
```

Add inside `GeminiResponseParser` class:

```kotlin
fun parsePoiContextResponse(text: String): Triple<String, String, String>? {
    return try {
        val cleaned = stripMarkdownFences(text)
        val ctx = json.decodeFromString<PoiContextJson>(cleaned)
        Triple(ctx.contextBlurb, ctx.whyNow, ctx.localTip)
    } catch (e: Exception) {
        AppLogger.e(e) { "GeminiResponseParser: failed to parse poi context response" }
        null
    }
}
```

---

**T7 — Add `generatePoiContext` to `AreaIntelligenceProvider` interface**
File: `composeApp/src/commonMain/kotlin/com/harazone/domain/provider/AreaIntelligenceProvider.kt`
Action: Add one method:

```kotlin
interface AreaIntelligenceProvider {
    fun streamAreaPortrait(areaName: String, context: AreaContext): Flow<BucketUpdate>
    fun streamChatResponse(query: String, areaName: String, conversationHistory: List<ChatMessage>): Flow<ChatToken>
    suspend fun generatePoiContext(poiName: String, poiType: String, areaName: String, timeHint: String): Triple<String, String, String>? // ADD
}
```

---

**T8 — Implement `generatePoiContext` in `GeminiAreaIntelligenceProvider`**
File: `composeApp/src/commonMain/kotlin/com/harazone/data/remote/GeminiAreaIntelligenceProvider.kt`
Action: Add `override suspend fun generatePoiContext(...)` using the existing non-streaming `generateContent` endpoint.

The existing `extractTextFromGenerateContent` in the parser handles non-streaming response format. Use the same `BASE_URL/$GEMINI_MODEL:generateContent` URL pattern already in the file (check existing usages — there may be a non-SSE call already; if not, add a simple `HttpClient.post` call).

```kotlin
override suspend fun generatePoiContext(
    poiName: String,
    poiType: String,
    areaName: String,
    timeHint: String,
): Triple<String, String, String>? {
    return try {
        val apiKey = apiKeyProvider.geminiApiKey
        if (apiKey.isBlank()) return null
        val prompt = promptBuilder.buildPoiContextPrompt(poiName, poiType, areaName, timeHint)
        val requestBody = buildRequestBody(prompt)  // reuse existing helper
        val responseJson = httpClient.post("$BASE_URL/$GEMINI_MODEL:generateContent") {
            parameter("key", apiKey)
            contentType(ContentType.Application.Json)
            setBody(requestBody)
        }.body<String>()
        val text = responseParser.extractTextFromGenerateContent(responseJson)
        responseParser.parsePoiContextResponse(text)
    } catch (e: Exception) {
        AppLogger.e(e) { "GeminiAreaIntelligenceProvider: generatePoiContext failed" }
        null
    }
}
```

Note: `httpClient.post(...).body<String>()` requires `io.ktor.client.call.body` import. Check existing imports in the file — add if needed.

---

**T9 — Stub `generatePoiContext` in `MockAreaIntelligenceProvider`**
File: `composeApp/src/commonMain/kotlin/com/harazone/data/remote/MockAreaIntelligenceProvider.kt`
Action: Add:
```kotlin
override suspend fun generatePoiContext(
    poiName: String,
    poiType: String,
    areaName: String,
    timeHint: String,
): Triple<String, String, String> = Triple(
    "Mock context blurb for $poiName in $areaName.",
    "Great time to visit.",
    "",
)
```

---

**T10 — Fetch context block in `ChatViewModel.openChatForPoi()`**
File: `composeApp/src/commonMain/kotlin/com/harazone/ui/map/ChatViewModel.kt`

Action: Modify `openChatForPoi()` to launch context fetch alongside the existing chat seed:

```kotlin
fun openChatForPoi(poi: POI, areaName: String, pois: List<POI>, activeDynamicVibe: DynamicVibe?) {
    openChat(areaName, pois, activeDynamicVibe, ChatEntryPoint.PoiCard(poi), forceReset = true)
    tapIntentPill(ContextualPill(
        label = poi.name,
        message = "Tell me about ${poi.name}.",
        intent = ChatIntent.DISCOVER,
        emoji = "\uD83D\uDCCD",
    ))
    // ADD: parallel context block fetch
    viewModelScope.launch {
        fetchPoiContext(poi, areaName)
    }
}
```

Add new private method `fetchPoiContext`:

```kotlin
private suspend fun fetchPoiContext(poi: POI, areaName: String) {
    _uiState.value = _uiState.value.copy(
        contextBlurb = null, whyNow = null, localTip = null, isContextLoading = true,
    )
    val hour = (clock.nowMs() / 3_600_000L % 24).toInt()
    val timeHint = when {
        hour in 5..10 -> "morning"
        hour in 11..16 -> "afternoon"
        hour in 17..20 -> "evening"
        else -> "night"
    }
    val result = aiProvider.generatePoiContext(poi.name, poi.type, areaName, timeHint)
    val (blurb, whyNow, tip) = result ?: Triple(
        "${poi.name} is a ${poi.type} in $areaName.",
        when (timeHint) {
            "morning" -> "A great way to start your morning."
            "afternoon" -> "Perfect for an afternoon visit."
            "evening" -> "A lovely spot for the evening."
            else -> "Worth a visit at any time."
        },
        "",
    )
    _uiState.value = _uiState.value.copy(
        contextBlurb = blurb.ifBlank { "${poi.name} is a ${poi.type} in $areaName." },
        whyNow = whyNow.ifBlank { null },
        localTip = tip.ifBlank { null },
        isContextLoading = false,
    )
}
```

Also: reset context fields in `openChat()` when `forceReset = true` (inside the reset branch):
```kotlin
// Inside the forceReset / new-area branch of openChat(), after chatJob?.cancel():
_uiState.value = ChatUiState()  // already resets all fields including new ones (they have defaults)
```
This is already handled since `ChatUiState()` uses default values — no additional change needed.

---

**T11 — Add `lightMode` param to `ChatBubbleItem`**
File: `composeApp/src/commonMain/kotlin/com/harazone/ui/map/ChatOverlay.kt`

Action: Add `lightMode: Boolean = false` parameter to `ChatBubbleItem`. Apply light colors only when `lightMode = true`:

```kotlin
@Composable
internal fun ChatBubbleItem(
    bubble: ChatBubble,
    onRetry: () -> Unit,
    lightMode: Boolean = false,  // ADD
) {
    // ...
    } else {
        // AI bubble
        val aiBg = if (lightMode) Color.White else AiBubbleColor
        val aiBorder = if (lightMode) BorderStroke(0.5.dp, Color.Gray.copy(alpha = 0.25f)) else null
        val aiTextColor = if (lightMode) Color(0xFF1A1A1A) else MaterialTheme.colorScheme.onSurface
        Surface(
            shape = RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp),
            color = aiBg,
            border = aiBorder,
            modifier = Modifier.fillMaxWidth(0.85f),
        ) {
            Row(modifier = Modifier.padding(12.dp)) {
                MarkdownText(
                    text = bubble.content,
                    style = MaterialTheme.typography.bodyMedium.copy(color = aiTextColor),
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (bubble.isStreaming) {
                    BlinkingCursor()
                }
            }
        }
    }
```

---

**T12 — Add `placeholder` param to `ChatInputBar`**
File: `composeApp/src/commonMain/kotlin/com/harazone/ui/map/ChatOverlay.kt`
Action: Find `ChatInputBar` composable. Add `placeholder: String = "..."` param (use existing placeholder text as default). `AiDetailPage` will pass `"Ask about this place..."`.

Note: Check the existing `ChatInputBar` signature for the current placeholder string and use it as the default. The existing callers in `ChatOverlay` pass no placeholder → backward compat preserved.

---

**T13 — Redesign `AiDetailPage` layout + light theme**
File: `composeApp/src/commonMain/kotlin/com/harazone/ui/map/components/AiDetailPage.kt`

This is the largest single task. Changes:

**1. Outer Box background → `DetailPageLight`**
```kotlin
// BEFORE:
Box(modifier = modifier.background(MapSurfaceDark.copy(alpha = 0.97f)))

// AFTER:
Box(modifier = modifier.background(DetailPageLight))
```
Import `com.harazone.ui.theme.DetailPageLight`.

**2. Remove top-level `MaterialTheme(colorScheme = DarkColorScheme)` wrapper**
The `MaterialTheme(colorScheme = DarkColorScheme) { Box(...) { ... } }` block — remove the outer MaterialTheme. Keep the inner content.

**3. Wrap `PoiDetailHeader` item in dark theme**
```kotlin
item(key = "header") {
    MaterialTheme(colorScheme = DarkColorScheme) {
        PoiDetailHeader(...)
    }
}
```
Add `import com.harazone.ui.theme.DarkColorScheme` (already imported — verify).

**4. Add dark background to PoiDetailHeader column**
Inside `PoiDetailHeader` composable, the `Column` after the hero image needs a dark background so the name/type/chips stay readable:
```kotlin
// In PoiDetailHeader, the Column below the Box hero image:
Column(modifier = Modifier.background(MapSurfaceDark).padding(16.dp)) { ... }
```
Import `com.harazone.ui.theme.MapSurfaceDark` (already imported — verify).

**5. Add `PoiContextBlock` item between header and chat bubbles**
Update function signature to accept context state params:
```kotlin
@Composable
internal fun AiDetailPage(
    // ... existing params ...
    contextBlurb: String?,      // ADD
    whyNow: String?,            // ADD
    localTip: String?,          // ADD
    isContextLoading: Boolean,  // ADD
    // ...
)
```
In the LazyColumn, add after the `item(key = "header")` block:
```kotlin
item(key = "context") {
    PoiContextBlock(
        contextBlurb = contextBlurb,
        whyNow = whyNow,
        localTip = localTip,
        isLoading = isContextLoading,
        modifier = Modifier.fillMaxWidth(),
    )
}
```

**6. Pass `lightMode = true` to ChatBubbleItem**
```kotlin
ChatBubbleItem(
    bubble = bubble,
    onRetry = { chatViewModel.retryLastMessage() },
    lightMode = true,  // ADD
)
```

**7. Pass placeholder to ChatInputBar**
```kotlin
ChatInputBar(
    inputText = chatState.inputText,
    isStreaming = chatState.isStreaming,
    placeholder = stringResource(Res.string.detail_page_chat_hint),  // ADD
    onInputChange = { chatViewModel.updateInput(it) },
    onSend = { chatViewModel.sendMessage() },
)
```
Add string resource `detail_page_chat_hint = "Ask about this place..."` to all `strings.xml` files (all locales). If the resource approach adds too much overhead, use a hardcoded string constant for tester release and mark `// TODO(BACKLOG-LOW): extract to strings.xml`.

**8. Update caller in MapScreen.kt**
`AiDetailPage(...)` call in `MapScreen.kt` (~line 433) needs new params added:
```kotlin
AiDetailPage(
    // ... existing params ...
    contextBlurb = chatState.contextBlurb,    // ADD
    whyNow = chatState.whyNow,                // ADD
    localTip = chatState.localTip,            // ADD
    isContextLoading = chatState.isContextLoading, // ADD
    // ...
)
```

---

**T14 — Add `PoiContextBlock` composable**
File: `composeApp/src/commonMain/kotlin/com/harazone/ui/map/components/AiDetailPage.kt`
(Added as a new private composable in the same file)

```kotlin
@Composable
private fun PoiContextBlock(
    contextBlurb: String?,
    whyNow: String?,
    localTip: String?,
    isLoading: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(Color.White)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (isLoading) {
            // Shimmer placeholders
            repeat(3) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(if (it == 2) 0.65f else 0.9f)
                        .height(14.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFFE0DDD9)),
                )
            }
            Spacer(Modifier.height(4.dp))
            // whyNow shimmer
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.75f)
                    .height(14.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFFE0DDD9)),
            )
        } else {
            if (!contextBlurb.isNullOrBlank()) {
                Text(
                    text = contextBlurb,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF2A2A2A),
                )
            }
            if (!whyNow.isNullOrBlank()) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFF0EDE9))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("⏰", style = MaterialTheme.typography.bodySmall)
                    Text(
                        text = whyNow,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF4A4A4A),
                    )
                }
            }
            if (!localTip.isNullOrBlank()) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFE8F5E9))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("💡", style = MaterialTheme.typography.bodySmall)
                    Text(
                        text = localTip,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF2E7D32),
                    )
                }
            }
        }
    }
}
```

---

**T15 — `MapScreen.kt` — hide bottom UI when detail page open**
File: `composeApp/src/commonMain/kotlin/com/harazone/ui/map/MapScreen.kt`

Four changes:

**A. MyLocation button AnimatedVisibility** (around line 521):
```kotlin
// BEFORE:
visible = state.showMyLocation && !state.isSearchingArea && !chatState.isOpen,
// AFTER:
visible = state.showMyLocation && !state.isSearchingArea && !chatState.isOpen && state.selectedPoi == null,
```

**B. SavesNearbyPill AnimatedVisibility** (around line 480):
```kotlin
// BEFORE:
visible = savedNearbyCount > 0 && !state.isSearchingArea && !chatState.isOpen && !state.isFabExpanded && !carouselVisible,
// AFTER:
visible = savedNearbyCount > 0 && !state.isSearchingArea && !chatState.isOpen && !state.isFabExpanded && !carouselVisible && state.selectedPoi == null,
```

**C. MapListToggle** (around line 546) — wrap in `if`:
```kotlin
// BEFORE:
MapListToggle(...)
// AFTER:
if (state.selectedPoi == null) {
    MapListToggle(...)
}
```

**D. AISearchBar** (around line 555) — wrap in `if`:
```kotlin
// BEFORE:
AISearchBar(...)
// AFTER:
if (state.selectedPoi == null) {
    AISearchBar(...)
}
```

Optional (already hidden by scrim but reduces unnecessary rendering):
- `AmbientTicker`: add `&& state.selectedPoi == null` to its `if` guard
- `VibeRail`: add `&& state.selectedPoi == null` to its `if` guard

---

### Acceptance Criteria

**AC1 — Nav/UI overlap (#47)**
- Given: user taps a carousel card to open AiDetailPage
- When: detail page is visible (`state.selectedPoi != null`)
- Then: AISearchBar is not visible; MapListToggle is not visible; MyLocation button is not visible; SavesNearbyPill is not visible; FAB is still visible; TopContextBar is still visible

**AC2 — Nav/UI overlap: map ambient strip**
- Given: detail page open
- Then: map is visible as a narrow strip at the top (~10% of screen height) above the detail page content — no additional code change required (existing padding `top = statusBarPadding + 56dp` achieves this)

**AC3 — Prompt engineering: POI guarantee (#48)**
- Given: user taps "Maison Rustique" (a café) and opens detail page
- When: the auto-seeded intro message completes
- Then: the `pois[]` array in the AI response contains an entry with `"n": "Maison Rustique"` in EVERY response

**AC4 — Prompt engineering: history sentence (#48)**
- Given: any detail page AI response
- Then: either the `prose` or the focused POI's `insight` field contains a sentence about history, origin, or cultural significance (manual verification in prompt regression test)

**AC5 — Prompt engineering: anti-clustering (#48)**
- Given: AI response that mentions 3 places
- Then: the `pois[]` array entries do NOT appear back-to-back without prose between them in the rendered chat flow; client-side rendering respects card ordering interspersed with prose bubbles

**AC6 — Context block shimmer (#49)**
- Given: user opens AiDetailPage
- When: `isContextLoading = true`
- Then: `PoiContextBlock` shows 4 shimmer placeholder bars; chat input bar is active (not blocked); chat seeds itself in parallel

**AC7 — Context block loaded (#49)**
- Given: context fetch completes successfully
- When: `isContextLoading = false`, `contextBlurb` and `whyNow` are non-null
- Then: `PoiContextBlock` shows `contextBlurb` as body text, `whyNow` in a clock-icon chip, `localTip` in a green bulb chip (or hidden if `localTip` is null/blank)

**AC8 — Context block fallback (#49)**
- Given: context fetch fails (network error or parse error)
- When: `generatePoiContext()` returns null
- Then: `contextBlurb` is set to metadata fallback (`"$name is a $type in $areaName."`); `whyNow` is time-of-day fallback; `localTip` is null (tip section hidden)

**AC9 — Light theme: hero stays dark (#50)**
- Given: detail page open
- Then: POI hero image section and info section (name, type, chips, action chips) are on dark background with white text

**AC10 — Light theme: content area is warm off-white (#50)**
- Given: detail page open
- Then: context block and chat section have `#F5F2EF` warm off-white background; AI chat bubbles are white with hairline border and dark text; user chat bubbles are indigo with white text; input bar background blends with light theme

**AC11 — Light theme: system dark mode (#50)**
- Given: device is in dark mode
- Then: the detail page chat section uses the existing dark color scheme (DarkColorScheme) without layout breakage; hero remains dark

---

## Additional Context

### Dependencies

- T2 (ChatPoiCard fields) must come before T4 (prompt schema — need to verify field names match)
- T5 (buildPoiContextPrompt) must come before T6 (parsePoiContextResponse — needs to know schema)
- T6 + T5 must come before T7 (interface) and T8 (implementation)
- T7 must come before T9 (MockAreaIntelligenceProvider stub — compiler error otherwise)
- T10 (ChatViewModel fetchPoiContext) requires T3 (state fields), T8 (interface), T9 (mock)
- T11 (AiDetailPage lightMode) requires T1 (DetailPageLight color) and T12 (ChatBubbleItem param)
- T13 (AiDetailPage redesign) requires T1, T11, T12, T14 (PoiContextBlock composable)
- T15 (MapScreen guards) has no dependencies — can be done anytime

Suggested implementation order: T1 → T2 → T3 → T4 → T5 → T6 → T7 → T9 (mock) → T8 (impl) → T10 → T11 → T12 → T14 → T13 → T15 → Tests (T16)

### Testing Strategy

**T16 — Tests (write after all implementation tasks)**

File: `composeApp/src/commonTest/kotlin/com/harazone/data/remote/GeminiPromptBuilderTest.kt`
Add to existing test class:
- `outputFormatBlock contains history instruction` — assert output contains `"HISTORY"` or `"history, origin story"`
- `outputFormatBlock contains POI guarantee` — assert contains `"POI GUARANTEE"` or `"MUST appear in the pois array"`
- `outputFormatBlock contains anti-clustering rule` — assert contains `"ANTI-CLUSTERING"`
- `outputFormatBlock schema contains insight and rating fields` — assert contains `"insight"` and `"rating"`
- `buildPoiContextPrompt contains timeHint` — call with `"morning"`, assert output contains `"morning"`
- `buildPoiContextPrompt contains history instruction` — assert contains `"history"` or `"cultural significance"`
- `buildPoiContextPrompt contains JSON schema` — assert contains `"contextBlurb"` and `"whyNow"` and `"localTip"`

File: `composeApp/src/commonTest/kotlin/com/harazone/data/remote/GeminiResponseParserTest.kt`
Add:
- `parsePoiContextResponse happy path` — valid JSON `{"contextBlurb":"...","whyNow":"...","localTip":"..."}` → returns Triple with all three fields
- `parsePoiContextResponse missing localTip` — JSON with no `localTip` key → returns Triple with `localTip = ""`
- `parsePoiContextResponse malformed JSON` — garbage input → returns null

File: `composeApp/src/commonTest/kotlin/com/harazone/ui/map/ChatViewModelTest.kt`
Add (using existing `FakeClock`, `FakeAreaRepository` patterns):
- `openChatForPoi sets isContextLoading true immediately` — assert `chatState.isContextLoading == true` before fake provider resolves
- `openChatForPoi populates context fields after load` — after provider resolves, assert `contextBlurb`, `whyNow` non-null, `isContextLoading == false`
- `openChatForPoi uses fallback on provider null` — if fake provider returns null, assert `contextBlurb` is non-empty (fallback), `isContextLoading == false`

**Prompt regression test (manual, 10 POIs):** Document the 10 diverse POI test cases in `_bmad-output/implementation-artifacts/prompt-regression-checklist.md`. Run manually before any future change to `outputFormatBlock()` or `buildPoiContextPrompt()`. Verify: POI guarantee present, no back-to-back cards, history sentence present, JSON parses cleanly.

### Notes

- **Do NOT run adversarial review inline or implement code from this session.** Stop after spec is complete. Open a new window for implementation.
- **`/simplify` rule:** Run `/simplify` on every new or significantly changed file after implementation (per workflow preferences).
- **`ChatInputBar` placeholder string:** If adding `Res.string.detail_page_chat_hint` to all locale files is too much effort for tester release, use a hardcoded constant `private const val DETAIL_PAGE_CHAT_HINT = "Ask about this place..."` in `AiDetailPage.kt` and mark as `TODO(BACKLOG-LOW): extract to strings.xml`.
- **Prototype reference:** All three HTML prototypes in `_bmad-output/brainstorming/` are the source of truth for visual decisions. Open them in a browser before implementing T13/T14 to verify color and layout intent.
- **iOS:** No platform-specific changes in this spec. Verify on iOS simulator after T13/T15 — the light theme and hiding logic are commonMain only.
- **`buildRequestBody(prompt)` in T8:** This private helper in `GeminiAreaIntelligenceProvider` takes a `String` prompt and returns the `GeminiRequest` body. Reuse it — don't duplicate.
- **`io.ktor.client.call.body` import** needed for `.body<String>()` in T8. Add `import io.ktor.client.call.body` if not present.

---
title: 'Gemini Prompt v2'
slug: 'gemini-prompt-v2'
created: '2026-03-07'
status: 'done'
stepsCompleted: [1, 2, 3, 4]
tech_stack: ['Kotlin Multiplatform', 'kotlinx.serialization', 'Ktor SSE', 'Gemini 2.5 Flash', 'SQLDelight']
files_to_modify:
  - 'composeApp/src/commonMain/kotlin/com/areadiscovery/data/remote/GeminiPromptBuilder.kt'
  - 'composeApp/src/commonMain/kotlin/com/areadiscovery/data/remote/GeminiResponseParser.kt'
  - 'composeApp/src/commonTest/kotlin/com/areadiscovery/data/remote/GeminiResponseParserTest.kt'
  - 'composeApp/src/commonTest/kotlin/com/areadiscovery/data/remote/GeminiPromptBuilderTest.kt'
  - 'composeApp/src/androidInstrumentedTest/kotlin/com/areadiscovery/PromptComparisonTest.kt'
code_patterns: ['DTO-to-domain mapping in GeminiResponseParser', 'Streaming SSE parser', 'ignoreUnknownKeys=true on JSON instance']
test_patterns: ['kotlin.test', 'commonTest', 'companion object fixture strings']
---

# Tech-Spec: Gemini Prompt v2

**Created:** 2026-03-07

## Overview

### Problem Statement

The current prompt in `GeminiPromptBuilder.kt` returns generic, underwhelming results even in well-known areas. Food dominates every vibe. Chains appear. Historical depth is thin. Token budget is wasted on hallucinated `sources` URLs, always-HIGH `confidence` fields, and `vibeInsights` sub-objects that are rarely displayed in UI.

### Solution

Rewrite `buildAreaPortraitPrompt()` with: (1) passionate local persona, (2) uniqueness filter (principle-based, no hardcoded brand names), (3) light food gate (unique/story-worthy food always welcome; gate only prevents generic pile-on), (4) required `why_special` (`w`) field per POI, (5) "dig deeper" instruction for less-obvious areas, (6) slim JSON schema with short keys. Update `PoiJson`/`BucketJson` DTOs and `parsePoisJson()`/`parseBucketJson()` methods to match the new schema. Domain models and DB schema stay **completely unchanged** — waste fields just default to empty/MEDIUM at the mapper layer.

### Scope

**In Scope:**
- Rewrite prompt string in `GeminiPromptBuilder.kt`
- Slim POI JSON schema: `n, t, v, w, h, s, r, lat, lng` (name, type, vibe, why_special, hours, status, rating, latitude, longitude)
- Drop `confidence` and `sources` from bucket JSON schema (prompt-side; DTO gets optional defaults)
- Update `PoiJson` DTO and `BucketJson` DTO in `GeminiResponseParser.kt`
- Update `parsePoisJson()` and `parseBucketJson()` mapper methods
- Update test fixtures and tests in `GeminiResponseParserTest.kt` and `GeminiPromptBuilderTest.kt`
- Manual validation across representative area types: busy/tourist area, suburban/residential area, major city, small town

**Out of Scope:**
- Domain model changes (`POI.kt`, `Bucket.kt`, `Confidence.kt`, `Source.kt`) — NO changes
- DB schema / SQLDelight migration — NO changes
- UI changes (`ExpandablePoiCard.kt`, `POIListView.kt`) — existing `isNotEmpty()` guards handle empty fields gracefully
- `buildAiSearchPrompt()` — not changing
- Dynamic vibes (Phase A), AI Chat (Phase B)
- Switching from single-call to multi-call architecture

## Context for Development

### Codebase Patterns

- **Kotlin Multiplatform** — all source in `commonMain`; tests in `commonTest`
- **DTO pattern**: `GeminiResponseParser.kt` defines internal `@Serializable` DTO data classes (`PoiJson`, `BucketJson`) that are mapped to stable domain models (`POI`, `BucketContent`). Only the DTOs and mapper logic change — domain models are untouched.
- **`Json { ignoreUnknownKeys = true }`** is already set on the `json` instance in `GeminiResponseParser`. Adding slim keys while dropping old fields from the schema is safe — unknown incoming fields are ignored.
- **Streaming parser**: `StreamingParser` inner class buffers SSE chunks and emits `BucketUpdate` events as delimiters are found. The POI schema change is fully isolated to `parsePoisJson()` — streaming/delimiter logic is untouched.
- **Test fixtures as `companion object` constants**: `GeminiResponseParserTest` stores multi-line JSON strings as named constants. All fixture strings must be updated to the slim schema.
- **Test framework**: `kotlin.test` (`@Test`, `assertTrue`, `assertEquals`, `assertFalse`) in `commonTest`.

### Files to Reference

| File | Purpose |
| ---- | ------- |
| `composeApp/src/commonMain/kotlin/com/areadiscovery/data/remote/GeminiPromptBuilder.kt` | **PRIMARY CHANGE** — full prompt rewrite |
| `composeApp/src/commonMain/kotlin/com/areadiscovery/data/remote/GeminiResponseParser.kt` | DTO + mapper updates |
| `composeApp/src/commonMain/kotlin/com/areadiscovery/domain/model/POI.kt` | Reference only — do NOT modify |
| `composeApp/src/commonMain/kotlin/com/areadiscovery/domain/model/Bucket.kt` | Reference only — do NOT modify |
| `composeApp/src/commonMain/kotlin/com/areadiscovery/domain/model/Source.kt` | Reference only — do NOT modify |
| `composeApp/src/commonTest/kotlin/com/areadiscovery/data/remote/GeminiResponseParserTest.kt` | Update all fixtures + vibeInsights/sources tests |
| `composeApp/src/commonTest/kotlin/com/areadiscovery/data/remote/GeminiPromptBuilderTest.kt` | Update coordinate key test + add 7 new tests |
| `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/map/components/ExpandablePoiCard.kt` | Reference only — `poi.insight` (L134), `poi.description` (L208), `poi.vibeInsights` (L220) all guarded by `isNotEmpty()` |
| `composeApp/src/commonMain/kotlin/com/areadiscovery/data/repository/AreaRepositoryImpl.kt` | Reference only — writes `BucketContent.confidence` + `.sources` to SQLDelight; will receive "MEDIUM"/`[]` defaults |

### Technical Decisions

1. **Domain models + DB schema frozen.** `POI.confidence`, `POI.description`, `POI.vibeInsights`, `BucketContent.confidence`, `BucketContent.sources` remain unchanged. Parser defaults them. No migration, no UI changes, zero blast radius beyond the 4 files above.
2. **Slim JSON keys in prompt + DTO only.** `n=name, t=type, v=vibe, w=why_special, h=hours, s=status, r=rating, lat, lng`. The mapper reads slim keys → writes human-readable POI domain fields.
3. **`w` (why_special) maps to `POI.insight`.** `insight` is the primary text shown on cards in `ExpandablePoiCard` (L134) and `POIListView` (L154). Making it `why_special` is the core quality improvement.
4. **`BucketJson.confidence` + `.sources` → optional with defaults.** DTO change: `confidence: String = "MEDIUM"`, `sources: List<SourceJson> = emptyList()`. Prompt no longer asks for them; DTO stays backward-compatible with any cached old responses.
5. **`PoiJson` becomes slim-only.** All old fields replaced with short-key fields. `ignoreUnknownKeys = true` means any cached old-format responses won't crash during the transition.

## Implementation Plan

### Tasks

- [x] **Task 1: Rewrite prompt in `GeminiPromptBuilder.kt`**
  - File: `composeApp/src/commonMain/kotlin/com/areadiscovery/data/remote/GeminiPromptBuilder.kt`
  - Action: Replace the entire `buildAreaPortraitPrompt()` prompt string. Keep the method signature and `trimIndent()` call unchanged. The new prompt must include all of the following sections in order:
    1. **Persona opener**: `You are a passionate local who has lived in "$areaName" for 20 years. You love showing visitors things they would NEVER find on Google Maps. Your mission: surface the genuinely unique, memorable, and local.`
    2. **Context block**: Same as current — time of day, day of week, preferred language
    3. **Output format instruction**: 6 JSON objects separated by `---BUCKET---` delimiter. Bucket schema drops `confidence` and `sources`: `{"type":"BUCKET_TYPE","highlight":"1-2 sentence whoa fact","content":"2-4 sentences supporting context"}`
    4. **6 bucket type definitions** in order: SAFETY, CHARACTER, WHATS_HAPPENING, COST, HISTORY, NEARBY. HISTORY keeps the timeline format (exact 4-digit years, no vague phrases). All bucket definitions otherwise unchanged.
    5. **Quality filter block** (add after bucket definitions, before POI section):
       ```
       UNIQUENESS RULE: Only include places with a genuine story or character that makes them worth visiting. A place is not interesting because it exists — it is interesting because of what it means to this area. If you cannot explain what makes it special in a sentence, do not include it.
       FOOD GATE: Food and drink places are welcome if they are unique, have a story, or offer something you cannot find anywhere else. Do not flood results with generic restaurants just because they are nearby. Aim for no more than 30% food POIs across all vibes unless the area is genuinely food-destination-famous.
       WHY SPECIAL REQUIRED: Every POI must have a compelling "w" field. One sentence minimum. Generic descriptions like "popular restaurant" or "nice park" are not acceptable.
       HISTORICAL DEPTH: Include at least 2-3 POIs with historical or cultural stories, especially for the "history" vibe.
       DIG DEEPER: For areas with less obvious attractions (suburbs, residential), look for: local parks with history, community landmarks, street art, murals, architectural details, cultural centers, ethnic enclaves, neighborhood stories, independent businesses with character.
       ```
    6. **`---POIS---` delimiter instruction**: same as current
    7. **Slim POI array schema**: `[{"n":"Name","t":"type","v":"vibe","w":"Why this place is genuinely special — what you'd tell a friend","h":"hours","s":"open|busy|closed","r":4.5,"lat":38.7100,"lng":-9.1300}]`
    8. **Valid value lists**: same as current (`t` values: food, entertainment, park, historic, shopping, arts, transit, safety, beach, district; `v` values: character, history, whats_on, safety, nearby, cost; `s` values: open, busy, closed)
    9. **Output rules**: Keep existing rules EXCEPT remove `confidence` and `sources` references. Keep GPS coordinates instruction (4 decimal places, required). Keep delimiter-inside-JSON warning.
  - Notes: Do NOT change `buildAiSearchPrompt()`. The delimiter constants `---BUCKET---` and `---POIS---` remain identical — the streaming parser depends on them.

- [x] **Task 2: Make `BucketJson.confidence` and `.sources` optional in `GeminiResponseParser.kt`**
  - File: `composeApp/src/commonMain/kotlin/com/areadiscovery/data/remote/GeminiResponseParser.kt`
  - Action: Change `BucketJson` data class — add default values so fields are optional when missing from JSON:
    ```kotlin
    @Serializable
    internal data class BucketJson(
        val type: String,
        val highlight: String,
        val content: String,
        val confidence: String = "MEDIUM",
        val sources: List<SourceJson> = emptyList()
    )
    ```
  - Notes: `parseBucketJson()` does NOT need to change — it already reads `bucketJson.confidence` and `bucketJson.sources`. With defaults, missing fields resolve to `"MEDIUM"` and `[]`, which the existing mapper handles correctly. `SourceJson` DTO is unchanged.

- [x] **Task 3: Replace `PoiJson` DTO with slim-key version in `GeminiResponseParser.kt`**
  - File: `composeApp/src/commonMain/kotlin/com/areadiscovery/data/remote/GeminiResponseParser.kt`
  - Action: Delete the existing `PoiJson` data class entirely. Replace with:
    ```kotlin
    @Serializable
    internal data class PoiJson(
        @SerialName("n") val n: String = "",
        @SerialName("t") val t: String = "",
        @SerialName("v") val v: String = "",
        @SerialName("w") val w: String = "",
        @SerialName("h") val h: String? = null,
        @SerialName("s") val s: String? = null,
        @SerialName("r") val r: Float? = null,
        val lat: Double? = null,
        val lng: Double? = null,
    )
    ```
  - Notes: All fields have defaults so a partially malformed POI won't throw during deserialization. `ignoreUnknownKeys = true` is already set — old-format cached responses with fat keys won't crash.

- [x] **Task 4: Update `parsePoisJson()` mapper in `GeminiResponseParser.kt`**
  - File: `composeApp/src/commonMain/kotlin/com/areadiscovery/data/remote/GeminiResponseParser.kt`
  - Action: Inside `parsePoisJson()`, replace the `poisJson.map { poiJson -> POI(...) }` block with:
    ```kotlin
    poisJson.map { poiJson ->
        POI(
            name = poiJson.n,
            type = poiJson.t,
            description = "",
            confidence = Confidence.MEDIUM,
            latitude = poiJson.lat,
            longitude = poiJson.lng,
            vibe = poiJson.v,
            insight = poiJson.w,
            hours = poiJson.h,
            liveStatus = poiJson.s,
            rating = poiJson.r,
            vibeInsights = emptyMap(),
        )
    }
    ```
  - Notes: `POI.insight` is the primary text shown on cards — `w` (why_special) maps directly here. `description` and `vibeInsights` are intentionally left empty; existing UI guards (`isNotEmpty()`) handle this gracefully with no UI change needed. `parseConfidence()` is no longer called for POIs; the private method can remain (still used by `parseBucketJson()`).

- [x] **Task 5: Update test fixtures in `GeminiResponseParserTest.kt`**
  - File: `composeApp/src/commonTest/kotlin/com/areadiscovery/data/remote/GeminiResponseParserTest.kt`
  - Action: Update all fixture constants in the `companion object` to use slim bucket schema (no `confidence`/`sources`) and slim POI schema (short keys). Specific changes:
    - **`COMPLETE_RESPONSE`**: Update all 6 bucket JSON strings — remove `"confidence":...` and `"sources":[...]` fields. Update POI array: replace `{"name":"Castle of São Jorge","type":"landmark","description":"Medieval castle with panoramic views","confidence":"HIGH","latitude":38.7139,"longitude":-9.1335}` with `{"n":"Castle of São Jorge","t":"historic","v":"history","w":"Medieval castle with panoramic views of Lisbon","h":"9a-9p","s":"open","r":4.7,"lat":38.7139,"lng":-9.1335}` (and similarly for Feira da Ladra).
    - **`PARTIAL_RESPONSE`**: Remove `"confidence"` and `"sources":[]` from bucket JSONs. Update POI array to `[]` (already empty, no change needed).
    - **`MALFORMED_SSE`**: Remove `"confidence"` and `"sources":[]` from the two valid bucket JSONs. Keep the malformed middle bucket unchanged.
    - **`V3_POIS_RESPONSE`**: Remove `"confidence"` from bucket. Replace POI array with slim schema: `[{"n":"Time Out Market","t":"food","v":"character","w":"Curated food hall with 24 local restaurants in a converted 1892 iron market hall","h":"Sun-Wed 10am-12am, Thu-Sat 10am-2am","s":"open","r":4.5,"lat":38.71,"lng":-9.13},{"n":"Old Church","t":"historic","v":"history","w":"Built in 1200, oldest surviving structure in the district","h":"10a-5p","s":"open","r":4.2,"lat":38.72,"lng":-9.14}]`
    - **`streamingParser_emitsBucketIncrementallyOnDelimiter`** inline fixture strings: remove `"sources":[]` and `"confidence"` from inline bucket JSONs. Update POI object to `{"n":"Park","t":"park","v":"nearby","w":"Peaceful community green space with century-old oak trees","lat":null,"lng":null}`.
    - **`streamingParser_handlesDelimiterSplitAcrossChunks`** inline fixtures: remove `"sources":[]` and `"confidence"`.
    - **`streamingParser_skipsUnknownBucketType`** inline fixtures: same.
  - Action (test updates):
    - `parseFullResponse_completeResponse_returns6BucketsAndPois`: Remove assertion `assertEquals(1, bucketCompletes[0].content.sources.size)` — sources is now always empty. Keep all other assertions.
    - `parseFullResponse_v3Pois_parsesVibeInsights`: Change assertion to `assertTrue(portrait.pois[0].vibeInsights.isEmpty())` — vibeInsights no longer populated.
    - `parseFullResponse_v3Pois_fallsBackToNameFieldWhenPoiMissing`: Delete this test — the `poi` key fallback logic is gone. Add a replacement test `parseFullResponse_slimPois_parsesNameFromNKey` that verifies `portrait.pois[0].name == "Time Out Market"` using the updated `V3_POIS_RESPONSE` fixture.
    - `parseFullResponse_v3Pois_parsesInsight`: Update expected value to match the new `w` field value in the updated fixture.
  - Add new test:
    ```kotlin
    @Test
    fun parseFullResponse_slimPois_mapsWhySpecialToInsight() {
        val result = parser.parseFullResponse(V3_POIS_RESPONSE)
        val portrait = result.getOrThrow().last() as BucketUpdate.PortraitComplete
        assertTrue(portrait.pois[0].insight.isNotEmpty())
        assertTrue(portrait.pois[0].insight.contains("food hall") || portrait.pois[0].insight.contains("market"))
    }
    ```

- [x] **Task 6: Update and extend `GeminiPromptBuilderTest.kt`**
  - File: `composeApp/src/commonTest/kotlin/com/areadiscovery/data/remote/GeminiPromptBuilderTest.kt`
  - Action — Update existing test:
    - `buildAreaPortraitPrompt_poiTemplateHasRealCoordinates`: Change assertion from `prompt.contains("\"latitude\":38.71")` to `prompt.contains("\"lat\":38.71")`. Update the negative assertion from `"latitude":null` to `"lat\":null"`.
  - Action — Add 7 new tests:
    ```kotlin
    @Test
    fun buildAreaPortraitPrompt_includesPassionateLocalPersona() {
        val prompt = builder.buildAreaPortraitPrompt("Alfama, Lisbon", testContext)
        assertTrue(prompt.contains("passionate local"))
    }

    @Test
    fun buildAreaPortraitPrompt_includesUniquenessRule() {
        val prompt = builder.buildAreaPortraitPrompt("Alfama, Lisbon", testContext)
        assertTrue(prompt.contains("UNIQUENESS RULE") || prompt.contains("genuine story") || prompt.contains("genuinely unique"))
    }

    @Test
    fun buildAreaPortraitPrompt_doesNotHardcodeChainBrandNames() {
        // Exclusion is principle-based, not brand-list-based — Starbucks/McDonald's must NOT appear in prompt
        val prompt = builder.buildAreaPortraitPrompt("Alfama, Lisbon", testContext)
        assertFalse(prompt.contains("Starbucks"))
        assertFalse(prompt.contains("McDonald"))
    }

    @Test
    fun buildAreaPortraitPrompt_includesFoodGate() {
        val prompt = builder.buildAreaPortraitPrompt("Alfama, Lisbon", testContext)
        assertTrue(prompt.contains("FOOD GATE"))
    }

    @Test
    fun buildAreaPortraitPrompt_includesWhySpecialInstruction() {
        val prompt = builder.buildAreaPortraitPrompt("Alfama, Lisbon", testContext)
        assertTrue(prompt.contains("WHY SPECIAL") || prompt.contains("why_special") || prompt.contains("\"w\""))
    }

    @Test
    fun buildAreaPortraitPrompt_includesDigDeeperInstruction() {
        val prompt = builder.buildAreaPortraitPrompt("Alfama, Lisbon", testContext)
        assertTrue(prompt.contains("DIG DEEPER") || prompt.contains("less obvious"))
    }

    @Test
    fun buildAreaPortraitPrompt_poiTemplateUsesSlimKeys() {
        val prompt = builder.buildAreaPortraitPrompt("Alfama, Lisbon", testContext)
        assertTrue(prompt.contains("\"n\":") && prompt.contains("\"w\":") && prompt.contains("\"lat\":"))
        assertFalse(prompt.contains("\"poi\":"))
        assertFalse(prompt.contains("\"insight\":"))
        assertFalse(prompt.contains("\"latitude\":"))
    }

    @Test
    fun buildAreaPortraitPrompt_doesNotIncludeSourcesInBucketTemplate() {
        val prompt = builder.buildAreaPortraitPrompt("Alfama, Lisbon", testContext)
        assertFalse(prompt.contains("\"sources\""))
    }
    ```
  - Notes: Import `kotlin.test.assertFalse` at top of file.

### Acceptance Criteria

- [x] **AC1 — Prompt persona**: Given `buildAreaPortraitPrompt()` is called, when the returned string is inspected, then it contains the phrase "passionate local".

- [x] **AC2 — Principle-based uniqueness, no hardcoded brands**: Given `buildAreaPortraitPrompt()` is called, when the returned string is inspected, then it does NOT contain "Starbucks" or "McDonald" and instead contains a principle-based uniqueness instruction (e.g. "genuine story" or "UNIQUENESS RULE").

- [x] **AC3 — Light food gate**: Given `buildAreaPortraitPrompt()` is called, when the returned string is inspected, then it contains "FOOD GATE" and the instruction welcomes unique/story-worthy food while capping generic restaurant dominance.

- [x] **AC4 — Slim POI schema in prompt**: Given `buildAreaPortraitPrompt()` is called, when the returned string is inspected, then the POI example uses `"n":`, `"w":`, `"lat":` and does NOT contain `"poi":`, `"insight":`, or `"latitude":`.

- [x] **AC5 — No sources/confidence in bucket template**: Given `buildAreaPortraitPrompt()` is called, when the returned string is inspected, then the bucket JSON example does NOT contain `"sources"` or `"confidence"`.

- [x] **AC6 — Slim parser maps `w` → `POI.insight`**: Given a POI JSON string `{"n":"Foo Gallery","t":"arts","v":"character","w":"A unique gallery with 30-year history","h":"10a-6p","s":"open","r":4.5,"lat":38.71,"lng":-9.13}` in the POIs section, when `parseFullResponse()` processes it, then `portrait.pois[0].insight == "A unique gallery with 30-year history"` and `portrait.pois[0].name == "Foo Gallery"`.

- [x] **AC7 — Slim parser maps lat/lng → `POI.latitude`/`POI.longitude`**: Given a slim POI with `"lat":38.7139,"lng":-9.1335`, when parsed, then `poi.latitude == 38.7139` and `poi.longitude == -9.1335`.

- [x] **AC8 — `vibeInsights` defaults to empty**: Given a slim-key POI with no `vibeInsights` field, when parsed, then `poi.vibeInsights.isEmpty() == true`.

- [x] **AC9 — Bucket parses without confidence/sources**: Given a bucket JSON string `{"type":"SAFETY","highlight":"Safe area","content":"Low crime."}` (no `confidence`, no `sources`), when `parseFullResponse()` processes it, then result is `Success`, `BucketContent.sources` is empty, and `BucketContent.confidence` is `Confidence.MEDIUM`.

- [x] **AC10 — All unit tests pass**: Given the updated code, when `./gradlew :composeApp:test` runs, then all tests in `GeminiResponseParserTest` and `GeminiPromptBuilderTest` pass with no failures.

- [x] **AC11 — Manual: busy/tourist area quality check**: Given prompt v2 is live, when the app loads area portrait for a well-known tourist-heavy area, then results contain no obviously generic or ubiquitous places; at least 2 POIs have culturally or historically significant `why_special` text.

- [x] **AC12 — Manual: suburban/residential area depth check**: Given prompt v2 is live, when the app loads area portrait for a less-obvious suburban or residential area, then results include locally unique POIs rather than generic nearby businesses; the "dig deeper" effect is visible (parks, landmarks, street art, cultural centers, neighborhood stories).

## Additional Context

### Dependencies

- No new library dependencies
- No DB migration (SQLDelight schema unchanged)
- No UI changes required

### Testing Strategy

- **Unit tests** (Tasks 5 + 6): Update fixture strings to slim schema; update 3 tests, add 8 new tests across both test files. Run with `./gradlew :composeApp:test`.
- **Manual validation** (after Task 1 prompt rewrite): Test live Gemini calls across 4 representative area types — busy/tourist area, suburban/residential area, major city, small town. Compare result quality before/after by noting POI uniqueness, food ratio, historical depth, and absence of generic results.
- **Regression safety**: `ignoreUnknownKeys = true` ensures old-format cached responses won't crash. No behavior change for buckets (same parser path). Only `parsePoisJson()` mapper changes.

### Notes

- **Brainstorm source**: `_bmad-output/brainstorming/brainstorming-session-2026-03-06-002.md` § "Gemini Prompt v2 — Diagnosis & Proposed Fixes"
- **Single-call architecture preserved**: One Gemini call per area populates all vibes. Cached for instant vibe switching. Do not split into per-vibe calls.
- **`SourceJson` DTO + `Source` domain + `SourceAttribution`** remain — never populated from v2 prompt but retained for forward compatibility.
- **`Confidence` enum** remains — defaults to `MEDIUM` for all POIs and buckets from v2 prompt.
- **`ExpandablePoiCard` expanded section** shows `poi.description` (L208) and `poi.vibeInsights` (L220-242) — both will be empty with v2. UI guards (`isNotEmpty()`) handle this gracefully. These sections will simply not render. Acceptable for now; if product wants them back they become Phase A work.
- **`parseConfidence()` private method** in `GeminiResponseParser` is still used by `parseBucketJson()`. Do not delete it even though it's no longer called for POIs.
- **Task order matters**: Do Task 3 (PoiJson DTO) before Task 4 (parsePoisJson mapper) since the mapper references the DTO fields. Do Tasks 1-4 before Task 5-6 so tests can be written against the final implementation shape.

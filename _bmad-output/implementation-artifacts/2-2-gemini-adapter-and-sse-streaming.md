# Story 2.2: Gemini Adapter & SSE Streaming

Status: review

## Story

As a developer,
I want a real Gemini API adapter that streams area portraits via SSE,
so that users receive live, AI-generated six-bucket area summaries instead of mock data.

## Acceptance Criteria

1. `GeminiAreaIntelligenceProvider` implements `AreaIntelligenceProvider` in `data/remote/`
2. `streamAreaPortrait(areaName, context)` sends a structured prompt to Gemini REST API via Ktor SSE streaming
3. `GeminiPromptBuilder` constructs a prompt requesting six-bucket JSON output with confidence levels and source attribution per bucket — prompt includes `AreaContext`: time of day, day of week, preferred language [FR2, FR12]
4. `GeminiResponseParser` parses SSE events into `Flow<BucketUpdate>` emissions matching the existing contract: `ContentDelta` → `BucketComplete` → `PortraitComplete`
5. All network calls use TLS 1.2+ (Ktor default) [NFR10]
6. API key read from `BuildKonfig.GEMINI_API_KEY` via `ApiKeyProvider` interface; adapter fails fast with `DomainError.ApiError(0, "Gemini API key not configured")` if key is empty/blank [NFR9]
7. Ktor client configured with `connectTimeoutMillis = 10_000` and `socketTimeoutMillis = 30_000` (no `requestTimeoutMillis` — SSE streams run 6-8s per NFR2), retry with exponential backoff (max 3 attempts), graceful error mapping to `DomainError` [NFR25]
8. Unit tests verify `GeminiResponseParser` handles: complete response, partial response, malformed SSE, empty buckets — test fixtures are `const val` JSON strings in test companion objects
9. `GeminiAreaIntelligenceProvider` injected via Koin as the `AreaIntelligenceProvider` implementation, replacing `MockAreaIntelligenceProvider`
10. `streamChatResponse` returns `emptyFlow()` (chat streaming is Story 4.x) — must not throw or crash
11. Gemini model name stored as a `companion object` constant (`GEMINI_MODEL = "gemini-2.5-flash"`), never hardcoded in URLs
12. All 3 build gates pass: `assembleDebug`, `allTests`, `lint`

## Tasks / Subtasks

- [x] Task 1: Add Ktor SSE dependency (AC: #1, #5)
  - [x] 1.1 SSE plugin is included in `ktor-client-core` 3.4.0 — no separate `ktor-client-sse` artifact needed
  - [x] 1.2 Verified SSE imports resolve from existing `ktor-client-core` dependency
- [x] Task 2: Create `ApiKeyProvider` interface + implementation (AC: #6)
  - [x] 2.1 Create `domain/provider/ApiKeyProvider.kt` — simple interface with `val geminiApiKey: String`
  - [x] 2.2 Create `data/remote/BuildKonfigApiKeyProvider.kt` — reads `BuildKonfig.GEMINI_API_KEY`
  - [x] 2.3 Wire in `DataModule.kt`: `single<ApiKeyProvider> { BuildKonfigApiKeyProvider() }`
- [x] Task 3: Create `GeminiPromptBuilder` (AC: #3)
  - [x] 3.1 Create `data/remote/GeminiPromptBuilder.kt` — builds structured prompt from `areaName` + `AreaContext`
  - [x] 3.2 Prompt must request JSON output with six buckets, each containing: `type`, `highlight`, `content`, `confidence` (HIGH/MEDIUM/LOW), `sources` array
  - [x] 3.3 Include time-of-day and day-of-week context in prompt for temporal adaptation
  - [x] 3.4 Include preferred language directive
  - [x] 3.5 Write unit test for prompt construction — verify all AreaContext fields included
- [x] Task 4: Create `GeminiResponseParser` (AC: #4, #8)
  - [x] 4.1 Create `data/remote/GeminiResponseParser.kt` — parses Gemini SSE JSON chunks into `BucketUpdate` emissions
  - [x] 4.2 Parse `candidates[0].content.parts[0].text` from each SSE chunk
  - [x] 4.3 Accumulate streamed text using a buffer; detect bucket boundaries via delimiter markers (prompt instructs Gemini to output each bucket as a separate JSON object separated by `\n---BUCKET---\n` markers) — emit `ContentDelta` per chunk
  - [x] 4.4 When bucket JSON is complete (delimiter detected), parse accumulated text to `BucketContent` and emit `BucketComplete`
  - [x] 4.5 When all 6 buckets done, emit `PortraitComplete` with POIs
  - [x] 4.6 Write 4 unit tests with canned SSE fixture strings as `const val` in test companion object: complete response, partial response, malformed SSE, empty buckets
- [x] Task 5: Create Ktor HTTP client factory (AC: #5, #7)
  - [x] 5.1 Create `data/remote/HttpClientFactory.kt` — configures `HttpClient` with SSE plugin, content negotiation, timeouts
  - [x] 5.2 Install `SSE` plugin, `ContentNegotiation` with JSON, `HttpTimeout` with `connectTimeoutMillis = 10_000` and `socketTimeoutMillis = 30_000` — do NOT set `requestTimeoutMillis` (SSE streams run 6-8s and would be killed mid-stream)
  - [x] 5.3 Wire client in `DataModule.kt` as singleton
- [x] Task 6: Create `GeminiAreaIntelligenceProvider` (AC: #1, #2, #4, #6, #7, #9, #10, #11)
  - [x] 6.1 Create `data/remote/GeminiAreaIntelligenceProvider.kt` implementing `AreaIntelligenceProvider`
  - [x] 6.2 Define `companion object { const val GEMINI_MODEL = "gemini-2.5-flash" }` — use this constant in URL construction, never hardcode model name
  - [x] 6.3 `streamAreaPortrait`: validate API key is not blank (fail fast with `DomainError.ApiError`) → build prompt → SSE request to `https://generativelanguage.googleapis.com/v1beta/models/$GEMINI_MODEL:streamGenerateContent?alt=sse&key={KEY}` → parse response → emit `BucketUpdate` flow
  - [x] 6.4 `streamChatResponse`: return `emptyFlow()` (full chat is Story 4.x) — do NOT use `TODO()` which would crash at runtime
  - [x] 6.5 Implement retry with exponential backoff: 3 attempts, 0s/1s/2s delays, map failures to `DomainError`. Only retry transient errors (timeout, 5xx, network) — never retry 4xx
  - [x] 6.6 Log all API calls and errors via `AppLogger`
  - [x] **Task dependencies:** Requires Tasks 2-5 to be complete before starting
- [x] Task 7: Update DI wiring (AC: #9)
  - [x] 7.1 In `DataModule.kt`: replace `MockAreaIntelligenceProvider()` with `GeminiAreaIntelligenceProvider(get(), get(), get(), get())`
  - [x] 7.2 Keep `MockAreaIntelligenceProvider` class in codebase (useful for testing/demos)
  - [x] 7.3 Verify Koin graph resolves correctly (assembleDebug passes)
- [x] Task 8: Build gates (AC: #12)
  - [x] 8.1 Run `./gradlew assembleDebug` — PASSED
  - [x] 8.2 Run `./gradlew allTests` — PASSED
  - [x] 8.3 Run `./gradlew lint` — PASSED

## Dev Notes

### Gemini REST API — SSE Streaming Endpoint

**Endpoint:**
```
POST https://generativelanguage.googleapis.com/v1beta/models/{MODEL}:streamGenerateContent?alt=sse&key={API_KEY}
```

**Request body:**
```json
{
  "contents": [{
    "parts": [{ "text": "prompt here" }]
  }]
}
```

**SSE response format:** Each chunk is a `GenerateContentResponse`:
```json
data: {"candidates":[{"content":{"parts":[{"text":"chunk of text"}],"role":"model"}}]}
```

The `alt=sse` query parameter is **critical** — without it you get a single JSON response, not streaming.

**Recommended model:** `gemini-2.5-flash` (fast, cost-effective for streaming area portraits).

### Ktor SSE Client Pattern (Ktor 3.4.0)

```kotlin
val client = HttpClient(engine) {
    install(SSE)
    install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    install(HttpTimeout) {
        connectTimeoutMillis = 10_000   // 10s to establish connection
        socketTimeoutMillis = 30_000    // 30s between chunks (SSE keep-alive)
        // Do NOT set requestTimeoutMillis — SSE streams run 6-8s total (NFR2)
        // and requestTimeout kills the entire request, including active streaming
    }
}

// SSE streaming call:
client.sse(
    urlString = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:streamGenerateContent",
    request = {
        method = HttpMethod.Post
        parameter("alt", "sse")
        parameter("key", apiKey)
        contentType(ContentType.Application.Json)
        setBody(requestBody)
    }
) {
    incoming.collect { event ->
        val data = event.data ?: return@collect
        // Parse JSON chunk from event.data
    }
}
```

**Key points:**
- SSE plugin is in `io.ktor:ktor-client-sse` — must add this dependency
- No additional Ktor engines needed — CIO/OkHttp/Darwin all support SSE
- SSE events have `.data`, `.id`, `.event` fields — we only need `.data`

### Prompt Engineering Strategy

The prompt must request structured JSON output for six buckets. Use Gemini's JSON mode or structured output. Each bucket response should include:
- `type`: one of SAFETY, CHARACTER, WHATS_HAPPENING, COST, HISTORY, NEARBY
- `highlight`: the "whoa" fact (1-2 sentences)
- `content`: supporting context (2-4 sentences)
- `confidence`: HIGH, MEDIUM, or LOW
- `sources`: array of `{title, url}` objects

Since SSE streams text incrementally, the parser must accumulate chunks and detect bucket boundaries. **Delimiter strategy:** The prompt must instruct Gemini to output each bucket as a separate JSON object, separated by `\n---BUCKET---\n` markers. This makes boundary detection reliable — the parser buffers text until it sees the delimiter, then parses the accumulated JSON into a `BucketContent`. The final bucket is followed by a `\n---POIS---\n` marker and a POI JSON array. This avoids fragile nested-JSON-boundary detection.

### Streaming Contract — Must Match MockAreaIntelligenceProvider

The existing `MockAreaIntelligenceProvider` establishes the emission pattern:
1. For each bucket: emit `ContentDelta(bucketType, textChunk)` repeatedly → emit `BucketComplete(bucketContent)`
2. After all 6 buckets: emit `PortraitComplete(pois)`

The UI (`SummaryStateMapper`, `SummaryViewModel`, `SummaryScreen`) already consumes this exact flow pattern. **Do not change the emission contract.**

### Error Handling → DomainError Mapping

| HTTP/Network Error | Maps To |
|--------------------|---------|
| Empty/blank API key | `DomainError.ApiError(0, "Gemini API key not configured")` — fail fast before network call |
| Timeout (connect) | `DomainError.NetworkError("Request timed out")` |
| No connectivity | `DomainError.NetworkError("No internet connection")` |
| 401/403 | `DomainError.ApiError(code, "Invalid API key")` |
| 429 Rate limited | `DomainError.ApiError(429, "Rate limited")` |
| 500+ Server error | `DomainError.ApiError(code, "Server error")` |
| Malformed JSON | `DomainError.ApiError(0, "Invalid response format")` |

### Retry Strategy

Wrap API call with exponential backoff:
- Attempt 1: immediate
- Attempt 2: delay 1s
- Attempt 3: delay 2s
- After 3 failures: emit error, stop

Only retry on transient errors (timeout, 5xx, network). Do NOT retry on 4xx (bad request, auth failure).

### Project Structure Notes

**New files to create:**
```
composeApp/src/commonMain/kotlin/com/areadiscovery/
├── domain/provider/ApiKeyProvider.kt
├── data/remote/
│   ├── BuildKonfigApiKeyProvider.kt
│   ├── GeminiAreaIntelligenceProvider.kt
│   ├── GeminiPromptBuilder.kt
│   ├── GeminiResponseParser.kt
│   └── HttpClientFactory.kt

composeApp/src/commonTest/kotlin/com/areadiscovery/
├── data/remote/
│   ├── GeminiResponseParserTest.kt
│   └── GeminiPromptBuilderTest.kt
```

**Files to modify:**
```
gradle/libs.versions.toml              — add ktor-client-sse
composeApp/build.gradle.kts            — add ktor-client-sse dependency
composeApp/src/commonMain/.../di/DataModule.kt — replace Mock with Gemini, add new bindings
```

**Do NOT modify:**
- `MockAreaIntelligenceProvider.kt` — keep for testing/demos
- Any domain models — they are stable
- Any UI code — UI already consumes the `BucketUpdate` flow correctly
- Platform modules — no platform-specific code needed for Ktor SSE

### Existing Patterns to Follow

- **Logging:** Use `AppLogger.d { }` / `AppLogger.e(throwable) { }` from `com.areadiscovery.util.AppLogger`
- **Testing:** Use `kotlin.test` assertions (`assertEquals`, `assertTrue`) — NOT `assert()` (fails Kotlin/Native). Use `runTest` coroutine test scope. Use Turbine (`app.cash.turbine:turbine:1.2.0`) for Flow testing.
- **JSON serialization:** `kotlinx.serialization` with `@Serializable` and `@SerialName` for field mapping
- **Koin wiring:** `single<Interface> { Implementation(get(), get()) }` pattern in module definitions
- **DI pattern:** Interface in `domain/provider/`, implementation in `data/remote/`
- **Error pattern:** Map all external errors to `DomainError` sealed class variants

### Anti-Patterns to Avoid

- Do NOT use Firebase AI Logic SDK — it's Android-only, breaks KMP
- Do NOT use Gemini Kotlin SDK — use REST via Ktor for KMP portability
- Do NOT expose `MutableStateFlow` — only relevant for ViewModels (not this story)
- Do NOT import `android.*` or `ios.*` in `commonMain` — all Ktor SSE code is common
- Do NOT log API keys — ever, even in debug
- Do NOT hardcode model name — use a constant (e.g., `GEMINI_MODEL = "gemini-2.5-flash"`)
- Do NOT parse SSE manually — use Ktor's SSE plugin which handles the SSE protocol

### Previous Story Intelligence (Story 2.1)

**Key learnings from Story 2.1:**
- Interface pattern preferred over `expect/actual` for testability (e.g., `LocationProvider` is an interface, not expect/actual)
- `FakeXxx` classes in `commonTest/fakes/` for test doubles
- `PrivacyPipeline` lives in `domain/service/` as a pure business rule class
- Platform DI wiring goes in `PlatformModule.android.kt` / `PlatformModule.ios.kt`
- Common DI wiring goes in `DataModule.kt`
- All tests pass on all platforms — test with `./gradlew allTests`
- Geocoder fallback priority pattern: try best option first → degrade gracefully

**Files created in Story 2.1 that this story depends on:**
- `domain/service/PrivacyPipeline.kt` — outputs area name strings (input to this story)
- `location/LocationProvider.kt` — provides GPS coordinates (consumed by PrivacyPipeline)
- `di/DataModule.kt` — where mock provider binding lives (must be updated)

### Git Intelligence

Recent commits show consistent patterns:
- Atomic commits per story with clear descriptions
- Code review findings addressed in follow-up commits
- Build gates always verified before marking done
- Story 2.1 had 3 commits (initial + 2 review rounds)

### References

- [Source: _bmad-output/planning-artifacts/epics.md — Epic 2, Story 2.2]
- [Source: _bmad-output/planning-artifacts/architecture.md — AI Provider Interface, Streaming Pipeline, Data Architecture]
- [Source: _bmad-output/planning-artifacts/prd.md — FR1, FR2, FR4, FR9, FR12, NFR1, NFR2, NFR9, NFR10, NFR11, NFR25]
- [Source: _bmad-output/planning-artifacts/ux-design-specification.md — Streaming UX, Bucket Visual Identity]
- [Source: _bmad-output/implementation-artifacts/2-1-location-service-and-privacy-pipeline.md — Previous Story Dev Notes]
- [Ktor SSE Client Docs](https://ktor.io/docs/client-server-sent-events.html)
- [Gemini API Streaming REST](https://github.com/google-gemini/cookbook/blob/main/quickstarts/rest/Streaming_REST.ipynb)
- [Ktor 3.4 Changelog](https://ktor.io/changelog/3.4/)

## Dev Agent Record

### Agent Model Used

Claude Opus 4.6 (claude-opus-4-6)

### Debug Log References

- Task 1 deviation: `ktor-client-sse` does not exist as a separate Maven artifact in Ktor 3.4.0. SSE client support (`io.ktor.client.plugins.sse.SSE`) is included in `ktor-client-core`. No additional dependency needed.
- `DomainErrorException` wrapper class created to bridge `DomainError` sealed class with Kotlin's exception system for flow error propagation.

### Completion Notes List

- Created `ApiKeyProvider` interface in domain layer and `BuildKonfigApiKeyProvider` implementation reading from BuildKonfig
- Created `GeminiPromptBuilder` with structured prompt requesting 6-bucket JSON output with `---BUCKET---` and `---POIS---` delimiters, including AreaContext fields (timeOfDay, dayOfWeek, preferredLanguage)
- Created `GeminiResponseParser` with SSE event text extraction and full response parsing (bucket JSON + POI JSON)
- Created `HttpClientFactory` configuring Ktor HttpClient with SSE, ContentNegotiation, and HttpTimeout plugins (10s connect, 30s socket, no request timeout)
- Created `GeminiAreaIntelligenceProvider` implementing full SSE streaming flow with API key validation, retry (3 attempts, exponential backoff), and DomainError mapping
- `streamChatResponse` returns `emptyFlow()` safely (Story 4.x scope)
- Updated `DataModule.kt` to wire all new components, replacing MockAreaIntelligenceProvider with GeminiAreaIntelligenceProvider
- MockAreaIntelligenceProvider kept in codebase for testing/demos
- 7 prompt builder tests + 6 response parser tests pass on all platforms (Android + iOS Simulator)
- All 3 build gates pass: assembleDebug, allTests, lint

### File List

**New files:**
- `composeApp/src/commonMain/kotlin/com/areadiscovery/domain/provider/ApiKeyProvider.kt`
- `composeApp/src/commonMain/kotlin/com/areadiscovery/data/remote/BuildKonfigApiKeyProvider.kt`
- `composeApp/src/commonMain/kotlin/com/areadiscovery/data/remote/GeminiPromptBuilder.kt`
- `composeApp/src/commonMain/kotlin/com/areadiscovery/data/remote/GeminiResponseParser.kt`
- `composeApp/src/commonMain/kotlin/com/areadiscovery/data/remote/HttpClientFactory.kt`
- `composeApp/src/commonMain/kotlin/com/areadiscovery/data/remote/GeminiAreaIntelligenceProvider.kt`
- `composeApp/src/commonTest/kotlin/com/areadiscovery/data/remote/GeminiPromptBuilderTest.kt`
- `composeApp/src/commonTest/kotlin/com/areadiscovery/data/remote/GeminiResponseParserTest.kt`
- `composeApp/src/commonTest/kotlin/com/areadiscovery/data/remote/GeminiAreaIntelligenceProviderTest.kt`

**Modified files:**
- `composeApp/src/commonMain/kotlin/com/areadiscovery/di/DataModule.kt`
- `composeApp/src/commonMain/kotlin/com/areadiscovery/domain/model/DomainError.kt` (added `DomainErrorException` wrapper class)

## Change Log

- 2026-03-04: Implemented Gemini adapter with SSE streaming, prompt builder, response parser, HTTP client factory, API key provider, and DI wiring (Story 2.2)

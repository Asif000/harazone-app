---
stepsCompleted: [1, 2, 3, 4, 5, 6, 7, 8]
lastStep: 8
status: 'complete'
completedAt: '2026-03-03'
inputDocuments:
  - '_bmad-output/planning-artifacts/product-brief-AreaDiscovery-2026-03-03.md'
  - '_bmad-output/planning-artifacts/prd.md'
  - '_bmad-output/planning-artifacts/prd-validation-report.md'
  - '_bmad-output/planning-artifacts/ux-design-specification.md'
workflowType: 'architecture'
project_name: 'AreaDiscovery'
user_name: 'Asifchauhan'
date: '2026-03-03'
---

# Architecture Decision Document

_This document builds collaboratively through step-by-step discovery. Sections are appended as we work through each architectural decision together._

## Project Context Analysis

### Requirements Overview

**Functional Requirements:**
41 FRs across 10 capability areas, phased across 4 development phases:
- **Phase 1a (Core Magic):** 10 FRs — Proactive area summary (6-bucket), interactive map with POI markers, simple AI chat, multilingual responses, streaming delivery, location detection, privacy foundations
- **Phase 1b (Complete MVP):** 22 FRs — Full chat with context, manual search, bookmarks, offline caching with question queue, adaptive content intelligence (visit tracking, home detection), emergency info, onboarding, sharing, feedback, data deletion
- **Phase 2:** 3 FRs — Voice interaction, push notification digest, safety alerts
- **Phase 3-4:** 6 FRs — Day planning, area comparison, trip summaries, local app toolkit, driving mode

The AI layer is the architectural spine — 70%+ of FRs depend on AI request/response flows. The six-bucket structured output model is the core data contract between AI and UI.

**Non-Functional Requirements:**
25 NFRs across 5 categories driving architectural decisions:

| Category | Count | Key Architectural Drivers |
|----------|-------|--------------------------|
| Performance | 8 | 2s streaming start, 5s cold start, 500ms cache load, 3s map render, <2% battery/hr |
| Security | 5 | API key protection (no backend proxy yet), TLS 1.2+, no raw GPS to APIs, encrypted local storage, user data deletion |
| Scalability | 4 | Per-area caching, $0.01/DAU/day API cost target, <100MB device cache, 10x growth without architectural changes |
| Accessibility | 4 | WCAG 2.1 AA, 48dp touch targets, TalkBack, text alternatives for map |
| Integration | 4 | AI API resilience, news API graceful degradation, map tile fallback, 10s timeout with retry |

**Scale & Complexity:**
- Primary domain: Cross-platform mobile (Kotlin Multiplatform + Compose Multiplatform)
- Complexity level: Medium-High
- Estimated architectural components: ~15-20 (location service, AI client, cache layer, streaming pipeline, area repository, bookmark store, offline queue, map integration, chat engine, search, visit tracker, home detector, share renderer, connectivity monitor, privacy manager)

### Technical Constraints & Dependencies

| Constraint | Source | Architectural Impact |
|-----------|--------|---------------------|
| **No backend server (V1)** | Product brief — solo dev, cost control | Client-to-API direct calls; API key security is a critical unsolved tension |
| **AI provider-agnostic design (Gemini first)** | User decision | Domain-layer interface defines the contract (prompt → structured six-bucket response as Flow&lt;Token&gt;). Gemini adapter is the V1 implementation. Architecture supports swapping providers, using different models per bucket, or adding fallback providers — without touching domain or UI layers. |
| **MapLibre for maps** | PRD platform requirements | Open-source, no Google dependency; tile caching built-in; custom marker rendering |
| **Room/SQLDelight for storage** | PRD platform requirements | Room (Android), SQLDelight (shared KMP) for offline cache, visit history, bookmarks |
| **Ktor for networking** | PRD platform requirements | KMP-native HTTP client; SSE/streaming support needed for AI API |
| **Koin for DI** | PRD platform requirements | KMP-compatible dependency injection |
| **KMP shared logic** | PRD platform requirements | Business logic in `commonMain`; platform-specific `expect/actual` for location, maps, storage |
| **Android API 26+ (8.0+)** | PRD platform requirements | Geofencing APIs available; WorkManager for background tasks |
| **Portrait-only Phase 1a** | UX spec | Simplifies layout; landscape deferred to Phase 1b |
| **No push notifications V1** | PRD scoping | No FCM/APNs infrastructure needed initially |

### Cross-Cutting Concerns Identified

1. **Streaming data flow + AI provider abstraction** — Unified provider interface produces `Flow<StructuredAreaResponse>`. Gemini adapter handles Gemini-specific SSE parsing. Future adapters (OpenAI, Claude, local models) implement the same interface. UI and domain layers never reference a specific provider.

2. **Offline/connectivity management** — Affects every feature. Four connectivity states (online, cached-offline, nearby-cache, fully-offline) require a connectivity-aware repository pattern with automatic fallback chain.

3. **Location privacy pipeline** — GPS → reverse geocode to area name → area name sent to APIs. This transformation must be enforced architecturally (not by convention) to guarantee NFR11.

4. **Error resilience** — NFR22-25 mandate graceful degradation for every external dependency. No single API failure cascades. Architecture needs a resilience wrapper pattern for all external calls.

5. **Caching strategy** — Per-area (not per-user) caching is both a cost model and data architecture decision. Cache key = area name + time window. Affects data layer, repository pattern, and storage schema.

6. **Accessibility** — WCAG 2.1 AA across all components. TalkBack, 48dp targets, color independence, reduced motion support. Must be built into component architecture from day one.

7. **Phased feature delivery** — Phase 1a must be complete without Phase 1b code. Architecture must support clean feature boundaries without over-engineering.

8. **API key security** — NFR9 requires keys not extractable from binary or interceptable. Without a backend proxy, this requires platform-secure key storage (Android Keystore) and certificate pinning at minimum. This remains the highest-risk architectural tension.

## Starter Template Evaluation

### Primary Technology Domain

Cross-platform mobile (Kotlin Multiplatform + Compose Multiplatform) based on project requirements analysis.

### Current Technology Versions (March 2026)

| Technology | Version | Role |
|-----------|---------|------|
| Kotlin | 2.3.10 | Language (2.3.20 coming Mar-Apr 2026) |
| Compose Multiplatform | 1.10.0 | Shared UI framework (stable: Android, iOS, desktop) |
| Ktor | 3.4.0 | HTTP client — AI API, News API, SSE/streaming |
| Koin | 4.x (4.2.0-RC2) | Dependency injection with compile-time safety |
| SQLDelight | 2.2.1 | KMP-native typed SQL — offline cache, bookmarks, visits |
| MapLibre Android | 11.11.0 | Map rendering — Vulkan backend |
| Gemini API | REST via Ktor | Not using Firebase AI Logic SDK (Android-only); Ktor keeps it KMP-portable and provider-agnostic |

### Starter Options Considered

| Option | Description | Verdict |
|--------|-------------|---------|
| **JetBrains KMP Wizard** (kmp.jetbrains.com) | Official project generator. Compose Multiplatform, Android + iOS targets. Always-current versions. | **Selected** — official, maintained, clean foundation |
| Android Studio KMP Plugin | IDE-integrated wizard, same output | Viable but less configurable |
| Clone harazone2 | Fork existing project with similar stack | Carries project-specific code, older versions |

### Selected Starter: JetBrains KMP Wizard + Manual Library Addition

**Rationale:** Official JetBrains generator ensures current Kotlin/Compose versions and correct Gradle configuration. Clean starting point without legacy code. Libraries added manually to `libs.versions.toml` for full control over dependency versions and module structure.

**Initialization:**
1. Generate via kmp.jetbrains.com with Android + iOS targets, Compose Multiplatform shared UI
2. Add to `libs.versions.toml`: Ktor 3.4.0, Koin 4.x, SQLDelight 2.2.1, MapLibre 11.11.0
3. Structure modules per Clean Architecture (defined in architectural decisions)

**Architectural Decisions Provided by Starter:**

| Decision Area | What the Wizard Provides |
|--------------|------------------------|
| Language & Runtime | Kotlin 2.3.10, KMP plugin configured |
| UI Framework | Compose Multiplatform 1.10.0 with `commonMain` shared UI |
| Build Tooling | Gradle with Kotlin DSL, version catalog (`libs.versions.toml`) |
| Source Sets | `commonMain`, `androidMain`, `iosMain` configured |
| Android Target | Minimum API configured, Compose integration |
| iOS Target | Framework export, Xcode project integration |
| Project Structure | Single `composeApp` module (expanded to multi-module by architecture) |

**What We Add Ourselves:**

| Technology | Purpose | Why Manual |
|-----------|---------|-----------|
| Ktor 3.4.0 | AI API + News API client with SSE/streaming | KMP-portable HTTP client |
| Koin 4.x | Dependency injection | KMP-compatible, compile-time safety via compiler plugin |
| SQLDelight 2.2.1 | Offline cache, bookmarks, visit history | KMP-native typed SQL |
| MapLibre 11.11.0 | Interactive map with POI markers | `expect/actual` platform wrappers |
| Gemini REST via Ktor | AI provider (V1 implementation) | Provider-agnostic; avoids Android-only Firebase SDK |

**Note:** Project initialization using the KMP wizard should be the first implementation story.

## Core Architectural Decisions

### Decision Priority Analysis

**Critical Decisions (Block Implementation):**
1. Project module structure — single module with package separation
2. Data architecture — per-bucket cache with TTL tiers
3. AI provider interface — streaming via SSE with `Flow<BucketUpdate>`
4. API key security — phased approach (BuildKonfig now, proxy later)

**Important Decisions (Shape Architecture):**
5. State management — MVVM + StateFlow
6. Navigation — Jetpack Compose Navigation
7. Testing — hybrid KMP-native + Android-specific

**Deferred Decisions (Post-MVP):**
- Backend proxy infrastructure (Phase 1b/V1.5)
- Push notification infrastructure (V1.5)
- Background location monitoring architecture (V2)
- Multi-module promotion (evaluate at Phase 1b)

### Project Structure

**Decision:** Single `composeApp` module with package-level Clean Architecture separation.

**Package structure:**
```
composeApp/src/commonMain/kotlin/com/areadiscovery/
├── domain/                    # Pure Kotlin — no platform dependencies
│   ├── model/                 # Area, Bucket, POI, ChatMessage, VisitHistory
│   ├── repository/            # Repository interfaces
│   └── usecase/               # GetAreaPortrait, SendChatQuery, etc.
├── data/                      # Implementation — Ktor, SQLDelight, API adapters
│   ├── remote/                # AI provider adapters, News API client
│   ├── local/                 # SQLDelight DAOs, cache manager
│   ├── repository/            # Repository implementations
│   └── mapper/                # API/DB ↔ domain model mappers
├── ui/                        # Compose screens + ViewModels
│   ├── summary/               # Summary screen + ViewModel
│   ├── map/                   # Map screen + ViewModel
│   ├── chat/                  # Chat screen + ViewModel
│   ├── saved/                 # Bookmarks screen + ViewModel
│   ├── search/                # Manual search screen + ViewModel
│   ├── components/            # Shared composables (StreamingText, BucketHeader, etc.)
│   └── theme/                 # MaterialTheme, colors, typography, spacing tokens
├── di/                        # Koin modules
├── location/                  # Location service abstraction
└── util/                      # Connectivity monitor, privacy pipeline
```

**Rationale:** Fast to develop for solo dev. Package naming mirrors module boundaries — if complexity warrants multi-module later, packages promote to modules with minimal refactoring. Convention enforces layer separation; compile-time enforcement deferred.

### Data Architecture

**Decision:** SQLDelight with per-bucket cache rows and three-tier TTL.

**Schema:**

| Table | Key | Purpose |
|-------|-----|---------|
| `area_bucket_cache` | `area_name + bucket_type + language` | Per-bucket cached content with independent TTL |
| `chat_messages` | `area_name + query_hash` | Cached Q&A, session-scoped |
| `bookmarks` | `area_name` | Saved areas with metadata |
| `visit_history` | `area_name + timestamp` | On-device visit tracking (never synced) |
| `offline_queue` | `id` | Pending questions with area name, priority, status; synced sequentially (max 1-2 concurrent) when online |

**Cache TTL Tiers:**

| Tier | Buckets | TTL | Rationale |
|------|---------|-----|-----------|
| Static | History, Character | 14 days | Content changes rarely |
| Semi-static | Cost, Nearby | 3 days | POIs and prices shift slowly |
| Dynamic | Safety, What's Happening | 12 hours | News, events, crime are time-sensitive |

**Cache Patterns:**
- **Stale-while-revalidate:** Serve expired cache immediately, refresh in background. User never sees empty buckets.
- **Per-bucket refresh:** Only expired buckets re-fetched, not the full summary. Directly supports $0.01/DAU/day cost target.
- **Language-aware caching:** Cache key includes language — switching languages triggers fresh fetch, not stale content.
- **No active invalidation in V1:** Pure TTL expiry. Backend-driven invalidation is V2.
- **Schema positions for future shared cache:** Per-bucket rows work equally in local DB or future server-side cache.

### Authentication & Security

**Decision:** Phased approach — BuildKonfig obfuscation now, backend proxy later.

| Phase | Approach | Risk Level |
|-------|----------|------------|
| Phase 1a | BuildKonfig + ProGuard/R8 obfuscation | Moderate — acceptable for validation with few hundred users |
| Phase 1b/V1.5 | Lightweight backend proxy (Cloud Functions or similar) | Low — keys never on device |

**Additional security measures (all phases):**
- TLS 1.2+ for all network communication (Ktor default)
- No raw GPS coordinates sent to APIs — area names only (enforced by location privacy pipeline)
- SQLDelight database encryption via SQLCipher (Phase 1b)
- Certificate pinning on AI API endpoints (Phase 1a)

### API & Communication Patterns

**Decision:** SSE streaming via Ktor with provider-agnostic interface.

**AI Provider Interface:**
```kotlin
interface AreaIntelligenceProvider {
    fun streamAreaPortrait(
        areaName: String,
        context: AreaContext
    ): Flow<BucketUpdate>

    fun streamChatResponse(
        query: String,
        areaName: String,
        conversationHistory: List<ChatMessage>
    ): Flow<ChatToken>
}
```

- `BucketUpdate`: sealed class carrying bucket type + content delta
- `AreaContext`: time of day, day of week, visit count, preferred language
- Gemini adapter: handles SSE parsing, structured JSON prompting, error mapping
- Future adapters (OpenAI, Claude, local models) implement same interface

**Error handling pattern:**
- All external API calls wrapped with: timeout (10s) → retry with exponential backoff (max 3 attempts) → fallback to cache → graceful degradation message
- No single API failure cascades to app-wide failure
- News API failure → "What's Happening" bucket falls back to AI knowledge

**Reverse Geocoding:**
- Android `Geocoder` via `expect/actual` (free, offline-capable)
- Extract locality/sublocality for area names
- Fallback: manual area name extraction from geocode result
- iOS: `CLGeocoder` via `actual` implementation (post-V1)

### Frontend Architecture

**Decision:** MVVM + StateFlow + Jetpack Compose Navigation.

**State management:**
- ViewModels expose `StateFlow<UiState>` to Compose screens
- `UiState` sealed class per screen:
  - `Loading` — GPS resolving, initial request
  - `Streaming(buckets: Map<BucketType, BucketState>)` — live AI streaming
  - `PartialCache(cached: Map<BucketType, BucketContent>, streaming: Map<BucketType, BucketState>)` — stale-while-revalidate: some buckets cached, others refreshing
  - `Complete` — all buckets rendered
  - `Cached` — full summary from cache (rendered with section-by-section fade-in, ~200ms per bucket, not instant wall-of-text)
  - `Error` — with recovery action
- Koin injects ViewModels with repository dependencies

**Navigation:**
- Jetpack Compose Navigation with `NavHost` + `NavController`
- 4-tab bottom nav: Summary, Map, Chat, Saved
- Type-safe route arguments (area name passed between screens)
- Chat screen receives area context from Summary via nav arguments

**Cached content animation:**
- Cached summaries use section-by-section fade-in (~200ms per bucket, ~1.2s total) to maintain the content rhythm without faking streaming
- Matches the reduced-motion fallback pattern from UX spec
- Distinguishes from live streaming (token-by-token) while avoiding jarring instant render

### Analytics Event Schema (Phase 1a Validation)

Minimum tracking events for validating success criteria:

| Event | Parameters | Validates |
|-------|-----------|-----------|
| `summary_viewed` | area_name, source (gps/search/cache) | Core engagement |
| `summary_scroll_depth` | area_name, max_bucket_reached, percent | 60%+ full summary target |
| `bucket_viewed` | area_name, bucket_type, duration_ms | Bucket-level engagement |
| `chat_query_sent` | area_name, query_length, source (inline_prompt/tab) | 2+ queries/session target |
| `map_opened` | area_name, poi_count | Map discovery engagement |
| `poi_tapped` | area_name, poi_name, poi_type | POI relevance |
| `bookmark_saved` | area_name | 15% bookmark rate target |
| `share_triggered` | area_name, bucket_type, share_target | Organic growth signal |
| `session_start` | has_gps, connectivity_state | Session context |
| `error_occurred` | error_type, recovery_action | Reliability tracking |

Implementation: Firebase Analytics via `expect/actual` wrapper for KMP portability.

### Infrastructure & Deployment

**Decision:** Minimal infrastructure for V1.

| Concern | V1 Approach |
|---------|------------|
| Hosting | No backend — client-direct-to-API |
| CI/CD | GitHub Actions — build, test, lint on PR |
| Distribution | Google Play internal testing → open beta |
| Monitoring | Firebase Crashlytics (free, KMP-compatible) |
| Analytics | Firebase Analytics (free tier) — event schema above |
| Logging | Kermit (KMP-native logging library) for debug logs |

### Testing Strategy

**Decision:** Hybrid KMP-native + Android-specific.

| Layer | Framework | Scope |
|-------|-----------|-------|
| `commonTest` | kotlin.test + Turbine + manual fakes | Domain use cases, repository logic, cache TTL, offline queue, provider interface contract tests |
| `androidUnitTest` | JUnit + MockK + Turbine | ViewModels, Android-specific integrations |
| `androidInstrumentedTest` | Compose UI testing | Screen-level interaction tests (Phase 1b) |

**Phase 1a validation spike:** Parallel-path the streaming UI with hardcoded mock data to validate the UX before the AI adapter is complete. Don't block UX validation on API integration.

### Decision Impact Analysis

**Implementation Sequence:**
1. Project setup (KMP wizard + dependencies)
2. Domain models + AI provider interface
3. **Parallel:** Gemini adapter + SSE streaming || Mock data streaming UI spike
4. SQLDelight schema + cache layer
5. Summary screen (ViewModel + streaming composables)
6. Map screen (MapLibre + POI markers)
7. Chat screen
8. Location service + reverse geocoding + privacy pipeline
9. Connectivity monitor + offline fallback
10. Analytics event integration

**Cross-Component Dependencies:**
- AI Provider Interface → used by Summary ViewModel, Chat ViewModel, Map ViewModel (POI generation)
- Cache Layer → used by all ViewModels as the local source of truth
- Location Service → feeds area name to AI provider and cache lookups
- Connectivity Monitor → drives fallback chain in all repositories
- Streaming pipeline (Flow) → connects AI adapter through repository to UI across all screens
- Analytics wrapper → integrated into ViewModels and repository actions

## Implementation Patterns & Consistency Rules

### Naming Patterns

**SQLDelight Naming:**
- Tables: `snake_case` plural — `area_bucket_cache`, `chat_messages`, `visit_history`
- Columns: `snake_case` — `area_name`, `bucket_type`, `expires_at`, `created_at`
- Primary keys: `id` (auto-increment Long) or composite key via `PRIMARY KEY (area_name, bucket_type, language)`
- Indexes: `idx_tablename_column` — `idx_area_bucket_cache_expires_at`

**Kotlin Code Naming:**
- Classes/Objects: `PascalCase` — `AreaPortrait`, `BucketUpdate`, `GeminiAdapter`
- Functions: `camelCase` — `getAreaPortrait()`, `streamBucketUpdates()`
- Properties/Variables: `camelCase` — `areaName`, `bucketType`, `isLoading`
- Constants: `SCREAMING_SNAKE_CASE` — `MAX_RETRY_COUNT`, `CACHE_TTL_STATIC_DAYS`
- Packages: `lowercase` — `com.areadiscovery.domain.model`
- Sealed classes: parent `PascalCase`, variants `PascalCase` — `UiState.Streaming`, `UiState.Cached`

**Compose Naming:**
- Composables: `PascalCase` — `BucketSectionHeader`, `StreamingTextContent`, `ConfidenceTierBadge`
- Composable files: match primary composable name — `BucketSectionHeader.kt`
- Preview functions: `PascalCase` + `Preview` suffix — `BucketSectionHeaderPreview`
- Theme tokens: accessed via `MaterialTheme.colorScheme`, `MaterialTheme.typography` — never hardcoded values

**File Naming:**
- Kotlin files: `PascalCase` matching primary class — `AreaRepository.kt`, `SummaryViewModel.kt`
- SQLDelight files: `snake_case` matching table — `area_bucket_cache.sq`
- Test files: source file + `Test` suffix — `AreaRepositoryTest.kt`, `SummaryViewModelTest.kt`

### Structure Patterns

**Package Organization Rules:**
- One primary class per file (small data classes can share a file)
- Interfaces in `domain/repository/`, implementations in `data/repository/`
- Use cases are single-purpose classes with `operator fun invoke()` — `GetAreaPortraitUseCase`
- ViewModels are one-per-screen — `SummaryViewModel`, `ChatViewModel`
- Shared composables in `ui/components/`, screen-specific composables in `ui/{screen}/components/`

**Test Location:**
- `commonTest` mirrors `commonMain` package structure exactly
- `androidTest` mirrors `androidMain` + `commonMain` Android-specific tests
- Test fakes/doubles in `commonTest/fakes/` — `FakeAreaIntelligenceProvider`, `FakeAreaRepository`

### Format Patterns

**AI Structured Output (Gemini JSON):**
```json
{
  "area_name": "Alfama, Lisbon",
  "buckets": [
    {
      "type": "safety",
      "highlight": "Pickpocketing increases near tourist clusters...",
      "content": "Alfama is generally safe during daytime...",
      "confidence": "high",
      "sources": [{"title": "...", "url": "..."}]
    }
  ],
  "pois": [
    {
      "name": "Castelo de São Jorge",
      "type": "landmark",
      "description": "...",
      "confidence": "high"
    }
  ]
}
```
- JSON field naming: `snake_case` (API boundary)
- Kotlin models: `camelCase` (with `@SerialName` annotation for mapping)
- Bucket types: lowercase enum — `safety`, `character`, `whats_happening`, `cost`, `history`, `nearby`
- Confidence levels: enum — `high`, `medium`, `low`

**Domain Models:**
- All domain models are `data class` or `sealed class` — immutable
- No nullable fields unless genuinely optional — prefer sealed class variants over nulls
- Domain models never reference SQLDelight or Ktor types — mappers in `data/mapper/`

### Communication Patterns

**Flow Conventions:**
- Repository methods returning live data: `Flow<T>`
- Repository methods returning single result: `suspend fun`
- ViewModels expose: `StateFlow<UiState>` (never `MutableStateFlow` publicly)
- One-shot events (navigation, toasts): `SharedFlow<UiEvent>` or Compose `LaunchedEffect`

**State Update Pattern:**
```kotlin
// ViewModel pattern — consistent across all screens
private val _uiState = MutableStateFlow<SummaryUiState>(SummaryUiState.Loading)
val uiState: StateFlow<SummaryUiState> = _uiState.asStateFlow()

// Updates via copy() for data classes, or direct sealed class assignment
_uiState.value = SummaryUiState.Streaming(buckets = updatedBuckets)
```

**Koin Module Pattern:**
```kotlin
// One Koin module per layer — consistent structure
val domainModule = module {
    factory { GetAreaPortraitUseCase(get()) }
}
val dataModule = module {
    single<AreaIntelligenceProvider> { GeminiAdapter(get()) }
    single<AreaRepository> { AreaRepositoryImpl(get(), get(), get()) }
}
val uiModule = module {
    viewModel { SummaryViewModel(get(), get()) }
}
```

### Process Patterns

**Error Handling:**
- Domain layer: custom sealed class `Result<T>` or `kotlin.Result` — never throw exceptions across layer boundaries
- Data layer: catch all API/DB exceptions, map to domain `Result.Error(DomainError)`
- UI layer: ViewModel maps `DomainError` to user-facing strings — never show raw exceptions
- Error types: `sealed class DomainError` — `NetworkError`, `ApiError(code, message)`, `CacheError`, `LocationError`

**Loading & Streaming States:**
- Every screen's `UiState` handles: initial load, streaming, success, cache hit, partial cache, error
- No global loading state — each ViewModel owns its state
- Streaming state carries per-bucket progress: `Map<BucketType, BucketState>` where `BucketState` is `Pending | Streaming(content) | Complete(content) | Error`

**Retry Pattern:**
```kotlin
// Consistent retry wrapper for all external calls
suspend fun <T> withRetry(
    maxAttempts: Int = 3,
    initialDelayMs: Long = 1000,
    maxDelayMs: Long = 10000,
    block: suspend () -> T
): Result<T>
```

**Cache Access Pattern:**
```kotlin
// Repository pattern — consistent for all data access
// 1. Check cache → 2. If valid, return cached → 3. If stale, return stale + fetch fresh → 4. If miss, fetch fresh
fun getAreaBucket(areaName: String, bucketType: BucketType, language: String): Flow<BucketResult>
```

### Enforcement Guidelines

**All AI Agents MUST:**
1. Follow the package structure exactly — no new top-level packages without architectural review
2. Use `MaterialTheme` tokens for all colors, typography, spacing — zero hardcoded values in composables
3. Never expose `MutableStateFlow` from ViewModels — always `StateFlow` via `.asStateFlow()`
4. Never reference SQLDelight or Ktor types in `domain/` package — all mapping in `data/mapper/`
5. Write tests for all new use cases and repository methods — `commonTest` for shared logic
6. Use `@SerialName` for all JSON field mapping — never rely on Kotlin property names matching JSON
7. Handle all error states — no `TODO()`, no swallowed exceptions, no empty catch blocks
8. Include `contentDescription` on all interactive composables — accessibility from day one

**Anti-Patterns to Reject:**
- Hardcoded colors/sizes in composables (use theme tokens)
- `var` state in ViewModels (use `MutableStateFlow`)
- Direct API/DB calls from ViewModels (go through repository)
- Platform-specific code in `commonMain` (use `expect/actual`)
- Nullable domain models where sealed class variants fit better
- Tests that depend on real API calls or real database

## Project Structure & Boundaries

### Complete Project Directory Structure

```
AreaDiscovery/
├── .github/
│   └── workflows/
│       └── ci.yml                              # Build, test, lint on PR
├── .gitignore
├── build.gradle.kts                            # Root build config
├── settings.gradle.kts                         # Module declaration
├── gradle.properties                           # Kotlin/Compose/Android properties
├── gradle/
│   ├── libs.versions.toml                      # Version catalog
│   └── wrapper/
│       └── gradle-wrapper.properties
├── local.properties                            # API keys (gitignored)
│
├── composeApp/
│   ├── build.gradle.kts                        # KMP + Compose + SQLDelight + BuildKonfig config
│   │
│   ├── src/
│   │   ├── commonMain/
│   │   │   ├── kotlin/com/areadiscovery/
│   │   │   │   ├── App.kt                      # Root Compose entry point
│   │   │   │   │
│   │   │   │   ├── domain/
│   │   │   │   │   ├── model/
│   │   │   │   │   │   ├── Area.kt             # Area name, coordinates, metadata
│   │   │   │   │   │   ├── AreaPortrait.kt     # Complete six-bucket portrait
│   │   │   │   │   │   ├── Bucket.kt           # BucketType enum, BucketContent data class
│   │   │   │   │   │   ├── BucketUpdate.kt     # Sealed class: streaming delta per bucket
│   │   │   │   │   │   ├── ChatMessage.kt      # User/AI message with metadata
│   │   │   │   │   │   ├── ChatToken.kt        # Streaming chat token
│   │   │   │   │   │   ├── Confidence.kt       # High/Medium/Low enum + source attribution
│   │   │   │   │   │   ├── DomainError.kt      # Sealed class: NetworkError, ApiError, CacheError, LocationError
│   │   │   │   │   │   ├── POI.kt              # Point of interest with type, description, confidence
│   │   │   │   │   │   ├── VisitHistory.kt     # Area + timestamp + visit count
│   │   │   │   │   │   └── AreaContext.kt       # Time, day, visit count, language for AI prompt
│   │   │   │   │   │
│   │   │   │   │   ├── provider/
│   │   │   │   │   │   ├── AreaIntelligenceProvider.kt   # AI provider interface (domain contract)
│   │   │   │   │   │   └── ApiKeyProvider.kt             # API key access interface
│   │   │   │   │   │
│   │   │   │   │   ├── repository/
│   │   │   │   │   │   ├── AreaRepository.kt            # Area portrait + cache access
│   │   │   │   │   │   ├── ChatRepository.kt            # Chat history + cache
│   │   │   │   │   │   ├── BookmarkRepository.kt        # Saved areas CRUD
│   │   │   │   │   │   ├── VisitRepository.kt           # Visit tracking, home detection
│   │   │   │   │   │   └── OfflineQueueRepository.kt    # Offline question queue
│   │   │   │   │   │
│   │   │   │   │   ├── service/
│   │   │   │   │   │   └── PrivacyPipeline.kt           # GPS → area name policy (domain business rule)
│   │   │   │   │   │
│   │   │   │   │   └── usecase/
│   │   │   │   │       ├── GetAreaPortraitUseCase.kt     # Orchestrates cache check + AI stream + cache write
│   │   │   │   │       ├── SendChatQueryUseCase.kt       # Chat with conversation context
│   │   │   │   │       ├── SearchAreaUseCase.kt          # Manual area search
│   │   │   │   │       ├── ToggleBookmarkUseCase.kt      # Add/remove bookmark
│   │   │   │   │       ├── RecordVisitUseCase.kt         # Track area visit
│   │   │   │   │       ├── DetectHomeAreaUseCase.kt      # Frequency-based home detection
│   │   │   │   │       ├── GetEmergencyInfoUseCase.kt    # Nearest hospitals, police, embassies
│   │   │   │   │       └── ProcessOfflineQueueUseCase.kt # Sequential queue processing on reconnect
│   │   │   │   │
│   │   │   │   ├── data/
│   │   │   │   │   ├── remote/
│   │   │   │   │   │   ├── GeminiAdapter.kt              # Gemini REST + SSE streaming implementation
│   │   │   │   │   │   ├── GeminiPromptBuilder.kt        # Six-bucket structured prompt construction
│   │   │   │   │   │   ├── GeminiResponseParser.kt       # SSE → BucketUpdate parsing
│   │   │   │   │   │   ├── BuildKonfigApiKeyProvider.kt  # ApiKeyProvider impl using BuildKonfig
│   │   │   │   │   │   ├── NewsApiClient.kt              # News API for "What's Happening" bucket
│   │   │   │   │   │   └── HttpClientFactory.kt          # Ktor client configuration (TLS, timeout, retry)
│   │   │   │   │   │
│   │   │   │   │   ├── local/
│   │   │   │   │   │   ├── AreaCacheDao.kt               # SQLDelight queries for area_bucket_cache
│   │   │   │   │   │   ├── BookmarkDao.kt                # SQLDelight queries for bookmarks
│   │   │   │   │   │   ├── VisitHistoryDao.kt            # SQLDelight queries for visit_history
│   │   │   │   │   │   ├── ChatCacheDao.kt               # SQLDelight queries for chat_messages
│   │   │   │   │   │   ├── OfflineQueueDao.kt            # SQLDelight queries for offline_queue
│   │   │   │   │   │   ├── CacheManager.kt               # TTL expiry logic, stale-while-revalidate orchestration
│   │   │   │   │   │   └── DatabaseFactory.kt            # expect/actual SQLDelight driver factory
│   │   │   │   │   │
│   │   │   │   │   ├── repository/
│   │   │   │   │   │   ├── AreaRepositoryImpl.kt         # Cache-first with streaming fallback
│   │   │   │   │   │   ├── ChatRepositoryImpl.kt
│   │   │   │   │   │   ├── BookmarkRepositoryImpl.kt
│   │   │   │   │   │   ├── VisitRepositoryImpl.kt
│   │   │   │   │   │   └── OfflineQueueRepositoryImpl.kt
│   │   │   │   │   │
│   │   │   │   │   └── mapper/
│   │   │   │   │       ├── BucketMapper.kt               # DB entity ↔ domain model
│   │   │   │   │       ├── PortraitMapper.kt             # API JSON ↔ domain model
│   │   │   │   │       ├── ChatMapper.kt
│   │   │   │   │       └── POIMapper.kt
│   │   │   │   │
│   │   │   │   ├── ui/
│   │   │   │   │   ├── navigation/
│   │   │   │   │   │   ├── AppNavigation.kt              # NavHost + route definitions
│   │   │   │   │   │   └── Routes.kt                     # Type-safe route constants
│   │   │   │   │   │
│   │   │   │   │   ├── summary/
│   │   │   │   │   │   ├── SummaryScreen.kt              # Full-screen continuous scroll
│   │   │   │   │   │   ├── SummaryViewModel.kt           # Streaming + cache + partial cache states
│   │   │   │   │   │   └── SummaryStateMapper.kt         # Pure Kotlin: BucketUpdate stream → UiState (testable in commonTest)
│   │   │   │   │   │
│   │   │   │   │   ├── map/
│   │   │   │   │   │   ├── MapScreen.kt                  # MapLibre + POI markers + bottom sheet
│   │   │   │   │   │   └── MapViewModel.kt               # POI data, selected marker state
│   │   │   │   │   │
│   │   │   │   │   ├── chat/
│   │   │   │   │   │   ├── ChatScreen.kt                 # Conversational UI + streaming responses
│   │   │   │   │   │   ├── ChatViewModel.kt              # Conversation history, streaming state
│   │   │   │   │   │   └── ChatStateMapper.kt            # Pure Kotlin: ChatToken stream → UiState
│   │   │   │   │   │
│   │   │   │   │   ├── saved/
│   │   │   │   │   │   ├── SavedScreen.kt                # Bookmarked areas list
│   │   │   │   │   │   └── SavedViewModel.kt
│   │   │   │   │   │
│   │   │   │   │   ├── search/
│   │   │   │   │   │   ├── SearchScreen.kt               # Manual area search + category chips
│   │   │   │   │   │   └── SearchViewModel.kt
│   │   │   │   │   │
│   │   │   │   │   ├── components/
│   │   │   │   │   │   ├── StreamingTextContent.kt       # Token-by-token text rendering
│   │   │   │   │   │   ├── BucketSectionHeader.kt        # Icon + title + streaming indicator
│   │   │   │   │   │   ├── HighlightFactCallout.kt       # Orange-border "whoa" fact
│   │   │   │   │   │   ├── ConfidenceTierBadge.kt        # AssistChip: Verified/Approximate/Limited
│   │   │   │   │   │   ├── InlineChatPrompt.kt           # Summary → Chat navigation bridge
│   │   │   │   │   │   ├── ShareCardRenderer.kt          # Bitmap share card generation
│   │   │   │   │   │   ├── POIDetailCard.kt              # Map bottom sheet POI card
│   │   │   │   │   │   └── OfflineStatusIndicator.kt     # Connectivity state banner (Phase 1b)
│   │   │   │   │   │
│   │   │   │   │   └── theme/
│   │   │   │   │       ├── Theme.kt                      # MaterialTheme with orange/beige/white palette
│   │   │   │   │       ├── Color.kt                      # Light + dark mode color schemes
│   │   │   │   │       ├── Typography.kt                 # Inter font, custom type scale
│   │   │   │   │       ├── Shape.kt                      # Rounded cards, pill chips
│   │   │   │   │       └── Spacing.kt                    # 8dp base unit spacing tokens
│   │   │   │   │
│   │   │   │   ├── di/
│   │   │   │   │   ├── AppModule.kt                      # Root Koin module aggregation
│   │   │   │   │   ├── DomainModule.kt                   # Use case bindings
│   │   │   │   │   ├── DataModule.kt                     # Repository + adapter bindings
│   │   │   │   │   └── UiModule.kt                       # ViewModel bindings
│   │   │   │   │
│   │   │   │   ├── location/
│   │   │   │   │   └── LocationProvider.kt               # expect: GPS + reverse geocode interface
│   │   │   │   │
│   │   │   │   └── util/
│   │   │   │       ├── ConnectivityMonitor.kt            # expect: network state as Flow<ConnectivityState>
│   │   │   │       ├── RetryHelper.kt                    # withRetry() exponential backoff wrapper
│   │   │   │       ├── AnalyticsTracker.kt               # expect: Firebase Analytics wrapper
│   │   │   │       └── DateTimeUtils.kt                  # Time-of-day, day-of-week context helpers
│   │   │   │
│   │   │   └── sqldelight/com/areadiscovery/
│   │   │       ├── area_bucket_cache.sq                  # Cache table: area_name, bucket_type, language, content, expires_at
│   │   │       ├── chat_messages.sq                      # Chat cache: area_name, query_hash, messages, session_id
│   │   │       ├── bookmarks.sq                          # Saved areas: area_name, created_at, metadata
│   │   │       ├── visit_history.sq                      # Visits: area_name, visited_at, visit_count
│   │   │       └── offline_queue.sq                      # Queue: id, area_name, query, status, priority, created_at
│   │   │
│   │   ├── androidMain/
│   │   │   └── kotlin/com/areadiscovery/
│   │   │       ├── MainApplication.kt                    # Android Application + Koin init
│   │   │       ├── MainActivity.kt                       # Single activity, Compose entry
│   │   │       ├── location/
│   │   │       │   └── AndroidLocationProvider.kt        # actual: FusedLocationProvider + Geocoder
│   │   │       ├── util/
│   │   │       │   ├── AndroidConnectivityMonitor.kt     # actual: ConnectivityManager
│   │   │       │   └── AndroidAnalyticsTracker.kt        # actual: Firebase Analytics
│   │   │       └── data/local/
│   │   │           └── AndroidDatabaseFactory.kt         # actual: Android SQLDelight driver
│   │   │
│   │   ├── iosMain/
│   │   │   └── kotlin/com/areadiscovery/
│   │   │       ├── MainViewController.kt                 # iOS Compose entry point
│   │   │       ├── location/
│   │   │       │   └── IosLocationProvider.kt            # actual: CLLocationManager + CLGeocoder
│   │   │       ├── util/
│   │   │       │   ├── IosConnectivityMonitor.kt         # actual: NWPathMonitor
│   │   │       │   └── IosAnalyticsTracker.kt            # actual: Firebase Analytics iOS
│   │   │       └── data/local/
│   │   │           └── IosDatabaseFactory.kt             # actual: iOS SQLDelight driver
│   │   │
│   │   ├── commonTest/
│   │   │   └── kotlin/com/areadiscovery/
│   │   │       ├── fakes/
│   │   │       │   ├── FakeAreaIntelligenceProvider.kt   # Emits predefined BucketUpdates
│   │   │       │   ├── FakeAreaRepository.kt
│   │   │       │   ├── FakeCacheManager.kt
│   │   │       │   └── FakeConnectivityMonitor.kt
│   │   │       ├── testdata/
│   │   │       │   ├── alfama_full_response.json         # Complete six-bucket sample
│   │   │       │   ├── sparse_area_response.json         # Data-sparse area sample
│   │   │       │   └── malformed_sse_stream.txt          # Error handling test fixture
│   │   │       ├── domain/usecase/
│   │   │       │   ├── GetAreaPortraitUseCaseTest.kt
│   │   │       │   ├── SendChatQueryUseCaseTest.kt
│   │   │       │   ├── DetectHomeAreaUseCaseTest.kt
│   │   │       │   └── ProcessOfflineQueueUseCaseTest.kt
│   │   │       ├── data/
│   │   │       │   ├── repository/
│   │   │       │   │   ├── AreaRepositoryImplTest.kt     # Cache hit/miss/stale paths
│   │   │       │   │   └── OfflineQueueRepositoryImplTest.kt
│   │   │       │   ├── remote/
│   │   │       │   │   ├── GeminiAdapterTest.kt          # Adapter orchestration: retry, timeout, error mapping
│   │   │       │   │   └── GeminiResponseParserTest.kt   # SSE parsing edge cases
│   │   │       │   └── local/
│   │   │       │       └── CacheManagerTest.kt           # TTL expiry logic
│   │   │       └── ui/
│   │   │           ├── summary/SummaryStateMapperTest.kt  # BucketUpdate → UiState transformation
│   │   │           └── chat/ChatStateMapperTest.kt        # ChatToken → UiState transformation
│   │   │
│   │   └── androidUnitTest/
│   │       └── kotlin/com/areadiscovery/
│   │           └── ui/
│   │               ├── summary/SummaryViewModelTest.kt   # Lifecycle, coroutine scope, Koin
│   │               ├── chat/ChatViewModelTest.kt
│   │               └── map/MapViewModelTest.kt
│   │
│   └── src/androidMain/
│       ├── AndroidManifest.xml                           # Permissions, single activity
│       └── res/
│           ├── values/
│           │   ├── strings.xml
│           │   └── themes.xml
│           └── mipmap-*/                                 # App icons
│
├── iosApp/
│   ├── iosApp.xcodeproj/
│   └── iosApp/
│       ├── Info.plist
│       ├── ContentView.swift                             # Compose Multiplatform host
│       └── GoogleService-Info.plist                      # Firebase config (iOS)
│
└── docs/                                                 # Project documentation
```

### Architectural Boundaries

**Layer Boundaries (enforced by package convention):**

| Boundary | Rule | Violation Example |
|----------|------|-------------------|
| `domain/` → nothing | Domain has zero dependencies on data, ui, or platform | `import com.areadiscovery.data.*` in domain |
| `data/` → `domain/` only | Data depends on domain interfaces, never ui | `import com.areadiscovery.ui.*` in data |
| `ui/` → `domain/` only | UI depends on domain models/use cases via ViewModel | `import com.areadiscovery.data.*` in ui |
| `data/remote/` ↔ `data/local/` | Remote and local don't import each other | Repository orchestrates both |
| `commonMain` → no platform imports | Shared code never imports android.* or ios.* | Use `expect/actual` instead |
| `domain/provider/` → defines contracts | AI provider + API key interfaces owned by domain | Implementations live in `data/remote/` |
| `domain/service/` → pure business rules | PrivacyPipeline enforces NFR11 architecturally | Never bypassed by data or ui layers |

**Data Flow:**

```
GPS → LocationProvider (expect/actual)
    → PrivacyPipeline (domain/service — strips GPS, outputs area name)
    → AreaRepository (checks cache → calls AI provider)
    → AreaIntelligenceProvider (domain/provider — streams BucketUpdates)
    → CacheManager (writes per-bucket to SQLDelight)
    → SummaryStateMapper (pure Kotlin — transforms to UiState)
    → SummaryViewModel (exposes StateFlow<UiState>)
    → SummaryScreen (Compose — renders)
```

**External Integration Points:**

| External Service | Interface | Adapter Location | Fallback |
|-----------------|-----------|-----------------|----------|
| Gemini REST API | `domain/provider/AreaIntelligenceProvider` | `data/remote/GeminiAdapter` | Cache → graceful degradation |
| News API | `NewsApiClient` | `data/remote/NewsApiClient` | AI knowledge fallback |
| MapLibre | Platform map composable | `ui/map/MapScreen` | Cached tiles → minimal base map |
| Firebase Analytics | `util/AnalyticsTracker` (expect/actual) | `util/AndroidAnalyticsTracker` | Silent no-op |
| Firebase Crashlytics | SDK init | `MainApplication` | Silent |
| Android Geocoder | `location/LocationProvider` (expect/actual) | `location/AndroidLocationProvider` | Manual search fallback |
| API Keys | `domain/provider/ApiKeyProvider` | `data/remote/BuildKonfigApiKeyProvider` | N/A — required |

### Requirements to Structure Mapping

| FR Category | FRs | Primary Packages |
|------------|-----|-----------------|
| Area Intelligence (FR1-7) | Proactive summary, six-bucket, adaptive | `domain/usecase/GetAreaPortrait`, `data/remote/GeminiAdapter`, `ui/summary/` |
| AI Conversation (FR8-13) | Chat, confidence, multilingual | `domain/usecase/SendChatQuery`, `ui/chat/`, `data/remote/GeminiAdapter` |
| Map & Discovery (FR14-16) | Map, POI markers, tap details | `ui/map/`, `ui/components/POIDetailCard` |
| Location & Search (FR17-19) | Manual search, location detection | `ui/search/`, `location/`, `domain/usecase/SearchArea` |
| Bookmarks (FR20-22) | Save, list, navigate | `ui/saved/`, `domain/usecase/ToggleBookmark`, `data/local/BookmarkDao` |
| Emergency Info (FR23-24) | Hospitals, police, embassies | `domain/usecase/GetEmergencyInfo`, `data/remote/GeminiAdapter` |
| Offline & Caching (FR25-28) | Cache, queue, nearby | `data/local/CacheManager`, `domain/usecase/ProcessOfflineQueue` |
| Privacy & Trust (FR29-31) | Feedback, on-device history | `domain/service/PrivacyPipeline`, `data/local/VisitHistoryDao` |
| Onboarding (FR32-33) | Permission flow, fallback | `ui/summary/` (permission), `ui/search/` |
| Engagement (FR34-35) | Share, home digest | `ui/components/ShareCardRenderer`, `domain/usecase/DetectHomeArea` |

**Cross-Cutting Concerns:**

| Concern | Files |
|---------|-------|
| Streaming pipeline | `data/remote/GeminiAdapter` → `data/repository/AreaRepositoryImpl` → `ui/summary/SummaryStateMapper` → `SummaryViewModel` |
| Offline/connectivity | `util/ConnectivityMonitor` → all repository impls → all ViewModels |
| Location privacy | `domain/service/PrivacyPipeline` (single enforcement point) |
| Error resilience | `util/RetryHelper` → all `data/remote/` clients |
| Caching | `data/local/CacheManager` → all repository impls |
| Analytics | `util/AnalyticsTracker` → ViewModels |
| Accessibility | Built into every `ui/components/` composable |

## Architecture Validation Results

### Coherence Validation ✅

**Decision Compatibility:**
All technology choices are harmonious and conflict-free:
- Kotlin 2.3.10 + Compose Multiplatform 1.10.0 + Ktor 3.4.0 + Koin 4.x + SQLDelight 2.2.1 — all KMP-native, version-compatible
- Gemini REST via Ktor (not Firebase AI Logic SDK) preserves KMP portability and supports provider-agnostic interface
- BuildKonfig for API keys aligns with KMP build system
- `multiplatform-settings` (russhwolf) for preferences — KMP-native DataStore equivalent, consistent with "pure KMP in commonMain" rule
- No contradictory decisions found

**Pattern Consistency:**
- MVVM + StateFlow aligns naturally with Compose recomposition model
- Flow-based streaming pipeline connects cleanly end-to-end: Ktor SSE → `Flow<BucketUpdate>` → StateMapper → `StateFlow<UiState>` → Compose
- Repository cache-first pattern aligns with SQLDelight typed queries
- Koin modules mirror layer boundaries (domain, data, ui)
- Naming conventions internally consistent across SQLDelight (snake_case), Kotlin (camelCase/PascalCase), Compose (PascalCase), and JSON API boundary (snake_case with `@SerialName`)

**Structure Alignment:**
- Directory tree supports every architectural decision
- `expect/actual` properly used for all platform-specific concerns: location, connectivity, analytics, database
- `domain/provider/` correctly owns the `AreaIntelligenceProvider` interface (domain contract)
- `domain/service/PrivacyPipeline` correctly enforces NFR11 as a domain business rule
- StateMapper extraction enables `commonTest` testing of view logic

### Requirements Coverage Validation ✅

**Functional Requirements Coverage:**

All 35 Phase 1a/1b FRs have full architectural support:

| FR Category | FRs | Architectural Support | Status |
|------------|-----|----------------------|--------|
| Area Intelligence | FR1-4 (Phase 1a) | `GetAreaPortraitUseCase`, `GeminiAdapter`, `StreamingTextContent`, `AreaContext` | ✅ |
| Area Intelligence | FR5-7 (Phase 1b) | `VisitRepository`, `DetectHomeAreaUseCase`, `SettingsScreen` + `PreferencesManager` (briefing config) | ✅ |
| AI Conversation | FR8-12 (Phase 1a/1b) | `SendChatQueryUseCase`, `ConfidenceTierBadge`, language in `AreaContext` + cache key | ✅ |
| AI Conversation | FR13 (Phase 2) | Deferred — architecture doesn't need to address yet | ✅ Deferred |
| Map & Discovery | FR14-16 (Phase 1a) | `MapScreen`, `POIDetailCard`, MapLibre built-in pan/zoom, `POIListView` (accessibility) | ✅ |
| Location & Search | FR17-19 (Phase 1a/1b) | `SearchScreen`, `SearchAreaUseCase`, `LocationProvider` | ✅ |
| Bookmarks | FR20-22 (Phase 1b) | `ToggleBookmarkUseCase`, `SavedScreen`, navigation | ✅ |
| Emergency Info | FR23-24 (Phase 1b) | `GetEmergencyInfoUseCase`, `EmergencyAccessButton` (top app bar, persistent) | ✅ |
| Offline & Caching | FR25-28 (Phase 1b) | `CacheManager`, `OfflineQueueRepository`, `ProcessOfflineQueueUseCase`, cached area browse list (FR27) | ✅ |
| Privacy & Trust | FR29-31 (Phase 1a/1b) | `PrivacyPipeline`, `ThumbsFeedbackRow` + `feedback` table, `SettingsScreen` (data deletion) | ✅ |
| Onboarding | FR32-33 (Phase 1b) | `OnboardingScreen` + `PermissionExplanationCard`, `SearchScreen` fallback | ✅ |
| Engagement | FR34-35 (Phase 1b/Phase 2) | `ShareCardRenderer`, home digest deferred to Phase 2 | ✅ |
| Advanced | FR36-41 (Post-MVP) | Deferred — acknowledged in phased decisions | ✅ Deferred |

**Non-Functional Requirements Coverage:**

All 25 NFRs have full architectural support:

| NFR Category | NFRs | Architectural Support | Status |
|-------------|------|----------------------|--------|
| Performance | NFR1-8 | Streaming pipeline (`Flow<BucketUpdate>`), cache load paths (`CacheManager`), `ConnectivityMonitor` (1s detection) | ✅ |
| Security | NFR9-13 | BuildKonfig + ProGuard (phased to proxy), TLS 1.2+ (`HttpClientFactory`), `PrivacyPipeline`, SQLCipher (Phase 1b), `SettingsScreen` (data deletion) | ✅ |
| Scalability | NFR14-17 | Per-area cache (`area_bucket_cache`), per-bucket refresh, `CacheManager.evictOldest()` with `last_accessed_at` LRU (<100MB), backend proxy later | ✅ |
| Accessibility | NFR18-21 | Theme tokens (`Color.kt`, `Typography.kt`), 48dp enforcement, `contentDescription` rule, `POIListView` (screen reader alternative) | ✅ |
| Integration | NFR22-25 | `RetryHelper` (3 attempts, exponential backoff), fallback chains, `HttpClientFactory` (10s timeout), News API → AI knowledge fallback | ✅ |

### Implementation Readiness Validation ✅

**Decision Completeness:**
- All 7 critical decisions documented with exact library versions
- Implementation patterns include code examples for every major pattern (ViewModel, Koin, retry, cache access, JSON format)
- 8 enforcement rules + 6 anti-patterns provide clear guardrails for AI agent consistency
- Analytics event schema (10 events) defined for Phase 1a validation metrics

**Structure Completeness:**
- 80+ files mapped in complete directory tree (including Party Mode additions)
- All source sets (`commonMain`, `androidMain`, `iosMain`, `commonTest`, `androidUnitTest`) fully defined
- SQLDelight `.sq` files mapped 1:1 with tables (6 tables including `feedback`)
- Test fakes, test data fixtures, and StateMapper tests included
- Onboarding, settings, feedback, and POI list view all have architectural homes

**Pattern Completeness:**
- Naming conventions cover 4 dimensions (SQL, Kotlin, Compose, files)
- All communication patterns specified (Flow, StateFlow, SharedFlow, LaunchedEffect)
- Process patterns documented with code (retry, cache access, error handling, loading/streaming states)
- Domain model rules clear (immutable, no nulls, no platform types)
- Anti-patterns explicitly listed for rejection

### Party Mode Refinements Applied

The following refinements were identified and approved during Party Mode review:

| # | Refinement | Resolves | New Files/Changes |
|---|-----------|----------|-------------------|
| 1 | Settings/Preferences infrastructure | FR7, FR31, NFR13 | `ui/settings/SettingsScreen.kt`, `ui/settings/SettingsViewModel.kt`, `data/local/PreferencesManager.kt` (using `multiplatform-settings` library) |
| 2 | Onboarding flow | FR32 | `ui/onboarding/OnboardingScreen.kt`, `ui/onboarding/OnboardingViewModel.kt`, `ui/onboarding/components/PermissionExplanationCard.kt` |
| 3 | Emergency one-tap access | FR24 | `ui/components/EmergencyAccessButton.kt` (top app bar, receives `isHighlighted: Boolean` from ViewModel) |
| 4 | Feedback mechanism | FR29 | `feedback.sq` SQLDelight table (key: `area_name + bucket_type`), `domain/repository/FeedbackRepository.kt`, `data/repository/FeedbackRepositoryImpl.kt`, `ui/components/ThumbsFeedbackRow.kt` |
| 5 | POI list view for accessibility | NFR21 | `ui/map/POIListView.kt` with `SegmentedButton` map/list toggle |
| 6 | Cache eviction strategy | NFR16 | `last_accessed_at` column added to `area_bucket_cache`, `CacheManager.evictOldest(maxBytes: Long)` method (LRU) |
| 7 | Nearby cached clarification | FR27 | Simple "Browse cached areas" list query from `area_bucket_cache` distinct area names — no geo-proximity math in V1 |
| 8 | FR-to-package mapping update | Consistency | Updated Requirements to Structure Mapping table below |

### Updated Requirements to Structure Mapping

| FR Category | FRs | Primary Packages |
|------------|-----|-----------------|
| Area Intelligence (FR1-7) | Proactive summary, six-bucket, adaptive, briefing config | `domain/usecase/GetAreaPortrait`, `data/remote/GeminiAdapter`, `ui/summary/`, `ui/settings/` (FR7) |
| AI Conversation (FR8-13) | Chat, confidence, multilingual | `domain/usecase/SendChatQuery`, `ui/chat/`, `data/remote/GeminiAdapter` |
| Map & Discovery (FR14-16) | Map, POI markers, tap details, accessible list | `ui/map/`, `ui/map/POIListView`, `ui/components/POIDetailCard` |
| Location & Search (FR17-19) | Manual search, location detection | `ui/search/`, `location/`, `domain/usecase/SearchArea` |
| Bookmarks (FR20-22) | Save, list, navigate | `ui/saved/`, `domain/usecase/ToggleBookmark`, `data/local/BookmarkDao` |
| Emergency Info (FR23-24) | Hospitals, police, embassies, one-tap access | `domain/usecase/GetEmergencyInfo`, `data/remote/GeminiAdapter`, `ui/components/EmergencyAccessButton` |
| Offline & Caching (FR25-28) | Cache, queue, nearby browse, eviction | `data/local/CacheManager`, `domain/usecase/ProcessOfflineQueue` |
| Privacy & Trust (FR29-31) | Feedback, on-device history, data deletion | `domain/service/PrivacyPipeline`, `data/local/VisitHistoryDao`, `ui/components/ThumbsFeedbackRow`, `ui/settings/` |
| Onboarding (FR32-33) | Permission flow, fallback | `ui/onboarding/OnboardingScreen`, `ui/onboarding/components/PermissionExplanationCard`, `ui/search/` |
| Engagement (FR34-35) | Share, home digest | `ui/components/ShareCardRenderer`, `domain/usecase/DetectHomeArea` |

### Architecture Completeness Checklist

**✅ Requirements Analysis**

- [x] Project context thoroughly analyzed (41 FRs, 25 NFRs, medium-high complexity)
- [x] Scale and complexity assessed (KMP, ~20 architectural components)
- [x] Technical constraints identified (no backend V1, AI provider-agnostic, MapLibre, KMP shared logic)
- [x] Cross-cutting concerns mapped (8 concerns: streaming, offline, privacy, resilience, caching, accessibility, phasing, API key security)

**✅ Architectural Decisions**

- [x] Critical decisions documented with versions (Kotlin 2.3.10, Compose MP 1.10.0, Ktor 3.4.0, Koin 4.x, SQLDelight 2.2.1, MapLibre 11.11.0)
- [x] Technology stack fully specified (including multiplatform-settings for preferences)
- [x] Integration patterns defined (AI provider interface, News API, MapLibre, Firebase Analytics/Crashlytics, Geocoder)
- [x] Performance considerations addressed (streaming pipeline, cache TTL tiers, stale-while-revalidate, LRU eviction)

**✅ Implementation Patterns**

- [x] Naming conventions established (SQLDelight, Kotlin, Compose, files — 4 dimensions)
- [x] Structure patterns defined (package organization, test location, one-class-per-file)
- [x] Communication patterns specified (Flow, StateFlow, SharedFlow, Koin modules)
- [x] Process patterns documented (error handling, retry, loading/streaming states, cache access)

**✅ Project Structure**

- [x] Complete directory structure defined (80+ files across all source sets)
- [x] Component boundaries established (7 boundary rules with violation examples)
- [x] Integration points mapped (7 external services with fallback chains)
- [x] Requirements to structure mapping complete (all 35 Phase 1a/1b FRs mapped)

### Architecture Readiness Assessment

**Overall Status:** READY FOR IMPLEMENTATION

**Confidence Level:** HIGH — all validation checks passed, all gaps resolved

**Key Strengths:**

- Provider-agnostic AI design allows future model swapping without architectural changes
- Streaming pipeline is coherent end-to-end (Ktor SSE → Flow → StateMapper → StateFlow → Compose)
- Per-bucket caching with three-tier TTL is both cost-efficient and architecturally clean
- Privacy enforced architecturally via PrivacyPipeline (not by developer convention)
- StateMapper extraction enables cross-platform testing of view logic in commonTest
- Every FR has a clear package mapping — no ambiguity about where code lives
- Complete test infrastructure with fakes, fixtures, and test coverage for all layers

**Areas for Future Enhancement (post-V1):**

- Multi-module promotion (evaluate at Phase 1b if package separation becomes unwieldy)
- Backend proxy for API key security (Phase 1b/V1.5)
- SQLCipher database encryption (Phase 1b)
- Voice interaction architecture (Phase 2)
- Background location monitoring architecture (V2)
- Push notification infrastructure (V1.5)

### Implementation Handoff

**AI Agent Guidelines:**

- Follow all architectural decisions exactly as documented
- Use implementation patterns consistently across all components
- Respect project structure and boundaries — no new top-level packages without review
- Refer to this document for all architectural questions
- Use enforcement guidelines and anti-pattern list as validation checkpoints

**First Implementation Priority:**

1. Project scaffold via JetBrains KMP Wizard + dependency setup (`libs.versions.toml`)
2. Domain models + AI provider interface (`domain/model/`, `domain/provider/`)
3. **Parallel:** Gemini adapter + SSE streaming (`data/remote/`) || Mock data streaming UI spike (`ui/summary/` with hardcoded data)
4. SQLDelight schema + cache layer (`data/local/`, `.sq` files)
5. Summary screen (ViewModel + streaming composables)
6. Map screen (MapLibre + POI markers + list view)
7. Chat screen
8. Location service + reverse geocoding + privacy pipeline
9. Connectivity monitor + offline fallback
10. Analytics event integration

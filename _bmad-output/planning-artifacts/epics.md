---
stepsCompleted: ['step-01-validate-prerequisites', 'step-02-design-epics', 'step-03-create-stories', 'step-04-final-validation']
inputDocuments:
  - '_bmad-output/planning-artifacts/prd.md'
  - '_bmad-output/planning-artifacts/architecture.md'
  - '_bmad-output/planning-artifacts/ux-design-specification.md'
---

# AreaDiscovery - Epic Breakdown

## Overview

This document provides the complete epic and story breakdown for AreaDiscovery, decomposing the requirements from the PRD, UX Design, and Architecture into implementable stories.

## Requirements Inventory

### Functional Requirements

**Area Intelligence (7 FRs)**
- FR1: User can view a proactive area summary organized into six knowledge buckets (Safety, Character, What's Happening, Cost, History, Nearby) based on their current GPS location `[Phase 1a]`
- FR2: User can view area summaries that adapt content based on time of day and day of week `[Phase 1a]`
- FR3: User can view area summary content as it streams in progressively, without waiting for full completion `[Phase 1a]`
- FR4: User can receive an area summary with available information for data-sparse or obscure locations, with the AI acknowledging limited knowledge rather than omitting content `[Phase 1a]`
- FR5: User can view differentiated content on return visits to a previously viewed area (only what's changed) `[Phase 1b]`
- FR6: User can have their home area automatically detected based on visit frequency `[Phase 1b]`
- FR7: User can configure area briefing behavior: Always, Only New Places, or Ask Me `[Phase 1b]`

**AI Conversation (6 FRs)**
- FR8: User can ask a location-aware question via text and receive an AI-generated response with source attribution `[Phase 1a]`
- FR9: User can view confidence tiering indicators on all AI-generated content `[Phase 1a]`
- FR10: User can maintain a multi-turn conversation within a session with context preserved `[Phase 1b]`
- FR11: User can follow outbound links to deeper sources referenced in AI responses `[Phase 1b]`
- FR12: User can receive area summaries and chat responses in their preferred language with local language context (place names, cultural terms, key phrases) naturally embedded `[Phase 1a]`
- FR13: User can ask questions via voice input and receive spoken responses `[Phase 2]`

**Map & Visual Discovery (3 FRs)**
- FR14: User can view an interactive map displaying AI-generated points of interest as markers `[Phase 1a]`
- FR15: User can tap any POI marker to view details including history, significance, and tips `[Phase 1a]`
- FR16: User can pan and zoom the map to explore surrounding areas `[Phase 1a]`

**Location & Search (3 FRs)**
- FR17: User can search for any area by name and view its full area portrait without being physically present `[Phase 1b]`
- FR18: User can use manual search as a fallback when location permission is denied `[Phase 1b]`
- FR19: System can detect significant location changes without continuous GPS polling `[Phase 1a]`

**Bookmarks & Saved Areas (3 FRs)**
- FR20: User can bookmark an area with a single tap `[Phase 1b]`
- FR21: User can view and access a list of all bookmarked areas `[Phase 1b]`
- FR22: User can navigate to a bookmarked area's summary from the saved list `[Phase 1b]`

**Emergency Information (2 FRs)**
- FR23: User can view nearest hospitals, police stations, and embassies sourced from verified data `[Phase 1b]`
- FR24: User can access emergency information within one tap from any screen `[Phase 1b]`

**Offline & Caching (4 FRs)**
- FR25: User can view previously accessed area summaries and chat responses without internet connectivity `[Phase 1b]`
- FR26: User can queue questions while offline that automatically send when connectivity returns `[Phase 1b]`
- FR27: User can see suggestions for nearby cached areas when current location has no cached data `[Phase 1b]`
- FR28: System can cache area summaries by area and time window so repeat requests are served locally `[Phase 1b]`

**Privacy & Trust (3 FRs)**
- FR29: User can provide thumbs up/down feedback on any AI-generated response `[Phase 1b]`
- FR30: System processes all location history on-device without sending raw GPS coordinates to external services `[Phase 1a]`
- FR31: User can view and delete all stored visit patterns and location history `[Phase 1b]`

**Onboarding & Permissions (2 FRs)**
- FR32: User can view an explanation of why location permission is needed before granting it `[Phase 1b]`
- FR33: User can use manual search to view area portraits before granting location permission `[Phase 1b]`

**Engagement & Sharing (2 FRs)**
- FR34: User can share area facts or summaries via the platform share sheet `[Phase 1b]`
- FR35: User can receive a weekly home turf digest notification with neighborhood updates `[Phase 2]`

**Advanced Exploration — Post-MVP (6 FRs)**
- FR36: User can receive safety alerts based on background location monitoring `[Phase 3]`
- FR37: User can generate an AI-powered day plan itinerary for an area `[Phase 3]`
- FR38: User can compare two or more areas side-by-side `[Phase 3]`
- FR39: User can view a summary of areas explored over a time period `[Phase 3]`
- FR40: User can receive recommendations for locally relevant apps in their current area/country `[Phase 3]`
- FR41: User can use a hands-free driving mode with voice-only interaction `[Phase 4]`

### NonFunctional Requirements

**Performance (8 NFRs)**
- NFR1: Area summary streaming must begin rendering content within 2 seconds of location lock
- NFR2: Full six-bucket area summary must complete within 8 seconds on a typical mobile connection (4G/LTE)
- NFR3: Cached area summaries must load within 500ms (no network dependency)
- NFR4: Map with POI markers must render within 3 seconds of area summary request
- NFR5: AI chat responses must begin streaming within 1.5 seconds of query submission
- NFR6: App background location monitoring must consume less than 2% battery per hour
- NFR7: App cold start to first content must complete within 5 seconds (GPS lock + summary stream start)
- NFR8: Offline mode detection and cache fallback must engage within 1 second of connectivity loss

**Security (5 NFRs)**
- NFR9: AI API keys must never be extractable from the client application binary or interceptable via network traffic inspection
- NFR10: All network communication must use TLS 1.2+ encryption
- NFR11: Location data sent to AI APIs must be area names only — raw GPS coordinates must never leave the device
- NFR12: On-device visit history and behavioral data must be stored in encrypted local storage
- NFR13: User must be able to delete all locally stored data (visit history, cache, bookmarks) from within the app

**Scalability (4 NFRs)**
- NFR14: Per-area caching must reduce repeat API calls — area summaries cached by area name + time window, not per-user
- NFR15: API cost must not exceed $0.01 per DAU per day, decreasing as per-area caching matures
- NFR16: Cache storage on device must stay under 100MB for typical usage (50-100 cached areas) with user-configurable clearing
- NFR17: API gateway infrastructure must handle 10x concurrent user growth without architectural changes

**Accessibility (4 NFRs)**
- NFR18: All UI text must meet WCAG 2.1 AA contrast ratios (4.5:1 for normal text, 3:1 for large text)
- NFR19: All interactive elements must have minimum 48dp touch targets per Material 3 guidelines
- NFR20: Area summaries and chat responses must be compatible with TalkBack (Android) screen reader
- NFR21: Map POI markers must have text-based alternatives accessible via list view for screen reader users

**Integration (4 NFRs)**
- NFR22: App must handle AI API provider downtime or rate limiting by showing cached content or informative fallback — never a crash or empty screen
- NFR23: News API failures must not block area summary generation — "What's Happening" bucket degrades gracefully to AI knowledge
- NFR24: Map tile provider failures must fall back to cached tiles or a minimal base map
- NFR25: All external API integrations must implement timeout (10s max) and resilient retry behavior — no single API failure may cascade into app-wide failure

### Additional Requirements

**From Architecture:**
- **Starter Template:** JetBrains KMP Wizard (kmp.jetbrains.com) with Android + iOS targets, Compose Multiplatform shared UI — this must be Epic 1, Story 1
- **Module Structure:** Single `composeApp` module with package-level Clean Architecture separation (domain/, data/, ui/, di/, location/, util/)
- **Database:** SQLDelight with per-bucket cache rows and three-tier TTL (Static: 14 days, Semi-static: 3 days, Dynamic: 12 hours)
- **Cache Pattern:** Stale-while-revalidate — serve expired cache immediately, refresh in background
- **Language-aware caching:** Cache key includes language — switching languages triggers fresh fetch
- **API Key Security:** Phase 1a uses BuildKonfig + ProGuard/R8 obfuscation; Phase 1b adds backend proxy
- **AI Provider Interface:** Provider-agnostic `AreaIntelligenceProvider` interface in domain layer; Gemini adapter as V1 implementation via SSE streaming
- **Structured Output:** Six-bucket JSON response format with confidence levels and source attribution per bucket
- **Frontend Architecture:** MVVM + StateFlow + Jetpack Compose Navigation with 4-tab bottom nav (Summary, Map, Chat, Saved)
- **Cached content animation:** Section-by-section fade-in (~200ms per bucket) for cached summaries — distinct from live streaming
- **Reverse Geocoding:** Android `Geocoder` via `expect/actual` (free, offline-capable) for GPS → area name
- **Error Handling:** Domain sealed class `Result<T>` or `kotlin.Result` — never throw exceptions across boundaries; consistent `withRetry()` wrapper for all external calls
- **Analytics:** Firebase Analytics via `expect/actual` wrapper — minimum event schema for Phase 1a validation (summary_viewed, scroll_depth, chat_query_sent, bookmark_saved, etc.)
- **Monitoring:** Firebase Crashlytics for crash reporting; Kermit for debug logging
- **CI/CD:** GitHub Actions — build, test, lint on PR
- **Distribution:** Google Play internal testing → open beta
- **Testing:** Hybrid KMP-native (commonTest with kotlin.test + Turbine + fakes) + Android-specific (JUnit + MockK + Turbine)
- **Validation Spike:** Parallel-path streaming UI with hardcoded mock data to validate UX before AI adapter is complete
- **Naming Conventions:** SQLDelight tables `snake_case`, Kotlin `PascalCase`/`camelCase`, composable files match primary composable name
- **Enforcement:** No platform-specific code in commonMain (use expect/actual), no hardcoded colors/sizes (use theme tokens), no direct API/DB calls from ViewModels

**From UX Design:**
- **Summary-first layout:** Full-screen area portrait card is the hero experience; map is one tap away (not split-screen) in Phase 1a
- **Portrait-only:** Locked via manifest in Phase 1a; landscape support deferred to Phase 1b
- **Design System:** Material 3 (Material You) heavily themed with custom `ColorScheme`, `Typography`, `Shapes`
- **Color Palette:** Primary orange (#E8722A), beige (#F5EDE3), dark charcoal (#2D2926), white — warm, exploratory, inviting
- **Typography:** Inter font family optimized for readability; Display → Headline → Title → Body → Label hierarchy
- **Custom Components Required:** StreamingTextContent, BucketSectionHeader, HighlightFactCallout, ConfidenceTierBadge, InlineChatPrompt, ShareCardRenderer, POIDetailCard, OfflineStatusIndicator
- **Map Pattern:** Google Maps-style three-stop bottom sheet (collapsed/half/full) for POI/area details
- **Streaming UX:** Token-by-token text rendering as signature brand interaction; bucket-by-bucket progressive reveal
- **Attention Choreography:** First 10 seconds must have deliberate emotional arc — what draws eye first, reveals second, invites exploration third
- **Chat Entry Point:** Inline chat prompt at end of summary + persistent input affordance for seamless transition from passive reading to active conversation
- **Responsive Design:** Primary target is compact phones (<600dp); medium phones get wider margins; large phones/foldables get max-width constraint (600dp centered); tablet two-pane is Phase 2
- **Accessibility:** WCAG 2.1 AA compliance, TalkBack heading navigation for buckets, LiveRegion.Polite for streaming, reduced motion support, RTL layout support, color independence (never color alone), 48dp touch targets, 200% font scale testing
- **Reduced Motion:** When system `prefers-reduced-motion`, streaming replaced with section fade-in (200ms); no parallax or spring animations
- **One-Handed Use:** All primary actions reachable in bottom 60% of screen
- **Shareable Content Cards:** Individual facts formatted as visually complete, self-contained share cards (bitmap rendering)
- **Dark Mode:** M3 dynamic color and dark theme support for nighttime use

### FR Coverage Map

- FR1: Epic 2 — Proactive area summary (six-bucket) via live AI
- FR2: Epic 2 — Time-of-day/day-of-week adapted content
- FR3: Epic 1 — Streaming progressive content rendering (validated with mock data)
- FR4: Epic 2 — Graceful degradation for data-sparse areas
- FR5: Epic 8 — Return visit differentiated content
- FR6: Epic 8 — Home area auto-detection
- FR7: Epic 8 — Configurable briefing behavior
- FR8: Epic 4 — Simple location-aware AI chat with source attribution
- FR9: Epic 2 — Confidence tiering indicators on AI content
- FR10: Epic 4 — Multi-turn conversation with context
- FR11: Epic 4 — Outbound source links in AI responses
- FR12: Epic 2 — Multilingual responses with local language context
- FR13: Epic 10 — Voice input and spoken responses
- FR14: Epic 3 — Interactive map with AI-generated POI markers
- FR15: Epic 3 — Tap POI marker for details
- FR16: Epic 3 — Pan and zoom map exploration
- FR17: Epic 5 — Search any area by name
- FR18: Epic 5 — Manual search fallback for location-denied users
- FR19: Epic 2 — Significant location change detection
- FR20: Epic 6 — One-tap bookmark
- FR21: Epic 6 — View bookmarked areas list
- FR22: Epic 6 — Navigate to bookmarked area summary
- FR23: Epic 9 — Emergency services info (hospitals, police, embassies)
- FR24: Epic 9 — Emergency info accessible within one tap
- FR25: Epic 7 — Offline access to cached summaries and chat
- FR26: Epic 7 — Offline question queue with auto-sync
- FR27: Epic 7 — Nearby cached area suggestions
- FR28: Epic 7 — Per-area time-windowed caching
- FR29: Epic 9 — Thumbs up/down feedback on AI responses
- FR30: Epic 2 — On-device location processing (privacy pipeline)
- FR31: Epic 9 — View and delete stored data
- FR32: Epic 5 — Location permission explanation
- FR33: Epic 5 — Manual search before granting location permission
- FR34: Epic 9 — Share area facts via platform share sheet
- FR35: Epic 10 — Weekly home turf digest notification
- FR36: Epic 11 — Safety alerts with background location
- FR37: Epic 11 — AI-powered day plan itinerary
- FR38: Epic 11 — Compare areas side-by-side
- FR39: Epic 11 — Trip summary over time period
- FR40: Epic 11 — Local app recommendations
- FR41: Epic 11 — Hands-free driving mode

### NFR Integration Strategy

NFRs are woven into epic stories as acceptance criteria, not a separate epic:

- **NFR1, NFR2, NFR7** (streaming/cold-start latency): Epic 2 story ACs
- **NFR3** (cached load time): Epic 7 story ACs
- **NFR4** (map render time): Epic 3 story ACs
- **NFR5** (chat streaming latency): Epic 4 story ACs
- **NFR6** (battery efficiency): Epic 2 story ACs (location service)
- **NFR8** (offline detection): Epic 7 story ACs
- **NFR9** (API key security): Epic 1 story ACs (BuildKonfig setup)
- **NFR10** (TLS): Epic 2 story ACs (Ktor client config)
- **NFR11** (privacy pipeline): Epic 2 story ACs
- **NFR12** (encrypted storage): Epic 7 story ACs (SQLDelight/SQLCipher)
- **NFR13** (data deletion): Epic 9 story ACs
- **NFR14, NFR15** (caching cost model): Epic 7 story ACs
- **NFR16** (storage budget): Epic 7 story ACs
- **NFR17** (infrastructure scaling): Deferred to backend proxy phase
- **NFR18, NFR19** (contrast, touch targets): Epic 1 story ACs (theme/design system)
- **NFR20** (TalkBack): Every UI epic's story ACs
- **NFR21** (POI text alternatives): Epic 3 story ACs
- **NFR22, NFR25** (API resilience): Epic 2 story ACs (retry wrapper)
- **NFR23** (News API fallback): Epic 2 story ACs
- **NFR24** (map tile fallback): Epic 3 story ACs

CI/CD (GitHub Actions) and analytics (Firebase) are explicit stories in Epic 1.

## Epic List

### Epic 1: Project Skeleton & Streaming UX Spike (Phase 1a)
Users can see a beautiful streaming area portrait rendered with mock data — validating the core UX before any API integration. Developers have a fully configured KMP project with CI/CD, theme, domain models, and the streaming composable architecture.
**FRs covered:** FR3 (streaming rendering)
**Infrastructure:** KMP Wizard project setup, package structure, design system (M3 theme, colors, typography, shapes), domain models (Area, Bucket, POI, Confidence), mock AI provider with hardcoded Alfama data, StreamingTextContent composable, BucketSectionHeader, HighlightFactCallout, ConfidenceTierBadge, summary screen, 4-tab navigation shell, GitHub Actions CI/CD, Firebase Analytics/Crashlytics setup
**NFRs as ACs:** NFR9 (BuildKonfig), NFR18-19 (contrast, touch targets)

### Epic 2: Live Area Portrait & AI Integration (Phase 1a)
Users open the app and within seconds receive a real, streaming, time-adapted, multilingual six-bucket area portrait with confidence indicators — the "whoa, I didn't know that" moment, powered by live AI.
**FRs covered:** FR1, FR2, FR4, FR9, FR12, FR19, FR30
**Infrastructure:** Gemini adapter with SSE streaming, AreaIntelligenceProvider interface, GeminiPromptBuilder, GeminiResponseParser, location service (expect/actual), privacy pipeline (GPS → area name), reverse geocoding, AreaContext (time/day/language), per-bucket caching foundation, connectivity monitor, RetryHelper
**NFRs as ACs:** NFR1, NFR2, NFR6, NFR7, NFR10, NFR11, NFR22-23, NFR25

### Epic 3: Interactive Map & POI Discovery (Phase 1a)
Users switch to an interactive map displaying AI-generated points of interest as markers, tap for details, and explore surrounding areas visually.
**FRs covered:** FR14, FR15, FR16
**Infrastructure:** MapLibre integration (expect/actual), POI markers from AI response, three-stop bottom sheet, POIDetailCard, accessible POI list view
**NFRs as ACs:** NFR4, NFR21, NFR24

### Epic 4: AI Chat — Ask About Your Area (Phase 1a + 1b)
Users ask follow-up questions about their area and get streaming AI responses with source attribution. Phase 1b adds multi-turn conversation with context and outbound source links.
**FRs covered:** FR8, FR10, FR11
**Infrastructure:** Chat screen, ChatViewModel, ChatStateMapper, InlineChatPrompt (summary → chat bridge), conversation history storage, source link rendering
**NFRs as ACs:** NFR5

### Epic 5: Manual Search & Onboarding (Phase 1b)
Users can explore any area worldwide by name, use the app fully without granting location permission, and get a value-first onboarding experience that demonstrates the app's worth before asking for trust.
**FRs covered:** FR17, FR18, FR32, FR33
**Infrastructure:** Search screen with category chips, SearchViewModel, location permission flow with value explanation, manual search as first-class fallback

### Epic 6: Bookmarks & Saved Areas (Phase 1b)
Users can save interesting areas with a single tap, browse their saved collection, and jump back to any bookmarked area's full portrait.
**FRs covered:** FR20, FR21, FR22
**Infrastructure:** Saved screen, SavedViewModel, BookmarkRepository, bookmarks SQLDelight table

### Epic 7: Offline Experience & Caching (Phase 1b)
Users access previously viewed area summaries and chat responses without connectivity, queue questions for later, and see nearby cached area suggestions when current location has no data.
**FRs covered:** FR25, FR26, FR27, FR28
**Infrastructure:** CacheManager with TTL tiers, offline queue (SQLDelight), connectivity-aware repository pattern, OfflineStatusIndicator, nearby cached area suggestions
**NFRs as ACs:** NFR3, NFR8, NFR12, NFR14-16

### Epic 8: Adaptive Intelligence & Return Visits (Phase 1b)
Users see differentiated content on return visits (only what's changed), have their home area automatically detected based on visit frequency, and can configure when they receive area briefings.
**FRs covered:** FR5, FR6, FR7
**Infrastructure:** VisitRepository, visit_history SQLDelight table, DetectHomeAreaUseCase, configurable briefing settings, return-visit content diffing

### Epic 9: Safety, Trust & Engagement (Phase 1b)
Users can access emergency services info from any screen, provide feedback on AI content, manage and delete their data, and share discoveries as beautiful cards via the platform share sheet.
**FRs covered:** FR23, FR24, FR29, FR31, FR34
**Infrastructure:** Emergency info via AI (verified sources), feedback UI (thumbs up/down), data management screen, ShareCardRenderer (bitmap), platform share sheet integration
**NFRs as ACs:** NFR13

### Epic 10: Voice Interaction & Push Notifications (Phase 2)
Users ask questions via voice and receive spoken responses. Home turf users receive a weekly neighborhood digest notification.
**FRs covered:** FR13, FR35
**Infrastructure:** STT/TTS integration, push notification infrastructure (FCM/APNs)

### Epic 11: Advanced Exploration (Phase 3-4)
Users receive safety alerts, generate AI-powered day plans, compare areas side-by-side, view trip summaries, get local app recommendations, and use hands-free driving mode.
**FRs covered:** FR36, FR37, FR38, FR39, FR40, FR41
**Infrastructure:** Background location monitoring, itinerary generation, area comparison UI, trip summary aggregation, local app toolkit, driving mode voice loop

---

## Epic 1: Project Skeleton & Streaming UX Spike

Users can see a beautiful streaming area portrait rendered with mock data — validating the core UX before any API integration. Developers have a fully configured KMP project with CI/CD, theme, domain models, and the streaming composable architecture.

### Story 1.1: KMP Project Initialization & Package Structure

As a **developer**,
I want a fully configured KMP project with the correct package structure and all dependencies declared,
So that all subsequent stories have a solid, buildable foundation to work from.

**Acceptance Criteria:**

**Given** a fresh project generated via JetBrains KMP Wizard (Android + iOS targets, Compose Multiplatform)
**When** the project is opened in Android Studio
**Then** it builds successfully for Android target with zero errors
**And** `libs.versions.toml` declares: Kotlin 2.3.x, Compose Multiplatform 1.10.x, Ktor 3.4.x, Koin 4.x, SQLDelight 2.2.x, MapLibre 11.x
**And** `composeApp/src/commonMain/kotlin/com/areadiscovery/` contains empty packages: `domain/model/`, `domain/repository/`, `domain/usecase/`, `domain/provider/`, `domain/service/`, `data/remote/`, `data/local/`, `data/repository/`, `data/mapper/`, `ui/summary/`, `ui/map/`, `ui/chat/`, `ui/saved/`, `ui/search/`, `ui/components/`, `ui/theme/`, `ui/navigation/`, `di/`, `location/`, `util/`
**And** `BuildKonfig` is configured to read API keys from `local.properties` (never committed to git) [NFR9]
**And** `local.properties` is listed in `.gitignore`
**And** Android manifest locks orientation to portrait [UX: portrait-only Phase 1a]
**And** Android minimum SDK is set to API 26

### Story 1.2: CI/CD Pipeline

As a **developer**,
I want automated build, test, and lint checks on every pull request,
So that code quality is enforced from the first commit.

**Acceptance Criteria:**

**Given** a GitHub Actions workflow file at `.github/workflows/ci.yml`
**When** a pull request is opened or updated
**Then** the workflow runs `./gradlew :composeApp:assembleDebug` and passes
**And** the workflow runs `./gradlew :composeApp:allTests` and passes
**And** the workflow runs lint checks and passes
**And** the workflow fails the PR check if any step fails

### Story 1.3: Design System & Theme

As a **user**,
I want the app to have a warm, inviting visual identity with excellent readability,
So that reading area content feels like pleasure, not work.

**Acceptance Criteria:**

**Given** a custom `MaterialTheme` defined in `ui/theme/`
**When** the app launches
**Then** the light theme uses: primary orange `#E8722A`, background beige `#F5EDE3`, text dark charcoal `#2D2926`, surface white
**And** a dark theme color scheme is defined and toggles with system setting
**And** typography uses Inter font family with the Material 3 type scale (Display → Label)
**And** all body text is minimum 16sp, labels minimum 12sp
**And** all text meets WCAG 2.1 AA contrast ratios: 4.5:1 for body text, 3:1 for large text/UI components [NFR18]
**And** shape system uses rounded corners consistent with card-based content
**And** spacing tokens use 8dp base unit defined in a `Spacing` object
**And** all interactive elements use minimum 48dp touch targets [NFR19]
**And** colors are accessed exclusively via `MaterialTheme.colorScheme` — zero hardcoded values

### Story 1.4: Domain Models & Mock AI Provider

As a **developer**,
I want core domain models and a mock AI provider that streams realistic area portrait data,
So that the UI can be built and validated independently of real API integration.

**Acceptance Criteria:**

**Given** domain models defined in `domain/model/`
**When** the models are used by the UI layer
**Then** `BucketType` enum contains: `SAFETY`, `CHARACTER`, `WHATS_HAPPENING`, `COST`, `HISTORY`, `NEARBY`
**And** `BucketUpdate` is a sealed class carrying bucket type + content delta for streaming
**And** `Confidence` enum contains: `HIGH`, `MEDIUM`, `LOW` with source attribution data class
**And** `AreaPortrait`, `Area`, `POI`, `ChatMessage`, `AreaContext`, `DomainError` data classes are defined as immutable
**And** `AreaIntelligenceProvider` interface is defined in `domain/provider/` with `streamAreaPortrait(areaName, context): Flow<BucketUpdate>`
**And** a `MockAreaIntelligenceProvider` in `data/remote/` emits hardcoded Alfama, Lisbon data across all six buckets with realistic delays (~200ms between tokens)
**And** mock data includes at least one "whoa" fact per bucket, mixed confidence levels, and 3+ POIs
**And** all domain models have no dependencies on SQLDelight, Ktor, or platform-specific types
**And** unit tests in `commonTest` verify mock provider emits all six bucket types and completes

### Story 1.5: Streaming Composables & Shared UI Components

As a **user**,
I want to see area content materialize on screen progressively — bucket by bucket, word by word,
So that the experience feels like a story unfolding rather than a page loading.

**Acceptance Criteria:**

**Given** custom composables defined in `ui/components/`
**When** `StreamingTextContent` receives a flow of text tokens
**Then** text renders token-by-token with visible progressive appearance
**And** when system `prefers-reduced-motion` is enabled, streaming is replaced with section fade-in (~200ms) [UX: reduced motion]
**And** `BucketSectionHeader` displays bucket icon + title + streaming indicator (animated dot while streaming, checkmark when complete)
**And** each `BucketSectionHeader` uses `semantics { heading() }` for TalkBack heading navigation [NFR20]
**And** `HighlightFactCallout` renders the "whoa" fact in a visually distinct callout (orange border accent)
**And** `ConfidenceTierBadge` displays as an AssistChip with icon + color + text label — never color alone [UX: color independence]
**And** `ConfidenceTierBadge` has `contentDescription = "Confidence level: $tierName"` [NFR20]
**And** all composables use `MaterialTheme` tokens exclusively — zero hardcoded colors or sizes

### Story 1.6: Summary Screen with Mock Data & Navigation Shell

As a **user**,
I want to open the app and immediately see a beautiful, streaming area portrait filling the screen,
So that I experience the "whoa" moment within seconds of launch.

**Acceptance Criteria:**

**Given** the app launches with mock data (no real API or GPS)
**When** the summary screen appears
**Then** a full-screen scrollable card renders the six-bucket area portrait for Alfama, Lisbon
**And** content streams bucket-by-bucket using `StreamingTextContent` within each bucket section
**And** the area name ("Alfama, Lisbon") displays prominently at the top
**And** each bucket uses `BucketSectionHeader` with the correct icon and title
**And** at least one `HighlightFactCallout` is visible per bucket
**And** `ConfidenceTierBadge` appears inline on AI-generated content
**And** `SummaryViewModel` exposes `StateFlow<SummaryUiState>` with states: `Loading`, `Streaming`, `Complete`
**And** `SummaryStateMapper` (pure Kotlin in `ui/summary/`) transforms `BucketUpdate` stream to `UiState` — tested in `commonTest`
**And** a 4-tab bottom `NavigationBar` is visible: Summary (active), Map (placeholder), Chat (placeholder), Saved (placeholder)
**And** `InlineChatPrompt` appears at the bottom of the summary content as a bridge to the Chat tab
**And** the screen is readable top-to-bottom with TalkBack enabled [NFR20]
**And** the screen functions correctly at 200% system font scale

### Story 1.7: Analytics & Crash Reporting Foundation

As a **developer**,
I want analytics and crash reporting wired from the first build,
So that we can validate success metrics and catch issues from day one.

**Acceptance Criteria:**

**Given** `AnalyticsTracker` defined as `expect` in `util/` with `actual` implementations per platform
**When** the app starts
**Then** Firebase Crashlytics is initialized and captures unhandled exceptions
**And** Firebase Analytics is initialized
**And** Kermit logger is configured for debug logging
**And** `AnalyticsTracker` exposes `trackEvent(name, params)` method usable from `commonMain`
**And** the summary screen fires a `summary_viewed` event with `source = "mock"` when the mock portrait finishes streaming

---

## Epic 2: Live Area Portrait & AI Integration

Users open the app and within seconds receive a real, streaming, time-adapted, multilingual six-bucket area portrait with confidence indicators — the "whoa, I didn't know that" moment, powered by live AI.

### Story 2.1: Location Service & Privacy Pipeline

As a **user**,
I want the app to know what area I'm in without sending my exact GPS coordinates to any external service,
So that I get area-relevant content while my precise location stays private.

**Acceptance Criteria:**

**Given** the user grants fine location permission
**When** the app detects the device location
**Then** `LocationProvider` (expect/actual) uses Android `FusedLocationProviderClient` to get current coordinates
**And** coordinates are passed to `PrivacyPipeline` (domain/service) which reverse geocodes via Android `Geocoder` to extract locality/sublocality area name
**And** only the area name string (e.g., "Alfama, Lisbon") is ever passed beyond the privacy pipeline — raw GPS coordinates never leave `LocationProvider` [NFR11]
**And** significant location change detection uses geofencing/significant location change APIs — no continuous GPS polling [FR19, NFR6]
**And** unit tests in `commonTest` verify `PrivacyPipeline` strips coordinates and outputs only area name strings
**And** if reverse geocoding fails, a fallback extracts the best available area name from the geocode result

### Story 2.2: Gemini Adapter & SSE Streaming

As a **developer**,
I want a provider-agnostic AI adapter that streams structured six-bucket area portraits from Gemini via SSE,
So that the app can deliver real-time AI content and swap providers in the future.

**Acceptance Criteria:**

**Given** `GeminiAdapter` implements `AreaIntelligenceProvider` in `data/remote/`
**When** `streamAreaPortrait(areaName, context)` is called
**Then** it sends a structured prompt via Ktor HTTP client to Gemini REST API using SSE streaming
**And** `GeminiPromptBuilder` constructs a prompt that requests six-bucket JSON output with confidence levels and source attribution per bucket
**And** the prompt includes `AreaContext`: time of day, day of week, preferred language [FR2, FR12]
**And** `GeminiResponseParser` parses SSE events into `Flow<BucketUpdate>` emissions
**And** all network calls use TLS 1.2+ (Ktor default) [NFR10]
**And** API key is read from `BuildKonfig` via `ApiKeyProvider` interface [NFR9]
**And** Ktor client configured with 10s timeout, retry with exponential backoff (max 3 attempts), and graceful error mapping to `DomainError` [NFR25]
**And** unit tests in `commonTest` verify `GeminiResponseParser` handles: complete response, partial response, malformed SSE, empty buckets
**And** `GeminiAdapter` is injected via Koin as the `AreaIntelligenceProvider` implementation, replacing `MockAreaIntelligenceProvider`

### Story 2.3: Area Repository & Caching Foundation

As a **user**,
I want area summaries to load instantly when I revisit an area, and refresh in the background if they're stale,
So that I always see content immediately and it stays fresh.

**Acceptance Criteria:**

**Given** `AreaRepositoryImpl` in `data/repository/` orchestrates cache and AI provider
**When** an area portrait is requested
**Then** the repository checks `area_bucket_cache` SQLDelight table with key `area_name + bucket_type + language`
**And** if cache is valid (within TTL), returns cached content immediately as `Flow<BucketUpdate>` [NFR3 target: <500ms]
**And** if cache is stale (past TTL), returns stale content immediately AND triggers background refresh (stale-while-revalidate)
**And** if cache miss, streams from AI provider and writes each bucket to cache as it completes
**And** TTL tiers are enforced: History/Character = 14 days, Cost/Nearby = 3 days, Safety/What's Happening = 12 hours
**And** cache key includes language — switching languages triggers fresh fetch
**And** SQLDelight schema for `area_bucket_cache` includes: `area_name`, `bucket_type`, `language`, `content`, `highlight`, `confidence`, `sources_json`, `expires_at`, `created_at`
**And** unit tests verify all cache paths: hit, stale-revalidate, miss

### Story 2.4: Connectivity Monitor & Error Resilience

As a **user**,
I want the app to handle network problems gracefully without ever showing an error screen,
So that I always see something useful regardless of connectivity.

**Acceptance Criteria:**

**Given** `ConnectivityMonitor` (expect/actual) in `util/`
**When** network state changes
**Then** it emits `Flow<ConnectivityState>` with states: `Online`, `Offline`
**And** Android implementation uses `ConnectivityManager` callback API
**And** `RetryHelper.withRetry()` is available as a consistent wrapper for all external calls: max 3 attempts, exponential backoff (1s/2s/4s), max 10s delay [NFR25]
**And** when AI API is down or rate-limited, the repository serves cached content with a subtle indicator that content may not be current [NFR22]
**And** when News API fails, "What's Happening" bucket falls back to AI general knowledge — never blocks the full summary [NFR23]
**And** no single API failure cascades to app-wide failure [NFR25]
**And** the app never shows a crash, empty screen, or raw error message to the user

### Story 2.5: Live Summary Screen Integration

As a **user**,
I want to open the app and see a real area portrait for my actual location stream onto the screen within seconds,
So that I experience the genuine "whoa, I didn't know that" moment.

**Acceptance Criteria:**

**Given** the app has location permission and network connectivity
**When** the user opens the app
**Then** GPS locks and `LocationProvider` delivers coordinates to `PrivacyPipeline`
**And** area name is resolved and passed to `AreaRepository`
**And** `SummaryViewModel` connects to `GetAreaPortraitUseCase` which orchestrates cache check → AI stream → cache write
**And** streaming content begins rendering on the summary screen within 2 seconds of location lock [NFR1]
**And** full six-bucket summary completes within 8 seconds on 4G/LTE [NFR2]
**And** cold start to first visible content completes within 5 seconds [NFR7]
**And** the area name updates dynamically based on resolved location
**And** `AreaContext` passes current time of day and day of week to the AI prompt [FR2]
**And** for data-sparse areas, AI acknowledges limited knowledge rather than omitting content [FR4]
**And** content displays in user's preferred language with local terms naturally embedded [FR12]
**And** confidence tiering is visible on all AI-generated content [FR9]
**And** analytics fires `summary_viewed` with `source = "gps"`, `area_name`, and `summary_scroll_depth` events

---

## Epic 3: Interactive Map & POI Discovery

Users switch to an interactive map displaying AI-generated points of interest as markers, tap for details, and explore surrounding areas visually.

### Story 3.1: MapLibre Integration & Base Map

As a **user**,
I want to see an interactive map centered on my current area that I can pan and zoom,
So that I can visually explore my surroundings.

**Acceptance Criteria:**

**Given** the user taps the Map tab in bottom navigation
**When** the map screen loads
**Then** a MapLibre map renders centered on the current area within 3 seconds [NFR4]
**And** the map uses `expect/actual` wrappers for KMP portability
**And** the user can pan and zoom freely using standard touch gestures [FR16]
**And** if map tile loading fails, cached tiles are served or a minimal base map is shown [NFR24]
**And** `MapViewModel` exposes `StateFlow<MapUiState>` with current area name and POI data
**And** the map fills the full screen width with a collapsible bottom sheet overlay
**And** the Map tab in bottom nav shows as active

### Story 3.2: AI-Generated POI Markers

As a **user**,
I want to see interesting points of interest appear as markers on the map based on AI recommendations,
So that I can discover hidden gems and notable places I wouldn't have found on my own.

**Acceptance Criteria:**

**Given** an area portrait has been loaded (from Epic 2 or cache)
**When** the map screen is displayed
**Then** AI-generated POIs from the area portrait are rendered as map markers [FR14]
**And** markers use distinct icons per POI type (landmark, food, culture, nature, etc.)
**And** POI data comes from the same `AreaIntelligenceProvider` response used by the summary screen — no duplicate AI calls
**And** each marker has `contentDescription = "[POI name], [category]"` for TalkBack [NFR21]
**And** a mini pin count or preview badge on the Map tab button signals content is ready before the user taps
**And** analytics fires `map_opened` with `area_name` and `poi_count`

### Story 3.3: POI Detail Card & Bottom Sheet

As a **user**,
I want to tap a POI marker and see its history, significance, and tips in a detail card,
So that I can learn about interesting places without leaving the map.

**Acceptance Criteria:**

**Given** POI markers are displayed on the map
**When** the user taps a marker
**Then** a three-stop bottom sheet slides up (collapsed: POI name + category; half: full description + tips; full: deep detail with source links) [FR15]
**And** the `POIDetailCard` composable displays: POI name, type, description, confidence tier, and source attribution
**And** the bottom sheet has both drag and tap-to-expand/collapse controls (gesture alternatives for motor accessibility)
**And** a bookmark action is visible on the POI card (wired in Epic 6)
**And** a share action is visible on the POI card (wired in Epic 9)
**And** analytics fires `poi_tapped` with `area_name`, `poi_name`, `poi_type`
**And** the detail card is readable top-to-bottom with TalkBack

### Story 3.4: POI Accessible List View

As a **user relying on a screen reader**,
I want to browse all POIs in a text-based list as an alternative to the map,
So that I can discover points of interest without needing visual map interaction.

**Acceptance Criteria:**

**Given** POI data is available for the current area
**When** the user activates the list view toggle on the map screen
**Then** a scrollable list of all POIs replaces the map view [NFR21]
**And** each list item shows: POI name, type, description snippet, and confidence tier
**And** tapping a list item opens the same `POIDetailCard` bottom sheet as marker tap
**And** the list is fully navigable via TalkBack with meaningful content descriptions
**And** the user can toggle back to map view at any time

---

## Epic 4: AI Chat — Ask About Your Area

Users ask follow-up questions about their area and get streaming AI responses with source attribution. Phase 1b adds multi-turn conversation with context and outbound source links.

### Story 4.1: Simple Chat Screen & Streaming Responses

As a **user**,
I want to ask a question about my area and see the AI's answer stream in word by word,
So that I can explore deeper when the area summary sparks my curiosity.

**Acceptance Criteria:**

**Given** the user navigates to the Chat tab or taps the `InlineChatPrompt` from the summary
**When** the user types a question and submits it
**Then** a streaming AI response begins rendering within 1.5 seconds [NFR5, FR8]
**And** the chat uses `AreaIntelligenceProvider.streamChatResponse()` with the current area name as context
**And** responses include source attribution (source name + URL) displayed inline
**And** confidence tiering is visible on the AI response
**And** `ChatViewModel` exposes `StateFlow<ChatUiState>` with states: `Idle`, `Streaming`, `Complete`, `Error`
**And** `ChatStateMapper` transforms `ChatToken` stream to `UiState` — tested in `commonTest`
**And** the chat input field has `contentDescription = "Ask a question about $areaName"` [NFR20]
**And** the area name context is displayed at the top of the chat screen
**And** analytics fires `chat_query_sent` with `area_name` and `query_length`

### Story 4.2: Multi-Turn Conversation with Context

As a **user**,
I want to ask follow-up questions that build on my previous questions within the same session,
So that I can have a natural conversation exploring a topic in depth.

**Acceptance Criteria:**

**Given** the user has already asked one or more questions in the current chat session
**When** the user asks a follow-up question
**Then** the full conversation history is sent to the AI provider as context [FR10]
**And** the AI response reflects awareness of previous questions and answers
**And** conversation history is stored in `chat_messages` SQLDelight table keyed by `area_name + session_id`
**And** conversation history is scoped to the current session — a new session starts when the area changes or the app restarts
**And** the chat screen displays the full conversation thread (user messages + AI responses) in chronological order
**And** cached chat responses are served when the same question is asked for the same area

### Story 4.3: Source Links & Deep Reading

As a **user**,
I want to tap on source links in AI responses to read the original content,
So that I can verify information or explore topics beyond what the AI summarized.

**Acceptance Criteria:**

**Given** an AI chat response contains source attribution with URLs
**When** the source links are displayed
**Then** each source appears as a tappable link with the source title [FR11]
**And** tapping a link opens the URL in the device's default browser or in-app browser
**And** source links are visually distinct from regular text (underlined, themed color)
**And** sources have `contentDescription = "Source: $sourceTitle, opens in browser"` for TalkBack
**And** if a response has no sources, no source section is shown (no empty "Sources:" header)

---

## Epic 5: Manual Search & Onboarding

Users can explore any area worldwide by name, use the app fully without granting location permission, and get a value-first onboarding experience that demonstrates the app's worth before asking for trust.

### Story 5.1: Manual Area Search

As a **user**,
I want to search for any area by name and see its full area portrait,
So that I can explore places I'm planning to visit or curious about without being there.

**Acceptance Criteria:**

**Given** the user taps the search icon or navigates to the Search screen
**When** the user types an area name (e.g., "Shibuya, Tokyo") and submits
**Then** the app resolves the area name and requests a full area portrait from `AreaRepository` [FR17]
**And** the summary screen displays the streaming portrait for the searched area
**And** the search screen shows category chips (e.g., "Popular cities," "Nearby areas") as zero-state suggestions rather than a blank input [UX: Google Maps pattern]
**And** `SearchViewModel` handles debounced input and search history
**And** recent searches are persisted locally and shown as suggestions
**And** analytics fires `summary_viewed` with `source = "search"` and `area_name`

### Story 5.2: Location Permission Flow & Value-First Onboarding

As a **first-time user**,
I want to understand why the app needs my location before being asked to grant it,
So that I feel informed and in control rather than pressured.

**Acceptance Criteria:**

**Given** the app is launched for the first time
**When** the onboarding flow begins
**Then** the user sees a brief explanation of what AreaDiscovery does and why location enables proactive discovery [FR32]
**And** the explanation uses warm, non-technical language focused on the benefit ("See what's special about wherever you are")
**And** the user can choose to grant location permission OR skip to manual search
**And** if the user skips, the search screen appears immediately with full functionality [FR33, FR18]
**And** there is no permission wall — all features except GPS-triggered summaries work without location
**And** if permission is denied, a non-intrusive prompt appears after the user has viewed one manual search result, offering to enable location for proactive discovery
**And** the prompt is shown at most once per session — no nagging

---

## Epic 6: Bookmarks & Saved Areas

Users can save interesting areas with a single tap, browse their saved collection, and jump back to any bookmarked area's full portrait.

### Story 6.1: Bookmark an Area

As a **user**,
I want to save an interesting area with a single tap,
So that I can easily return to it later.

**Acceptance Criteria:**

**Given** the user is viewing an area summary, POI detail card, or chat response
**When** the user taps the bookmark icon
**Then** the area is saved to the `bookmarks` SQLDelight table with area name, timestamp, and cached summary metadata [FR20]
**And** the bookmark icon toggles to a filled state indicating saved
**And** tapping again removes the bookmark (toggle behavior)
**And** `ToggleBookmarkUseCase` handles add/remove logic
**And** the bookmark action has `contentDescription = "Save $areaName to bookmarks"` / `"Remove $areaName from bookmarks"` [NFR20]
**And** analytics fires `bookmark_saved` with `area_name`

### Story 6.2: Saved Areas List & Navigation

As a **user**,
I want to browse all my saved areas and jump back to any one of them,
So that I can revisit places I found interesting.

**Acceptance Criteria:**

**Given** the user taps the Saved tab in bottom navigation
**When** the saved areas screen loads
**Then** a scrollable list displays all bookmarked areas sorted by most recently saved [FR21]
**And** each list item shows: area name, date saved, and a brief snippet from the cached summary
**And** tapping a list item navigates to the full area summary for that area [FR22]
**And** if the area has cached data, it loads from cache; if not, it fetches fresh from AI
**And** the user can remove bookmarks via swipe-to-delete or a delete action on each item
**And** if no bookmarks exist, a friendly empty state message is shown: "Save your favorite areas and they'll appear here"
**And** the list is fully navigable via TalkBack

---

## Epic 7: Offline Experience & Caching

Users access previously viewed area summaries and chat responses without connectivity, queue questions for later, and see nearby cached area suggestions when current location has no data.

### Story 7.1: Offline Area Summary Access

As a **user without internet connectivity**,
I want to view area summaries I've previously seen,
So that I still have useful information even in connectivity dead zones.

**Acceptance Criteria:**

**Given** the device has no internet connectivity
**When** the user opens the app or navigates to a previously viewed area
**Then** cached area summaries load from SQLDelight within 500ms [NFR3, FR25]
**And** cached summaries render with section-by-section fade-in (~200ms per bucket) — distinct from live streaming [Architecture: cached content animation]
**And** the `OfflineStatusIndicator` banner appears showing "Offline — showing cached content" with `LiveRegion.Polite` for TalkBack [NFR20]
**And** offline mode detection engages within 1 second of connectivity loss [NFR8]
**And** cached chat responses for the area are also accessible [FR25]
**And** the user can browse between any cached areas via the Saved tab

### Story 7.2: Offline Question Queue

As a **user in a connectivity dead zone**,
I want to ask questions that will be answered when I'm back online,
So that I never lose a question just because I don't have signal.

**Acceptance Criteria:**

**Given** the device is offline and the user is on the chat screen
**When** the user types and submits a question
**Then** the question is stored in the `offline_queue` SQLDelight table with area name, query text, and GPS coordinates [FR26]
**And** a message appears: "Your question will be sent when you're back online"
**And** when connectivity returns, queued questions are processed sequentially (max 1-2 concurrent) via `ProcessOfflineQueueUseCase`
**And** the user is notified in-app when queued responses arrive
**And** the offline queue displays pending questions with their status (queued, sending, answered)

### Story 7.3: Nearby Cached Area Suggestions

As a **user in an area with no cached data**,
I want to see suggestions for nearby areas that I have cached,
So that I still get value even when my current location has no data.

**Acceptance Criteria:**

**Given** the user is in an area with no cached summary (online or offline)
**When** the summary screen would otherwise be empty
**Then** the app shows a warm message: "No data for this area yet" followed by suggestions for nearby cached areas [FR27]
**And** suggestions are sorted by proximity (using last known GPS coordinates and cached area metadata)
**And** tapping a suggestion loads that area's cached summary
**And** the message uses friendly, non-technical tone [UX: warmth in degradation]

### Story 7.4: Cache Management & Storage Budget

As a **user**,
I want control over how much storage the app uses for cached content,
So that the app doesn't consume excessive space on my device.

**Acceptance Criteria:**

**Given** the app caches area summaries, chat responses, and map tiles over time
**When** the user navigates to cache settings
**Then** the current cache size is displayed [NFR16: target <100MB for 50-100 areas]
**And** the user can clear all cached data with a single action
**And** the user can clear cache for individual areas
**And** cache storage is encrypted via SQLCipher [NFR12]
**And** per-area caching uses area name + time window keys (not per-user) to support cost efficiency [NFR14]

---

## Epic 8: Adaptive Intelligence & Return Visits

Users see differentiated content on return visits (only what's changed), have their home area automatically detected based on visit frequency, and can configure when they receive area briefings.

### Story 8.1: Visit Tracking & Return Visit Differentiation

As a **returning user**,
I want to see only what's changed since my last visit to an area,
So that I get fresh, relevant content instead of reading the same summary again.

**Acceptance Criteria:**

**Given** the user has previously viewed an area summary
**When** the user returns to the same area
**Then** `RecordVisitUseCase` logs the visit in `visit_history` SQLDelight table with area name, timestamp, and incremented visit count [FR5]
**And** the summary screen shows differentiated content: "Welcome back to $areaName — here's what's changed"
**And** static buckets (History, Character) are collapsed or summarized since they haven't changed
**And** dynamic buckets (Safety, What's Happening, Cost) are highlighted with fresh content
**And** visit history is stored entirely on-device — never synced to any server [Privacy]
**And** first visit vs. return visit is visually distinct (e.g., different header, collapsed static sections)

### Story 8.2: Home Area Detection

As a **frequent visitor to the same area**,
I want the app to recognize my home neighborhood and show me "what's new" content,
So that I can continuously discover new things about where I live.

**Acceptance Criteria:**

**Given** the user has visited the same area frequently over time
**When** `DetectHomeAreaUseCase` determines an area exceeds the home threshold (e.g., 5+ visits in 14 days)
**Then** the area is flagged as "home" in the visit history [FR6]
**And** the summary screen uses home turf framing: "Your neighborhood — $areaName" instead of the standard header
**And** content shifts to "what's new" mode — only changed/new information since last visit
**And** home detection uses only on-device visit frequency data — no server component
**And** unit tests verify detection threshold logic with various visit patterns

### Story 8.3: Configurable Briefing Behavior

As a **user**,
I want to control when the app shows me area briefings,
So that I can choose between always getting briefings, only for new places, or being asked each time.

**Acceptance Criteria:**

**Given** the user navigates to briefing settings
**When** the user selects a briefing preference
**Then** three options are available: "Always," "Only New Places," "Ask Me" [FR7]
**And** "Always" triggers a proactive summary on every app open
**And** "Only New Places" triggers summaries only for areas not previously visited
**And** "Ask Me" shows a brief prompt asking whether to load the summary for the current area
**And** the setting persists across sessions (stored locally)
**And** default is "Always" for new users

---

## Epic 9: Safety, Trust & Engagement

Users can access emergency services info from any screen, provide feedback on AI content, manage and delete their data, and share discoveries as beautiful cards via the platform share sheet.

### Story 9.1: Emergency Information

As a **user in an unfamiliar area**,
I want to quickly find the nearest hospitals, police stations, and embassies,
So that I can get help fast in an emergency.

**Acceptance Criteria:**

**Given** the user is viewing any screen in the app
**When** the user taps the emergency info action (accessible within one tap from any screen) [FR24]
**Then** `GetEmergencyInfoUseCase` returns nearest hospitals, police stations, and embassies sourced from verified data [FR23]
**And** the emergency info is displayed as a prioritized list with names, addresses, and distances
**And** each entry has a "Navigate" action that opens the device's map app with directions
**And** emergency info uses `Confidence.HIGH` tier only — sourced from verified data, not AI-generated speculation
**And** the emergency action is visible as a persistent icon in the top app bar on all screens

### Story 9.2: AI Content Feedback

As a **user**,
I want to give thumbs up or thumbs down on AI responses,
So that I can signal when content is helpful or inaccurate.

**Acceptance Criteria:**

**Given** the user is viewing an AI-generated area summary bucket or chat response
**When** the user taps thumbs up or thumbs down [FR29]
**Then** the feedback is stored locally with area name, bucket/query reference, and feedback type
**And** the feedback icon toggles to show the selected state
**And** feedback data is available for future analytics export (not sent anywhere in V1)
**And** both icons have `contentDescription`: "Rate this helpful" / "Rate this unhelpful"
**And** the interaction is subtle and non-intrusive — inline, not a modal

### Story 9.3: Data Management & Deletion

As a **user**,
I want to view and delete all my stored data — visit history, cache, and bookmarks,
So that I maintain full control over my personal information.

**Acceptance Criteria:**

**Given** the user navigates to data management settings
**When** the user views their stored data
**Then** a summary shows: number of visited areas, cached areas, bookmarked areas, and total storage used [FR31]
**And** the user can delete all visit history with a single action
**And** the user can delete all cached data with a single action
**And** the user can delete all bookmarks with a single action
**And** the user can delete ALL locally stored data (complete wipe) with confirmation dialog [NFR13]
**And** deletion is immediate and irreversible — a confirmation dialog warns the user
**And** after deletion, the app returns to a fresh state as if newly installed

### Story 9.4: Share Area Discoveries

As a **user who discovered something fascinating**,
I want to share an area fact or summary as a beautiful card via the platform share sheet,
So that I can show friends what I learned and spread the word about the app.

**Acceptance Criteria:**

**Given** the user is viewing a summary bucket, highlight fact, or chat response
**When** the user taps the share action [FR34]
**Then** `ShareCardRenderer` generates a visually formatted bitmap card containing: the fact/content, area name, confidence tier, and AreaDiscovery branding
**And** the card is self-contained — makes sense without additional context
**And** the platform share sheet opens with the card image and a text fallback
**And** share is available on: summary buckets, highlight callouts, POI detail cards, and chat responses
**And** the share action has `contentDescription = "Share $contentType about $areaName"`
**And** analytics fires `share_triggered` with `area_name`, `bucket_type`, and `share_target`

---

## Epic 10: Voice Interaction & Push Notifications (Phase 2 — Placeholder)

Users ask questions via voice and receive spoken responses. Home turf users receive a weekly neighborhood digest notification. **Stories to be defined when Phase 2 planning begins.**

**FRs covered:** FR13, FR35

---

## Epic 11: Advanced Exploration (Phase 3-4 — Placeholder)

Users receive safety alerts, generate AI-powered day plans, compare areas side-by-side, view trip summaries, get local app recommendations, and use hands-free driving mode. **Stories to be defined when Phase 3 planning begins.**

**FRs covered:** FR36, FR37, FR38, FR39, FR40, FR41

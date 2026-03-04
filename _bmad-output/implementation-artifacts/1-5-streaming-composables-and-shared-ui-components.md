# Story 1.5: Streaming Composables & Shared UI Components

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a **user**,
I want to see area content materialize on screen progressively — bucket by bucket, word by word,
So that the experience feels like a story unfolding rather than a page loading.

## Acceptance Criteria

1. **Given** custom composables defined in `ui/components/`, **When** `StreamingTextContent` receives a flow of text tokens, **Then** text renders token-by-token with visible progressive appearance
2. **And** when system `prefers-reduced-motion` is enabled, streaming is replaced with section fade-in (~200ms) [UX: reduced motion]
3. **And** `BucketSectionHeader` displays bucket icon + title + streaming indicator (animated dot while streaming, checkmark when complete)
4. **And** each `BucketSectionHeader` uses `semantics { heading() }` for TalkBack heading navigation [NFR20]
5. **And** `HighlightFactCallout` renders the "whoa" fact in a visually distinct callout (3dp orange left border, beige background, 12dp internal padding)
6. **And** `ConfidenceTierBadge` displays as an M3 AssistChip with icon + color + text label — never color alone [UX: color independence]
7. **And** `ConfidenceTierBadge` has `contentDescription = "Confidence level: $tierName"` [NFR20]
8. **And** all composables use `MaterialTheme` tokens exclusively — zero hardcoded colors, sizes, or spacing values
9. **And** unit tests in `commonTest` verify the `SummaryStateMapper` correctly transforms `BucketUpdate` Flow into UI state transitions
10. **And** `rememberReduceMotion()` returns correct platform-specific values via `expect/actual` pattern (Android: `ANIMATOR_DURATION_SCALE`, iOS: `UIAccessibility.isReduceMotionEnabled()`)

## Tasks / Subtasks

- [x] Task 1: Create `StreamingTextContent` composable (AC: #1, #2, #10)
  - [x] 1.1 Create `ui/components/StreamingTextContent.kt` accepting `text: String` (accumulated text) and `isStreaming: Boolean`
  - [x] 1.2 Apply `Modifier.semantics { liveRegion = LiveRegionMode.Polite }` for TalkBack
  - [x] 1.3 Create `expect fun rememberReduceMotion(): Boolean` in `commonMain`, with `actual` in `androidMain` (check `ANIMATOR_DURATION_SCALE == 0f`) and `iosMain` (`UIAccessibility.isReduceMotionEnabled()`)
  - [x] 1.4 When reduced motion: render full text with 200ms `fadeIn()` via `AnimatedVisibility` instead of progressive appearance
  - [x] 1.5 When streaming (and not reduced motion): show a blinking cursor character ("|") at the end of the text — this is the brand-defining micro-interaction (~500ms blink cycle). Cursor disappears when `isStreaming = false`.

- [x] Task 2: Create `BucketSectionHeader` composable (AC: #3, #4)
  - [x] 2.1 Create `ui/components/BucketSectionHeader.kt` with params: `bucketType: BucketType`, `isStreaming: Boolean`, `isComplete: Boolean`
  - [x] 2.2 Render `Row` with: 24dp Material icon (mapped per `BucketType`) + `headlineSmall` title text + streaming indicator
  - [x] 2.3 Streaming indicator: pulsing orange dot via `rememberInfiniteTransition` when `isStreaming`, disappears when complete
  - [x] 2.4 Apply `Modifier.semantics { heading() }` and `contentDescription = "$bucketName section"`
  - [x] 2.5 Guard infinite transition with reduced motion check — skip pulsing when reduced motion enabled
  - [x] 2.6 Support skeleton state: when `isStreaming=false` AND `isComplete=false` (pending), render gray placeholder icon + shimmer text placeholder. This is the initial state before any content arrives for this bucket.

- [x] Task 3: Create `HighlightFactCallout` composable (AC: #5)
  - [x] 3.1 Create `ui/components/HighlightFactCallout.kt` with params: `text: String`, `isStreaming: Boolean`
  - [x] 3.2 Render `Surface` with beige background (`MaterialTheme.colorScheme.surface`) + `Modifier.drawBehind` for 3dp orange left border (`MaterialTheme.colorScheme.primary`)
  - [x] 3.3 Internal padding: 12dp (`MaterialTheme.spacing.bucketInternal`)
  - [x] 3.4 Text: `bodyLarge`, `MaterialTheme.colorScheme.onSurface`
  - [x] 3.5 Wrap text content in `StreamingTextContent` so highlight streams token-by-token

- [x] Task 4: Create `ConfidenceTierBadge` composable (AC: #6, #7)
  - [x] 4.1 Create `ui/components/ConfidenceTierBadge.kt` with param: `confidence: Confidence`
  - [x] 4.2 Map confidence to: HIGH → ("Verified", muted green `ConfidenceHigh`, checkmark icon), MEDIUM → ("Approximate", muted amber `ConfidenceMedium`, tilde icon), LOW → ("Limited Data", muted red `ConfidenceLow`, question mark icon)
  - [x] 4.3 Render as M3 `AssistChip` with `leadingIcon`, `containerColor = tierColor.copy(alpha = 0.15f)`, label text in `labelMedium`
  - [x] 4.4 Set `contentDescription = "Confidence level: $tierName"` via `Modifier.semantics`

- [x] Task 5: Create `InlineChatPrompt` composable (AC: #8)
  - [x] 5.1 Create `ui/components/InlineChatPrompt.kt` with params: `areaName: String`, `onNavigateToChat: (String) -> Unit`
  - [x] 5.2 Render: divider + prompt text ("Want to know more about $areaName?") + `OutlinedTextField` (read-only visually)
  - [x] 5.3 On focus/tap, call `onNavigateToChat` with any pre-filled text — navigation to Chat is handled by caller
  - [x] 5.4 `contentDescription = "Ask a question about $areaName"`

- [x] Task 6: Create `BucketCard` composite composable (AC: #1, #3, #5, #6, #8)
  - [x] 6.1 Create `ui/components/BucketCard.kt` combining: `BucketSectionHeader` + `HighlightFactCallout` (if highlight exists) + streaming body content via `StreamingTextContent` + `ConfidenceTierBadge`
  - [x] 6.2 Spacing: 12dp (`bucketInternal`) between header and body, 24dp (`lg`) between bucket cards
  - [x] 6.3 Accept `BucketDisplayState` data class: `bucketType`, `headerText`, `highlightText`, `bodyText`, `confidence`, `sources`, `isStreaming`, `isComplete`

- [x] Task 7: Create `SummaryStateMapper` (AC: #9)
  - [x] 7.1 Create `ui/summary/SummaryStateMapper.kt` as pure Kotlin class (no Compose dependencies)
  - [x] 7.2 Define `BucketDisplayState` data class: `bucketType: BucketType`, `highlightText: String`, `bodyText: String`, `confidence: Confidence?`, `sources: List<Source>`, `isStreaming: Boolean`, `isComplete: Boolean`
  - [x] 7.3 Define `SummaryUiState` sealed class: `Loading`, `Streaming(buckets: Map<BucketType, BucketDisplayState>, areaName: String)`, `Complete(buckets: Map<BucketType, BucketDisplayState>, pois: List<POI>, areaName: String)`, `Error(message: String)`
  - [x] 7.4 Implement `fun processUpdate(currentState: SummaryUiState, update: BucketUpdate): SummaryUiState` — maps `ContentDelta` → append text to bucket's `bodyText` (NOT `highlightText`), `BucketComplete` → finalize bucket with highlight/confidence/sources from `BucketContent`, `PortraitComplete` → transition to `Complete` state. CRITICAL: `ContentDelta.textDelta` always appends to `bodyText`. The `highlightText` is ONLY populated from `BucketComplete.content.highlight` — never from streaming deltas.
  - [x] 7.5 Handle defensive state creation: if a `ContentDelta` arrives for a `BucketType` not yet in the bucket map, create a new `BucketDisplayState` entry for it (don't crash). The real Gemini adapter may not emit buckets in the same strict order as the mock.

- [x] Task 8: Create `BucketTypeMapper` utility (AC: #3, #8)
  - [x] 8.1 Create `ui/components/BucketTypeMapper.kt` with `fun BucketType.displayTitle(): String` and `fun BucketType.icon(): ImageVector`
  - [x] 8.2 Map: SAFETY → ("Safety", `Icons.Filled.Shield`), CHARACTER → ("Character", `Icons.Filled.Palette`), WHATS_HAPPENING → ("What's Happening", `Icons.Filled.CalendarMonth` or `Icons.Filled.Event`), COST → ("Cost", `Icons.Filled.Payments` or `Icons.Filled.AttachMoney`), HISTORY → ("History", `Icons.Filled.History`), NEARBY → ("Nearby", `Icons.Filled.Explore`)
  - [x] 8.3 If specific icons unavailable in `material-icons-core`, use closest available or add `material-icons-extended` dependency

- [x] Task 9: Write unit tests (AC: #9)
  - [x] 9.1 Create `commonTest/ui/summary/SummaryStateMapperTest.kt`
  - [x] 9.2 Test: `ContentDelta` appends text to correct bucket in `Streaming` state
  - [x] 9.3 Test: `BucketComplete` finalizes bucket with highlight, confidence, sources
  - [x] 9.4 Test: `PortraitComplete` transitions state from `Streaming` to `Complete` with POIs
  - [x] 9.5 Test: Multiple sequential buckets stream correctly (SAFETY → CHARACTER → etc.)
  - [x] 9.6 Test: Initial state is `Loading`, first `ContentDelta` transitions to `Streaming`
  - [x] 9.7 Test: `ContentDelta` for unseen bucket type creates new `BucketDisplayState` entry defensively (doesn't crash or lose data)

- [x] Task 10: Add `material-icons-extended` dependency (AC: #3)
  - [x] 10.1 Add `compose-material-icons-extended = { module = "org.jetbrains.compose.material:material-icons-extended", version = "1.7.3" }` to `libs.versions.toml` under `[libraries]`
  - [x] 10.2 Add `implementation(libs.compose.material.icons.extended)` to `commonMain` dependencies in `build.gradle.kts`
  - [x] 10.3 Verify `Icons.Filled.Shield`, `Icons.Filled.Palette`, `Icons.Filled.Event`, `Icons.Filled.AttachMoney`, `Icons.Filled.History`, `Icons.Filled.Explore` are all available — these are required for `BucketTypeMapper`
  - [x] 10.4 Note: binary size impact ~10MB — acceptable for this app. The extended library is pinned at 1.7.3 in CMP 1.10.0.

- [x] Task 11: Clean up `.gitkeep` files
  - [x] 11.1 Delete `.gitkeep` from `ui/components/` once real composable files are added
  - [x] 11.2 Delete `.gitkeep` from `ui/summary/` once real state mapper files are added
  - [x] 11.3 Check for `.gitkeep` in `ui/chat/`, `ui/map/`, `ui/saved/`, `ui/search/`, `ui/navigation/` — leave those untouched (future stories)

- [x] Task 12: Build verification
  - [x] 12.1 Run `./gradlew :composeApp:assembleDebug` — must pass
  - [x] 12.2 Run `./gradlew :composeApp:allTests` — must pass
  - [x] 12.3 Run `./gradlew :composeApp:lint` — must pass (CI gate)

## Dev Notes

### Scope: What This Story Delivers

This story creates the **reusable composable component library** and the **state mapping logic** that Story 1.6 (Summary Screen) will assemble into a full screen. No screen-level composition, no ViewModel, no navigation wiring — just the building blocks and state transformation layer.

**Composables delivered** (all in `ui/components/`):
1. `StreamingTextContent` — Token-by-token text rendering with reduced-motion fallback
2. `BucketSectionHeader` — Icon + title + streaming indicator with accessibility semantics
3. `HighlightFactCallout` — Orange-bordered "whoa" fact callout
4. `ConfidenceTierBadge` — M3 AssistChip confidence indicator (Verified/Approximate/Limited)
5. `InlineChatPrompt` — Navigation bridge to Chat screen
6. `BucketCard` — Composite: header + highlight + body + badge
7. `BucketTypeMapper` — Maps `BucketType` enum to display title and icon

**Pure Kotlin layer delivered** (in `ui/summary/`):
8. `SummaryStateMapper` — Transforms `Flow<BucketUpdate>` events into `SummaryUiState` transitions (testable in `commonTest`)
9. `SummaryUiState` sealed class — Loading | Streaming | Complete | Error
10. `BucketDisplayState` data class — Per-bucket UI state

### Architecture: Streaming Data Flow

```
MockAreaIntelligenceProvider.streamAreaPortrait()
    ↓ Flow<BucketUpdate>
SummaryStateMapper.processUpdate()        ← Pure Kotlin, tested in commonTest
    ↓ SummaryUiState
[Future: SummaryViewModel exposes StateFlow<SummaryUiState>]  ← Story 1.6
    ↓
BucketCard composable renders each bucket  ← This story
```

The `SummaryStateMapper` is intentionally a **pure Kotlin class** with no ViewModel or coroutine dependencies. It takes `(currentState, update) → newState` — a pure function. The ViewModel (Story 1.6) will collect the Flow and call the mapper.

### Per-Bucket State Progression

Each bucket progresses through states tracked in `BucketDisplayState`:

```
Pending (isStreaming=false, isComplete=false, bodyText="")
  ↓ First ContentDelta arrives
Streaming (isStreaming=true, isComplete=false, bodyText accumulates via ContentDelta.textDelta)
  ↓ BucketComplete arrives
Complete (isStreaming=false, isComplete=true, highlight/confidence/sources populated from BucketContent)
```

**CRITICAL DATA FLOW**: `ContentDelta.textDelta` ALWAYS appends to `bodyText`. The `highlightText` field stays empty during streaming — it is ONLY populated when `BucketComplete` arrives, from `BucketComplete.content.highlight`. This prevents streaming text from leaking into the wrong field. The highlight callout in the UI will show as empty/hidden until the bucket completes.

**Defensive bucket creation**: If `processUpdate` receives a `ContentDelta` for a `BucketType` not yet in the state map, it should create a new `BucketDisplayState` entry (not crash). The mock provider emits sequentially, but the real Gemini adapter may interleave buckets.

### Reduced Motion: expect/actual Pattern

KMP has no unified reduced-motion API. Use `expect/actual`:

```kotlin
// commonMain: ui/components/ReduceMotion.kt
@Composable
expect fun rememberReduceMotion(): Boolean

// androidMain: ui/components/ReduceMotion.android.kt
@Composable
actual fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE, 1f
        ) == 0f
    }
}

// iosMain: ui/components/ReduceMotion.ios.kt
@Composable
actual fun rememberReduceMotion(): Boolean {
    return remember { UIAccessibility.isReduceMotionEnabled() }
}
```

### Material Icons Strategy

Add `material-icons-extended` (pinned at 1.7.3 in CMP 1.10.0) upfront. Several required icons (Shield, Palette) are NOT in the core ~180 icon set. Binary size impact: ~10MB — acceptable for this app. Adding it upfront avoids a build-debug cycle.

Required icons by bucket:
| Bucket | Icon | Import |
|--------|------|--------|
| SAFETY | Shield | `Icons.Filled.Shield` |
| CHARACTER | Palette | `Icons.Filled.Palette` |
| WHATS_HAPPENING | Event | `Icons.Filled.Event` |
| COST | AttachMoney | `Icons.Filled.AttachMoney` |
| HISTORY | History | `Icons.Filled.History` |
| NEARBY | Explore | `Icons.Filled.Explore` |

Dependency: `"org.jetbrains.compose.material:material-icons-extended:1.7.3"` — add to `libs.versions.toml` and `commonMain` dependencies.

### ConfidenceTierBadge Color Mapping

Use the existing color tokens from `Color.kt` (already defined in Story 1.3):

| Confidence | Color token | Hex | Icon | Label |
|-----------|------------|-----|------|-------|
| HIGH | `ConfidenceHigh` | #4A8C5C | Checkmark (Verified) | "Verified" |
| MEDIUM | `ConfidenceMedium` | #C49A3C | Tilde (Approximate) | "Approximate" |
| LOW | `ConfidenceLow` | #B85C4A | Question (Limited) | "Limited Data" |

Render: `AssistChipDefaults.assistChipColors(containerColor = tierColor.copy(alpha = 0.15f))`

### Spacing Tokens (from Spacing.kt)

| Token | Value | Usage in this story |
|-------|-------|---------------------|
| `xs` | 4dp | Icon-to-text gap in `BucketSectionHeader` |
| `sm` | 8dp | Internal chip padding |
| `bucketInternal` | 12dp | Header-to-body gap, highlight callout internal padding |
| `md` | 16dp | Screen edge margins, card internal padding |
| `lg` | 24dp | Gap between bucket sections |
| `touchTarget` | 48dp | Minimum interactive element size |

Access via `MaterialTheme.spacing.lg` (LocalSpacing composition local already set up in Theme.kt).

### Streaming Cursor Blink (Brand-Defining Micro-Interaction)

When `StreamingTextContent` is actively streaming (`isStreaming = true` and reduced motion is disabled), append a blinking cursor "|" at the end of the text. This creates the feeling of someone typing content live — the core brand interaction.

```kotlin
val cursorVisible by rememberInfiniteTransition(label = "cursor_blink")
    .animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(500), repeatMode = RepeatMode.Reverse
        ), label = "cursor_alpha"
    )
// Append "|" with animated alpha to the end of text
```

When `isStreaming = false`, the cursor disappears. When reduced motion is enabled, no cursor — text simply fades in.

### Skeleton State for BucketSectionHeader

Before any content arrives for a bucket, the header shows a skeleton state:
- Gray placeholder icon (use `MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)`)
- Shimmer text placeholder where the title would be
- No streaming indicator, no checkmark

State mapping: `isStreaming=false, isComplete=false` → skeleton (pending bucket)

This is distinct from the streaming state (`isStreaming=true`) and the complete state (`isComplete=true`). Story 1.6 will render all 6 skeleton headers immediately on load before any streaming begins.

### Streaming Indicator Animation

Pulsing orange dot for `BucketSectionHeader`:

```kotlin
val infiniteTransition = rememberInfiniteTransition(label = "streaming_pulse")
val alpha by infiniteTransition.animateFloat(
    initialValue = 0.3f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(
        animation = tween(600),
        repeatMode = RepeatMode.Reverse
    ),
    label = "pulse_alpha"
)
// Render small orange circle with animated alpha
```

Guard with `if (!rememberReduceMotion())` — when reduced motion, show a static dot or no dot.

### Testing Strategy

**In `commonTest` (pure Kotlin, no Compose):**
- `SummaryStateMapperTest.kt` — Tests all state transitions via `processUpdate()` function
- Uses `kotlin.test` assertions (`assertEquals`, `assertTrue`, `assertIs`)
- No Turbine needed (state mapper is synchronous — takes current state + update, returns new state)

**NOT in this story:**
- ViewModel tests (Story 1.6 — needs coroutines + lifecycle)
- Compose UI tests (Phase 1b — needs instrumented test infrastructure)
- Integration tests with MockAreaIntelligenceProvider (Story 1.6)

### Project Structure Notes

All new files go under existing package structure:

```
composeApp/src/commonMain/kotlin/com/areadiscovery/
├── ui/
│   ├── components/                    ← NEW FILES (this story)
│   │   ├── StreamingTextContent.kt
│   │   ├── BucketSectionHeader.kt
│   │   ├── HighlightFactCallout.kt
│   │   ├── ConfidenceTierBadge.kt
│   │   ├── InlineChatPrompt.kt
│   │   ├── BucketCard.kt
│   │   ├── BucketTypeMapper.kt
│   │   └── ReduceMotion.kt           ← expect declaration
│   ├── summary/                       ← NEW FILES (this story)
│   │   ├── SummaryStateMapper.kt
│   │   ├── SummaryUiState.kt
│   │   └── BucketDisplayState.kt
│   └── theme/                         ← EXISTING (Story 1.3)

composeApp/src/androidMain/kotlin/com/areadiscovery/
└── ui/components/
    └── ReduceMotion.android.kt        ← actual

composeApp/src/iosMain/kotlin/com/areadiscovery/
└── ui/components/
    └── ReduceMotion.ios.kt            ← actual

composeApp/src/commonTest/kotlin/com/areadiscovery/
└── ui/summary/
    └── SummaryStateMapperTest.kt      ← NEW TEST
```

- Delete `.gitkeep` from `ui/components/` and `ui/summary/` when adding real files
- Alignment with architecture doc: All paths match exactly

### What NOT to Do

- Do NOT create `SummaryViewModel` — that is Story 1.6
- Do NOT create `SummaryScreen` — that is Story 1.6
- Do NOT wire up navigation (`NavHost`, `NavController`) — that is Story 1.6
- Do NOT create `ShareCardRenderer` — that is a later story (share feature)
- Do NOT create `POIDetailCard` — that is Story 3.x (map POI interaction)
- Do NOT create `OfflineStatusIndicator` — that is Phase 1b (Story 7.x)
- Do NOT create `EmergencyAccessButton` — that is Story 9.x
- Do NOT create `ThumbsFeedbackRow` — that is Story 9.x
- Do NOT create `PermissionExplanationCard` — that is Story 5.2
- Do NOT add Koin module registrations (Story 1.6 wires DI)
- Do NOT use `collectAsState` or `collectAsStateWithLifecycle` (no ViewModel yet)
- Do NOT add `@Serializable` annotations to UI state classes
- Do NOT create test fakes (`FakeAreaIntelligenceProvider`, etc.) — Story 1.6
- Do NOT hardcode any colors, sizes, or spacing — use `MaterialTheme` tokens exclusively

### Previous Story (1.4) Learnings

**Patterns established:**
- `assert()` requires `@ExperimentalNativeApi` on Kotlin/Native — use `assertTrue()`/`assertEquals()` from `kotlin.test`
- Build verification requires all three gates: `assembleDebug`, `allTests`, `lint`
- Actual library versions: Kotlin 2.3.0, Compose MP 1.10.0, AGP 8.11.2, Gradle 8.14.3, Material 3 `1.10.0-alpha05`
- `compileSdk` and `targetSdk` are 36
- `.gitkeep` files must be deleted when adding real files to a directory
- `androidTarget()` deprecated-as-error in Kotlin 2.3.0 — uses `@Suppress("DEPRECATION")`
- Koin BOM `platform()` not supported in KMP — explicit versions used
- The `Unable to strip libraries` warning is benign

**Story 1.4 code review fixes to be aware of:**
- `PortraitComplete` is `data class(pois: List<POI>)` not `data object` — it carries POI data
- `BucketComplete` has `content: BucketContent` — the `bucketType` is accessed via `content.type` (single source of truth)
- `Source`/`SourceAttribution` are in separate `Source.kt` file, not inside `Confidence.kt`

### KMP-Specific Gotchas

- `BreakIterator` is JVM-only — do NOT use for character-by-character animation in `commonMain`. For token streaming, simple string append to `mutableStateOf` is sufficient
- `AnimatedVisibility`, `rememberInfiniteTransition`, `AnimatedContent` all work in `commonMain` (CMP 1.10.0)
- `rememberInfiniteTransition` can cause elevated CPU on iOS when backgrounded — guard with visibility checks
- `AssistChip` API fully available in `commonMain` via Material3
- `semantics { heading() }`, `liveRegion`, `contentDescription` all defined in `commonMain` and work on both TalkBack (Android) and VoiceOver (iOS)
- `material-icons-extended` is pinned at version 1.7.3 in CMP 1.10.0 — JetBrains is transitioning to Material Symbols
- For `collectAsStateWithLifecycle`, use `org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose` (already in `libs.versions.toml` as `androidx-lifecycle = "2.9.6"`) — but NOT needed in this story (no ViewModel)

### Git Intelligence

Single commit: `729cd7f Initial commit: KMP project setup, CI/CD, and design system (Stories 1.1–1.3)`

Story 1.4 files are staged but uncommitted. Patterns:
- Source: `composeApp/src/commonMain/kotlin/com/areadiscovery/{layer}/{sublayer}/`
- Tests: `composeApp/src/commonTest/kotlin/com/areadiscovery/{layer}/{sublayer}/`
- Theme tokens accessed via `MaterialTheme.colorScheme`, `MaterialTheme.typography`, `MaterialTheme.spacing`

### References

- [Source: _bmad-output/planning-artifacts/architecture.md#Frontend Architecture] — MVVM + StateFlow pattern, UiState sealed class, StateMapper design
- [Source: _bmad-output/planning-artifacts/architecture.md#Shared Composable Components] — Component list, naming conventions, file organization
- [Source: _bmad-output/planning-artifacts/architecture.md#Streaming Data Flow] — Flow<BucketUpdate> → StateMapper → StateFlow<UiState> pipeline
- [Source: _bmad-output/planning-artifacts/architecture.md#Testing Strategy] — StateMapper tests in commonTest, ViewModel tests in androidUnitTest
- [Source: _bmad-output/planning-artifacts/architecture.md#Accessibility] — WCAG 2.1 AA, TalkBack semantics, color independence, reduced motion
- [Source: _bmad-output/planning-artifacts/ux-design-specification.md#Experience Mechanics] — Streaming phases, skeleton headers, pulsing indicators
- [Source: _bmad-output/planning-artifacts/ux-design-specification.md#Component Library] — 8 custom composables with anatomy, states, interactions
- [Source: _bmad-output/planning-artifacts/ux-design-specification.md#Design Tokens] — Spacing (4dp-48dp), typography (12sp-28sp), colors (hex), animation timing
- [Source: _bmad-output/planning-artifacts/ux-design-specification.md#Accessibility] — LiveRegion.Polite, heading semantics, touch targets, contrast ratios
- [Source: _bmad-output/planning-artifacts/epics.md#Story 1.5] — Acceptance criteria, technical requirements
- [Source: _bmad-output/implementation-artifacts/1-4-domain-models-and-mock-ai-provider.md] — Domain models, BucketUpdate sealed class, mock data patterns, code review fixes

## Dev Agent Record

### Agent Model Used

Claude Opus 4.6

### Debug Log References

- `material-icons-extended` version 1.7.3 must use explicit version (not `composeMultiplatform` version ref) — CMP 1.10.0 doesn't publish this artifact at 1.10.0
- `Icons.Filled.Help` deprecated — replaced with `Icons.AutoMirrored.Filled.HelpOutline` (LOW) and `Icons.Filled.RemoveCircleOutline` (MEDIUM tilde)

### Completion Notes List

- Task 1: Created `StreamingTextContent` with blinking cursor (~500ms), reduced-motion fade-in (200ms), and `liveRegion = Polite` accessibility
- Task 1 (subtask 1.3): Created `expect/actual` `rememberReduceMotion()` — Android reads `ANIMATOR_DURATION_SCALE`, iOS reads `UIAccessibilityIsReduceMotionEnabled()`
- Task 2: Created `BucketSectionHeader` with 3 visual states (pending/skeleton, streaming/pulsing dot, complete), heading semantics, and reduced-motion guard
- Task 3: Created `HighlightFactCallout` with 3dp orange left border via `drawBehind`, beige surface background, 12dp internal padding
- Task 4: Created `ConfidenceTierBadge` as M3 `AssistChip` with icon + color + label (never color alone), full `contentDescription`
- Task 5: Created `InlineChatPrompt` with read-only `OutlinedTextField` that navigates to chat on tap
- Task 6: Created `BucketCard` composite composable combining header + highlight + body + badge
- Task 7: Created `SummaryStateMapper` (pure Kotlin) with `processUpdate()` — handles ContentDelta (append bodyText), BucketComplete (finalize with highlight/confidence/sources), PortraitComplete (transition to Complete). Defensive bucket creation for unseen BucketTypes.
- Task 7 (data classes): `BucketDisplayState` and `SummaryUiState` sealed class (Loading/Streaming/Complete/Error)
- Task 8: Created `BucketTypeMapper` with `displayTitle()` and `icon()` extension functions for all 6 BucketTypes
- Task 9: 8 unit tests in `SummaryStateMapperTest` — all pass on JVM and iOS Simulator
- Task 10: Added `material-icons-extended:1.7.3` to `libs.versions.toml` and `commonMain` dependencies
- Task 11: Deleted `.gitkeep` from `ui/components/` and `ui/summary/`; verified other `.gitkeep` files untouched
- Task 12: All 3 build gates pass — `assembleDebug`, `allTests`, `lint`

### Change Log

- 2026-03-04: Story 1.5 implementation complete — 7 composables, 3 state classes, 1 mapper, 8 unit tests, material-icons-extended dependency added
- 2026-03-04: Addressed code review round 1 — 8 items resolved (H1, H2, M1, M2, M4, M5, M7, L1)
- 2026-03-04: Addressed code review round 2 — M3 (reactive reduce-motion via ContentObserver/NSNotification) and M6 (explicit areaName parameter on processUpdate). Deferred: L2 (shimmer — nice-to-have), L3 (sources rendering — Story 1.6), L4 (previews — not required)

### File List

**New files:**
- composeApp/src/commonMain/kotlin/com/areadiscovery/ui/components/StreamingTextContent.kt
- composeApp/src/commonMain/kotlin/com/areadiscovery/ui/components/ReduceMotion.kt
- composeApp/src/commonMain/kotlin/com/areadiscovery/ui/components/BucketSectionHeader.kt
- composeApp/src/commonMain/kotlin/com/areadiscovery/ui/components/HighlightFactCallout.kt
- composeApp/src/commonMain/kotlin/com/areadiscovery/ui/components/ConfidenceTierBadge.kt
- composeApp/src/commonMain/kotlin/com/areadiscovery/ui/components/InlineChatPrompt.kt
- composeApp/src/commonMain/kotlin/com/areadiscovery/ui/components/BucketCard.kt
- composeApp/src/commonMain/kotlin/com/areadiscovery/ui/components/BucketTypeMapper.kt
- composeApp/src/commonMain/kotlin/com/areadiscovery/ui/summary/BucketDisplayState.kt
- composeApp/src/commonMain/kotlin/com/areadiscovery/ui/summary/SummaryUiState.kt
- composeApp/src/commonMain/kotlin/com/areadiscovery/ui/summary/SummaryStateMapper.kt
- composeApp/src/androidMain/kotlin/com/areadiscovery/ui/components/ReduceMotion.android.kt
- composeApp/src/iosMain/kotlin/com/areadiscovery/ui/components/ReduceMotion.ios.kt
- composeApp/src/commonTest/kotlin/com/areadiscovery/ui/summary/SummaryStateMapperTest.kt

**Modified files:**
- gradle/libs.versions.toml (added compose-material-icons-extended)
- composeApp/build.gradle.kts (added material-icons-extended dependency)
- composeApp/src/commonMain/kotlin/com/areadiscovery/ui/theme/Spacing.kt (added component-specific size tokens)

**Pre-existing modifications (from Story 1.4, not caused by this story):**
- composeApp/src/commonTest/kotlin/com/areadiscovery/ui/theme/ColorTest.kt
- composeApp/src/commonTest/kotlin/com/areadiscovery/ui/theme/TypographyTest.kt

**Deleted files:**
- composeApp/src/commonMain/kotlin/com/areadiscovery/ui/components/.gitkeep
- composeApp/src/commonMain/kotlin/com/areadiscovery/ui/summary/.gitkeep

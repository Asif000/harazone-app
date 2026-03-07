# Cross-Epic Deferred Backlog

Items deferred during code review — to be picked up in relevant future stories.

---

## Story 3.2 — AI-Generated POI Markers

| Severity | Item | File | Deferred To |
|----------|------|------|-------------|
| LOW | Custom POI icons per type (landmark, food, culture, nature) using `org.maplibre.gl:android-plugin-annotation-v9` SymbolManager | `MapComposable.android.kt` | Dedicated icon pass |
| LOW | TalkBack per-marker `contentDescription` — map-mode accessibility enhancement (list view satisfies NFR21; this is for sighted TalkBack users exploring map) | `MapComposable.android.kt` | Future UX polish |
| LOW | `AppLaunchSmokeTest` assertion is weak (`assertNotNull(activity)`) — doesn't verify UI actually renders | `AppLaunchSmokeTest.kt` | Story 5.2 (onboarding/permission flow) |
| LOW | Generic "Can't find your location" message shown on permission denial — should detect and show specific guidance | `MapViewModel.kt` | Story 5.2 (permission flow) |
| INFO | Deprecated MapLibre marker API (`addMarker`, `removeMarker`, `MarkerOptions`) — V1 approach; migrate when adding custom icons | `MapComposable.android.kt` | Dedicated icon pass |

---

## Story 3.3 — POI Detail Card & Bottom Sheet

| Severity | Item | File | Deferred To |
|----------|------|------|-------------|
| LOW | `resolveActivity(packageManager)` deprecated in API 33+ — replace with `resolveActivity(packageManager, PackageManager.ResolveInfoFlags.of(0))` to silence lint | `MainActivity.kt` | Any story touching MainActivity |
| LOW | Scaffold + snackbar state (`rememberBottomSheetScaffoldState`, `SnackbarHostState`) created inside `is MapUiState.Ready` branch — resets to peek on retry (Ready→Failed→Ready) | `MapScreen.kt` | Future UX polish |
| INFO | Three-stop bottom sheet (collapsed/half/full) deferred — V1 uses two stops (peek 88dp / expanded). Requires `anchoredDraggable` with custom snap points | `MapScreen.kt` | Future UX polish |

---

## Architecture — Shared Area Session (from device testing, post-Epic 3)

| Severity | Item | Description | Deferred To |
|----------|------|-------------|-------------|
| MEDIUM | Shared AreaSessionManager | Summary + Map ViewModels both fetch location + portrait independently. Quick fix done (LocationProvider caching), but proper solution is a shared AreaSessionManager holding current area/name/portrait consumed by both screens. | Future architecture story |

---

## SummaryStateMapper — Missing Bucket Handling

| Severity | Item | File | Deferred To |
|----------|------|------|-------------|
| MEDIUM | PortraitComplete only marks received buckets as complete — missing buckets stay as loading skeletons forever. Fix: fill all 6 BucketType entries in finalBuckets map, marking missing ones as `isComplete=true` with empty content | `SummaryStateMapper.kt` | Summary screen redesign |

---

## Hero Redesign (Timeline & Right Now Cards)

| Severity | Item | File | Deferred To |
|----------|------|------|-------------|
| LOW | Hero cards have no click-to-scroll interaction to jump to full bucket content below | `TimelineCard.kt`, `RightNowCard.kt` | Future UX polish |
| LOW | No tablet/landscape adaptive layout for TimelineCard LazyRow | `TimelineCard.kt` | Phase 2+ responsive layout |
| LOW | liveRegion on RightNowCard may re-announce on unrelated recomposition — monitor | `RightNowCard.kt` | Manual testing / UX polish |

---

## V3 Full-Screen Map — Review Findings

| Severity | Item | File | Deferred To |
|----------|------|------|-------------|
| MEDIUM | `clusterPois` uses Manhattan distance in degrees — inconsistent glow zone radius at different latitudes (0.005° = ~550m equator, ~275m at 60°N). Replace with Haversine-based distance. | `MapComposable.android.kt` | Future UX polish |
| MEDIUM | AI search `conversationHistory` always empty — follow-up chips produce standalone answers with no context from prior questions. Need to store chat turns in state and pass to `streamChatResponse`. | `MapViewModel.kt` | AI search enhancement |
| **HIGH** | **Auto-refresh area portrait on pan/zoom** — Core UX: when user pans to a new area, detect camera idle, reverse-geocode new center, debounce (500ms), re-fetch portrait if area name changed. Needs `onCameraIdle(lat, lng)` callback through expect/actual MapComposable, ViewModel debounce logic, loading state while keeping old pins visible. **This is essential for the "discover anywhere" experience.** | `MapComposable.kt`, `MapViewModel.kt` | **Next quick spec** |
| MEDIUM | Add +/- zoom control buttons as Compose overlays on the map. MapLibre 11.x removed built-in zoom controls. Need `onZoomIn`/`onZoomOut` callbacks through expect/actual MapComposable. | `MapComposable.kt`, `MapScreen.kt` | UX polish |

---

## Brainstorming — Explore Later

| Priority | Item | Description | Source |
|----------|------|-------------|--------|
| MEDIUM | Contextual AI tip on app open | Slim banner below top bar, AI-generated from user context (time, weather, location, first visit vs returning, day of week, saved places). One tip per open, auto-dismiss 5-8s or swipe. Tap → deep-link to mentioned place. Fallback: pre-computed time-of-day tips. Reduce frequency if user consistently dismisses. | Brainstorming session 2026-03-06 party mode |
| LOW | Offline AI: pre-generated answer packs | When online, have Gemini generate & cache 10-15 common Q&A pairs per area (safety, food, nightlife, budget, etc.) in Room DB. Serve instantly offline — no on-device LLM needed. Future: Gemini Nano as fallback for free-form questions when available on more devices. On-device full LLM (Gemma 2B, Phi-3) not recommended due to app size (+1-2 GB), RAM, battery, and quality trade-offs. | Brainstorming session 2026-03-06 |

---

## Localisation Phase B — UI Strings, RTL Layout & Map Labels (Future Epic)

| Priority | Item | Description | Source |
|----------|------|-------------|--------|
| MEDIUM | `moko-resources` i18n library | Add `dev.icerock.moko:resources` to KMP. Extract all hardcoded UI strings into `commonMain/MR/base/strings.xml`. Create locale files for `ar`, `es`, `pt`. Replaces any hardcoded string literals in Composables. | Planning 2026-03-06 |
| MEDIUM | Arabic RTL layout audit | Audit all Composables for RTL breakage: swap `start/end` padding for `absolute` variants, verify `Row` ordering flips correctly with `LocalLayoutDirection.current`. MapScreen vibe rail (right side) and top context bar need special attention. | Planning 2026-03-06 |
| MEDIUM | MapLibre RTL Text Plugin | Add `com.mapbox.mapboxsdk:mapbox-android-plugin-localization-v9` (MapLibre fork). Required for Arabic text in map tile labels to render correctly (RTL shaping). | Planning 2026-03-06 |
| LOW | Locale-aware date/time formatting | Time-of-day and date displays should respect locale (Arabic-Indic numerals, locale-specific month names). Use `kotlinx-datetime` with locale-aware formatters. | Planning 2026-03-06 |
| LOW | App Store / Play Store listings | Translate store description, screenshots, and keywords for Arabic (ar), Spanish (es), Portuguese (pt-BR + pt-PT). Parallel track — not a code change. | Planning 2026-03-06 |

**Recommended timing:** After V3 stabilises. Phase B is a full epic (~3-4 stories). Phase A (AI locale injection) ships first as a quick spec — covers ~80% of user-facing content with minimal effort.

---

## Gemini Nano — On-Device Offline Fallback (Future)

| Priority | Item | Description | Source |
|----------|------|-------------|--------|
| LOW | Gemini Nano offline fallback for new areas | Use Android AICore / MediaPipe LLM Inference API to run Gemini Nano on-device when network unavailable. Architecture is already ready: add a `NanoAreaIntelligenceProvider` in `androidMain` implementing `AreaIntelligenceProvider`, wire via Koin platform module. A `FallbackAreaIntelligenceProvider` wrapper tries cloud first, falls back to Nano. **Key constraints**: ~2K token context → need stripped-down prompts (1-2 sentences per vibe, not full portrait); Pixel 8+ and select Samsung S24/S25 only — need graceful path for unsupported devices (3 states: cloud → Nano → no AI). **Recommended sequencing**: ship portrait caching first (covers 90% of offline case for revisits); then add Nano for genuinely new areas offline on supported devices. Show "Offline — limited detail" banner when Nano is used. | Discussion 2026-03-06 |

---

## Vibe Rail Redesign + Toggle Relocation — Deferred

| Severity | Item | File | Deferred To |
|----------|------|------|-------------|
| MEDIUM | Icon size (20dp) is hardcoded in VibeOrb regardless of dynamic circle size (32–48dp) — gradient less visible at 32dp circles. Consider scaling icon proportionally with circle size (e.g. `(sizeDp.value * 0.5f).dp`). | `VibeOrb.kt` | Phase A polish |
| LOW | `Vibe.orbIconName` and `Vibe.accentColorHex` are dead/duplicated fields on the Vibe enum — `orbIconName` is unmapped in UI (overridden by `toImageVector()`), `accentColorHex` is duplicated by `toColor()`. Clean up when migrating to dynamic vibes (Phase A Room DB). | `Vibe.kt` | Phase A dynamic vibes story |

---

## Resolved Items (for reference)

- ~~POI caching across sessions~~ — Fixed in commit 437ed06 (area_poi_cache table, migration 2)
- ~~Camera never centres on user location~~ — Fixed in commit 437ed06
- ~~No POI markers on Map screen~~ — Fixed in commit 437ed06
- ~~Slow app launch (GPS timeout + cache-hit state dropped)~~ — Fixed in commit e4c8c28 (lastLocation-first, SummaryStateMapper fix)

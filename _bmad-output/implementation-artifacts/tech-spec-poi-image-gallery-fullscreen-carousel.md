---
title: 'POI Image Gallery — Fullscreen Carousel Viewer'
slug: 'poi-image-gallery-fullscreen-carousel'
created: '2026-03-20'
status: 'implementation-complete'
stepsCompleted: [1, 2, 3, 4]
tech_stack:
  - 'Kotlin Multiplatform (commonMain + androidMain + iosMain)'
  - 'Compose Multiplatform (HorizontalPager, TransformableState, graphicsLayer, BoxWithConstraints)'
  - 'Coil 3.1.0 (coil3-compose + coil3-network-ktor3, singleton configured in App.kt)'
  - 'Ktor 3.4.0 (HTTP client, reused for image fetching)'
  - 'kotlinx.serialization 1.8.1 (POI model is @Serializable)'
  - 'Koin 4.1.1 (DI — WikipediaImageRepository injected into MapViewModel)'
  - 'Coroutines + Flow (viewModelScope, Semaphore, StateFlow.update)'
files_to_modify:
  - 'composeApp/src/commonMain/kotlin/com/harazone/domain/model/POI.kt'
  - 'composeApp/src/commonMain/kotlin/com/harazone/data/remote/WikipediaImageRepository.kt'
  - 'composeApp/src/commonMain/kotlin/com/harazone/ui/map/MapViewModel.kt'
  - 'composeApp/src/commonMain/kotlin/com/harazone/ui/map/components/AiDetailPage.kt'
  - 'composeApp/src/commonMain/kotlin/com/harazone/ui/map/components/FullscreenImageGallery.kt [NEW]'
code_patterns:
  - 'Overlay: Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha=0.9f))) + PlatformBackHandler (SafetyGateModal pattern)'
  - 'Back handler: PlatformBackHandler(enabled = true) { onDismiss() } in commonMain'
  - 'Image loading: AsyncImage(model, contentScale, placeholder, error) via Coil 3'
  - 'Pinch-to-zoom: rememberTransformableState + Modifier.transformable + graphicsLayer + BoxWithConstraints for pan clamping'
  - 'Pager: isZoomed Boolean hoisted above HorizontalPager; per-page scale/offset inside pager lambda with remember(page) for isolation'
  - 'Prefetch dedup: mutableStateMapOf<String, Job>() — safe because all mutations run on viewModelScope (main dispatcher); not a ConcurrentHashMap'
  - 'State update: _uiState.update { } for atomic read-modify-write from background coroutines'
test_patterns:
  - 'No existing WikipediaImageRepository tests — create new in commonTest or jvmTest'
  - 'Mock HttpClient via MockEngine (Ktor test utilities)'
  - 'Assert on List<String> return from getImageUrls()'
---

# Tech-Spec: POI Image Gallery — Fullscreen Carousel Viewer

**Created:** 2026-03-20

## Overview

### Problem Statement

POI images are display-only, cropped to 160dp on the detail page. There is no way to view images full-size, inspect details, or browse multiple images. The POI model holds a single `imageUrl: String?`, so only one Wikipedia image surfaces per POI — leaving richer Wikimedia Commons coverage untapped and giving users no way to appreciate the photography.

### Solution

Fullscreen image gallery overlay triggered by tapping the detail page hero image. Multi-image support (up to 5 images via Wikimedia Commons) preloaded in the background during POI streaming, so images are ready before the user opens the gallery. Custom `TransformableState` for pinch-to-zoom + pan with clamped bounds (fully KMP-safe, no new dependencies). Vibe-themed gradient fallbacks remain untouched when no photos are available.

### Scope

**In Scope:**
- Fullscreen image gallery overlay — triggered from detail page hero tap only
- Swipe left/right carousel navigation (`HorizontalPager`)
- Pinch-to-zoom + pan with pan clamping to image bounds using `BoxWithConstraints` (commonMain, KMP-safe, no new dependencies)
- Image count badge on detail page hero ("1/5 photos") — shown only when `imageUrls` is non-empty
- Multi-image fetch via Wikimedia Commons category pages (up to `MAX_GALLERY_IMAGES = 5` per POI), preloaded in background during POI streaming
- Loading placeholder (vibe-coloured) and error fallback (vibe-coloured) inside gallery overlay
- Vibe-themed gradient fallbacks when no photos are available (existing behaviour, no regression)
- `PlatformBackHandler` + scrim tap + X button to dismiss the overlay

**Out of Scope:**
- Gallery triggered from map carousel cards (detail page only — avoids gesture conflict with card tap → open detail)
- Double-tap to zoom/reset (`// TODO(BACKLOG-MEDIUM): standard gallery UX, deferred to keep initial scope focused`)
- Video playback (`// TODO(BACKLOG-LOW): requires ExoPlayer/AVPlayer KMP expect/actual bridge`)
- User photo uploads
- Google Places Photos API
- AI-generated image placeholders

---

## Context for Development

### Codebase Patterns

**Overlay pattern** — follow `SafetyGateModal.kt`:
```kotlin
Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.9f)).clickable { onDismiss() })
PlatformBackHandler(enabled = true) { onDismiss() }
```

**AsyncImage with placeholder + error** — use in gallery:
```kotlin
AsyncImage(
    model = url,
    contentDescription = "Photo ${page + 1} of ${images.size} for $poiName",
    contentScale = ContentScale.Fit,  // Fit (not Crop) in fullscreen — show full image
    placeholder = ColorPainter(vibeColor.copy(alpha = 0.3f)),  // vibe-coloured while loading
    error = ColorPainter(vibeColor.copy(alpha = 0.2f)),         // vibe-coloured on 404/timeout
    modifier = Modifier.fillMaxSize()...,
)
```

**Pinch-to-zoom + pan with per-page isolation and correct bounds clamping:**

Key design: hoist only `isZoomed: Boolean` above `HorizontalPager` (for `userScrollEnabled`). Keep `scale`/`offset`/`intrinsicSize` **per-page** inside the pager lambda using `remember(page)` for full state isolation. Pan clamp uses **rendered image dimensions** (not box dimensions) computed from Coil's `onSuccess` intrinsic size — necessary because `ContentScale.Fit` letterboxes images smaller than the box.

```kotlin
// Hoisted above HorizontalPager — only this crosses the pager boundary
var isZoomed by remember { mutableStateOf(false) }
LaunchedEffect(pagerState.currentPage) { isZoomed = false }

HorizontalPager(
    state = pagerState,
    userScrollEnabled = !isZoomed,  // epsilon handled via isZoomed flag (set at scale > 1.01f)
) { page ->
    // Per-page state — fully isolated, no cross-page contamination
    var scale by remember(page) { mutableFloatStateOf(1f) }
    var offset by remember(page) { mutableStateOf(Offset.Zero) }
    var intrinsicSize by remember(page) { mutableStateOf(Size.Zero) }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val boxW = constraints.maxWidth.toFloat()
        val boxH = constraints.maxHeight.toFloat()

        // Rendered image size under ContentScale.Fit (letterbox-aware)
        val knownSize = intrinsicSize.isSpecified && intrinsicSize.width > 0 && intrinsicSize.height > 0
        val (renderedW, renderedH) = if (!knownSize) Pair(boxW, boxH) else {
            val imageRatio = intrinsicSize.width / intrinsicSize.height
            val boxRatio = boxW / boxH
            if (imageRatio > boxRatio) Pair(boxW, boxW / imageRatio)
            else Pair(boxH * imageRatio, boxH)
        }

        val transformState = rememberTransformableState { zoomChange, panChange, _ ->
            scale = (scale * zoomChange).coerceIn(1f, 5f)
            isZoomed = scale > 1.01f
            val maxX = (renderedW * (scale - 1)) / 2f
            val maxY = (renderedH * (scale - 1)) / 2f
            offset = Offset(
                (offset.x + panChange.x).coerceIn(-maxX, maxX),
                (offset.y + panChange.y).coerceIn(-maxY, maxY)
            )
        }

        AsyncImage(
            onSuccess = { state -> intrinsicSize = state.painter.intrinsicSize },
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y }
                .transformable(state = transformState)
                .clickable { /* consume tap — do NOT dismiss */ },
            ...
        )
    }
}
```

**Prefetch in-flight dedup + atomic state update:**
```kotlin
private val prefetchJobs = mutableStateMapOf<String, Job>()  // safe: all mutations on viewModelScope (main dispatcher); not a ConcurrentHashMap
private val imagePrefetchSemaphore = Semaphore(MAX_GALLERY_IMAGES)

private fun prefetchGalleryImages(poi: POI) {
    if (poi.imageUrls.isNotEmpty()) return           // already done
    if (prefetchJobs.containsKey(poi.savedId)) return // already in flight
    prefetchJobs[poi.savedId] = viewModelScope.launch {
        try {
            val urls = imagePrefetchSemaphore.withPermit {
                wikipediaImageRepository.getImageUrls(poi.wikiSlug, poi.name)  // network only inside permit
            }
            if (urls.isNotEmpty()) updatePoiImages(poi.savedId, urls)          // state update outside permit
        } finally {
            prefetchJobs.remove(poi.savedId)
        }
    }
}

private fun updatePoiImages(savedId: String, urls: List<String>) {
    _uiState.update { state ->                        // atomic read-modify-write, no lost updates
        val ready = state as? MapUiState.Ready ?: return@update state
        ready.copy(
            allPois = ready.allPois.map { p ->
                if (p.savedId == savedId) p.copy(imageUrls = urls, imageUrl = p.imageUrl ?: urls.first()) else p
            },
            selectedPoi = ready.selectedPoi?.let { sel ->
                if (sel.savedId == savedId) sel.copy(imageUrls = urls, imageUrl = sel.imageUrl ?: urls.first()) else sel
            }
        )
    }
}
```

**Image count badge** — pill overlay bottom-left of hero:
```kotlin
if (poi.imageUrls.isNotEmpty()) {
    Box(
        modifier = Modifier
            .align(Alignment.BottomStart)
            .padding(8.dp)
            .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text("1/${poi.imageUrls.size} photos", color = Color.White, style = MaterialTheme.typography.labelSmall)
    }
}
```

### Files to Reference

| File | Purpose |
| ---- | ------- |
| `composeApp/src/commonMain/kotlin/com/harazone/domain/model/POI.kt` | Add `imageUrls: List<String> = emptyList()` field. Currently has `imageUrl: String?` (single URL). |
| `composeApp/src/commonMain/kotlin/com/harazone/data/remote/WikipediaImageRepository.kt` | Extend with `getImageUrls()`. Current Commons call uses `gsrlimit=1` — change to `gsrlimit=MAX_GALLERY_IMAGES` and collect all `thumburl` values. Add `const val MAX_GALLERY_IMAGES = 5`. |
| `composeApp/src/commonMain/kotlin/com/harazone/ui/map/MapViewModel.kt` | Add `prefetchJobs`, `imagePrefetchSemaphore`, `prefetchGalleryImages()`, `updatePoiImages()`. Hook into `mergePois()`. |
| `composeApp/src/commonMain/kotlin/com/harazone/ui/map/components/AiDetailPage.kt` | `PoiDetailHeader` (~line 369): add `onImageClick` param, image count badge, hero tap. Wire `showGallery` state in `AiDetailPage`. |
| `composeApp/src/commonMain/kotlin/com/harazone/ui/map/components/FullscreenImageGallery.kt` | **NEW FILE** — fullscreen gallery overlay composable. |
| `composeApp/src/commonMain/kotlin/com/harazone/ui/map/components/SafetyGateModal.kt` | Reference for overlay/scrim/back-handler pattern. |
| `composeApp/src/commonMain/kotlin/com/harazone/ui/components/PlatformBackHandler.kt` | Back handler expect declaration (Android = BackHandler, iOS = no-op). |
| `composeApp/src/commonMain/kotlin/com/harazone/App.kt` | Coil singleton setup (lines ~33–38). No change needed. |

### Technical Decisions

| Decision | Choice | Reason |
| -------- | ------ | ------ |
| Gallery entry point | Detail page hero only | Avoids gesture conflict with carousel card tap → open detail |
| Multi-image fetch strategy | Preloaded during streaming (background) | Images ready before user opens gallery; better UX than loading spinner |
| Pinch-to-zoom | Custom `TransformableState` in `BoxWithConstraints` | KMP-safe, no new dependency; `BoxWithConstraints` provides layout size for pan clamping |
| Pan clamping | `±(imageSize * (scale - 1)) / 2` per axis | Keeps image edge aligned to screen edge at any zoom level; never shows black void |
| Zoom-lock condition | `isZoomed: Boolean` hoisted flag (`set when scale > 1.01f`) | Epsilon on bool flag avoids float equality; hoisting only the bool (not scale/offset) preserves per-page state isolation |
| Per-page zoom state | `scale`/`offset`/`intrinsicSize` inside pager lambda with `remember(page)` | Keeps state fully isolated per page; adjacent pages don't share zoom state during swipe animations |
| Pan clamp dimensions | Rendered image size via `onSuccess` intrinsic size + Fit formula | `BoxWithConstraints` gives box size not image size; Fit letterboxes portrait images — clamp must use actual rendered dimensions or user can pan into black void |
| Carousel widget | `HorizontalPager` from compose.foundation | Native snap behaviour; `userScrollEnabled = !isZoomed` solves swipe-lock-when-zoomed cleanly |
| Zoom reset on page change | `LaunchedEffect(currentPage)` resets `isZoomed`; `remember(page)` resets per-page scale/offset | Two-pronged reset: hoisted flag via LaunchedEffect, per-page state via remember key |
| Loading + error state | `placeholder` + `error` both use vibe-coloured `ColorPainter` | Consistent with existing hero gradient fallback pattern; no blank pages |
| Double-tap to zoom | Explicit out of scope (`BACKLOG-MEDIUM`) | Standard UX but adds gesture complexity; deferred to keep initial scope focused |
| In-flight dedup | `mutableStateMapOf<String, Job>()` keyed by `poi.savedId` | Safe because all mutations run on `viewModelScope` (main dispatcher); not a `ConcurrentHashMap` — prevents concurrent fetches for same POI |
| State race condition | `_uiState.update { }` (atomic) + semaphore guards network only | `update {}` is atomically correct; semaphore now guards the network call only, not the state update |
| Gallery snapshot | Gallery receives `List<String>` snapshot at open time | Simpler than `StateFlow` propagation; badge on detail page updates live; user reopens gallery to see any new images loaded after opening |
| POI model field | Add `imageUrls: List<String> = emptyList()` alongside `imageUrl: String?` | Backwards compatible — all existing code using `imageUrl` untouched |
| imageUrl + imageUrls sync | Prefetch sets both: `imageUrls = urls`, `imageUrl = urls.first()` if was null | Single-image path (carousel cards, enrichment) continues to work unchanged |
| Dots + counter | Both present | Dots = spatial position; counter = total count. Distinct affordances. Dots capped at 9 per existing app convention; counter takes over above that |
| Video support | Deferred | Requires ExoPlayer/AVPlayer KMP bridge — separate spec |

---

## Implementation Plan

### Tasks

Tasks are ordered by dependency (lowest level first).

- [x] T1: Extend WikipediaImageRepository — add `getImageUrls()` and `MAX_GALLERY_IMAGES` constant
  - File: `composeApp/src/commonMain/kotlin/com/harazone/data/remote/WikipediaImageRepository.kt`
  - Action: (a) Add `const val MAX_GALLERY_IMAGES = 5` as a **top-level constant** at the top of the file (not in a companion object) so it can be imported by `MapViewModel` and used unqualified. (b) Add `suspend fun getImageUrls(wikiSlug: String?, poiName: String): List<String>`. Reuse existing Wikipedia thumbnail fetch for the first URL. Update Commons call: change `gsrlimit=1` to `gsrlimit=$MAX_GALLERY_IMAGES` and collect all `thumburl` values from the `pages` map (`.values.mapNotNull { it.imageinfo?.firstOrNull()?.thumburl }`). Combine Wikipedia result + Commons results, call `.distinct()`, `.take(MAX_GALLERY_IMAGES)`, return list. Return `emptyList()` on any exception — never propagate.
  - Notes: **`wikiSlug` null contract**: if `wikiSlug == null`, skip the wikiSlug Wikipedia call and attempt name-based Wikipedia lookup only, then Commons search. If `wikiSlug` is non-null but Wikipedia returns no thumbnail, still proceed to Commons. Never return early just because `wikiSlug` is null — `poiName` is always available as fallback. Keep existing `getImageUrl(): String?` unchanged for zero-risk backwards compatibility. The Commons `pages` map uses auto-generated negative integer keys for gsearch results — iterate `.values`, not a specific key. Existing `CommonsImageInfo` data class may need to be updated to a nullable `imageinfo: List<CommonsThumb>?` (array per entry) — verify the current structure and update accordingly.

- [x] T2: Update POI domain model — add `imageUrls` field
  - File: `composeApp/src/commonMain/kotlin/com/harazone/domain/model/POI.kt`
  - Action: Add `val imageUrls: List<String> = emptyList()` immediately after the `imageUrl` field.
  - Notes: Class is `@Serializable` — kotlinx.serialization handles `List<String>` with default value automatically, no migration needed. Domain model only (not a Room entity), no DB schema change.

- [x] T3: Add background gallery image prefetch in MapViewModel
  - File: `composeApp/src/commonMain/kotlin/com/harazone/ui/map/MapViewModel.kt`
  - Action: (a) Add `private val prefetchJobs = mutableStateMapOf<String, Job>()` and `private val imagePrefetchSemaphore = Semaphore(MAX_GALLERY_IMAGES)  // imported top-level const from WikipediaImageRepository.kt` properties. (b) Add `private fun prefetchGalleryImages(poi: POI)`: double-guard — early-return if `poi.imageUrls.isNotEmpty()` OR `prefetchJobs.containsKey(poi.savedId)`; otherwise store job in map, fetch with semaphore guarding network call only, call `updatePoiImages()` outside the permit, remove job from map in `finally`. (c) Add `private fun updatePoiImages(savedId: String, urls: List<String>)`: use `_uiState.update { }` for atomic read-modify-write — update matching POI in `allPois` and `selectedPoi` with `copy(imageUrls = urls, imageUrl = poi.imageUrl ?: urls.first())`. (d) Call `prefetchGalleryImages(poi)` for each POI inside `mergePois()` after the merged list is assembled — fire-and-forget.
  - Notes: `_uiState.update { }` is an atomic compare-and-set — prevents lost updates when discovery emits concurrent state changes. Semaphore guards only the `getImageUrls()` network call, not the state update (which is fast and synchronous). `mutableStateMapOf` mutations (put/remove) are safe here because all calls run on `viewModelScope` (main dispatcher on Android/KMP) — this is not a `ConcurrentHashMap` equivalent; if `mergePois()` is ever moved to `Dispatchers.IO`, switch to a `Mutex`-guarded `HashMap`. `selectPoiWithImageResolve()` remains as the fast-path fallback for POIs not yet prefetched.

- [x] T4: Create `FullscreenImageGallery` composable (new file)
  - File: `composeApp/src/commonMain/kotlin/com/harazone/ui/map/components/FullscreenImageGallery.kt` [CREATE]
  - Action: Implement composable with signature `fun FullscreenImageGallery(images: List<String>, poiName: String, initialIndex: Int = 0, vibeColor: Color, onDismiss: () -> Unit)`. Structure:
    1. Outer `Box(Modifier.fillMaxSize().background(Color.Black).clickable { onDismiss() })` as scrim + dismiss target.
    2. `PlatformBackHandler(enabled = true) { onDismiss() }`.
    3. `val pagerState = rememberPagerState(initialPage = initialIndex) { images.size }` — `pageCount` lambda is mandatory in Compose Foundation 1.5+.
    4. Hoist `var isZoomed by remember { mutableStateOf(false) }` ABOVE `HorizontalPager`. Add `LaunchedEffect(pagerState.currentPage) { isZoomed = false }` to reset the flag on page change.
    5. `HorizontalPager(state = pagerState, userScrollEnabled = !isZoomed)`.
    6. Per-page content — all state declared INSIDE the pager lambda using `remember(page)` for full isolation: `var scale by remember(page) { mutableFloatStateOf(1f) }`, `var offset by remember(page) { mutableStateOf(Offset.Zero) }`, `var intrinsicSize by remember(page) { mutableStateOf(Size.Zero) }`.
    7. `BoxWithConstraints(Modifier.fillMaxSize())` — capture `boxW = constraints.maxWidth.toFloat()`, `boxH = constraints.maxHeight.toFloat()`. Compute rendered image dimensions accounting for `ContentScale.Fit` letterboxing: `val knownSize = intrinsicSize.isSpecified && intrinsicSize.width > 0 && intrinsicSize.height > 0; val imageRatio = if (knownSize) intrinsicSize.width / intrinsicSize.height else boxW / boxH; val boxRatio = boxW / boxH; val renderedW = if (imageRatio > boxRatio) boxW else boxH * imageRatio; val renderedH = if (imageRatio > boxRatio) boxW / imageRatio else boxH`. **Guard note**: `intrinsicSize != Size.Zero` is insufficient — `Size.Unspecified` (returned by some Coil painters) also passes that check but has `width = Float.NaN`, causing NaN pan bounds. The `isSpecified && > 0` guard catches both cases; when unspecified, fall back to box dimensions (safe — Wikimedia images always carry dimensions). Use `renderedW`/`renderedH` for pan clamp: `maxX = (renderedW * (scale - 1)) / 2f`.
    8. `AsyncImage(model = images[page], contentDescription = "Photo ${page+1} of ${images.size} for $poiName", contentScale = ContentScale.Fit, placeholder = ColorPainter(vibeColor.copy(alpha = 0.3f)), error = ColorPainter(vibeColor.copy(alpha = 0.2f)), onSuccess = { state -> intrinsicSize = state.painter.intrinsicSize }, modifier = Modifier.fillMaxSize().graphicsLayer{...}.transformable(...).clickable { /* consume tap */ })`. Inside `transformState`: update `scale`, set `isZoomed = scale > 1.01f`, clamp `offset`.
    9. Top bar: Row with `"${pagerState.currentPage + 1}/${images.size} photos"` (white, dark pill, left) and X `IconButton { onDismiss() }` (right).
    10. Bottom dots: cap at 9 (same as `PoiCarousel`), active = 8dp white full opacity, inactive = 6dp white 40% opacity.
    11. Add at top of file: `// TODO(BACKLOG-MEDIUM): Double-tap to zoom/reset` and `// TODO(BACKLOG-LOW): Video support — requires ExoPlayer/AVPlayer KMP expect/actual bridge`.
  - Notes: `isZoomed` is the only state hoisted above the pager — it's a bool flag, not `scale`, so adjacent pages never share zoom values during swipe animations. `remember(page)` inside the lambda provides full per-page isolation: fresh state when a page enters composition, and no cross-contamination during mid-swipe when pager renders two pages simultaneously. `intrinsicSize` from `onSuccess` is needed because `ContentScale.Fit` letterboxes images — clamping to box dimensions would allow panning into the black letterbox area. Inner image `Modifier.clickable {}` consumes taps to prevent accidental scrim dismiss.

- [x] T5: Update `PoiDetailHeader` — add image count badge and tap handler
  - File: `composeApp/src/commonMain/kotlin/com/harazone/ui/map/components/AiDetailPage.kt`
  - Action: Add `onImageClick: () -> Unit` parameter to `PoiDetailHeader`. Add `.clickable(enabled = poi.imageUrls.isNotEmpty(), indication = null, interactionSource = remember { MutableInteractionSource() }) { onImageClick() }` to the hero `Box` modifier (no ripple). Inside the hero `Box` at `Alignment.BottomStart`, add image count badge (see Codebase Patterns section) — only when `poi.imageUrls.isNotEmpty()`.
  - Notes: Badge always shows "1/N" — indicates total count, not current index. Ripple suppressed because the entire hero is the tap target and a ripple over an image is visually noisy.

- [x] T6: Wire gallery state in `AiDetailPage`
  - File: `composeApp/src/commonMain/kotlin/com/harazone/ui/map/components/AiDetailPage.kt`
  - Action: Add `var showGallery by remember { mutableStateOf(false) }` near the top of the composable body. Pass `onImageClick = { showGallery = true }` to `PoiDetailHeader`. At the outermost `Box` (last child for Z-order): `if (showGallery && poi.imageUrls.isNotEmpty()) { FullscreenImageGallery(images = poi.imageUrls, poiName = poi.name, vibeColor = vibeColor, onDismiss = { showGallery = false }) }`.
  - Notes: Last child of outermost Box ensures gallery renders above all detail page content. `showGallery` resets automatically when `AiDetailPage` leaves composition (POI deselected) — no explicit cleanup needed. Gallery receives `poi.imageUrls` snapshot at open time — if prefetch completes while gallery is open, the badge on the detail page updates live but the open gallery keeps its original list; user must close and reopen to see additional images (explicit known limitation, acceptable trade-off over StateFlow propagation complexity).

- [x] T7: Write unit tests for `WikipediaImageRepository.getImageUrls()`
  - File: `composeApp/src/commonTest/kotlin/com/harazone/data/remote/WikipediaImageRepositoryTest.kt` [CREATE]
  - Action: Use Ktor `MockEngine` to mock HTTP responses. Write 5 tests: (1) **Happy path** — Commons returns 3 file entries with distinct thumburls → `getImageUrls()` returns list of 3 URLs. (2) **Wikipedia + Commons combined** — Wikipedia summary returns 1 thumbnail AND Commons returns 2 results → returned list contains all 3, deduplicated, max `MAX_GALLERY_IMAGES`. (3) **Total failure** — all HTTP calls throw `IOException` → returns `emptyList()`, no exception propagated. (4) **Dedup** — Wikipedia thumbnail URL also appears in Commons results → URL appears exactly once in returned list. (5) **wikiSlug == null** — call `getImageUrls(wikiSlug = null, poiName = "Some Place")` → slug-based Wikipedia call is NOT made, name-based Wikipedia lookup IS attempted, Commons search proceeds, results are returned normally.
  - Notes: Follow existing test patterns (check `AreaRepositoryTest.kt` for MockEngine setup). If `commonTest` doesn't support Ktor `MockEngine` directly, use `jvmTest` as the source set.

### Acceptance Criteria

- [ ] AC1: Given a POI with `imageUrls.size >= 1`, when the detail page hero is displayed, then a "1/N photos" badge is visible at the bottom-left of the hero image.
- [ ] AC2: Given a POI with `imageUrls.isEmpty()`, when the detail page hero is displayed, then no badge is shown and the hero renders exactly as today (no regression).
- [ ] AC3: Given a POI with `imageUrls.isNotEmpty()`, when the user taps the hero image on the detail page, then the fullscreen gallery overlay opens showing the first image with a counter "1/N photos".
- [ ] AC4: Given a POI with `imageUrls.isEmpty()`, when the user taps the hero image, then nothing happens (no crash, no empty gallery opens).
- [ ] AC5: Given gallery is open with N > 1 images, when the user swipes left, then the next image is shown and the counter updates to "2/N photos".
- [ ] AC6: Given gallery is open on the first image, when the user swipes right, then nothing happens (no wrap-around, no crash).
- [ ] AC7: Given gallery is open on the last image, when the user swipes left, then nothing happens.
- [ ] AC8: Given gallery is open, when the user pinches to zoom, then the image scales up (max 5×) and panning is enabled.
- [ ] AC9: Given the image is zoomed (scale > ~1f), when the user attempts to swipe to the next page, then the pager does NOT advance (`isZoomed = true` disables pager scroll).
- [ ] AC10: Given the user swipes to a new page in the gallery, then zoom and pan reset to 1× / Offset.Zero on the new page.
- [ ] AC11: Given image is zoomed at 3× and user pans, then the image does not move beyond its edges — the black background is never visible through the image area.
- [ ] AC12: Given gallery is open, when the user presses the Android back button, then the gallery closes and the detail page remains visible.
- [ ] AC13: Given gallery is open, when the user taps the black scrim area outside the image, then the gallery closes.
- [ ] AC14: Given gallery is open, when the user taps on the image itself, then the gallery does NOT close.
- [ ] AC15: Given gallery is open, when the user taps the X button, then the gallery closes.
- [ ] AC16: Given an image is still loading when the gallery opens, then a vibe-coloured placeholder is shown (not a blank frame).
- [ ] AC17: Given an image URL returns a 404 or timeout error, then a vibe-coloured fallback is shown (not a blank frame).
- [ ] AC18: Given a POI finishes Stage 2 enrichment during streaming, when the user opens the detail page, then `imageUrls` is already populated and the gallery opens without a loading delay.
- [ ] AC19: Given gallery is open showing a portrait image, then the full image height is visible without cropping (ContentScale.Fit).

---

## Additional Context

### Dependencies

No new Gradle dependencies required. All components are available in the current setup:
- `HorizontalPager`, `rememberPagerState` — `compose.foundation` (Compose Multiplatform, already in project)
- `TransformableState`, `rememberTransformableState`, `graphicsLayer`, `BoxWithConstraints` — `compose.foundation` (already in project)
- `AsyncImage` — `coil3-compose` v3.1.0 (already in project)
- `Semaphore`, `withPermit` — `kotlinx.coroutines` (already in project)
- `StateFlow.update` — `kotlinx.coroutines` (already in project)
- `PlatformBackHandler` — already implemented (`expect`/`actual` in commonMain/androidMain/iosMain)

### Testing Strategy

- **Unit tests (T7)**: `WikipediaImageRepository.getImageUrls()` with Ktor `MockEngine` — 5 cases: happy path, combined Wikipedia+Commons, total failure, dedup, wikiSlug == null path. Primary automated gate.
- **Manual smoke test checklist** (run on device after implementation):
  - [ ] Android: tap detail page hero → gallery opens on first image
  - [ ] Android: swipe left/right in gallery — counter updates correctly
  - [ ] Android: pinch-to-zoom — scales up, pan clamped (no black void), swipe locked while zoomed
  - [ ] Android: pinch back to ~1× — swipe re-enabled (epsilon handles float imprecision)
  - [ ] Android: swipe to next image — zoom resets to 1×
  - [ ] Android: back button closes gallery (NOT the detail page)
  - [ ] Android: tap image itself — gallery stays open
  - [ ] Android: tap scrim — gallery closes
  - [ ] Android: POI with no images — no badge visible, hero tap does nothing
  - [ ] Android: loading placeholder visible on slow network
  - [ ] Android: error fallback visible for broken URL
  - [ ] Android: preloading — open detail page after streaming, gallery opens without delay
  - [ ] iOS: tap hero → gallery opens
  - [ ] iOS: swipe navigation works
  - [ ] iOS: pinch-to-zoom works, pan clamped
  - [ ] iOS: tap scrim / X button closes gallery
  - [ ] iOS: POI with no images — no badge, hero tap does nothing

### Notes

**High-risk items (pre-mortem):**

1. **Gesture conflict: pager vs. transformable** — `userScrollEnabled = !isZoomed` should prevent the pager consuming horizontal drags while zoomed, but Compose gesture propagation can be subtle. If the pager still intercepts, fallback: use `Modifier.pointerInput` with `detectTransformGestures` instead of `rememberTransformableState`, and gate pager scroll via `pagerState.scroll { ... }`.

2. **Commons `pages` map key structure** — Commons API gsearch results use auto-generated negative integer page IDs (e.g. `"-1"`, `"-2"`). Multi-image code must iterate `.values` and collect from each entry. Verify `CommonsImageInfo` data class handles `imageinfo` as an array (not single object) — update if needed.

3. **`isZoomed` hoisting + per-page state isolation** — Only `isZoomed: Boolean` is hoisted above `HorizontalPager`; `scale`/`offset`/`intrinsicSize` live inside the pager lambda with `remember(page)`. During a swipe gesture the pager renders two pages simultaneously — the per-page `remember(page)` ensures they never share zoom state. `LaunchedEffect(pagerState.currentPage)` resets `isZoomed` after animation settles; `remember(page)` handles per-page reset when a page re-enters composition.

4. **Memory pressure from preloading** — Preloading up to 5 images for each of ~15 streaming POIs = up to 75 Coil memory cache entries. Coil's default memory cache is 25% of available RAM. Mitigate: call `prefetchGalleryImages` only after Stage 2 (enriched POIs), not Stage 1 (raw pins), to reduce the total count and avoid loading images for pins the user may never view.

**Known limitation:**
- Gallery receives `poi.imageUrls` as a snapshot at open time. If prefetch completes while the gallery is open (e.g. user opened early from a single-image POI), the gallery shows only the images available at open. The detail page badge updates live. User must close and reopen the gallery to see new images. This is an acceptable trade-off over wiring a `StateFlow` into the gallery composable.

**Other notes:**
- `imageUrl: String?` (single field) is kept fully unchanged. All carousel cards, enrichment merge, and `selectPoiWithImageResolve()` are untouched.
- Coil has no disk cache configured — prefetched images survive the session (memory cache) but re-fetch on restart. Disk cache is a separate infrastructure item.
- `ContentScale.Fit` in gallery (full image, no cropping) vs. `ContentScale.Crop` on hero/cards (fills frame) — intentional, document with inline comments in T4.
- `showGallery` state resets automatically when `AiDetailPage` leaves composition — no explicit cleanup needed.

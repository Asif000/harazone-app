---
title: 'POI List Card Thumbnail'
slug: 'poi-list-card-thumbnail'
created: '2026-03-08'
status: 'completed'
stepsCompleted: [1, 2, 3, 4]
tech_stack: ['Kotlin Multiplatform', 'Compose Multiplatform', 'Coil3 (coil3.compose + coil3.network.ktor3)', 'Material Icons']
files_to_modify:
  - 'composeApp/src/commonMain/kotlin/com/areadiscovery/ui/map/POIListView.kt'
  - 'composeApp/src/androidMain/kotlin/com/areadiscovery/ui/map/POIListViewPreviews.kt'
code_patterns:
  - 'coil3.compose.AsyncImage with placeholder/error Painter params'
  - 'rememberVectorPainter for icon placeholder'
  - 'Conditional rendering on poi.imageUrl != null'
  - 'ContentScale.Crop + clip(RoundedCornerShape)'
  - 'Outer Row layout with weight(1f) on text column'
test_patterns:
  - 'No new unit tests — composable design still fluid; preview update is the verification vehicle'
---

# Tech-Spec: POI List Card Thumbnail

**Created:** 2026-03-08

## Overview

### Problem Statement

`PoiListCard` in `POIListView.kt` never renders `POI.imageUrl`. The list view shows no image even when one is available, leaving all cards text-only and visually flat.

### Solution

Restructure `PoiListCard`'s outer `Column` into an outer `Row`. Add a conditional 56×56dp `AsyncImage` (Coil3, rounded corners, `ContentScale.Crop`) on the leading edge when `imageUrl != null`. Use `rememberVectorPainter(Icons.Default.Place)` for both loading placeholder and error state. No new dependencies or Coil config required — singleton is already wired in `App.kt`.

### Scope

**In Scope:**
- `PoiListCard` layout restructure and thumbnail rendering in `POIListView.kt`
- Preview update in `POIListViewPreviews.kt` to exercise the thumbnail path

**Out of Scope:**
- `ExpandablePoiCard` (already has image support)
- Any Coil configuration changes
- Image caching or prefetch strategy
- Image source / Wikipedia image pipeline

---

## Context for Development

### Codebase Patterns

- **AsyncImage usage**: `ExpandablePoiCard.kt` uses `coil3.compose.AsyncImage` with `ColorPainter` as placeholder. For list thumbnails, use `rememberVectorPainter(Icons.Default.Place)` for a recognizable icon placeholder instead.
- **Conditional image rendering**: always guard with `if (poi.imageUrl != null)` — same pattern as `ExpandablePoiCard`.
- **Coil singleton**: configured once in `App.kt` via `setSingletonImageLoaderFactory` with `KtorNetworkFetcherFactory`. No per-call config needed.
- **Card layout**: current `PoiListCard` uses `Column(padding=12dp)` > `Row(verticalAlignment=CenterVertically)` > inner `Column(weight=1f)` + rating/status. The outer `Column` must become an outer `Row` to host the thumbnail on the leading edge.
- **Semantics**: `Card` already has `semantics { contentDescription = "${poi.name}, ${poi.type}" }` — thumbnail `AsyncImage` should use `contentDescription = null` (decorative).
- **Dark card background**: `MapSurfaceDark` container color. Placeholder icon tint should be `Color.White.copy(alpha = 0.3f)` to be visible against the dark surface.

### Files to Reference

| File | Purpose |
| ---- | ------- |
| `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/map/POIListView.kt` | **Modify** — `PoiListCard` composable |
| `composeApp/src/androidMain/kotlin/com/areadiscovery/ui/map/POIListViewPreviews.kt` | **Modify** — add preview with `imageUrl` |
| `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/map/components/ExpandablePoiCard.kt` | **Reference** — existing `AsyncImage` pattern (line 57, 106–112) |
| `composeApp/src/commonMain/kotlin/com/areadiscovery/App.kt` | **Reference** — Coil singleton config (lines 21–25) |
| `composeApp/src/commonMain/kotlin/com/areadiscovery/domain/model/POI.kt` | **Reference** — `imageUrl: String?` at line 20 |

### Technical Decisions

1. **Placeholder**: Use `rememberVectorPainter(Icons.Default.Place)` for both `placeholder` and `error` params. Tint via a wrapping `Box` with `Icon` is NOT needed — `AsyncImage` accepts `Painter` directly for placeholder/error; vector painter renders the icon in white.
2. **Thumbnail shape**: `RoundedCornerShape(8.dp)` — consistent with card radius of 8dp (cards use default `CardDefaults` which is 12dp but 8dp feels right for a small inset thumbnail).
3. **Outer layout alignment**: `verticalAlignment = Alignment.Top` on the outer `Row` — cards with 2-line insight look better top-aligned than center-aligned.
4. **Spacing**: `Spacer(Modifier.width(12.dp))` between thumbnail and text column.
5. **No fallback slot when imageUrl is null**: when `imageUrl` is null, no space is reserved — text takes full width. This matches the existing behavior and avoids empty placeholder boxes.

---

## Implementation Plan

### Tasks

**Task 1 — Restructure `PoiListCard` and add thumbnail** (`POIListView.kt`)

File: `composeApp/src/commonMain/kotlin/com/areadiscovery/ui/map/POIListView.kt`

1. Add imports:
   ```kotlin
   import coil3.compose.AsyncImage
   import androidx.compose.ui.layout.ContentScale
   import androidx.compose.ui.graphics.vector.rememberVectorPainter
   import androidx.compose.material.icons.filled.Place
   import androidx.compose.foundation.shape.RoundedCornerShape
   ```
2. In `PoiListCard`, replace the outer `Column(modifier = Modifier.padding(12.dp))` with an outer `Row`:
   ```kotlin
   Row(
       modifier = Modifier.padding(12.dp),
       verticalAlignment = Alignment.Top,
   ) {
       if (poi.imageUrl != null) {
           AsyncImage(
               model = poi.imageUrl,
               contentDescription = null,
               contentScale = ContentScale.Crop,
               placeholder = rememberVectorPainter(Icons.Default.Place),
               error = rememberVectorPainter(Icons.Default.Place),
               modifier = Modifier
                   .size(56.dp)
                   .clip(RoundedCornerShape(8.dp)),
           )
           Spacer(Modifier.width(12.dp))
       }
       Column(modifier = Modifier.weight(1f)) {
           // existing Row (name/type/rating/liveStatus) + insight Text
       }
   }
   ```
3. The existing inner `Column(modifier = Modifier.weight(1f))` (name + type) stays as-is — it already has `weight(1f)`. The new outer `Column` wrapping it gains `modifier = Modifier.weight(1f)`.

**Task 2 — Update preview** (`POIListViewPreviews.kt`)

File: `composeApp/src/androidMain/kotlin/com/areadiscovery/ui/map/POIListViewPreviews.kt`

Add a third preview function `POIListViewWithThumbnailPreview` that includes a `POI` with a non-null `imageUrl` (use any https image URL for preview purposes).

### Acceptance Criteria

**AC-1: Thumbnail renders when imageUrl is present**
- Given a `POI` with `imageUrl = "https://..."`
- When `PoiListCard` renders
- Then a 56×56dp rounded image appears on the leading edge of the card, loaded via Coil3

**AC-2: Placeholder shown while loading**
- Given a `POI` with a valid `imageUrl`
- When the image has not yet loaded
- Then a `Place` icon placeholder appears in the 56×56dp slot

**AC-3: Error fallback on load failure**
- Given a `POI` with an invalid/broken `imageUrl`
- When Coil fails to load the image
- Then the `Place` icon error painter fills the 56×56dp slot (no crash, no blank space)

**AC-4: No thumbnail when imageUrl is null**
- Given a `POI` with `imageUrl = null`
- When `PoiListCard` renders
- Then no image slot is shown; text column takes full card width (identical to current behaviour)

**AC-5: Existing card content unchanged**
- Given any `POI`
- When `PoiListCard` renders
- Then name, type, rating, liveStatus, and insight all still display correctly at all combinations of null/non-null fields

---

## Additional Context

### Dependencies

No new dependencies. `coil3.compose` and `coil3.network.ktor3` are already in `composeApp/build.gradle.kts` (lines 107–108).

### Testing Strategy

Per project policy: Composable tests only after design stabilises. This is a small, focused UI-only change. Verification path:
1. Build + install on device and visually confirm thumbnails appear in list view
2. Confirm null-imageUrl cards are unchanged
3. Preview in `POIListViewPreviews.kt` provides design-time verification

### Notes

- `ExpandablePoiCard` uses `ColorPainter` as placeholder; list thumbnail uses `rememberVectorPainter` for an icon — both are valid Coil3 `Painter` implementations.
- If a future iteration wants a consistent placeholder (e.g., the vibe color fill like `ExpandablePoiCard`), the `placeholder` param can be swapped to `ColorPainter(vibeColor.copy(alpha=0.15f))` — but that requires passing `vibeColor` into `PoiListCard`, which is out of scope here.

## Review Notes
- Adversarial review completed
- Findings: 12 total, 5 fixed, 7 deferred
- Resolution approach: auto-fix real issues
- Fixed: F1 (black icon on dark bg), F2 (crossfade), F3 (image size hint), F7 (hardcoded names), F12 (blank URL guard)
- Deferred: F4 (placeholder consistency), F5 (a11y image hint), F6 (preview network URLs), F8 (placeholder slot feel), F9 (magic numbers), F10 (recomposition stability), F11 (snapshot tests)

# Tech Spec: Discovery Header Redesign

**Feature:** Unified Discovery Header + Bottom Bar + Saved Lens
**Beta Blocker:** #11
**Status:** READY FOR IMPLEMENTATION
**Source:** Brainstorming session `_bmad-output/brainstorming/brainstorming-session-2026-03-20-001.md` (22 decisions)
**Prototypes:** `_bmad-output/brainstorming/prototype-FINAL.html` (12 screens)
**Date:** 2026-03-21
**Review:** 8H / 16M / 7L — all resolved (2026-03-21); adversarial review 15 findings — 7 pre-planned, 4 spec-fixed, 4 design-decided (2026-03-21)

---

## Summary

Replace five separate header/bar components with two unified surfaces: a **Discovery Header** (top) and a **Unified Bottom Bar**. Add a **Saved Lens** toggle that transforms the same map into a saved-places view without navigation. This resolves the coexistence problem between Search, Surprise, Vibe Rail, Top Context Bar, Ambient Ticker, and Companion Orb — all competing for screen space and user attention.

**Net result:** 6 components → 2 surfaces. Cleaner map. One-thumb reach. No context loss.

---

## Dependencies

- **Safety Advisory** (`tech-spec-safety-travel-advisory.md`) — already implemented (commit `ac8f321`). Safety dot reads from `AdvisoryProvider` / `AdvisoryLevel` enum (in `AreaAdvisory.kt`). Values: `SAFE`, `CAUTION`, `RECONSIDER`, `DO_NOT_TRAVEL`, `UNKNOWN`. If `AdvisoryProvider` is unavailable, the dot is hidden (unknown/safe default). Do not redefine the `AdvisoryLevel` enum — import it.
- **Surprise Here** (`tech-spec-streaming-discovery-ux.md`) — already implemented. 🎲 calls `MapViewModel.onSurpriseMe()` unchanged.
- **Streaming discovery pipeline** — already implemented. `Discover` button calls `MapViewModel.onSearchThisArea()` unchanged.

---

## Components Removed

| Removed | Replaced By | Deleted In |
|---------|-------------|------------|
| `TopContextBar` | Discovery Header — collapsed row | Commit A |
| `GeocodingSearchBar` | Discovery Header — expanded panel search input | Commit A |
| `AmbientTicker` | Rotating meta line (collapsed) + Intel strip (expanded) | Commit A |
| `SearchSurpriseTogglePill` (private in MapScreen.kt) | 🎲 Surprise on header bar; `Discover` button on pan | Commit A |
| `VibeRail` (floating right-side) | Vibe chips inside expanded header | Commit A |
| `CompanionOrb` (floating FAB) | Unified bottom orb bar | Commit B |
| `CompanionCard` | Peek card in bottom bar | Commit B |
| `AISearchBar` | Orb bar text + mic | Commit B |
| `MapListToggle` | ▤/🗺 toggle in bottom bar | Commit B |
| `SavesNearbyPill` (private in MapScreen.kt) | Count pill in header | Commit B |

Each component is deleted in the SAME commit that introduces its replacement. No dead code windows between commits.

---

## Screen Anatomy

```
┌──────────────────────────────────────┐
│ FIND — Discovery Header    ~52dp     │
│ [Area Name 🟢] [rotating meta] [📍5 ♥2] [🎲] [▼] │
├──────────────────────────────────────┤
│ SEE — Full-screen map + pins         │
│                     ◎ (bottom-right) │
│                                      │
├──────────────────────────────────────┤
│ BROWSE — POI Carousel                │
├──────────────────────────────────────┤
│ UNDERSTAND — Bottom Bar    ~56dp     │
│ [☰] [▤/🗺] [🌟 orb bar........... 🎤] │
└──────────────────────────────────────┘
```

**Interactive elements (7 total):**

| # | Element | Zone | Purpose |
|---|---------|------|---------|
| 1 | Discovery Header (expandable) | Top | FIND |
| 2 | 🎲 Surprise button | Header right | DISCOVER |
| 3 | ♥ in count pill (tappable) | Header center | LENS SWITCH |
| 4 | ◎ / ⌂ location button | Map bottom-right | NAVIGATE |
| 5 | ☰ hamburger | Bottom bar left | APP CHROME |
| 6 | ▤ / 🗺 view toggle | Bottom bar center | VIEW SWITCH |
| 7 | 🌟 Orb bar + 🎤 mic | Bottom bar right | AI COMPANION |

---

## Discovery Header

### Location Acquiring / Pre-Lock State (H5)

Before GPS locks or while reverse geocoding is pending, the header must always render something. Three sub-states:

| Sub-state | Area Name Slot | Safety Dot | Count Pill | 🎲 |
|-----------|---------------|------------|------------|-----|
| GPS acquiring | "Locating..." (italic, muted) | Hidden | Hidden | Disabled (grey) |
| GPS locked, geocode pending | Skeleton shimmer ~80dp wide | Hidden | Hidden | Disabled |
| Geocode failed / no result | "Unknown Area" (muted) | Hidden | Show `📍0 ♥n` | Enabled (surprise fires on GPS coords) |

- Meta line during GPS acquiring: `🛰 Getting your location...` (static, no rotation)
- Skeleton shimmer uses the same shimmer animation as POI card skeletons

### Location Permission Denied State (H7)

When the user has denied location permission:

- Area name: "Search to explore" (muted, italic)
- Safety dot: hidden
- Count pill: hidden
- 🎲 Surprise: disabled (no location = no local surprise)
- Meta line: `🔍 Search any city, place, or area`
- Expanded panel: search input is still fully functional (ELSEWHERE results only; HERE section hidden)
- Orb bar: "Search for a place to get started"

This preserves Jamie's (location-denied user) path to value via manual search. The Discover button never appears for location-denied users (no GPS = no pan detection).

### Collapsed State (~52dp, always visible)

**Top safe area:** The `DiscoveryHeader` composable must apply `windowInsetsPadding(WindowInsets.safeDrawingTop)` (or `statusBarsPadding()`) so it renders below the status bar / Dynamic Island on iOS and below the Android status bar. Without this the header will render under the notch on iOS devices.

**Layout (left → right):**
```
[Area Name 🟢] [rotating meta line] [📍5 ♥2] [🎲] [▼]
```

| Slot | Content | Notes |
|------|---------|-------|
| Area name | Current area/neighborhood name | Truncate with ellipsis at ~120dp |
| Safety dot | Colored dot immediately after area name | See Safety Dot spec below |
| Rotating meta | 14px line, cycles every 4s | See Meta Ticker spec below |
| Count pill | `📍5 ♥2` | Tap ♥ → saved lens toggle (see Count Pill spec) |
| 🎲 Surprise | Visual: 38dp square, purple gradient + shimmer. Tap target: 48dp (extended via padding) | Always one-tap; larger visual than other buttons |
| ▼ Chevron | Indicates expandability | Animates to ▲ when expanded |

**Touch targets:** All interactive elements in the header must have minimum 48dp touch targets. Where the visual size is smaller (🎲 at 38dp visual), extend the tap area with transparent padding to reach 48dp. (WCAG 2.1 SC 2.5.5)

**Tappable areas in collapsed state:**
- Entire header bar (except 🎲 and count pill ♥) → expands header
- 🎲 → triggers Surprise Here (local, filter-aware)
- ♥ in count pill → saved lens toggle (48dp tap area extending beyond the ♥ glyph)

**Bottom bar during expanded header:** The bottom bar is NOT behind the scrim. It remains fully interactive when the header is expanded. Opening chat or hamburger from the bottom bar while header is expanded collapses the header first, then opens the requested surface. (M16) **Nudge deferral:** If a nudge arrives while the header is expanded, it queues silently and displays only after the header collapses. No z-order conflict between peek card and header scrim. (R15)

### Rotating Meta Ticker (D3)

Single 14px line. Rotates every 4s on a smooth crossfade. Priority order when multiple are relevant:

| Priority | Condition | Format | Color |
|----------|-----------|--------|-------|
| 1 | Safety CAUTION, RECONSIDER, or DO_NOT_TRAVEL | `⚠️ Reconsider travel · Check advisory` | Amber |
| 2 | Remote/teleported area | `From Dubai · 8,300 mi` | Teal |
| 3 | Active vibe filter (when collapsed) | `🎭 3/5 Arts · History` | Purple |
| 4 | Companion nudge available | `✨ Try Surprise — 3 spots match your taste` | Purple |
| 5 | Featured POI nearby | `🎡 Ain Dubai 1.2 km · sunset at 6:15 PM` | Teal |
| 6 | Default (always present) | `🌤 82°F · 3:42 PM · First visit` | White/muted |

Rules:
- Safety warning (priority 1) does NOT rotate — it stays fixed until area changes. This is the **primary accessible signal** for safety state (color dot is secondary). (H3)
- Remote context (priority 2) replaces default weather line when teleported
- Companion nudge (priority 3) competes with POI highlight in rotation
- Default line is always the fallback when no other context exists
- "First visit" vs "Visited 3×" vs "Home area" in default line: **"Home area" only appears when the current view IS the user's home area** — not as a reminder while teleported. When teleported, priority-2 remote line takes over. (L7)
- During spinner/discovery: meta line **pauses rotation** and shows `Discovering [Area]...` in muted white. Resumes rotation after POIs load. (M12)
- `contentDescription` for accessibility: on rotation, post a `LiveRegion.POLITE` announcement of the new line text. For safety warning, use `LiveRegion.ASSERTIVE`. (L5)

### Pan Map → Discover Transformation (D4)

**Pan threshold:** Reuse the same threshold defined in `tech-spec-streaming-discovery-ux.md` for "Search this area" appearance — the Discover button appears under the same conditions. Do not define a new threshold. (M1)

When threshold is crossed:

1. Count pill (`📍5 ♥2`) is **replaced** by a teal **`Discover`** button
2. Layout becomes: `[New Area Name 🟢?] [rotating meta] [Discover] [🎲] [▼]`
3. Safety dot reflects the new area's safety level (or hidden if unknown)
4. After user taps `Discover` and POIs load → `Discover` disappears, count chip returns with new counts
5. If user taps 🎲 during pan state → Surprise fires for the panned-to area
6. **Saved lens active:** Pan threshold detection is disabled while saved lens is active. The Discover button never appears in saved lens mode — cluster zoom does not trigger it. (M6)

**Count pill debounce:** The count pill updates on a 500ms debounce after pan settles, not on every drag frame. The pop animation fires at most once per 2s to prevent flicker. (M2)

### Spinning / Loading State (D13)

During POI discovery (after tapping Discover, or initial load):
```
[spinner] Discovering [Area Name]... [filter badge if active] [Cancel]
```
- Count pill hidden while spinning
- 🎲 Surprise disabled while spinning (greyed out)
- **Cancel tap → stops discovery, restores the previous area's POIs and count pill.** If the user had 5 POIs loaded in the prior area, those 5 remain visible with their count after Cancel. The Discover button re-appears for the panned-to area. (M3)
- Filter badge shows active vibe if vibes are selected (e.g., `🎭 Arts`)
- **Slideshow interaction:** The streaming slideshow from `tech-spec-streaming-discovery-ux.md` starts when discovery completes (first POI arrives), regardless of which surface triggered discovery (header Discover button, 🎲, or pan). The header spinner runs during discovery; the slideshow starts after. They do not overlap. Cancel aborts both the discovery pipeline and any pending slideshow start. (H6)

### Expanded State (D5)

Tapping collapsed bar (outside 🎲 and count pill) expands the header into a full panel. Slides down with a smooth spring animation. Scrim covers the map only (not the bottom bar). Tap scrim → collapses.

**Gesture conflict (iOS):** The scrim composable must consume ALL pointer events (tap, drag, pinch) so MapLibre's underlying gesture recognizers do not fire while the panel is open. `detectTapGestures` alone is insufficient — it only handles taps, allowing drag/pinch to leak through to MapLibre on iOS. Use `awaitPointerEventScope` with `PointerEventPass.Initial` consuming every `PointerEvent`:
```kotlin
Modifier.pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            event.changes.forEach { it.consume() }
        }
    }
}
```
The expanded panel's own horizontal chip scroll and vertical content scroll are unaffected — they sit above the scrim in z-order. **Implementation constraint:** Apply the consuming `pointerInput` to the scrim layer only (a sibling composable behind the panel content in the composition tree), NOT to any composable that wraps the panel content — otherwise panel scrolling will be blocked.

**Keyboard behavior:** On expand, the search input auto-focuses and the software keyboard opens. Use `Modifier.imePadding()` on the expanded panel so the bottom bar stays visible above the keyboard. The POI carousel is pushed off-screen (acceptable — the keyboard is for search). The map tiles remain visible above the keyboard.
- **Android only:** call `WindowCompat.setDecorFitsSystemWindows(window, false)` in `MainActivity` (likely already set). Do not add this call in common code.
- **iOS:** keyboard avoidance is handled automatically by the system — no additional Compose modifier needed. `imePadding()` is a no-op on iOS and safe to leave in common code. (M11)

**Panel contents (top to bottom):**

1. **Search input**
   - Placeholder: "Search places, areas, or vibes..."
   - Full smart search (see Search spec below)
   - Auto-focused with keyboard on expand

2. **Context grid** (3 tiles)
   - Weather: `🌤 82°F`
   - Time: `3:42 PM`
   - Visit status: `First visit` / `Visited 3×` / `Home area`

3. **Vibe filter chips** (horizontal scroll)
   - Named vibes: Character, Food & Drink, Arts, Nightlife, Nature, History
   - Tappable, multi-select
   - Color-coded per vibe (existing vibe color system)
   - Active chip = filled background; inactive = outlined
   - Active filter persists when panel collapses (meta shows `🎭 3/5` badge)
   - **Vibe filter on teleport:** Active vibe filters are **cleared** when the user teleports to a new area via search. Discovery for a new area always starts fresh. (M5)

4. **Intel strip**
   - One AI-generated area fact, rotating every 8s
   - **Data source:** Separate from the rotating meta line. The intel strip facts come from the area's `CHARACTER` bucket summary (same Gemini response, different extraction). The rotating meta line uses `MetaLine` sealed class items (weather, POI highlights, nudges). These are two distinct data streams with different rotation timers. (M14)
   - Teal accent border left
   - Tap → opens companion chat pre-loaded with that fact

5. **Action buttons** (horizontal row)
   - `✨ Surprise` (primary, purple) — same as 🎲 but labeled
   - `🔄 Refresh` — re-runs discovery for current area (`onSearchThisArea()`)
   - `🔖 Save Area` — hidden until area-save feature is implemented (future spec)

6. **Recent explorations** (bottom of panel)
   - Up to 3 cards: past teleportations
   - Each shows: area name, country, save count, distance from current
   - Tap → teleports to that area
   - **Empty state (first-time user / no teleports yet):** Section is hidden entirely. No placeholder, no empty card slots. The action buttons row becomes the last visible panel item. (M10)

### Smart Search (D6, D7)

One search input handles everything. Results split into three sections:

**HERE (local):**
- POIs matching the query within current viewport
- Distance shown on each result
- Vibe filter badge if relevant
- Color: Teal badge

**YOUR SAVES:**
- Previously saved places matching the query
- Show save date + area name
- Color: Yellow badge

**ELSEWHERE (remote):**
- Areas (teleport destination) — Blue badge
- Vibes (AI-curated collections) — Red badge
- Remote POIs — Teal badge with distance

**Search = Vibe chips = same filter system (D7):**
- Typing a named vibe keyword (e.g., "arts") is equivalent to tapping that chip — both set the same `activeVibeFilters` state
- Typing a non-vibe keyword (e.g., "sushi") creates an **ad-hoc filter chip** rendered below the search input
  - Ad-hoc chips use a neutral grey color (no vibe color) with a plain label (e.g., `sushi ✕`)
  - Ad-hoc chips appear in the same chip row as named vibe chips, after them
  - Ad-hoc chips can coexist with named chips (e.g., `Arts` + `sushi ✕`)
  - The `✕` on an ad-hoc chip removes it; named vibe chips are removed by tapping again
  - `contentDescription`: "[keyword] custom filter, tap to remove" (L1)
- Active filters visible as chips below search input while typing

### Vibe Chips (D8)

- Live inside expanded panel (not a floating rail)
- **Multi-select: OR filter** — tapping multiple vibes shows POIs that match **at least one** of the selected vibes (union, not intersection). Example: Arts + History shows POIs tagged with Arts OR History or both. (H1)
- When filter is active and panel is collapsed:
  - Meta line shows: `🎭 3/5 Arts · History` (count + active vibe names, truncated)
  - A small filter badge appears on the map canvas (top-left, non-interactive, status indicator only)
  - Non-matching pins dim to 30% opacity
- **Surprise with active filter:** 🎲 picks from the filtered (OR union) set. If the filtered set is empty (zero matching POIs for selected vibes), 🎲 does not fire — instead the orb bar shows: `"No [Vibe] spots here — try nearby?"` and the 🎲 button briefly pulses then returns to enabled. Repeated taps keep showing the same orb message (not silently no-op). The user can change vibes or clear the filter. (H8)
- Clear filter: tap active chip again, or swipe the filter badge in the meta line

### Safety Dot (D11)

Small colored dot rendered immediately after area name text, vertically centered. **Color is a secondary indicator.** The primary accessible signal for safety state is the priority-1 rotating meta ticker text. (H3)

| AdvisoryLevel | Color | Animation | `contentDescription` |
|---------------|-------|-----------|----------------------|
| `SAFE` | Green `#4CAF50` | None | "Area safety: safe" |
| `CAUTION` | Amber `#E3B341` | Slow pulse (2s) | "Area safety: exercise caution" |
| `RECONSIDER` | Orange `#DB6D28` | Slow pulse (2s) | "Area safety: reconsider travel" |
| `DO_NOT_TRAVEL` | Red `#DA3633` | Fast pulse (1s) | "Area safety: do not travel" |
| `UNKNOWN` | Hidden | — | (no dot, no announcement) |

Safety dot reads from `AdvisoryLevel` (defined in `AreaAdvisory.kt`, already implemented). Colors match existing `TopContextBar.kt` dot color mapping.

When `AdvisoryLevel` is `CAUTION`, `RECONSIDER`, or `DO_NOT_TRAVEL`: warning text occupies priority-1 in the rotating meta ticker (overrides all other lines, stays fixed). `SAFE` and `UNKNOWN` do not trigger a priority-1 override.

### Count Pill (D12)

```
📍5 ♥2
```

| State | Display |
|-------|---------|
| Normal | `📍[n] ♥[m]` |
| Filtered | `🏞 [match]/[total]` e.g. `🏞 3/5` |
| New POI arrives during streaming | Pill scales up briefly (pop animation, max once per 2s) |
| Saved lens active | Pill hidden; Saved banner shows instead |
| Spinner active | Pill hidden |

- ♥ number is a tap target — **tap area must be 48dp minimum** (extend with transparent padding beyond the ♥ glyph) (M13)
- Pin icon number = POIs currently on map (updates on 500ms debounce after pan settles)
- Heart number = saved places in current viewport (same debounce)
- `contentDescription`: "[n] discovered, [m] saved" (L5)

### Remote Exploring (D10)

When user has teleported to a remote area:
- Meta line shows (priority 2): `From [Home City] · [distance]` in teal
- Location button (◎) transforms to ⌂ with a teal ring
- Tap ⌂ → returns camera to GPS location, clears remote state, restores home area context
- Area name in header updates to remote area name with appropriate safety dot
- Active vibe filters are cleared on teleport (M5)

---

## Bottom Bar

### Layout (D14)

Fixed bottom bar, ~56dp content height + `WindowInsets.safeDrawingBottom` padding added below (handles iPhone home indicator ~34pt and Android gesture nav). The bar's visual content stays above the system gesture zone on all devices. (M9)

```
[☰]   [▤/🗺]   [🌟 ................... 🎤]
```

| Slot | Min touch width | Element |
|------|----------------|---------|
| Left | 48dp | ☰ Hamburger menu |
| Center-left | 48dp | ▤/🗺 View toggle |
| Right (expands) | Remaining | 🌟 Orb bar + 🎤 mic |

### Mutual Exclusion Rule (M8)

**Hamburger menu and orb peek card are mutually exclusive.** Opening the hamburger dismisses the peek card (if open). Triggering a peek card (nudge tap) dismisses the hamburger menu (if open). Only one bottom-bar overlay can be visible at a time.

### View Toggle (▤/🗺)

Single button, icon swaps based on current view:
- In map view: shows `▤` (list icon) — tap → switches to list view
- In list view: shows `🗺` (map icon) — tap → switches to map view. Icon tinted teal when in list view.
- Toggle works identically in both normal and saved lens modes

### Unified Orb Bar (D15)

The bottom bar orb IS the companion. Replaces the floating companion orb entirely. Proactive nudges, reactive chat — same surface.

**States:**

| State | Orb Icon | Text | Glow / Border |
|-------|----------|------|---------------|
| Idle | 🌟 | "Ask about [Area Name]..." | None |
| Nudge arrives | 💬 + notification pip | "Ain Dubai closes in 2 hrs!" | Purple border + shimmer streak |
| Surprise delivered | ✨ | "Here's why I picked these..." | Purple glow |
| Just saved a POI | ♥ | "Why you'd love this place..." | Red glow |
| Teleported to new area | 🌟 | "Tell me about [Area]..." | Teal glow |
| POIs streaming | 🌟 | "Want to know about these places?" | None |
| Active vibe filter | 🌟 | "Showing [Vibe] spots near you" | None |

**Nudge queue:** If a new nudge arrives while another nudge is already showing, the new nudge queues. After the current nudge is dismissed (or after 8s auto-dismiss), the queued nudge appears. Priority within the queue: safety nudges first, then time-sensitive nudges (closing soon), then taste/Surprise nudges. Maximum queue depth: 3. (L3)

**`contentDescription` per orb state:** Set dynamically — e.g., "AI companion: Ask about Dubai Marina", "AI companion: Ain Dubai closes in 2 hours, tap for details". (L5)

**Interaction on tap:**
- Tap nudge text → peek card expands upward from bar with full nudge text + 1-2 action buttons
- Swipe up on peek card → full chat overlay opens
- Tap orb icon → full chat overlay opens (always)
- Tap 🎤 → voice input (existing STT flow)

**Peek card:**
- Slides up 120dp above bar, contained to right side
- **Z-order:** Peek card renders above the POI carousel (carousel is not pushed up; peek overlaps it). The carousel remains visible and interactive in the left/center portion of the screen. (M4)
- Shows full nudge text + action buttons (e.g., "Navigate", "Save", "Dismiss")
- **"Navigate" action:** Opens platform maps intent. KMP `expect/actual`: Android = `Intent(ACTION_VIEW, geo:lat,lng?q=lat,lng(name))`, iOS = Maps URL scheme. Implement as `expect fun openMapsNavigation(lat: Double, lng: Double, name: String)` in `commonMain`. (L2)
- Tap scrim or swipe down → dismisses peek
- `PlatformBackHandler` required (collapses peek before closing chat)

### Hamburger Menu (D16)

- Tapping ☰ opens a bottom sheet that rises from the ☰ button position
- Scrim covers map + carousel (not the bottom bar itself)
- ☰ icon morphs to ✕ while menu open
- Menu items:
  1. Profile
  2. AI Personality
  3. Settings
  4. Send Feedback
- `PlatformBackHandler` required (dismisses menu)
- Tap scrim → closes menu
- **Mutual exclusion:** Opening hamburger dismisses orb peek if open (see Mutual Exclusion Rule above)

### No Floating FAB (D17)

The floating FAB is fully removed. Profile and Settings move to the hamburger menu. The only floating element on the map surface is ◎/⌂ (location button, bottom-right map overlay).

---

## Saved Lens (One Map, Two Views)

### Toggle (D18)

Entry point: tap ♥ in the count pill.

- Map context switches to "saved" mode
- **Saved lens blocks pan detection:** The pan → Discover threshold check is disabled while in saved lens. Cluster zoom-in does not show the Discover button.
- A persistent **Saved banner** appears below the header:
  ```
  ♥ Saved Places — [n] total   [✕ Exit]
  ```
- Tap ✕ on banner → exits saved lens, returns to discovery map
- `PlatformBackHandler` exits saved lens

### Saved Map (D19)

When saved lens is active:
- Map zooms out to show all saved places worldwide
- Discovered/local pins are hidden
- Saved places shown as heart pins (♥, red)
- Cluster circles where multiple saves are geographically close, labeled: "Dubai Marina · 4"
- Map background unchanged

### Saved List (D20)

▤/🗺 toggle works identically in saved lens:
- List view shows saved places grouped by area
- Each group: area name + country, date of first save in area
- Each item: POI name, vibe, save date
- Sort: most recently saved-to area first. **Tiebreaker:** alphabetical by area name. (L6)

### Cluster Drill-Down (D21)

- Tap a cluster circle → map animates and zooms to that area
- Individual heart pins become visible
- Tap any heart pin → POI detail card opens (existing detail card, read-only save mode)
- Cluster zoom does NOT trigger pan → Discover (saved lens blocks it)

### Save Triggers Discovery Loop (D22)

When user saves a POI (from any surface):
- Toast appears: `Saved! [n] similar nearby`
- 2-3 dashed-outline ghost pins appear on map near the saved POI
- **Ghost pin similarity:** Same `VibeCategory` as the saved POI, nearest by distance from the current POI set. No new API call — filter from the already-loaded POI list in `DiscoveryViewModel`. (M7)
- **Ghost pin lifetime:** Session-scoped. Ghost pins are cleared on the next discovery trigger (new area, Refresh, or Surprise). They are not persisted to the database. Managed as `List<GhostPin>` in `DiscoveryViewModel` state. (M7)
- Tap a ghost pin → opens its POI detail card. Bottom CTA reads: **"Discover this one too"** → action: saves the POI immediately (same as tapping the save button on the card). The ghost pin converts to a normal saved heart pin. (L4, M7)
- Taste profile update fires in background
- Next Surprise reflects the updated taste model

---

## Key User Flows

### Flow 1: First Open (GPS Area)

```
App open
→ Header: [Locating... ] [🛰 Getting your location...] [🎲 disabled] [▼]
→ GPS locks
→ Header: [skeleton shimmer] [weather/time] [🎲 disabled] [▼]
→ Geocode resolves: [Dubai Marina 🟢] [🌤 82°F · first visit] [📍0 ♥2] [🎲] [▼]
→ Spinner: [🔄 Discovering Dubai Marina...] [Cancel]
→ POIs load: count pill animates [📍5 ♥2]
→ Meta rotates to first POI highlight
→ Orb bar idle: "Ask about Dubai Marina..."
```

### Flow 2: Pan + Discover

```
User pans map to new area (threshold crossed)
→ Header: [Jumeirah 🟢?] [weather if known] [Discover] [🎲] [▼]
→ Tap Discover → spinner
→ POIs load → count chip returns
→ OR tap 🎲 → Surprise fires for panned area
```

### Flow 3: Vibe Filter

```
Tap collapsed header → expanded panel opens
→ Tap "Arts" chip → tap "History" chip (OR filter)
→ Panel collapses: meta shows "🎭 2 filters · 3/5"
→ Map dims non-matching pins to 30%
→ Tap 🎲 → Surprise picks from Arts OR History POI set
→ If zero matches → orb bar: "No Arts/History spots here — try nearby?"
```

### Flow 4: Search Elsewhere + Teleport

```
Tap header → expanded panel → type "Griffith Park"
→ ELSEWHERE section: "Griffith Park · Los Angeles" (Blue badge)
→ Tap → active vibes cleared → map teleports to LA
→ Header: [Griffith Park 🟢] [From Dubai · 8,300 mi] [Discover] [🎲] [▼]
→ ◎ becomes ⌂ with teal ring
→ Orb bar: "Tell me about Griffith Park..."
```

### Flow 5: Saved Lens

```
Tap ♥2 in count pill
→ Map zooms out: heart pins worldwide + cluster circles
→ Saved banner: "♥ Saved Places — 12 total  [✕]"
→ Pan detection disabled
→ Tap cluster "San Francisco · 5" → map zooms to SF (no Discover button)
→ Tap individual heart pin → POI card opens
→ Tap ✕ banner or press back → return to discovery map
```

### Flow 6: Companion Nudge

```
Orb bar: 💬 pip + "Ain Dubai closes in 2 hrs!"
→ Tap text → peek card slides up (over carousel, right side)
→ Peek card: full message + "Navigate" + "Save" + "Dismiss"
→ Tap "Navigate" → platform maps intent (Android: geo intent, iOS: Maps URL)
→ OR swipe up → full chat with context pre-loaded
```

---

## Acceptance Criteria

### Discovery Header — Pre-Lock / Permission Denied

- [ ] AC0a: GPS acquiring state shows "Locating..." + static meta "🛰 Getting your location..." + 🎲 disabled
- [ ] AC0b: Geocode pending shows skeleton shimmer in area name slot
- [ ] AC0c: Geocode failure shows "Unknown Area" + no safety dot + 🎲 enabled
- [ ] AC0d: Location denied shows "Search to explore" + all count/dot elements hidden + 🎲 disabled

### Discovery Header — Collapsed

- [ ] AC1: Header renders at ~52dp, always visible, not covered by map content
- [ ] AC2: Area name truncates correctly at long names
- [ ] AC3: Safety dot appears after area name; correct color + contentDescription per advisory level
- [ ] AC4: Meta line rotates every 4s with crossfade; safety warning stays fixed; meta pauses during spinner
- [ ] AC5: Count pill shows correct discovered + saved counts, updated on 500ms debounce after pan settles
- [ ] AC6: 🎲 visual is 38dp square, tap target is 48dp; purple gradient + shimmer; one-tap fires Surprise
- [ ] AC7: Chevron animates ▼ → ▲ on expand; collapses on tap outside or back press
- [ ] AC8: Pan past threshold → count pill replaced by teal Discover button; Discover absent in saved lens mode
- [ ] AC9: Spinner state shows during discovery; Cancel restores prior area's POIs and count pill
- [ ] AC10: ♥ tap (48dp target) toggles saved lens; ✕ banner or back press exits lens

### Discovery Header — Expanded

- [ ] AC11: Search input auto-focuses with keyboard; bottom bar stays visible above keyboard (imePadding)
- [ ] AC12: Search results categorized into HERE / YOUR SAVES / ELSEWHERE with correct color badges
- [ ] AC13: Typing a named vibe keyword sets the same filter as tapping that chip
- [ ] AC14: Vibe chips are multi-select OR filter; active filter persists when panel collapses
- [ ] AC15: Active filter dims non-matching map pins to ~30%
- [ ] AC16: 🎲 Surprise picks from OR-filtered set; zero-match → orb nudge shown, 🎲 stays enabled
- [ ] AC17: Recent explorations shown (up to 3); section hidden entirely when no teleport history
- [ ] AC18: Intel strip rotates area facts every 8s (separate from meta line); tap opens chat with fact
- [ ] AC19: Ad-hoc filter chip rendered for non-vibe search terms; removable with ✕; coexists with named chips
- [ ] AC20: Vibe filters cleared on teleport to new area
- [ ] AC21: Bottom bar remains interactive while header is expanded (not behind scrim)

### Bottom Bar

- [ ] AC22: Bottom bar content height ~56dp + safeDrawingBottom inset (iOS home indicator + Android nav)
- [ ] AC23: ▤/🗺 toggle swaps icon correctly; teal tint when in list view
- [ ] AC24: Orb bar displays correct text/icon/glow per all 7 states in the state table
- [ ] AC25: Orb contentDescription updates per state (screen reader)
- [ ] AC26: Tap nudge text → peek card slides up over carousel (right side); swipe up → full chat
- [ ] AC27: Hamburger and orb peek are mutually exclusive; opening one dismisses the other
- [ ] AC28: Tap ☰ → bottom sheet rises; ☰ morphs to ✕; closes on scrim tap or back press
- [ ] AC29: Hamburger menu contains Profile, AI Personality, Settings, Send Feedback
- [ ] AC30: No floating FAB on map surface (fully removed)
- [ ] AC31: "Navigate" in peek card uses platform maps intent (geo intent Android, Maps URL iOS)

### Saved Lens

- [ ] AC32: Saved lens shows all saves worldwide as heart pins with cluster circles
- [ ] AC33: Cluster tap zooms to area and shows individual pins; no Discover button appears
- [ ] AC34: ▤/🗺 toggle works in saved lens (list grouped by area, sorted by most recently saved, alpha tiebreaker)
- [ ] AC35: Save POI → toast + 2-3 ghost pins (same vibe, nearest); ghost pins cleared on next discovery
- [ ] AC36: Ghost pin tap → POI card; "Discover this one too" CTA saves the POI + converts pin to heart pin
- [ ] AC37: Saved banner shows count; ✕ exits lens; back press exits lens

### Additional Behaviors

- [ ] AC40: Nudge queue respects max depth 3; priority: safety → time-sensitive → taste; excess nudges dropped
- [ ] AC41: Intel strip tap opens companion chat pre-loaded with the tapped fact text as entry context
- [ ] AC42: "Refresh" button in expanded panel re-runs `onSearchThisArea()` and shows spinner
- [ ] AC43: "Save Area" button is hidden if area-save feature is not yet implemented; renders only when the feature exists
- [ ] AC44: Meta line posts `LiveRegion.POLITE` on rotation; `LiveRegion.ASSERTIVE` for safety warnings
- [ ] AC45: Repeated 🎲 taps with zero-match filter keep showing orb message each time (not silent no-op)
- [ ] AC46: Ghost pin visual converts from dashed outline to filled heart pin after "Discover this one too" tap
- [ ] AC47: DiscoveryHeader applies `statusBarsPadding()` — does not render under notch/Dynamic Island

### Back Button / PlatformBackHandler

- [ ] AC38: Back press priority (topmost wins): hamburger menu → expanded header → orb peek → saved lens → app exit (keyboard dismiss handled natively by Android, not via PlatformBackHandler)
- [ ] AC39: Each layer has its own enabled `PlatformBackHandler` that fires only when that layer is active

### Test Migration

Each commit must update or create tests for replaced components:

- **Commit A:** `AmbientTickerTest.kt` → `RotatingMetaTickerTest.kt` (test `buildMetaLines()` priority sorting, safety override, rotation pause). `TopContextBar`, `GeocodingSearchBar`, `VibeRail`, `SearchSurpriseTogglePill` had no tests — no migration needed, but new unit tests for `MetaLine` priority logic and `CountPill` debounce are required.
- **Commit B:** `CompanionOrb`, `CompanionCard`, `AISearchBar`, `MapListToggle`, `SavesNearbyPill` had no dedicated test files. New unit tests required: orb state mapping (7 states → correct icon/text/glow), mutual exclusion logic (hamburger dismisses peek and vice versa), nudge queue priority ordering + max depth 3. Verify `CompanionNudgeEngineTest.kt` still passes (engine is unchanged, but call sites move).
- **Commit C:** New unit tests required: ghost pin generation logic (same vibe filter, nearest by distance, max 2-3 pins), ghost pin → heart pin conversion on save, saved lens toggle state (pan detection disabled, Discover button suppressed). Test saved list sort order (most recent area first, alpha tiebreaker).

---

## Implementation Notes

### Kotlin/KMP Architecture

- `DiscoveryHeader` composable replaces `TopContextBar`, `GeocodingSearchBar`, `AmbientTicker`, `SearchSurpriseTogglePill`, `VibeRail`
- `UnifiedBottomBar` composable replaces floating `CompanionOrb` + existing bottom area
- Saved lens state lives in `MapUiState.Ready` (fields: `savedLensActive: Boolean`, `ghostPins: List<GhostPin>`), managed by `MapViewModel`. No separate ViewModel.
- Rotating meta: `LaunchedEffect` cycling a priority-sorted `List<MetaLine>` sealed class with `delay(4_000)`. Paused via a `isPaused` flag during spinner state.
- Intel strip: separate `LaunchedEffect` with `delay(8_000)` cycling `List<String>` from area CHARACTER bucket. No shared state with `MetaLine`.
- Safety dot: reads `AdvisoryLevel` from `AdvisoryProvider` (already defined in `AreaAdvisory.kt`). Import — do not redefine.
- Vibe filter: `MapUiState.Ready.activeVibeFilters: Set<String>` (matching `DynamicVibe.label` values) — cleared on `onSearchThisArea()` call that results from a teleport, not from a local Discover tap. Multi-select uses OR-union of `DynamicVibe.poiIds` lists. Label matching must be case-insensitive and trimmed (`label.trim().lowercase()`) to handle Gemini response variance. **Known limitation:** streaming POIs that arrive after vibe association may not appear in filtered results; accepted for now, fix in a future pass.
- Ghost pins: `MapUiState.Ready.ghostPins: List<GhostPin>` — managed by `MapViewModel`, cleared on next `onSearchThisArea()` call.
- Platform maps navigation: `expect fun openMapsNavigation(lat: Double, lng: Double, name: String)` in `commonMain`; `actual` in `androidMain` (geo intent with label) and `iosMain` (Maps URL scheme).
- Bottom bar safe area: `Modifier.windowInsetsPadding(WindowInsets.safeDrawingBottom)` on the bottom bar container.

### New Type Definitions

```kotlin
// domain/model/MetaLine.kt
sealed class MetaLine(val priority: Int) {
    data class SafetyWarning(val text: String) : MetaLine(1)       // Amber, fixed (no rotation)
    data class RemoteContext(val fromCity: String, val distance: String) : MetaLine(2) // Teal
    data class VibeFilter(val count: Int, val total: Int, val names: String) : MetaLine(3) // Purple
    data class CompanionNudge(val text: String) : MetaLine(4)      // Purple
    data class PoiHighlight(val text: String) : MetaLine(5)        // Teal
    data class Default(val text: String) : MetaLine(6)             // White/muted
    data object GpsAcquiring : MetaLine(99)                        // Static, no rotation
    data object LocationDenied : MetaLine(99)                      // Static
    data class Discovering(val areaName: String) : MetaLine(99)    // Pauses rotation
}

// domain/model/GhostPin.kt
data class GhostPin(
    val poi: POI,               // The suggested POI
    val sourcePoiSavedId: String, // The saved POI that triggered this suggestion
)

// expect/actual in commonMain
expect fun openMapsNavigation(lat: Double, lng: Double, name: String)
// Android actual: Intent(ACTION_VIEW, Uri.parse("geo:$lat,$lng?q=$lat,$lng($name)"))
// iOS actual: URL("http://maps.apple.com/?daddr=$lat,$lng&dirflg=d")
```

### Phased Implementation Plan (3 commits on one branch)

**Commit A — DiscoveryHeader:** Build `DiscoveryHeader` (collapsed + expanded), `RotatingMetaTicker`, `CountPill`, `SafetyDot` (extracted), `SmartSearchPanel`, `VibeChipRow`. Add `activeVibeFilters: Set<String>` to `MapUiState.Ready`. Header expanded/collapsed is ephemeral UI state — use `remember { mutableStateOf(false) }` local to `DiscoveryHeader`, NOT in `MapUiState.Ready`. Wire into `MapScreen.kt` replacing `TopContextBar` + `GeocodingSearchBar` + `AmbientTicker` + `SearchSurpriseTogglePill` + `VibeRail`. Delete replaced component files in this commit. Update `AmbientTickerTest` → `RotatingMetaTickerTest`. Smoke test.

**Commit B — UnifiedBottomBar:** Build `UnifiedBottomBar`, `OrbBar` (7 states), `PeekCard`, `HamburgerMenu`. Add `expect fun openMapsNavigation(lat, lng, name)`. Wire into `MapScreen.kt` replacing `CompanionOrb` + `CompanionCard` + `AISearchBar` + `MapListToggle` + `SavesNearbyPill`. Delete those 5 old component files/private fns in this commit. Add mutual exclusion + PlatformBackHandler chain. Smoke test.

**Commit C — SavedLens:** Build `SavedLensBanner`, saved map overlay, ghost pin rendering. Add `savedLensActive: Boolean` + `ghostPins: List<GhostPin>` to `MapUiState.Ready`. Wire saved lens toggle, pan detection disable, ghost pin CTA. No component deletions in this commit (all deletions handled in A and B). Smoke test.

### Component Deprecation

Old components (`TopContextBar`, `GeocodingSearchBar`, `AmbientTicker`, `SearchSurpriseTogglePill` (private in MapScreen.kt), `VibeRail`, floating `CompanionOrb`, `AISearchBar`, `MapListToggle`, `SavesNearbyPill` (private in MapScreen.kt)) must be deleted in the same PR. Do not leave dead code behind.

---

## Out of Scope

- **Teleportation ambient UX** (flag, clock, temp, day/night layer) — BRAINSTORM item #8, deferred
- **"Get There" / Itinerary flow** — BRAINSTORM item #4, separate spec needed
- **AI taste profile persistence** (D22 ghost pins update taste model, but backend persistence is a separate workstream)
- **Voice search (🎤) full implementation** — existing STT wired up; orb bar just surfaces the mic button

---

## Reference Prototypes

| Prototype | What It Shows |
|-----------|--------------|
| `prototype-FINAL.html` | 12-screen end-to-end journey — canonical reference |
| `prototype-discovery-header-v10.html` | Full 9-step user journey |
| `prototype-vibe-filters.html` | Vibe chips: unfiltered → single → multi → filtered surprise |
| `prototype-local-vs-remote-search.html` | HERE vs ELSEWHERE search |
| `prototype-rotating-meta.html` | Animated rotating meta + safety state |
| `prototype-unified-orb-bar.html` | All 6 orb bar states |
| `prototype-saved-map-toggle.html` | One map, two lenses |
| `prototype-bottom-bar-final.html` | FINAL: ☰ + ▤/🗺 + orb bar + 🎲 |

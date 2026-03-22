---
stepsCompleted: [1, 2, 3, 4]
status: COMPLETE
inputDocuments: []
session_topic: 'Search Bar UX Redesign — Unified Discovery Header + Bottom Bar'
session_goals: 'Clear DECISIONS on how search, surprise, teleportation, vibe filters, saved places, orb, and navigation coexist. Beta blocker #11.'
selected_approach: 'AI-Recommended Techniques'
techniques_used: [First Principles, Cross-Pollination, Morphological Analysis, Reverse Brainstorming]
ideas_generated: []
context_file: ''
---

# Brainstorming Session Results

**Facilitator:** Asifchauhan
**Date:** 2026-03-20/21
**Techniques:** First Principles, Cross-Pollination, Morphological Analysis, Reverse Brainstorming
**Status:** DECISIONS FINALIZED — ready for spec

---

## Final Screen Anatomy

```
┌──────────────────────────────────────┐
│ FIND — Discovery Header              │
│ [Area · meta ticker] [📍5 ♥2] [🎲] [▼] │
├──────────────────────────────────────┤
│ SEE — Map + Pins                      │
│   ◎ (bottom-right)                    │
│                                       │
├──────────────────────────────────────┤
│ BROWSE — POI Carousel                 │
├──────────────────────────────────────┤
│ UNDERSTAND — Bottom Bar               │
│ [☰ menu] [▤ toggle] [🌟 orb...... 🎤] │
└──────────────────────────────────────┘
```

**Interactive elements: 6 total**
1. Discovery Header (expandable) — FIND
2. 🎲 Surprise (38px, always visible on header) — DISCOVER
3. ♥ saved toggle (in count pill) — LENS SWITCH
4. ◎ / ⌂ location (floating on map) — NAVIGATE
5. ☰ hamburger menu (bottom bar left) — APP CHROME
6. ▤ / 🗺 view toggle (bottom bar middle) — VIEW SWITCH
7. 🌟 orb bar (bottom bar right, prime real estate) — AI COMPANION

---

## Decisions

### TOP — Discovery Header

**D1: Kill 5 elements, replace with single Discovery Header**
Replace TopContextBar + GeocodingSearchBar + AmbientTicker + SearchSurprisePill + VibeRail with ONE expandable surface.

**D2: Collapsed state (~52dp, always visible)**
`[Area Name 🟢] [rotating meta] [📍5 ♥2] [🎲 38px] [▼]`
- Area name + safety dot (green/orange/red)
- Meta line ROTATES every 4s: weather/time → POI highlight → companion nudge → area fact
- Count pill: `📍5` discovered + `♥2` saved (♥ is tappable = saved lens toggle)
- 🎲 Surprise: 38px (larger than other buttons), purple gradient, shimmer, always one-tap
- Chevron hints expandability

**D3: Rotating meta ticker**
Same 14px line height, cycles through:
- Default: `🌤 82°F · 3:42 PM · First visit`
- POI highlight: `🎡 Ain Dubai 1.2 km · sunset at 6:15 PM` (teal)
- Companion nudge: `✨ Try Surprise — 3 spots match your taste` (purple)
- Safety warning: `⚠️ Reconsider travel · Check advisory` (amber, for caution areas)
- Remote context: `From Dubai · 8,300 mi` (teal, when teleported)

**D4: Pan map → header transforms in-place**
Count chip replaced by teal **Discover** button. No floating pill. Two choices: Discover (what's here?) or 🎲 (surprise me). After POIs load, Discover disappears, count chip returns.

**D5: Expanded state (tap collapsed bar)**
1. Search input — "Search places, areas, or vibes..."
2. Context grid — weather, time, visit status
3. Vibe filter chips — tappable, multi-select, color-coded
4. Intel strip — AI facts (was AmbientTicker)
5. Action buttons — Surprise (primary), Refresh, Save Area
6. Recent explorations — past teleportations with save counts + distances

**D6: Smart search — one input, HERE vs ELSEWHERE**
Results split into:
- **HERE** — local POIs with distance, Vibe filter matches
- **YOUR SAVES** — previously saved places (yellow badge)
- **ELSEWHERE** — remote POIs, Areas (teleport), Vibes (AI-curated)
Color-coded badges: POI (teal), Area (blue), Vibe (red), Saved (yellow), Here (teal)

**D7: Search = Vibe filters = same system**
Typing "scenic" = tapping Scenic chip = same filter. Typing "sushi" = ad-hoc vibe filter.

**D8: Vibe chips replace floating vibe rail**
Inside expanded header. Tappable, multi-select. Active filter persists when collapsed (meta line + count "3/5"). Non-matching pins dim. Filter badge on map. Surprise respects active filter.

**D9: Surprise = "surprise me HERE"**
Always local to current view. Filter-aware when vibes active. Consistent mental model.

**D10: Remote exploring**
Collapsed meta: `From Dubai · 8,300 mi` (teal). ◎ becomes ⌂ (return home, teal ring).

**D11: Safety dot**
Small colored dot after area name in collapsed header. Green (safe), orange/pulsing (caution), red/pulsing (do not travel). Warning text rotates in meta ticker.

**D12: Count pill**
`📍5 ♥2` — minimal pill with pin icon + number + saved count. Pops/scales on new POI arrival during streaming. Shows `🏞 3/5` when filtered. ♥ is tappable = saved lens toggle.

**D13: Spinning state**
`[spinner] Discovering [Area]... [filter badge if active] [Cancel]`

### BOTTOM — Unified Orb Bar + Navigation

**D14: Bottom bar layout (left → right by frequency)**
`[☰ menu] [▤/🗺 toggle] [🌟 orb bar............... 🎤]`
- ☰ hamburger: opens menu (Profile, AI Personality, Settings, Feedback). Least used = leftmost.
- ▤/🗺 toggle: single button, swaps icon. ▤ in map view, 🗺 in list view. Teal highlight when in list.
- 🌟 orb bar: rightmost = prime thumb zone. Most used CTA. AI companion text + mic.

**D15: Unified orb bar (no separate floating orb)**
The bottom bar IS the companion. Proactive (text changes, orb bounces/pulses, glow) + reactive (tap to chat).
| State | Orb | Text | Glow |
|-------|-----|------|------|
| Idle | 🌟 | "Ask about Dubai Marina..." | None |
| Nudge arrives | 💬 + pip | "Ain Dubai closes in 2 hrs!" | Purple border + shimmer streak |
| Surprise delivered | ✨ | "Here's why I picked these..." | Purple |
| Just saved | ♥ | "Why you'd love this place..." | Red |
| Teleported | 🌟 | "Tell me about Griffith Park..." | Teal |
| POIs streaming | 🌟 | "Want to know about these places?" | None |
Tap nudge → peek card expands upward with full text + action. Swipe up → full chat.

**D16: Hamburger menu (from bottom bar)**
Menu rises from ☰ button. Scrim. Items: Profile, AI Personality, Settings, Send Feedback. ☰ → ✕ while open.

**D17: No floating FAB on map**
Hamburger lives IN the bottom bar. Map only has ◎/⌂ floating (bottom-right). Cleaner map surface.

### SAVED PLACES — One Map, Two Lenses

**D18: ♥ in count pill = saved lens toggle**
Tap ♥2 → entire screen context switches to saved. Tap again or banner ✕ → exit.

**D19: Saved map (same map, different pin layer)**
Map zooms out to show all saves worldwide. Heart pins + cluster circles with area labels + counts. Discovered pins hide.

**D20: Saved list (same toggle)**
▤/🗺 toggle works identically in saved lens. Shows saves grouped by area with save dates.

**D21: Cluster drill-down**
Tap cluster → map zooms to that area, shows individual heart pins.

**D22: Save triggers discovery loop**
Save POI → toast "Saved! 2 similar nearby" + dashed pins on map. Taste profile updates. Next Surprise is smarter.

---

## Components Killed
- `TopContextBar` → merged into Discovery Header collapsed row
- `GeocodingSearchBar` → merged into Discovery Header expanded panel
- `AmbientTicker` → merged into rotating meta line + intel strip
- `SearchSurprisePill` → Surprise on header bar, Discover replaces count on pan
- `VibeRail` (floating) → Vibe chips in expanded panel
- `Floating companion orb` → merged into unified bottom orb bar

## Prototypes (chronological)

| File | What it shows |
|------|--------------|
| `prototype-test-phone.html` | Phone frame test (layout verification) |
| `prototype-discovery-header-v10.html` | Full 9-step user journey |
| `prototype-vibe-filters.html` | Vibe chips: unfiltered → single → multi → filtered surprise |
| `prototype-local-vs-remote-search.html` | HERE vs ELSEWHERE search, vibe search = chip tap |
| `prototype-rotating-meta.html` | Animated rotating meta line + frozen frames + safety |
| `prototype-unified-orb-bar.html` | Unified orb bar states: idle, nudge, peek, surprise, save, teleport |
| `prototype-orb-profile-list.html` | Orb + profile + list (earlier iteration) |
| `prototype-count-and-saves.html` | Count badge options + saved places tabs |
| `prototype-saved-map-toggle.html` | One map, two lenses: discovered ↔ saved |
| `prototype-fab-profile.html` | FAB profile iteration (superseded) |
| `prototype-final-bottombar.html` | Hamburger in bottom bar + segmented toggle (superseded) |
| `prototype-bottom-bar-final.html` | FINAL: [☰] [▤/🗺] [🌟 orb bar] + larger 🎲 |

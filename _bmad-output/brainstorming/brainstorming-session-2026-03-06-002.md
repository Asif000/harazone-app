---
stepsCompleted: [1, 2, 3, 4, 5]
inputDocuments: []
session_topic: 'Animation excellence & full UX redesign for AreaDiscovery discovery flow'
session_goals: 'Evolve from abstract galaxy dashboard into a unified full-screen map experience with category-filtered POI discovery, rich POI cards, and area search'
selected_approach: 'ai-recommended'
techniques_used: ['Cross-Pollination', 'Sensory Exploration', 'Chaos Engineering', 'Iterative Prototyping']
ideas_generated: ['Breathing Orbits', 'Reactive Radius', 'Living Canvas Background', 'Breathing Text Not Labels', 'Map Integration', 'Galaxy-Map Fusion', 'Split Horizon Orbs', 'Full-Screen Map with Category Rail', 'Rich POI Cards', 'Contextual Lens', 'Area Search with Weather/Time', 'Map Pan Auto-Populate', 'Material Symbols Pin Icons', 'Smart AI Search Bar', 'Glow Zones', 'Expandable POI Card', 'Save with Pin Badge']
context_file: ''
session_continued: true
continuation_date: '2026-03-06'
---

# Brainstorming Session Results

**Facilitator:** Asifchauhan
**Date:** 2026-03-06

## Session Overview

**Topic:** Animation excellence & full UX redesign for AreaDiscovery
**Goals:** Started with animation brainstorming, evolved into a complete redesign of the discovery flow through iterative HTML prototyping

## Design Principles Established

1. **Push over Pull** — app adapts to user, minimum effort required
2. **Discovery Fingerprint** — engagement-weighted personalization
3. **Vibe-first Hierarchy** — Character/What's On > Safety/Cost
4. **Galaxy-Map Fusion** — categories + map as one unified surface

## Prototype Evolution

### v0: Strip Variants (`prototype-0-strip-variants.html`)
- Pill tab strip with engagement-ordered categories
- Tappable POI dots on map below
- User approved: "excellent"

### v1: Galaxy Map (`prototype-galaxy-map-v1.html`)
- Galaxy orbs as navigation above map
- FAB nav replacing bottom nav
- User asked to merge orbs with map

### v1: Orbital Map (`prototype-orbital-map-v1.html`)
- Two approaches: circular orbital vs edge orbiting
- User chose edge orbiting (Approach B)

### v2: Split Horizon (`prototype-orbital-map-v2.html`)
- Top row = less engaged (Safety, Nearby, Cost — smaller)
- Bottom row = most engaged (Character, History, What's On — bigger)
- Zoom level simulation per category
- FAB expandable nav menu
- Tethers from orbs to map

### v2 additions:
- Rich POI cards with photo carousel, rating, live status, buzz meter, contextual insights
- Always-visible non-overlapping pin labels
- Single tap = full card (removed two-tier)
- Removed bottom sheet (redundant with cards)
- Context strip (weather, local time, buzz count)
- Location badge on map (replaced header)
- Area search overlay (nearby, saved, trending with weather/time)
- Map drag with auto-populating pins

### v3: Full-Screen Map (`prototype-orbital-map-v3.html`) — CURRENT
- **Map fills entire viewport** — maximum visual impact
- **Vibe rail** — 6 orbs stacked vertically on right side, above FAB, with result count badges
- **Bottom search bar** — "Search or ask anything..." — doubles as AI chat
- **FAB** — bottom right, aligned with vibe rail (removed Chat with AI option, now in search)
- **Top bar** — centered: city + tag + weather + time (e.g. "Doral, FL · First visit · ☀️ 78°F · 7:12 PM EST")
- **Zoom controls** — +/- buttons, left side, vertically centered
- **Auto-selects most engaging vibe** (Character) on load

### v3 continuation (session 2):
- **Material Symbols pin icons** — replaced emojis with crisp vector icons (restaurant, nightlife, park, etc.)
- **10 pin types**: food, entertain, park, historic, shopping, arts, transit, safety, beach, district
- **Smart AI search bar** — detects questions vs location searches; shows AI response card with typing animation, contextual answers (safety, food, nightlife, cost, history, parks), and tappable follow-up suggestions
- **Ask AI chip on POI cards** — opens search pre-filled with POI context
- **Glow zones** — soft colored clouds behind pin clusters, intensity varies by cluster density + buzz level; hot clusters pulse/breathe; replaces traditional heat map (party mode decision: glow zones > heat maps for sparse data)
- **Expandable POI card** — centered pop-out (not bottom sheet), "More details" button expands to show: full description, hours, all-vibe insights with colored dots, local tip; collapse back with "Less"
- **Save interaction** — one-tap save chip → "✓ Saved" feedback + bookmark badge on pin
- **No detail page** — expandable card replaces it (party mode decision: avoids breaking map flow)
- **List mode** — first-class alternate view, toggled via map/list icons in top-right. Horizontal vibe chips at top, scrollable POI cards below with same data: icon, name, type, price, reviews, rating, live status, insight, action chips (Directions, Save, Ask AI). Same expandable card on tap. Shared data source with map mode.

## Key UX Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Bottom nav | Removed — FAB menu | Less chrome, more map |
| Bottom sheet | Removed | Redundant with POI cards |
| Header | Removed | Centered top bar instead |
| Vibe layout | Vertical rail, right side | Thumb-friendly, doesn't obscure map |
| Pin interaction | Single tap = full card | Faster, less friction |
| Pin icons | Material Symbols (10 types) | Crisp at small sizes, works in Compose Multiplatform |
| Pin labels | Always visible | No hunting — see what's where instantly |
| Area switching | Top bar tap + bottom search bar | Dual entry points, always visible |
| Default state | Auto-select most engaging | No blank screen — immediate value |
| Top bar | City + tag + weather + time, centered | At-a-glance context, especially for travel |
| AI integration | Search bar + POI card chip | No separate AI screen — feels like smart search |
| Heat map | Glow zones (not traditional) | Works with sparse data, vibe-colored, atmospheric |
| Detail page | Expandable card instead | No navigation away from map — user stays in flow |
| Save | One-tap + pin badge | Minimal friction, visual confirmation on map |
| List mode | First-class alternate, not fallback | Accessibility, screen readers, offline, trip planning |
| Map ↔ List toggle | Top bar or FAB menu | Same data, same actions, two renders |
| Map failure | Auto-fallback to list + banner | Graceful degradation |
| Mode toggle | Map/List icons, top-right | Always accessible, both modes equal |

## Standalone Prototypes (reference only)
- `prototype-poi-card-v1.html` — POI card design exploration
- `prototype-full-flow-v1.html` — rejected (layout broken)

## Next Steps

1. **UX Spec** — Formalize v3 layout into UX design doc
2. **Quick Spec** — Break into implementable features
3. **Implement** — Build in Compose Multiplatform with MapLibre

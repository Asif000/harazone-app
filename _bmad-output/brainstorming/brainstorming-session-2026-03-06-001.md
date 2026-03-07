---
stepsCompleted: [1, 2, 3]
inputDocuments: ['_bmad-output/planning-artifacts/design-direction-v2.md', '_bmad-output/implementation-artifacts/tech-spec-summary-hero-redesign-timeline-right-now.md']
session_topic: 'AreaDiscovery UX Evolution — Chip Cloud Dashboard + Hyper-local Map + Whoa Engagements'
session_goals: 'Surface most relevant info immediately without scrolling; make map hyper-local; engineer data surprise and social proof moments'
selected_approach: 'ai-recommended + random (hybrid)'
techniques_used: ['Cross-Pollination', 'What-If Scenarios', 'Alien Anthropologist (partial)', 'Party Mode Multi-Agent']
ideas_generated: ['chip-cloud-dashboard', 'mini-timeline-strip', 'preference-tuner-sliders', 'wow-chip-detection', 'share-card-generation', 'hyper-local-map', 'bottom-search-bar', 'streaming-animations', 'new-since-yesterday-badges', 'fact-counter', 'single-screen-no-tabs']
context_file: ''
---

# Brainstorming Session Results

**Facilitator:** Asifchauhan
**Date:** 2026-03-06
**Method:** AI-Recommended + Random Hybrid techniques via Party Mode (Sally, John, Winston, Mary)

## Session Overview

**Topic:** AreaDiscovery UX Evolution — three focus areas:
1. Summary Screen — move beyond scroll-down list to surface the most relevant info immediately
2. Map — make it hyper-local to exact user location, not generic city markers
3. Real-time "whoa"-inducing engagement experiences (data surprise + social proof)

**Core Insight:** The app should make you the most interesting person in the room. Data surprise ("I never knew that about this place") + social proof ("I know something nobody else here knows") are the two emotional drivers.

## Major Design Decisions

### 1. Chip Cloud Dashboard (Replaces Bucket Card List)

The vertical scroll list of BucketCards is replaced by a **single-viewport dashboard** with a chip cloud. Each chip is an icon + short text, color-coded by bucket type, rendered in a `FlowRow`. Chips stream in progressively as AI buckets complete — creating a "constellation forming" effect.

**Key properties:**
- Chips sorted by weight (preference-adjusted relevance)
- Wow chips are physically larger with pulse animation
- Number chips count up rapidly (e.g., `47 cafes` counts from 0→47 in 400ms)
- Completion shimmer wave when all chips are loaded
- Tapping a chip shows tooltip popup (simple facts) or bottom sheet (rich content)

**Three zones, all visible without scrolling:**
```
Zone 1: Alert Banner (conditional — Right Now urgency, only when relevant)
Zone 2: Chip Cloud (icons + hashtags + mini-timeline strip)
Zone 3: Embedded Hyper-local Map (500m radius, tap to expand)
Bottom Bar: Search pill + Tuner icon (persistent)
```

### 2. Single-Screen App — No Tab Bar

Tab bar is eliminated. The dashboard IS the app.

- **Summary tab** → the Dashboard (chip cloud + embedded map + alert banner)
- **Map tab** → tap-to-expand from embedded map (shared element transition, same MapLibre instance)
- **Chat tab** → contextual entry from chip taps + below-fold entry point

Below-fold scrollable content:
- AI Chat entry (seeded by last tapped chip)
- Discovery suggestion (Phase 2)
- Full timeline expansion (alternate deep-scroll path)

### 3. Mini-Timeline Strip + Expansion

Timeline is sacred. In the chip cloud, it appears as a **special wider chip** — a compressed film strip showing `1847 → 1923 → 1987 → Now`. Tapping it opens a **bottom sheet** with the full horizontal scroll timeline experience.

**Future vision:** Timeline becomes the PRIMARY navigation axis — scrubbing through time changes the whole screen. "Temporal Navigation" — navigate through a place's past, present, and future instead of scrolling through categories.

### 4. Preference Tuner (Tucked Bottom Sheet)

Small tuner icon (🎛️) in the bottom bar. Tap to reveal sliders:

- `🏛️ History ←→ 🎉 What's Happening`
- `🌳 Outdoors ←→ 🍸 Nightlife`
- `💰 Budget ←→ 💎 Luxury`
- `👨‍👩‍👧 Family ←→ 🎶 Solo Adventure`

**Behavior:** Adjusting sliders reweights chip prominence in real-time (client-side, no AI call in v1). Chips animate reposition. Persisted in DataStore. First-time use: quick 3-slider onboarding (5 seconds).

### 5. Wow Chip Detection + Data Surprise

AI tags each bucket highlight with a surprise factor (1-5, one extra token in prompt). Chips with score 4+ get:
- Larger size in the cloud
- Pulse glow animation on entrance
- "Did you know?" prefix framing
- Long-press → shareable card generation

### 6. Share Card Generation (Social Proof)

Long-press a wow chip → generates a beautiful typographic card:
```
┌────────────────────────┐
│  📍 Coral Gables, FL   │
│                        │
│  "This building was    │
│   a speakeasy during   │
│   Prohibition — 1923"  │
│                        │
│  🏛️ AreaDiscovery      │
└────────────────────────┘
```
Rendered via Compose `drawToBitmap`, shared via intent. No server needed.

### 7. Hyper-Local Map

- 500m radius around user's exact GPS point
- Heat zone overlay (walkability/activity density gradient)
- Bidirectional chip↔pin linking (tap chip → map highlights pin, tap pin → chip highlights)
- Progressive pin loading (5-6 AI pins first, enrichment pass adds 20-30 more)
- Tap map to expand full-screen (shared element transition)

### 8. Bottom Search Bar (Google-Style)

Top search bar eliminated — frees space for chips. Bottom bar has persistent search pill: "Explore another area". Tap → bottom sheet rises with:
- Text input with keyboard
- Recent searches
- Saved locations
- "Use current location" option

Reuses existing Story 5.1 SearchAreaViewModel. UI reskin only.

### 9. Streaming Micro-Animations ("Whoa" Moments)

Three simple animations that make the app feel alive:
1. **Pulse Chip** — wow facts pulse-glow on entrance (single-cycle border alpha)
2. **Count-Up** — number chips animate from 0 to final value in ~400ms
3. **Constellation Shimmer** — light wave across all chips when loading completes (signals "intel complete")

### 10. Retention Features

- **"New since yesterday" badges** — ✨ sparkle on chips that are different from last session
- **Fact rotation** — different highlights surfaced each session from the same data pool
- **Knowledge portfolio** — running counter: "You've discovered 23 facts about your neighborhood"
- **"Hidden gem" labels** — `✨ Most people miss this` framing on niche facts

## What Dies (From Current Implementation)

- `BucketCard` composable
- `BucketList` LazyColumn
- `HighlightFactCallout` (absorbed into chip prominence)
- "More about this place" label
- Tab bar / bottom navigation
- Top search bar
- Separate Map screen

## What Survives (Reused)

- `BucketDisplayState` — still the data source, untouched
- `parseTimelineEras()` — powers the mini-timeline strip
- `SummaryViewModel` — still manages bucket states
- `BucketType` enum and mappers — chips color-coded by bucket type
- Streaming architecture — same Flow pipeline
- `SearchAreaViewModel` — same autocomplete/geocoding logic
- MapLibre instance — same map, different container

## What's New (To Build)

- `ChipCloud` composable — `FlowRow` with animated chip entries
- `DiscoveryChip` data class — icon, label, bucketType, weight, detail
- `ChipExtractor` — pure functions transforming `BucketDisplayState` → `List<DiscoveryChip>`
- `MiniTimelineStrip` composable — compressed horizontal era markers
- `TimelineBottomSheet` — full timeline on tap
- `AlertBanner` — conditional Right Now urgency zone
- `PreferenceTuner` bottom sheet — sliders + DataStore persistence
- `DashboardBottomBar` — search pill + tuner icon
- `SearchBottomSheet` — Google-style location search
- `HyperLocalMap` — tight radius, heat zone, bidirectional linking
- `WowDetector` — AI surprise factor parsing + visual treatment
- `ShareableFactCard` — Compose-rendered share image
- `DashboardScreen` — single root replacing tab navigation

## Implementation Priority (User-Confirmed)

1. Chip cloud dashboard (replaces bucket list) — this IS the product redesign
2. Mini-timeline strip + expansion — timeline is sacred
3. Wow chip detection + visual prominence — data surprise differentiator
4. Preference tuner sliders — personalization layer
5. Share card generation — social proof / viral loop
6. Hyper-local map with chip↔pin linking — map upgrade
7. "New since yesterday" badges — retention hook
8. Fact counter / knowledge portfolio — long-term engagement

## Open Questions (For Next Iteration)

- Exact chip extraction logic from bucket data (how many chips per bucket?)
- Number of preference axes and their weight mappings to bucket types
- Map heat zone data source and rendering approach
- Onboarding flow for first-time preference setting
- Knowledge portfolio UI placement and design
- AI prompt changes for surprise factor tagging
- Temporal Navigation as future primary nav (Phase 2/3)
- Chat discoverability — onboarding hint for first 3 sessions

## Next Steps

Build v1 of the chip cloud dashboard, see it on device, react to real pixels, then iterate with another brainstorming round.

---

## Session Extension — 2026-03-06 (Visual Treatment Brainstorm)

**Topic:** Better visual treatments for SummaryScreen chip cloud — current FlowRow feels underwhelming, exploring "constellation forming" alternatives

**HTML Prototype:** `_bmad-output/brainstorming/visual-treatments-v1.html` — 3 interactive treatments with streaming simulation

### Ideas Generated (25 total across layout, visual, entry animation, atmosphere domains)

Key divergent directions explored:
- Constellation Star Map (fixed node positions, canvas connecting lines)
- Radial Galaxy (HISTORY as gravity well, pentagon orbit, sonar pulses)
- Glass Chips + Starfield (enhanced FlowRow, frosted glass, particle bursts)
- Force-directed layout, Hex honeycomb, Spiral unfurl
- Magnetic fly-in, Ink drop reveal, Particle crystallization entry animations
- Twinkling starfield background, Connecting lines draw themselves, Sonar pulses
- Neon signs aesthetic, Circular orbs, Deep dark backgrounds
- Word cloud typography, Count-up front and center, Completion camera flash

### User Decision

**Selected: Treatment C — Radial Galaxy**

HISTORY orb at center (gravity well). Five buckets at pentagon positions (r=110). Canvas draws dashed orbital ring + spokes. Each chip arrival fires a sonar pulse from center. Completion: triple sonar burst.

**Why:** HISTORY-as-gravity-well makes thematic sense — place identity radiates from its history. Best "solar system forming" metaphor.

### Next Steps

1. Quick Spec: Radial Galaxy visual treatment replacing ChipCloud FlowRow
2. Implement with `/bmad-bmm-quick-dev` — Opus
3. Test on device — see real pixels
4. Code review + iterate

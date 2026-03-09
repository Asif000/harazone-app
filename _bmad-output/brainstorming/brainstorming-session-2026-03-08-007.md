---
stepsCompleted: [1, 2, 3, 4, 5]
inputDocuments: []
session_topic: 'MVP Launch Readiness — what makes users love it and come back'
session_goals: 'Identify gaps, aha moments, retention hooks, and polish needed for a compelling v1 launch'
selected_approach: 'party-mode'
techniques_used: ['Party Mode', 'Gap Analysis', 'User Journey Mapping', 'Iterative Prototyping']
ideas_generated: ['Saves in AI prompt', 'Saved pins on map', 'Saves count indicator', 'Directions button', 'Share place', 'Concierge card', 'Vibe taps in prompt', 'AI save acknowledgment', 'Saves nearby nudge', 'Galaxy saves view', 'Visited state', 'Universe stats', 'Animated share export', 'Travel rotation', 'User-centered universe']
context_file: '_bmad-output/brainstorming/brainstorming-session-2026-03-06-002.md'
---

# Brainstorming Session 7: MVP Launch Readiness

**Facilitator:** Asifchauhan
**Date:** 2026-03-08
**Technique:** Party Mode (PM, UX Designer, Architect, Analyst)

## The Two Gaps

1. APP DOESN'T KNOW WHO I AM -- same content for everyone, AI is amnesiac
2. SAVES ARE A DEAD END -- save a place and nothing happens

Connected insight: the app discovers FOR you but doesn't learn FROM you or ACT with you.

## Tier 1: Must Ship (~2-3 days)

"The app works for ME" -- saves become functional, no new screens

| # | Feature | What Ships | User Sees | Effort |
|---|---------|-----------|-----------|--------|
| 1 | Saves in AI prompt | Room query -> Gemini system prompt injection | "I see you saved Blue Note Jazz -- they have live music tonight" | 1-2 hrs |
| 2 | Saved pins on map | Gold border + checkmark on pin composable | Glance at map, see which places are "mine" | 2-3 hrs |
| 3 | Saves count indicator | Viewport bounds query -> pill overlay | "3 saved places nearby" when returning to an area | 1 hr |
| 4 | Directions button | Platform expect/actual: geo: intent (Android), MKMapItem (iOS) | Tap Directions -> Google/Apple Maps opens with route | 2-3 hrs |
| 5 | Share place | System share sheet, plain text template with map link | Send a friend "Check out Wynwood Walls -- found it on HaraZone" | 3-4 hrs |
| 6 | Concierge card | SharedPreferences flag + lightweight Gemini call on first open | "Welcome to Wynwood. If I were here right now..." with 2 POI cards | 3-4 hrs |

## Tier 2: Ship Within Week 1 (~3-5 days)

"The app is MINE" -- saves become emotional, one new screen (galaxy)

| # | Feature | What Ships | User Sees | Effort |
|---|---------|-----------|-----------|--------|
| 7 | Vibe taps in prompt | ViewModel tracks vibe selections -> prompt builder | AI skews responses toward vibes you browse | Half day |
| 8 | AI save acknowledgment | Save tap -> Gemini call -> response card with 1-2 related POIs | "Great pick! People who love this also check out..." | 3-4 hrs |
| 9 | Saves nearby nudge | App open -> Room query + location check -> gold banner | "You have 3 saved places nearby" with names | Half day |
| 10 | Galaxy saves view | Canvas composable, user at center, orbs by distance/vibe/recency | NEW SCREEN: "My Universe" -- personal constellation | 2-3 days |
| 11 | Visited state | Background proximity check (~100m, 5+ min) -> Room update | Visited orbs go solid bright, unvisited stay outlined | Half day |

## Deferred (Phase C/D)

| Feature | Phase | Why Defer |
|---------|-------|-----------|
| Engagement engine + vibe reordering | Phase A | 2+ weeks, needs interaction_log table |
| Ghost vibes | Phase A | Needs engagement engine first |
| Itinerary route lines in galaxy | Phase C | Needs route planning AI |
| Universe stats (tap center) | Phase C | Identity feature, needs visited state |
| Animated share export | Phase D | 3-sec breathing loop, shareable -- viral moment |
| Travel rotation | Phase D | Universe recenters when you travel |
| Push notifications | Needs backend | FCM/APNS, permission flow, geofencing |
| Cross-session AI memory | Phase D | Taste profile persistence, decay strategy |

## Galaxy Saves View -- The Big Idea

### Core Concept

User-centered universe. YOU are the pulsing blue dot at the center. Everything radiates outward.

- Distance from center = real-world distance from you
- Orb size = interaction depth (saves, AI asks, card expands)
- Orb color = vibe (Character=red, History=blue, Food=green, etc.)
- Orb brightness = recency (recent=bright, months ago=dim)
- Faint lines connect every save back to you at center
- Clusters form naturally by area proximity
- Area labels float near their clusters

### Three States of a Save

| State | Appearance | Meaning |
|-------|-----------|---------|
| Saved (unvisited) | Glowing outline, semi-transparent | "I want to go here" -- a plan |
| Visited | Solid, bright, subtle sparkle | "I went here" -- a memory |
| Fading | Dimmed, smaller | "Saved months ago, never went" -- forgotten |

### Interactions

| Gesture | Action |
|---------|--------|
| Tap an orb | Expand POI card (directions, share, ask AI) |
| Pinch out | Zoom into a cluster -- walking routes appear |
| Pinch in | Zoom out to full universe |
| Tap cluster label | Focus that cluster, others dim, plan CTA appears |
| Tap center (YOU) | Stats: saves/visited/countries/distance |

### Scaling (Multi-continent)

No hierarchy/drill-down (rejected -- too many clicks). Everything visible at once:
- Inner orbit = walking distance saves
- Second ring = driving distance
- Third ring = flight distance
- Outer space = far continents

As you travel, the universe ROTATES -- new city pulls inward, home drifts out. Your universe is always relative to where you ARE.

### Time Filters

- All time: full universe
- This week: only recent saves glow, rest dim to near-invisible
- This trip: only current trip saves

### Empty States

| Saves | Display |
|-------|---------|
| 0 | Pulsing center: "Your universe begins with your first save" |
| 1 | Single orb with line: "Your first star" |
| 2-4 | Small constellation forming |
| 5-9 | First cluster visible with area label |
| 10+ | Full universe, stats appear, share activates |

### Share Your Universe

Screenshot/animated export of your personal constellation. Nobody else's looks the same. "12,400 mi of curiosity." The viral organic growth moment.

### Galaxy vs List Toggle

Galaxy and List are two views of saves (toggle at bottom). Separate from the map/list toggle which is for browsing. Galaxy = planning space. Map = discovery space.

## The Retention Loop

```
First open -> Concierge card -> First save -> Saves in AI prompt ->
AI feels personal -> Save more -> Galaxy grows -> "Saves nearby" nudge on return ->
Visit places -> Universe fills with memories -> Share your universe ->
Friend downloads app
```

## Key Decisions

| # | Decision | Choice | Rationale |
|---|----------|--------|-----------|
| 1 | Fastest "knows me" | Inject saves into AI prompt | 80/20 of personalization, 1-2 hours |
| 2 | Aha moment trigger | First-time concierge card | Don't wait for user to discover value, SHOW it |
| 3 | Save dead-end fix | Directions + share + AI acknowledgment | Three ways saves become actionable |
| 4 | Retention hook | Unfinished business (saves nearby nudge) | Strongest driver for solo (non-social) app |
| 5 | Growth channel | Plain text share, no deep links | Message IS the share, works without app installed |
| 6 | Saves visualization | User-centered galaxy (not hierarchical drill-down) | You are the center, no clicks needed, everything visible |
| 7 | Galaxy scaling | Distance-based orbits, no zoom levels | Rejected multi-level hierarchy as too many clicks |
| 8 | Visited detection | Proximity auto-detect (~100m, 5+ min) | No manual check-in, app just knows |
| 9 | Tier 1 vs Tier 2 split | Functional (Tier 1) then emotional (Tier 2) | Ship useful fast, then ship beautiful |

## Prototypes

- `_bmad-output/brainstorming/mvp-launch-readiness.html` -- 7 tabs: concierge card, saves nudge, enriched POI card, share flow, AI save ack, saved pins, decision table
- `_bmad-output/brainstorming/galaxy-saves-view.html` -- single-city galaxy with cluster zoom, list view, itinerary view
- `_bmad-output/brainstorming/galaxy-multiscale.html` -- hierarchical zoom (REJECTED: too many clicks)
- `_bmad-output/brainstorming/galaxy-user-center.html` -- CHOSEN: user-centered universe, everything radiates from you

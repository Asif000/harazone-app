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
continuation_date: '2026-03-08'
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
| Vibe layout | Vertical rail, right side — round gradient circles + text labels, dynamic sized (32-48dp) by POI count | Thumb-friendly, readable, size = relevance signal |
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
| Mode toggle | Map/List segmented control, next to search bar (bottom-right) | Always visible, discoverable, paired with search action zone |

## Standalone Prototypes (reference only)
- `prototype-poi-card-v1.html` — POI card design exploration
- `prototype-full-flow-v1.html` — rejected (layout broken)

---

## Session 3: Dynamic Vibes & Product Pivot (2026-03-07)

### Core Vision Shift

> **Vibes are the starting point. AI is the depth. Itinerary is the outcome.**

The app is not a map with filters — it's an **AI-powered discovery & planning tool**. The user loop:

```
Vibes -> Browse -> Chat -> Discover -> Save -> Vibes update -> Browse deeper -> Chat more -> Itinerary forms
```

### Design Principles (added)

5. **Vibes are alive** — they change with age, mood, day, season, context — just like the person using the app
6. **Chat-first depth** — AI chat is the brain; vibes are the browse brain; together they're the full experience
7. **No announcement** — features explain themselves in context; no onboarding modals or tutorials
8. **Privacy-first** — all personalization data on-device; nothing leaves the phone

### Product Phasing

| Phase | Name | Scope | Effort |
|-------|------|-------|--------|
| **A** | Smarter Vibes | Dynamic vibe model, engagement reorder, ghost vibes, smart saves | ~2 weeks |
| **B** | AI Chat | Conversational discovery on the map, context-aware, pins from chat | ~3-4 weeks |
| **C** | Itinerary | Auto-organize saves into routed day plans, export, share | ~3 weeks |
| **D** | Vibe Evolution | User-created vibes, vibe learning over time, vibe sharing | ~2-3 weeks |

### Phase A Detailed Decisions

**Deliverables:**
- Dynamic vibe data model (Room DB, not hardcoded enum)
- Engagement-based rail reordering (30-day rolling window)
- Ghost vibes (AI-suggested new vibes from behavior patterns)
- Smart save annotations (AI-generated contextual notes)
- Manage Vibes screen (reorder, rename, delete)

**Decision Log:**

| # | Question | Decision |
|---|----------|----------|
| 1 | Max vibes on rail | Top 6-8 by engagement, "+N" overflow indicator for the rest |
| 2 | Delete default vibes | No — engagement reorder + overflow handles it naturally |
| 3 | Ghost vibe frequency | One at a time, strongest first. Next appears after Keep/Dismiss, 5-10 sec delay |
| 4 | Ghost vibe position | Above overflow divider. Glow + NEW badge + sparkles (Approach B) |
| 5 | Vibe management | Dedicated "Manage Vibes" screen (no long-press gesture in Phase A) |
| 6 | Smart save offline | Save instantly, silently backfill AI note via WorkManager |
| 7 | Engagement decay | 30-day rolling window (simple SQL, no decay coefficients) |
| 8 | Ghost threshold | 5+ interactions with unmapped POI types, minimum 2 saves |
| 9 | Smart save note length | ~30 words, middot-separated tips (best time, cost, insider tip, nearby pairing) |
| 10 | Analytics | Defer to Phase B (no backend in Phase A) |

**Engine Architecture (all on-device):**

```
1. USER ACTS (tap, save, browse, expand card)
2. INTERACTION LOG records it (Room insert)
3. ENGAGEMENT AGGREGATOR updates scores (SQL, 30-day window)
4. RAIL REORDER on app open (sort by score = taps*1 + saves*3 + time*0.5)
5. GHOST DETECTOR on app open, max 1 per session
   - Query unmapped POI type clusters (>= 5 interactions, >= 2 saves)
   - ONE Gemini call to name/emoji/color the vibe
6. SMART SAVE on save action
   - ONE Gemini call for ~30 word contextual tips
   - Offline: save immediately, backfill note via WorkManager
```

**Gemini cost:** ~3-5 calls per day per user. Rail reorder = zero AI.

**New DB tables:** vibe_table, vibe_engagement_table, interaction_log_table, save_annotation_table

**Ghost Vibe UX:**
- Visual: Glow aura + sparkles + "NEW" badge (Approach B)
- Position: Above overflow divider, below established vibes
- On Keep: badge dissolves, sparkles fade, orb solidifies — "graduates" to real vibe
- On Dismiss: fades out, pattern not suggested again
- First ghost gets slightly more aggressive pulse to establish the pattern

**Key Insight — External Data:**
- No third-party integrations (no Spotify, no Instagram, no OAuth)
- On-app behavior is the primary data source
- Manual import (paste Google Maps link, text list) as easy bridge
- "We don't harvest your social media — we learn from how you explore" = privacy differentiator

### Prototypes (this session)

- `prototype-phase-a-dynamic-vibes.html` — 5 interactive scenarios: default rail, reordered rail, ghost vibe appearance, smart save annotation, saves list with AI notes

### Competitive Positioning

> "Your AI travel companion that lives on the map."

| Competitor | Does | Doesn't |
|-----------|------|---------|
| Google Maps | Find specific places | Discover, plan, personalize |
| TripAdvisor | Reviews, top 10 lists | AI, map-first, personalization |
| Wanderlog/TripIt | Organize itineraries | Discovery, AI, map exploration |
| **HaraZone** | Explore + Discover + Plan in one conversational flow on a map | — |

Nobody does all three jobs: Speed (find what I want), Discovery (find what I didn't know I wanted), Identity (express who I am).

### Gemini Prompt v2 — Diagnosis & Proposed Fixes

**Problem:** Results are underwhelming even in busy places (Orlando, Doral). Generic, obvious places. Food dominates. Historical depth missing. Feels like Google Maps, not a discovery tool.

**Current prompt file:** `GeminiPromptBuilder.kt`

**Root causes identified:**

| # | Problem | Impact |
|---|---------|--------|
| 1 | No uniqueness filter | Returns popular/common places, not special ones |
| 2 | Bland persona ("expert assistant") | Generic tone, safe results |
| 3 | No food gate | Restaurants flood every vibe — Gemini defaults to food because it has the most data |
| 4 | Sources array | Gemini hallucinates URLs, wasted tokens |
| 5 | vibeInsights per POI | 3 sub-fields per POI rarely displayed, wasted tokens |
| 6 | confidence field | Always HIGH or useless |
| 7 | No "dig deeper" instruction | For less obvious areas (Doral), Gemini gives up and returns generic results |
| 8 | No chain exclusion | Starbucks, Walmart, malls can appear |

**Approach: Keep single call, make it BETTER not smaller.**

One heavy call per area is fine — it populates all vibes and the user switches between them instantly from cache. The fix is reallocating tokens from waste into quality.

**Proposed changes:**

1. **Passionate local persona** — "You are a passionate local who has lived here 20 years. You love showing visitors things they'd NEVER find on Google Maps."
2. **Uniqueness filter** — "Only include places genuinely unique to this area, not chains or generic businesses"
3. **Food gate** — "Only include food places if: (a) local institution 10+ years, (b) culturally significant to area identity, or (c) truly one-of-a-kind"
4. **Chain exclusion** — explicit list: no Starbucks, McDonald's, Walmart, Target, generic malls
5. **why_special required** — every POI must have a compelling reason to visit; if you can't explain why, don't include it
6. **Dig deeper instruction** — "For areas with less obvious attractions: local parks with history, community landmarks, street art, architectural details, cultural centers, neighborhood stories"
7. **Drop sources array** — save ~200 tokens of hallucinated URLs
8. **Drop confidence field** — never useful
9. **Drop vibeInsights object** — 3 sub-fields per POI rarely used
10. **Slim JSON keys** — shorter field names save ~30% output tokens per POI
11. **Historical depth** — "Include at least 2-3 POIs with historical or cultural stories per area"

**Proposed slim POI schema:**
```json
{"n":"HOPE Gallery","t":"arts","v":"character","w":"Iconic street art spanning 3 stories, ever-changing murals by local and international artists","h":"10a-6p","s":"open","r":4.5,"lat":38.71,"lng":-9.13}
```
Fields: n=name, t=type, v=vibe, w=why_special, h=hours, s=status, r=rating, lat/lng=coordinates

**Net effect:** Similar token count, dramatically better quality. Waste tokens (URLs, redundant fields, generic results) reallocated to value (uniqueness, stories, why_special).

**Testing plan:** Validate prompt across multiple city types:
- Busy tourist city (Orlando)
- Suburban area (Doral)
- Major city (Austin)
- Small town (test depth of "dig deeper" instruction)

**Status:** Needs its own quick spec for implementation.

### Next Steps

1. **Gemini Prompt v2 Quick Spec** — Rewrite prompt, slim schema, test across cities
2. **UX Spec (Phase A)** — Formalize dynamic vibes, ghost vibes, smart saves into UX design doc
3. **Quick Spec (Phase A)** — Break Phase A into implementable features
4. **Implement** — Build in Compose Multiplatform
5. **Phase B planning** — AI Chat brainstorm after Phase A ships

---

## Session 4: Vibe Rail & Map/List Toggle Redesign (2026-03-07)

### Problems Identified

1. **Vibe rail icons have no labels** — vertical orbs on the right side show only icons (Palette, Shield, Event, etc.) with no text. Users can't tell what each vibe is without tapping.
2. **Map/List toggle buried in FAB menu** — not discoverable; users don't know they can switch views.

### Decisions

| # | Decision | Choice | Rationale |
|---|----------|--------|-----------|
| 1 | Vibe rail position | Keep vertical, right side (unchanged) | Thumb-friendly, established pattern |
| 2 | Vibe circle style | Round gradient circles with text label below | Instantly readable, familiar IG-stories pattern |
| 3 | Circle size | Dynamic: min 32dp, max 48dp based on POI count | Size communicates relevance at a glance — no numbers needed |
| 4 | Size formula | `size = 32 + 16 * (count - min) / (max - min)` | Linear interpolation across the range |
| 5 | Active state | Bright glow border (vibe color) + white label text | Clear selection indicator |
| 6 | Inactive state | Dimmed (opacity 0.55) + muted label color | Visible but recessed |
| 7 | Default state (all pins) | All circles glowing in their vibe color, dynamic sized | Shows area personality before user picks a vibe |
| 8 | Filtered state (one vibe) | Selected = bright glow, rest dimmed. Only that vibe's pins on map | |
| 9 | Deselect | Tap active vibe again -> back to all-glow default | Toggle behavior |
| 10 | Count badges | Removed — dynamic sizing replaces them | Cleaner rail, size IS the signal |
| 11 | Map/List toggle | Move from FAB menu -> always-visible segmented control next to search bar (bottom) | Discoverable, paired with search = unified action zone |
| 12 | Toggle style | Two-button segmented (map icon / list icon), active button highlighted | Matches common mobile patterns |

### Sizing Edge Cases

- **All same count** -> all circles = 40dp (midpoint)
- **0 POIs** -> still show at min (32dp), dimmed further
- **Outlier (one vibe has 20, rest have 2)** -> consider capping ratio at 1.5x

### Sizing Spec

```
Min: 32dp (fewest POIs in area)
Max: 48dp (most POIs in area)
Emoji scales with circle size
Text label: constant 9sp, always below circle
```

### State Machine

```
DEFAULT (app open / deselect):
  - All circles: glowing in vibe color, dynamic sized
  - All pins: multi-color on map
  - Labels: vibe-colored text

FILTERED (tap a vibe):
  - Selected circle: bright glow + border, white label
  - Other circles: dimmed (0.55 opacity), muted labels
  - Map: only selected vibe's pins visible
  - Animate: circles resize if counts change (area switch)

DESELECT (tap active vibe again):
  - Return to DEFAULT state
```

### Prototypes (reference)

- `/tmp/vibe-rail-mockup.html` — initial 5 options for vibe display
- `/tmp/vibe-combo-mockup.html` — pill rail + search toggle combo
- `/tmp/vibe-compact-mockup.html` — space-saving labeled options
- `/tmp/vibe-round-image-mockup.html` — horizontal round image vibes (rejected: wrong placement)
- `/tmp/vibe-vertical-labeled-mockup.html` — vertical rail with labels (Option C chosen)
- `/tmp/vibe-dynamic-size-mockup.html` — dynamic sizing by POI count
- `/tmp/vibe-all-pins-mockup.html` — default all-pins state (Option A chosen)

---

## Session 5: AI Chat — Epic 4a First Iteration (2026-03-08)

### Core Question

What makes a great first single-turn AI interaction on a map-first discovery app?

### User Personas × First Queries

| Persona | First Query | What they REALLY want |
|---------|------------|----------------------|
| Tourist just landed | "what should I do tonight" | Curated evening plan, not a list of 50 bars |
| Local rediscovering home | "anything cool I don't know about near me" | Surprise — hidden gems they've walked past |
| Anxious parent | "is this area safe to walk at night" | Reassurance + specific advice, not a crime stat |
| Foodie | "best food that's not on Google Maps" | Insider knowledge, not Yelp top 10 |

**Key insight:** Nobody types a precise address. They ask vague, emotional questions. The AI's job is to turn vague intent into a satisfying, map-anchored answer.

### Taste Model

Two signal types that supplement each other:

- **Silent signals** (no questions needed): vibe taps, time of day, day of week, weather
- **Spoken signal**: one clarifier question when silent signals aren't enough

**Rules:**
1. Safety/urgent queries → answer instantly, no clarifier
2. Discovery queries → one quick taste question, max 1 follow-up
3. Never a form — it's a conversation, not onboarding
4. The more silent signals we have, the fewer questions we ask

| Context (silent) | Clarifier (spoken) | Response flavor |
|------------------|--------------------|-----------------|
| Browsed Character, History | "Chill or high energy?" | Historic jazz bar, not a nightclub |
| Browsed What's On, Safety | "With kids?" | Family-friendly events in safe zones |
| No vibe activity yet | "What are you into?" | Broader question since no signal |
| Heavy Safety vibe | (skip — answer directly) | Safety-first framing on everything |

### Time-of-Day Awareness (free signal)

| Time window | How it shifts responses |
|-------------|----------------------|
| Morning (6-11a) | Cafés, parks, markets, breakfast spots |
| Midday (11a-2p) | Lunch spots, indoor escapes (hot climate), museums |
| Afternoon (2-5p) | Activities, shopping, sightseeing, golden hour tips |
| Evening (5-9p) | Dinner, live music, sunset spots, happy hours |
| Night (9p+) | Nightlife, safe routes, late-night eats. Safety framing automatic |

### Response Anatomy (stolen from the best)

| Source | What they nail | Stolen for us |
|--------|---------------|-------------|
| Perplexity | Sourced, structured, scannable | Bulleted answer with POI names as tappable links |
| Hotel concierge | Opinionated, warm, 3 picks not 30 | "I'd personally go to..." tone, max 3-5 POIs |
| Instagram stories | Visual, quick, swipeable | Response card with POI photos inline |
| Google Maps "nearby" | Pins light up on map | Mentioned POIs pulse on the map behind the overlay |

**Response structure:**
1. **One-liner opinion** — "This area comes alive at night. Here's what I'd do:"
2. **3-5 POI cards** — name, why_special, photo, tappable → pin pulses on map
3. **Follow-up chips** — "More like this" / "Different vibe" / "Plan my evening"

### v1 Scope (MVP Line)

**IN (10 components):**

| # | Component | Rationale |
|---|-----------|-----------|
| 1 | One-liner opinion | Sets tone, easy to build |
| 2 | 3-5 POI cards with photos | Core value — map-anchored answers. Image pipeline already working |
| 3 | POIs pulse on map behind overlay | Connects chat to map — the differentiator |
| 4 | Time-of-day awareness | Free signal, already have time data |
| 5 | Vibe behavior as context | Already tracking taps, just pass to prompt |
| 6 | One clarifier question (when needed) | Simple conditional logic |
| 7 | Follow-up chips | Already exist in overlay |
| 8 | "More like this" chip | Re-query with same filters |
| 9 | "Different vibe" chip | Re-query with opposite vibe |
| 10 | Tappable POI → dismiss overlay, zoom to pin | Connects response to map |

**DEFERRED:**

| Component | When | Why defer |
|-----------|------|-----------|
| Multi-turn memory | Phase B iter 2 | Needs conversation state management, token accumulation, "new conversation" UX |
| "Plan my evening" chip | Phase C (Itinerary) | Itinerary feature |
| Day-of-week weighting | Phase B | Nice-to-have, time-of-day is enough for v1 |
| Weather-aware responses | Phase B | Have data but adds prompt complexity |
| Cross-session taste memory | Phase D (Vibe Evolution) | Needs persistence layer, taste profile schema, decay strategy |

### Definitions

- **Multi-turn memory**: AI remembers what you said earlier in the same conversation. Without it, each query is standalone. With it, "what about food nearby" knows you just asked about nightlife and suggests restaurants near those bars.
- **Cross-session memory**: AI remembers your taste across separate app opens. Builds a taste profile over time — you train your concierge by using the app. Vibe tap behavior is a crude version of this already.

### Key Decisions

| # | Decision | Choice | Rationale |
|---|----------|--------|-----------|
| 1 | First interaction model | Single-turn with smart context | Multi-turn adds scope, single-turn with silent signals is already better than most apps |
| 2 | Taste signals | Silent (vibes, time) + spoken (clarifier) supplementing each other | More signals = fewer questions over time |
| 3 | Safety queries | Answer immediately, no clarifier | Urgency > personalization |
| 4 | Response length | 3-5 POIs max | Concierge, not search engine |
| 5 | Response tone | Opinionated local, not neutral assistant | Matches Gemini prompt v2 persona |
| 6 | POI photos in chat | v1 (already working) | Image pipeline shipped |
| 7 | Map connection | POIs pulse on map behind overlay | Core differentiator vs ChatGPT/Perplexity |
| 8 | Party mode for this topic | Skipped | Converging on decisions, not diverging — decision velocity > creative conflict |

### Next Steps

1. **Quick Spec** — formalize v1 AI Chat into implementable spec
2. **Implement** — build in Compose Multiplatform
3. **Test on device** — validate response feel with real Gemini calls
4. **Iterate** — back to brainstorm if response quality needs rethinking

---

### Session 5b: Search Bar Dual Mode — Place Jump + Refresh (2026-03-08)

**Scope: TOP search bar ONLY.** Bottom bar (AI chat), vibe rail, FAB, and all other UI elements are out of scope. No changes to anything below the top search area.

#### Problem

Currently the top bar has "Refresh area" — useful for reloading POIs, but no way to jump to a different city/neighborhood/landmark without manually zooming and panning.

#### Solution

Replace "Refresh area" button with a dual-purpose search bar that handles both place search and area refresh.

**Placeholder text:** "Jump to..."

#### Behavior

| User action | Result |
|-------------|--------|
| Tap bar, type nothing, hit enter | Refresh current area (same as today) |
| Tap bar, type a place name | Autocomplete suggestions appear (3-5 results via MapTiler geocoding) |
| Tap an autocomplete suggestion | Fly to location, update top bar city name, load POIs |
| Hit enter with text | Select first autocomplete match |
| Tap ✕ clear button | Clear text, return to idle state |
| Hit Escape | Clear and dismiss |

#### Visual States

| State | Search bar | Autocomplete | ↻ icon | ✕ icon |
|-------|-----------|-------------|--------|--------|
| **Idle** | Rounded pill, placeholder text | Hidden | Visible | Hidden |
| **Active/Typing** | Flat top corners (connected to dropdown) | Visible with results | Hidden | Visible |
| **Place selected** | Shows selected place name | Hidden | Hidden | Visible |
| **Empty submit** | Stays idle | Hidden | Spins (refresh) | Hidden |

#### Autocomplete Item

Each suggestion shows:
- 📍 Pin icon
- Place name (with typed portion bolded)
- Detail line (type + region)
- Distance from current location

#### Key Decisions

| # | Decision | Choice | Rationale |
|---|----------|--------|-----------|
| 1 | What replaces "Refresh area" | Dual-purpose "Jump to..." bar | One control, two jobs — cleaner UI. "Jump to" = navigation, "Ask anything" = AI — no confusion |
| 2 | Refresh behavior | Empty submit = refresh | Preserves existing functionality, no extra button |
| 3 | Autocomplete source | MapTiler geocoding API | Already in the app for reverse geocoding |
| 4 | Max suggestions | 3-5 | Fast to scan, no scrolling |
| 5 | After fly-to | Update top bar city name + load POIs for new area | Same as manual pan + refresh |
| 6 | Placeholder text | "Jump to..." | Navigation verb, no overlap with bottom "Ask anything..." (AI). Playful, implies speed |

#### Prototype

- `/tmp/search-bar-dual-mode.html` — interactive mockup with 4 states (idle, typing, selected, refresh). **Note:** bottom bar in mockup is placeholder only — not part of this spec.

---

## Session 6: AI Chat Reward Loop & Progressive Display (2026-03-08)

### Party Mode — Making Chat Rewarding Beyond Wow Factor

**Context:** v1 AI Chat is working and wowing. Now deepen the reward — create the urge to save places and plan itineraries.

### Two Chat Contexts (Resolved)

| | Search Bar Chat | POI "Ask AI" |
|---|---|---|
| Entry point | Bottom search bar | POI card "Ask AI" chip |
| Context to Gemini | Full area: all POIs, active vibe, time-of-day, weather, engagement history, saves | Single POI: name, type, vibe, why_special, hours, coordinates |
| System persona | Passionate local who knows the whole area | Knowledgeable guide about this specific place |
| Response format | Rich POI mini-cards with photos, save chips, urgency flags | Text + inline photos (food, drinks, interiors) — no discovery cards |
| Follow-up chips | "Save all", "Best order?", "Different vibe" (planning intent) | "How to get there?", "Similar places?", "What to bring?" |
| Discovery counter | Yes (grows as you save) | No (investigating, not discovering) |
| Map pulsing | Yes (POIs pulse behind overlay) | No (already zoomed to pin) |
| Voice input | Yes (mic button) | Yes (mic button) |
| Conversation memory | Full session (up to 20 turns) | Lighter (up to 10 turns) |
| Photos | Yes — POI cards with thumbnails | Yes — inline photo strips (food, interiors, views) |

**Key decision:** Both chats show photos for attraction. The difference is structure (discovery cards vs attraction photos), not richness.

### Reward Loop Elements

| Element | Purpose | Trigger |
|---------|---------|---------|
| POI mini-cards in chat | Emotional hook (photo + story) | Every AI response with place mentions |
| Inline save chip | Zero-friction capture | Every POI card |
| "Save all N" chip | Batch dopamine spike | When 2+ POIs in response |
| Discovery counter | Accumulation reward | Header, increments on save |
| Urgency flag | Scarcity = must save | Time-sensitive or day-specific POIs |
| Planning-intent chips | Shift mindset to "plan this trip" | After 2+ saves |
| Voice input | Natural, hands-free, conversational | Mic button always visible |

### Saves → Itinerary Bridge

**Flow:** Discover (chat) → Save (inline) → Accumulate (counter) → Nudge (proximity trigger) → Plan (AI itinerary in chat) → Refine (swap/add chips)

- After 3+ saves in same area, AI nudges: "These N places are all within walking distance. Want me to plan your evening?"
- AI builds time-ordered itinerary factoring opening hours, walking time, best-time-to-visit
- Itinerary lives INSIDE chat — no separate screen
- Post-plan chips: "Save this plan", "Swap a stop", "Add dinner", "Share"
- Plans auto-persist when user leaves area, auto-restore on return

### Plans Persistence (Phase C — validated direction, deferred)

- **FAB → My Plans** — grouped by area, current area first
- **Map route overlay** — numbered pins + route banner when returning to area with plan
- **Area indicators** — purple dot (has plan) vs amber dot (has saves only) in Jump To search
- **Unsorted saves** — dashed amber cards with "Ask AI to plan these" CTA
- **Edit always through chat** — "Edit in chat" reopens conversation with full history
- **Zero manual organization** — plans persist and restore automatically

### Progressive Display (Response Time Fix)

**Problem:** Area discovery Gemini call returns everything at once — perceived slow.

**Solution (for this iteration):**
1. Skeleton shimmer on POI cards while loading (quick win, pure UI)
2. Streaming JSON parser — emit each POI as its JSON object completes
3. Cards fade in progressively as data arrives

### Contextual Education (First-Time User)

**Problem:** First-time users don't know what the app does or what to ask.

**Solution:**
- Ghost text pulse in bottom search bar — placeholder cycles through example queries:
  - "Ask: is it safe at night?"
  - "Ask: best food here?"
  - "Ask: hidden gems nearby?"
  - "Try: plan my evening"
- Chat empty state hint line above starter chips: "I know this area. Ask me anything, save what you like, and I'll help you plan."
- No modals, no tutorials — features explain themselves (principle #7)

### v1.1 Iteration Scope (Quick Spec)

1. **Progressive POI display** — skeleton shimmer + streaming parse
2. **Inline save chips** in chat POI cards
3. **Contextual education** — ghost text cycling + empty state hint

### Prototypes

- `/tmp/chat-reward-loop-mockup.html` — rich chat with POI cards, save chips, photo strips, mic button
- `/tmp/chat-plans-persistence-mockup.html` — My Plans, map route overlay, area indicators, full flow diagram

### Key Decisions

| # | Decision | Choice | Rationale |
|---|----------|--------|-----------|
| 1 | Both chats show photos | Yes | Attraction is visual — food/drink/interior photos in both flows |
| 2 | Chat context tiers | Search bar = full area, POI = scoped | Different jobs, different depth |
| 3 | Voice input | Mic button in both chats | Natural, hands-free |
| 4 | Itinerary surface | Inside chat, not separate screen | Chat IS the planning tool |
| 5 | Plans persistence | Auto-persist/restore per area | Zero manual organization |
| 6 | Edit plans | Through chat conversation | No settings screen |
| 7 | Response time fix | Progressive display (skeleton + stream) | First impression retention |
| 8 | First-time education | Ghost text cycling + empty state hint | Contextual, no modals (principle #7) |
| 9 | Plans persistence scope | Validated direction, defer to Phase C | Ship speed + save chip first |

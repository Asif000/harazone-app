---
stepsCompleted: [1, 2, 3]
session_status: 'COMPLETE'
inputDocuments: [/tmp/saves-awareness-list-v2.html]
session_topic: 'Dual-topic: POI Data Grounding (external data source for Gemini) + Saved Places List Redesign (rich cards, vibe-first UX)'
session_goals: 'Architecture decisions for POI grounding service, integration patterns, prompt changes, cost strategy; Saved places list UX redesign with rich cards, grouping, actions, vibe-first browsing'
selected_approach: 'ai-recommended'
techniques_used: ['Morphological Analysis', 'Cross-Pollination + SCAMPER', 'Six Thinking Hats']
ideas_generated: []
context_file: ''
---

# Brainstorming Session Results

**Facilitator:** Asifchauhan
**Date:** 2026-03-09

## Session Overview

**Topic 1 — POI Data Grounding:** Three CRITICAL/HIGH bugs share a root cause: total reliance on Gemini for POI data (coordinates, hours, existence). Failures: (1) recommends closed places, (2) saved POIs invisible due to coord drift, (3) pins in water from hallucinated coords. Need external data source to ground-truth Gemini. Also: text-vs-card mismatch where AI prose and JSON POI cards show different places.

**Topic 2 — Saved Places List Redesign:** Current list is plain. Want rich detail cards (name, area, type, why_special, userNote, saved date, distance). Grouping strategies, actions (navigate, delete, note, share), and "Saved is a vibe" principle — browsing saves should feel like exploring a vibe, not managing a database. Starting mockup: `/tmp/saves-awareness-list-v2.html` (image background cards with gold save indicators).

### Context Guidance

_Key bugs from memory: saved POIs invisible on map (savedId uses exact coords but Gemini returns different coords each call), POI pins in water (hallucinated coordinates), real-time hours missing (Gemini confidently recommends closed places). Existing mockup shows 3 card style options: gold icon only, gold left border, both combined. Product principle: "Save and Plan are two sides of the same coin."_

### Session Setup

_Dual-topic brainstorm session. Topic 1 is architectural/technical (data source selection, integration patterns). Topic 2 is UX/design (card layout, grouping, interaction). Both topics interconnect — the POI data grounding decision directly affects what data is available for the saved places cards._

### Additional Constraints (added during setup)

- LATENCY: Gemini search response takes 15+ seconds. User must see visible response within 5 seconds. Architecture must account for progressive/staged loading.
- SATELLITE TOPIC: User Profile / "AI Mirror" page — visualization of what AI has learned about the user over time, preference management. Separate session planned, but capture any connections that surface here.

## Technique Selection

**Approach:** AI-Recommended Techniques
**Analysis Context:** Dual-topic (architecture + UX) with latency constraint and data quality requirements

**Recommended Techniques:**
- Phase 1: Morphological Analysis — Map all parameter combinations for POI data grounding architecture
- Phase 2: Cross-Pollination + SCAMPER — Transform saved places list using patterns from other domains
- Phase 3: Six Thinking Hats — Converge on top candidates with structured multi-perspective evaluation

**AI Rationale:** Topic 1 needs systematic trade-off exploration (morphological grid). Topic 2 needs creative UX inspiration from outside the travel domain + structured transformation of existing mockup. Both need rigorous convergence to avoid shiny-object syndrome.

---

## Phase 1: Morphological Analysis — POI Data Grounding

### Morphological Grid (6 Parameters x ~5 Options Each)

**Parameter 1 — DATA SOURCE:** Google Places API (New) | Overture Maps (free) | Foursquare Places API | OpenStreetMap + Nominatim | MapTiler (already integrated) | Hybrid multiple sources

**Parameter 2 — INTEGRATION PATTERN:** Post-Validate (Gemini then validate) | Pre-Seed (fetch real, feed to Gemini) | Replace (external POIs only, Gemini writes prose) | Race (parallel, merge) | Tiered (external for Hungry, Gemini for others)

**Parameter 3 — LATENCY STRATEGY:** Streaming Gemini | Two-stage (fast external + slow Gemini) | Skeleton + fill | Cache-first (stale-while-revalidate) | Predictive prefetch

**Parameter 4 — COST MODEL:** Per-request | Aggressive local cache (SQLite) | Regional bulk pre-fetch | Freemium gate | Smart budget (quota per user/day)

**Parameter 5 — SAVED POI IDENTITY:** External place_id as canonical | Fuzzy match (name + area) | Hybrid (external ID + fuzzy fallback) | Store full pin data at save time

**Parameter 6 — TEXT-VS-CARD MISMATCH FIX:** Strict 1:1 prompt rule | Post-process parse + match | Cards-first (JSON then prose about those) | Template prose | Prose IS the card

### Architecture Options Explored (7 Options)

1. THE GROUNDED STORYTELLER — Foursquare provides verified POIs, Gemini ranks/writes + 2 "AI Discovery" bonus per vibe
2. THE VALIDATOR — Gemini generates freely, validate each POI against Foursquare after
3. THE RACE PATTERN — Fire Foursquare + Gemini in parallel, merge as results arrive
4. THE PRE-SEEDED PROMPT — Foursquare first, inject verified POIs into Gemini prompt
5. THE PROGRESSIVE DISCOVERY — 3 stages: instant Foursquare, cached Gemini, fresh Gemini deep
6. GEMINI LITE + DEEP — Two Gemini calls: fast lightweight (3s) + slow deep (15s)
7. THE LOCAL KNOWLEDGE GRAPH — SQLite cache grows smarter over time, repeat visits instant

### Key Decision: Data Source Capability Comparison

| Data Point | Gemini | Foursquare | Google Places |
|------------|--------|------------|---------------|
| POI exists (not hallucinated) | ~85% real | 100% real | 100% real |
| Coordinates | Drift every call, sometimes in water | Stable, correct | Stable, correct |
| Hours | CONFIDENTLY WRONG | Partial (~40-50%, often stale) | GOLD STANDARD (real-time, 90%+) |
| Photos | None | Some, inconsistent | Excellent, multiple per POI |
| Stable ID for saves | None | fsq_id (permanent) | place_id (permanent) |
| Categories | Good but inconsistent | Standardized (800+) | Standardized |
| Why it's special / vibe | EXCELLENT (superpower) | Nothing | Nothing |
| Hidden gems | Can recommend obscure places | Only places in database | Only places in database |
| Cost | Already paying | Free 100K/mo | $17-32 per 1000 calls |

### DECISION: Foursquare + Surgical Google Places (Hybrid Path 2)

**Rationale:** No single source solves all three critical bugs AND preserves Gemini's storytelling strength.

**Architecture:**
- FOURSQUARE handles heavy lifting (FREE): verified coordinates, existence confirmation, photos, standardized categories, stable fsq_id for saves
- GOOGLE PLACES called surgically ONLY when hours matter: "Hungry" intent results, user taps a specific card for details, user planning a visit
- GEMINI remains the storyteller: vibe ranking, why_special prose, cultural context, hidden gem recommendations (unconstrained creativity)

**Integration pattern:** Pre-Seed (#4) as core pipeline + Race (#3) two-stage loading for latency + Knowledge Graph (#7) cache for repeat visits

**How it flows:**
1. User searches an area
2. Foursquare Places API fetches real POIs (~1-2s) → pins appear on map immediately
3. Verified POI list injected into Gemini prompt: "Here are real places. Rank by vibe, write why_special. You may also suggest unlisted hidden gems — mark them as unverified."
4. Gemini response streams in (~10-15s) → cards fill with prose, vibe badges, hidden gems appear
5. For "Hungry" intent or user tap: Google Places called for real-time hours
6. All results cached in local SQLite with TTL → repeat visits are instant

**Latency target:** Map pins visible within 2s (Foursquare). Basic cards within 5s. Full AI prose within 15s.

**Cost strategy — UPDATED:** V1 MUST deliver best quality even if expensive. Google Places called for ALL POIs (not just surgical). Paid tier is the default premium experience. Free tier gets Foursquare-only quality (good coords, partial hours) with clear communication about why premium is better. Pricing covers the cost of Google Places calls. This is a product principle: quality first, optimize costs later.

**Saved POI identity fix:** fsq_id as canonical savedId for Foursquare-matched POIs. For Gemini-only hidden gems: name + area fuzzy match (with "AI Discovery" badge, no hours guarantee).

**Text-vs-card mismatch fix:** Pre-seeding the prompt with verified POIs structurally eliminates the mismatch for 80%+ of results. Gemini writes prose ABOUT the POIs we provided. Hidden gems are clearly separated.

**Monetization lever:** Free users get Foursquare-quality data (good coords, some hours). Paid users get Google Places enrichment (real-time hours, better photos) — natural upgrade path.

**Profile page connection:** Every POI interaction (viewed, saved, dismissed, chatted about) logged with verified Foursquare category data. Enables trustworthy taste profile: "You gravitate toward independent art spaces" grounded in real category taxonomy, not Gemini guesses.

---

## Phase 2: Cross-Pollination + SCAMPER — Saved Places List Redesign

### Design Exploration Process

Explored 6 cross-pollination patterns (Spotify Playlists, Rich Cards, Pinterest Masonry, Stories/Swipe, Map-First, Notion Multi-View) + 7 SCAMPER transformations of existing mockup.

Then explored multi-view concept (Collections + List + Map + Timeline with "My World" toggle). DECISION: Multi-view / Collections / Timeline concepts belong on the PROFILE PAGE (roadmap #19), NOT the saves list. Saves list should stay simple.

Then explored 6 card variations with image backgrounds (Tall Immersive, Compact Horizontal, Split Layout, Stacked, Frosted Glass, Magazine). Selected Magazine layout (F) as base — hero card for most relevant + compact cards for rest.

### Final Direction: AI Contextual Saved List

Selected approach: Magazine layout (hero + compact) with AI-powered contextual intelligence.

Explored 3 AI concepts:
1. AI Summary + Smart Sections (local user)
2. International Traveler (multi-currency, country grouping)
3. Time-Aware AI Curation (list reorganizes by time of day, builds routes from saves)

**DECISION: Combine concepts 1 + 2 — works for both local and international users.**

### Saved Places List — Final Design Spec

**Header:**
- "Saved Places" title + count
- Search bar (search within saves)
- Filter chips: All, Nearby, Open Now, Has Notes, AI Picks

**AI Summary Card (top of list, always visible):**
- AI-generated "Your Discovery Story" — personality summary based on save patterns
- Personality tags (e.g., "art lover", "night explorer", "chain avoider")
- Updates as user saves/visits more places
- Feeds into / mirrors the Profile page (roadmap #19)

**Smart Sections (AI-generated, context-aware):**
- Sections change based on time of day, user location, weather
- Examples: "Open & Close By" (evening), "Your Morning Trail" (morning), "Still Waiting For You" (saved but never visited), "Better Tomorrow" (closed/too far right now — dimmed)
- Each section has icon, label, count, optional AI explanation

**AI Nudges (inline between sections):**
- Contextual suggestions connecting saves: "These 3 are walkable — perfect evening trail"
- Cross-cultural pattern spotting (int'l): "You save jazz clubs everywhere — want me to find one in your next destination?"
- Route/cost summaries: "Total route: 950m, ~$30-40 for the evening. All from YOUR saves."

**Card Design — Magazine Layout (image background):**

Hero card (most contextually relevant):
- Full image background with gradient overlay
- Name, area, why_special (AI prose)
- User note (if exists, gold accent)
- Badges: OPEN/CLOSED (real-time), distance, vibe tag
- Price tier icon: $/$$/$$$/$$$$
- Currency with local symbol when international (e.g., "€15 (~$16)")
- Language flag icon on card (when international)
- Action buttons: navigate, share, edit note
- Saved date

Compact cards (rest of list):
- Image background with left-to-right gradient
- Name with emoji prefix
- Sub-line: area, date
- Right side: OPEN/CLOSED badge, distance, price tier
- Note indicator dot (if has note)
- Currency/language icons (when international)

**Price Tier Display:**
- $ = cheap/budget
- $$ = moderate
- $$$ = upscale
- $$$$ = luxury
- "Free" badge for free venues
- When international: local currency symbol + home currency conversion, e.g., "€15 (~$16)"

**Bottom Bar:**
- AI input bar: "Ask AI about saves..."
- Map toggle: shows saves on map (gold pins)
- FAB: AI chat

### Design Iteration (v7 — Final)

Iterated through v4-v7 mockups. Final design decisions:

**Card format:** Compact uniform cards — 100px image area on top, info panel below (name, area, footer). No why_special or notes on card face (shown on tap/detail page). All cards same height.

**Capsule filters (AI-generated, dynamic):** Replace static filter chips (Nearby, Open Now, etc.) with AI-generated distance clusters based on user's current location. Clusters adapt automatically:
- Home city example: `All (23)` | `Wynwood (8)` | `Greater Miami (11)` | `Day Trip (4)` | `Closed Now (4)` | `AI Picks`
- Traveling example: `All (31)` | `Gothic Quarter (5)` | `Barcelona (12)` | `London (8)` | `Miami (11)` | `Closed Now (6)` | `AI Picks`
- Proximity rings: neighborhood > city > region > country > continent. Green capsule = nearest cluster. Reorganizes when user moves. List never goes stale.

**Card actions (match detail page):** Bookmark (unsave), Directions, Share, Ask AI — same 4 actions as ExpandablePoiCard.kt.

**Unsave from list:** User can unsave directly from the list without opening detail page.

### TODO: Custom Icon Suite
Need to design a unified icon set that works across Android and iOS for all card/detail actions (save, directions, share, ask AI, etc.). Currently using Material Icons on Android. Deferred — will tackle later as a design task.

### Mockup Files (preserved for quick-spec)
- Original mockup baseline: `/tmp/saves-awareness-list-v2.html`
- 6 pattern exploration: `/tmp/saves-redesign-brainstorm.html`
- 6 card variations: `/tmp/saves-card-variations.html`
- Multi-view flow (FOR PROFILE PAGE): `/tmp/saves-multiview-flow.html`
- AI contextual concepts (FINAL DIRECTION): `/tmp/saves-ai-contextual.html`
- FINAL card design (v7): `/tmp/saves-final-v7.html`

### Profile Page Decision (Satellite Topic)
The multi-view concept (Collections, List, Map, Timeline, AI personality summary, Vibe Fingerprint, pattern insights, preference toggles) belongs on the USER PROFILE / AI MIRROR page, NOT the saves list. This is roadmap item #19. Mockup preserved at `/tmp/saves-multiview-flow.html`.

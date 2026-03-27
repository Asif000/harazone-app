---
stepsCompleted: [1, 2, 3, 4]
inputDocuments: []
session_topic: '"Move Here" mode — resident-lens area intelligence for AreaDiscovery'
session_goals: 'UI entry points, data categories, AI prompt shift, traveler/resident coexistence, data sourcing & accuracy (especially international), monetization, phasing'
selected_approach: 'progressive-flow'
techniques_used: ['morphological-analysis', 'six-thinking-hats', 'cross-pollination', 'constraint-mapping']
ideas_generated: [50+]
session_active: false
workflow_completed: true
context_file: ''
---

# Brainstorming Session: "Move Here" Mode

**Facilitator:** Asifchauhan
**Date:** 2026-03-25
**Approach:** Progressive Technique Flow (4 phases)
**Ideas Generated:** 50+ across 7 parameters and 9 themes

---

## Session Overview

**Topic:** "Move Here" mode — a new intent alongside Save/Go/Been that shifts the entire AI context from traveler to potential resident. When activated for an area, the app changes what data it surfaces: real estate & rental prices, school districts, safety/crime stats, job markets & industries, cost of living, commute patterns, healthcare, community vibe, etc.

**Goals:**
- UI entry points and mode-switching UX
- Data categories unique to relocation vs shared with traveler mode
- AI prompt architecture shift
- Coexistence with traveler mode (not replacement)
- Data sourcing & accuracy — especially international
- Monetization angles
- Phasing strategy

**Core Insight:** AreaDiscovery's motto is area discovery — discovering a place to LIVE, not just visit. "Move Here" is the natural extension of the product's identity. Nobody else combines map-native UX + AI companion + international data + origin-aware personalization + honest confidence signals for relocation.

---

## Phase 1: Morphological Analysis — Parameter Grid

### Parameter 1: Mode Entry Point

**Decision:** "Move Here" button on area header + "Move" as 4th lifecycle state (Save/Go/Been/Move).

- Explicit activation only in Phase 1. No AI inference, no nudges.
- Log implicit signals silently (practical POI saves, relocation-adjacent queries, dwell patterns) for Phase 2 AI inference.
- Area-scoped — user can be traveler in Paris, resident in Lisbon simultaneously.
- Zero friction — no onboarding, no questions on first tap.

**Ideas explored:**
- #1-5: Explicit entry points (header button, lifecycle state, chat chip, saved places filter, swipe gesture)
- #6-9: Additional explicit (dedicated tab, chat quick-action, expanded header card)
- #10-13: AI-inferred (pattern detection, chat sentiment, dwell time, search query)
- #14-15: Hybrid (soft transition breadcrumbs, "Surprise me as a local")
- #16-19: Simplified explicit (header chip, lifecycle state, chat action, pull-down card)

### Parameter 2: Data Categories (9 for Phase 1a)

| # | Category | Source (Phase 1a) | Data Classification |
|---|----------|-------------------|---------------------|
| D1 | Rental prices | Gemini | Dynamic |
| D2 | Buy prices | Gemini | Dynamic |
| D3 | Cost of living index | Gemini | Dynamic |
| D4 | Safety / crime stats | Gemini + existing safety spec | Dynamic |
| D6 | Job market / top industries | Gemini (facts first, chat for depth) | Dynamic |
| D9 | Community vibe | Gemini | Static-ish |
| D10 | Weather / climate | Open-Meteo (already built) | Static |
| D12 | Visa / immigration | Gemini + "verify locally" gov links | Volatile |
| D21 | Cultural & community fit | Gemini + Google Places POIs | Static-ish |

**D21 key insight:** Not just international expats — ethnic/cultural communities within same country (Brazilian community in FL, Korean in LA, Nigerian in Houston), professional communities (tech workers in Austin), lifestyle communities (retirees in Portugal). Makes "Move Here" valuable for domestic moves too — much bigger addressable market.

**Phase 2 additions:** Schools (D5), Commute (D8), Walkability (D16)
**Phase 3 additions:** Healthcare (D7), Internet (D11), Language (D13), Expat presence (D14), Tax (D15), Grocery costs (D17), Pet friendliness (D18), Noise/pollution (D19), Natural disaster risk (D20)

### Parameter 3: Data Sources

**Phase 1a: Gemini-only (free)**
- Gemini as primary source for all 9 categories (except D10 weather = Open-Meteo, already built)
- Google Places API for daily-life POI discovery (schools, hospitals, grocery, transit, gyms) — already integrated
- All data labeled "AI estimate" with confidence disclaimers
- Shadow benchmark: manually test Gemini accuracy vs Numbeo website for 20 cities (see benchmark spreadsheet)

**Phase 1b: Numbeo API upgrade ($260/mo)**
- Numbeo as verified quantitative backbone for D1-D4, D6
- Upgrades badges from "AI estimate" to "Verified data"
- `city_cost_estimator` endpoint enables Budget Simulator (household size + children → full cost breakdown)
- Additional endpoints: crime, healthcare, traffic, pollution indices
- Aggressive caching (7-day per city) keeps query volume low
- Monetization trigger: Numbeo cost justifies premium user tier

**Numbeo API key details:**
- Basic plan: 200K queries/mo @ $260/mo (overkill for Phase 1b)
- No free tier. No academic pricing found.
- Endpoints cover: city_prices, city_cost_estimator, indices, city_crime, city_healthcare, city_traffic, city_pollution, rankings, historical data, close_cities_with_prices, currency_exchange_rates

### Parameter 4: AI Lens Shift

**Decision:** Prompt layering + data injection + persona shift (combined approach).

| Layer | What changes |
|-------|-------------|
| Prompt layering | Keep base traveler prompt, append resident lens overlay: "User is considering moving here. Prioritize D1-D21 categories. Emphasize daily-life relevance over tourist appeal." |
| Data injection | Feed structured data points into prompt as grounding facts. AI must not contradict them. |
| Persona shift | Enthusiastic travel guide → honest relocation advisor. "The food scene is great but groceries are 30% above national average — here's what locals do to save." |
| Origin-aware framing | Comparison to user's current city: "Rent is 60% less than Miami." "You'll need a VITEM-V visa as a US citizen." |
| Progressive context gathering | AI learns personal details (citizenship, household, priorities) through natural conversation, not forms or onboarding screens. |

**Not in Phase 1:** Full prompt swap (loses existing area intelligence), dual-channel response (too complex).

### Parameter 5: UI Surfaces

**Phase 1a (Must):**

| Surface | Resident mode behavior |
|---------|----------------------|
| Ticker | Transforms: "☀️ 28°C · 💵 USD" → "🏠 Avg rent $1,400 · 📊 CoL index 72 · 🛡️ Safety: Above avg" |
| Dashboard card | New expandable card with 9 categories. Headline numbers + trust badges. Tap to expand each. |
| Map pins | Shift to daily-life POIs via Google Places API: grocery, schools, hospitals, transit, gyms, pharmacies |
| Detail page | Resident section added: "5 min walk to Metro · 3 grocery stores within 500m · School district rating: 7/10" |
| Companion chat | Context shifts to honest advisor. Orb bar prompt: "Ask about living here" |
| Mode indicator | Persistent 🏠 badge/chip on header. Always visible. Tap to toggle off. |

**Phase 1b:** Saved Places "Relocate" filter
**Phase 2:** Comparison view (side-by-side cities — killer feature, screenshots/shares)

**Pin shift design decision (deferred):** Toggle between "Daily Life" and "Explore" pin layers vs full replacement. To be decided during spec.

### Parameter 6: Trust Signals

**Phase 1a (Must):**

| Signal | Implementation |
|--------|---------------|
| Source attribution | "Gemini AI estimate · Mar 2026" inline under every data point. User always sees where it came from and when. |
| Confidence meter | Low / Medium / High per category per city. Honest about data gaps. |
| AI disclaimer badge | "AI insight" label on all Gemini-synthesized sections. Reuse existing `advisory_ai_disclaimer` pattern. |
| "Verify locally" links | For visa/tax/legal — link to official government sources. "For immigration: visit [country].gov/visa" |

**Phase 2:** Stale data warning (yellow caution if data > 6 months), community verification (user flags inaccurate data), data coverage transparency page.

**Core principle:** Radical honesty. When confidence is Low, point users to better sources rather than serving questionable data. App never BS's the user. Sometimes helpful means "go here instead."

**Data classification system:**
- **Static** (language, climate, demographics): Cite source + year
- **Dynamic** (rent, CoL, crime, jobs): Cite source + date + "as of [month]"
- **Volatile** (visa, tax, safety advisories): Cite source + date + "verify before acting" warning + direct gov link

### Parameter 7: Monetization

**Decision:** Free. No monetization in Phase 1.

Measure adoption, engagement depth, which data categories users interact with most. Telemetry decides the monetization strategy.

**Ideas explored for future phases:**
- Freemium gate (traveler free, resident premium)
- Relocation partner referrals (local agents, immigration lawyers)
- Exportable "Move Here" report (PDF)
- Comparison view unlock (compare 2+ cities = premium)
- Sponsored listings (real estate platforms)
- Relocation concierge (AI-powered paid deep guidance)
- B2B data API (anonymized relocation intent data)
- Local business promotion (popular with new residents)
- Subscription tiers (Free: 1 area, Basic $5: 5 areas, Pro $15: unlimited)

---

## Phase 2: Six Thinking Hats — Multi-Perspective Analysis

### White Hat (Facts)

**Key unknowns identified:**
1. Gemini accuracy on relocation-specific facts (rent, crime, visa) — benchmark needed
2. Google Places coverage for daily-life POIs internationally (schools, hospitals outside US/EU)
3. User demand signal — any existing chat logs showing relocation-type questions?
4. Visa data volatility — how stale can Gemini be before it's dangerous?

**Research tasks before building:**
- Shadow benchmark: Gemini vs Numbeo for 20 cities across 4 tiers (see spreadsheet)
- Google Places daily-life POI query test for 10 int'l cities
- Existing chat log search for relocation keywords

### Red Hat (Emotions)

**User's gut reactions:**
- "I can't wait to test it" — personally motivated (evaluating Brazil, within FL, and Atlanta)
- Feature must use origin context (US citizen looking at Brazil ≠ UK citizen looking at Brazil)
- Scope feels right, just don't overdo anything — use existing infra
- Can't identify weak spots until testing — ship and learn

**Key emotional insight:** User is own first customer. Personal stake = high quality bar.

### Yellow Hat (Benefits)

**For users:**
- One app for the full journey: Discover → Visit → Move (no competitor owns this)
- International relocation made approachable (replaces 15-tab Google sessions)
- Honest data with cited sources (rare in SEO-listicle space)
- Origin-aware comparison ("30% cheaper than where you live now")
- AI companion as conversational relocation advisor
- Domestic value via cultural/community fit (D21)

**For the product:**
- Massive retention driver (relocation decisions = weeks/months of engagement)
- Natural monetization path (high-intent, high-value users)
- Moat (map + AI + international + origin-aware + honest = hard to replicate)
- Content flywheel (relocation queries improve prompts and reveal data priorities)

### Black Hat (Risks)

| Risk | Severity | Mitigation |
|------|----------|-----------|
| Regression of existing features | HIGH | Feature flag. `DiscoveryMode` enum isolation. Existing test suite as gate. |
| State confusion | MEDIUM | Mode indicator always visible. Area-scoped. One-tap reset. Traveler = safe fallback. |
| Mode bleed (resident data leaking into traveler) | MEDIUM | State strictly per-area, cleared on area change unless marked "Move." |
| Wrong data → bad decisions | MEDIUM | Not source of truth. Cite everything. Confidence meter. "Verify locally" links. |
| Visa info staleness | HIGH | Volatile classification. Always link to gov source. "Verify before acting." |
| Legal liability | MEDIUM | Disclaimer on first activation. Per-section disclaimers for visa/tax/safety. |
| Identity confusion (travel vs relocation app) | LOW | Opt-in mode, clearly separated, traveler unchanged unless user taps "Move Here." |
| Phase 1a scope too large | MEDIUM | Split into 1a/1b. Feature flag allows partial rollout. |

### Green Hat (Creative Possibilities)

| # | Idea | Phase | User priority |
|---|------|-------|--------------|
| #1 | Day in the Life simulation | Phase 2 | "Great after honing down on a place" |
| #2 | Budget simulator | Phase 1b | "Very very helpful" |
| #3 | Neighborhood matchmaker | Phase 1b | "Makes my job easier" — high value |
| #4 | Relocation timeline generator | Phase 2 | "Not always relevant" |
| #5 | Community connector (domestic + international) | Phase 1b | Extended to ethnic/cultural/professional communities |
| #6 | Climate lifestyle mapping | Phase 2 | — |
| #7 | Cost shock alerts | Phase 1b | "Great" — proactive value |
| #8 | "People like you" stories | Phase 1b | "Sounds awesome, if possible" |

### Blue Hat (Process)

**Key principle established:** "Move Here" is a helpful aggregator, not source of truth. Cite everything, link to sources, guide the user. Authority grows over time. The value is putting it all in one place on a map with an AI companion — not data precision.

**Graceful degradation:** If mode state gets confused, default to traveler. If data confidence is Low, redirect to better sources. App should have a clean understanding of relocation mode with clean reset path.

---

## Phase 3: Cross-Pollination — Competitive Landscape

| Platform | What they do well | What they do poorly | What we steal |
|----------|-------------------|---------------------|---------------|
| **Numbeo** | CoL comparison, massive int'l coverage, crowdsourced | Ugly UI, no map, no AI, no storytelling | Their data (via API in Phase 1b). Comparison methodology. |
| **Nomad List** | City scores, lifestyle filters, community | Nomad-only persona, paywall on basics | Lifestyle filtering. City "scores" per dimension. Community-as-feature. |
| **Zillow** | Neighborhood-level data, school ratings, budget tools | US only, no lifestyle context, transaction-focused | Neighborhood granularity. "What can I afford" concept. |
| **Expatistan** | Granular line-item cost comparison (cappuccino, transit) | Limited cities, dated UI, no AI | Visceral line-item costs ("Coffee: $1.50 · Transit: $45/mo"). |
| **Teleport** | 17-dimension QoL scores, beautiful data viz, "best cities for you" | Shutting down, limited coverage, desktop-first | Dimension radar scores. Recommendation engine concept. |
| **InterNations** | Expat community surveys, local meetups | Membership wall, survey-based | Community connector. "Life from actual residents" angle. |

**AreaDiscovery's unique position:** No one combines map-native UX + AI companion + international data + origin-aware personalization + honest confidence signals + domestic AND international moves. Each competitor does 1-2 of these.

**Adapted for Phase 1a:**
- Expatistan-style granular line items in dashboard (visceral, shareable)
- Teleport-style dimension scores as visual indicators (quick scannable profile)
- Nomad List-style lifestyle tags in resident mode ("Family-friendly · Walkable · Growing tech scene")
- Radical transparency as the differentiator nobody else does

---

## Phase 4: Constraint Mapping — Path to Implementation

### Technical Constraints

| # | Constraint | Severity | Path through |
|---|-----------|----------|-------------|
| T1 | No Numbeo in Phase 1a | Resolved | Gemini-only. Numbeo deferred to 1b. |
| T2 | Pin shift = new POI discovery | Medium | Use Google Places API for daily-life POIs (schools, hospitals, grocery). Already integrated. No extra Gemini pass needed. |
| T3 | Mode state management | Medium | Single `DiscoveryMode` enum flows through ViewModel → UI. Area-scoped. Clean isolation. |
| T4 | Gemini prompt size with data injection | Low | Data points are small (~30 per city). Fits in context window. |
| T5 | Regression risk | High | Feature flag. Existing test suite as gate. Can disable entirely. |

### Data Constraints

| # | Constraint | Severity | Path through |
|---|-----------|----------|-------------|
| D-C1 | Gemini accuracy unknown | High | Shadow benchmark for 20 cities before launch. Confidence meter shows uncertainty honestly. |
| D-C2 | Visa info staleness | High | Volatile classification. Gov links. "Verify before acting." |
| D-C3 | No neighborhood-level data int'l | Medium | Phase 1a = city level. Phase 1b matchmaker uses Places POI density + Gemini qualitative. |
| D-C4 | Community/ethnic data sourcing | Medium | Phase 1b. Reddit API, Facebook Groups, Gemini knowledge for major communities. |
| D-C5 | Currency conversion | Low | Free forex API or Open-Meteo's existing exchange data. Cache daily. |

### UX Constraints

| # | Constraint | Severity | Path through |
|---|-----------|----------|-------------|
| U1 | 9 categories in dashboard = clutter | Medium | Collapsible sections. Top 3 headlines + "See all" expand. |
| U2 | Mode indicator obvious but not intrusive | Low | Small persistent 🏠 chip. Tap to toggle. |
| U3 | Pin transition could be jarring | Medium | Animate: traveler pins fade, resident pins appear. Brief loading state. |
| U4 | Too many trust badges = noise | Low | Subtle inline: "Gemini · Mar 2026" in small text. One "AI insight" label per section, not per line. |

### Business Constraints

| # | Constraint | Severity | Path through |
|---|-----------|----------|-------------|
| B1 | Feature is free | By design | Phase 1 is validation. Telemetry answers "what would users pay for?" |
| B2 | No API cost in Phase 1a | Resolved | Gemini-only = free. |
| B3 | Legal liability | Medium | Disclaimer on first activation. Per-section disclaimers. Terms of service update. |

---

## Phasing Summary

### Phase 1a — Core "Move Here" (Gemini-powered, free)

**Entry:**
- "Move Here" button on area header
- "Move" as 4th lifecycle state (Save/Go/Been/Move)
- `DiscoveryMode.TRAVELER` / `DiscoveryMode.RESIDENT` state architecture
- Feature flag wrapping everything

**Data (9 categories):**
- D1 Rent, D2 Buy, D3 CoL, D4 Safety, D6 Jobs — Gemini with "AI estimate" badges
- D9 Community vibe, D21 Cultural & community fit — Gemini + Google Places POIs
- D10 Weather — Open-Meteo (existing)
- D12 Visa/immigration — Gemini + gov verification links

**UI (6 surfaces):**
- Ticker transforms to resident data
- Dashboard card with 9 categories + trust badges
- Map pins shift to daily-life POIs (Google Places API)
- Detail page adds resident section (proximity to daily-life)
- Companion chat shifts to honest advisor persona
- Mode indicator (🏠 persistent badge, tap to reset)

**Trust:**
- Source attribution + date on every data point
- Confidence meter (Low/Med/High)
- "AI insight" badge on Gemini sections
- "Verify locally" links for visa/tax/legal
- Static/Dynamic/Volatile classification system
- Disclaimer on first "Move Here" activation

**AI:**
- Prompt layering (resident overlay on traveler base)
- Origin-aware via GPS/locale (zero friction, no questions)
- Persona shift (enthusiastic guide → honest advisor)
- Progressive context gathering through conversation

**Safety:**
- Area-scoped mode state, cleared on area change unless marked "Move"
- One-tap reset, traveler as safe fallback
- Feature flag for kill switch
- Regression test suite

**Research:**
- Shadow benchmark: Gemini vs Numbeo for 20 cities (manual spreadsheet)
- Google Places daily-life POI coverage test for 10 int'l cities
- Log implicit relocation signals silently for Phase 2

### Phase 1b — Enhanced Intelligence (Numbeo-powered, $260/mo)

- Numbeo API integration → "Verified data" badges upgrade
- Budget simulator (Numbeo `city_cost_estimator` endpoint)
- Neighborhood matchmaker ("Based on your priorities, these 3 neighborhoods fit")
- Cost shock alerts ("Private insurance here costs $200/mo — budget for this")
- Community connector (ethnic/cultural/professional communities — Reddit, Facebook, WhatsApp)
- "People like you" stories (sourced from blogs/Reddit/forums)
- Saved Places "Relocate" filter
- Monetization decision based on Phase 1a telemetry

### Phase 2 — Power Features

- AI-inferred intent detection (Phase 1 telemetry-powered)
- Comparison view (side-by-side cities — the screenshot/share feature)
- Day in the Life simulation
- Relocation timeline generator
- Additional data categories (schools, commute, walkability)
- Community verification (users flag inaccurate data)

---

## Key Principles

1. **Not a source of truth** — helpful aggregator. "Friend who did the research." Authority grows over time.
2. **Radical honesty** — when confidence is Low, redirect to better sources. App never BS's the user.
3. **Facts first, chat second** — show data points, companion handles "tell me more."
4. **Zero friction** — no onboarding, no forms. GPS = assumed origin. Context gathered through conversation.
5. **Area-scoped** — resident mode is per-area, not global. Traveler experience unchanged unless user opts in.
6. **Graceful degradation** — confusion defaults to traveler. Bad data defaults to "check these sources instead."
7. **Cite everything** — Static (source + year), Dynamic (source + date), Volatile (source + date + "verify" + gov link).
8. **Domestic + international** — cultural/community fit makes this valuable for moves within same country.

---

## Session Insights

**Breakthrough moments:**
- D21 Cultural & community fit as a category — transforms "Move Here" from international-only to universal (domestic moves too)
- Origin-aware context via GPS (zero friction) with progressive detail gathering through conversation
- Gemini-first strategy eliminates Phase 1a third-party dependency and sets up Numbeo as a clear Phase 1b upgrade + monetization trigger
- Radical transparency as competitive moat — nobody else is honest about data gaps
- "Facts first, chat second" as the UX principle for job market and other complex categories

**Facilitator's product validation:** User is own first customer — evaluating Brazil, within FL, and Atlanta for personal relocation. Highest possible motivation for quality.

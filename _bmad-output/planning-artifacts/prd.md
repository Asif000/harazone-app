---
stepsCompleted: ['step-01-init', 'step-02-discovery', 'step-02b-vision', 'step-02c-executive-summary', 'step-03-success', 'step-04-journeys', 'step-05-domain', 'step-06-innovation', 'step-07-project-type', 'step-08-scoping', 'step-09-functional', 'step-10-nonfunctional', 'step-11-polish', 'step-12-complete']
inputDocuments: ['_bmad-output/planning-artifacts/product-brief-AreaDiscovery-2026-03-03.md']
workflowType: 'prd'
documentCounts:
  briefs: 1
  research: 0
  brainstorming: 0
  projectDocs: 0
classification:
  projectType: mobile_app
  domain: general
  complexity: medium
  projectContext: greenfield
editHistory:
  - date: '2026-03-03'
    source: 'validation-report-fixes'
    changes: '12 fixes applied: 4 FR measurability, 7 NFR implementation leakage, 1 new FR12 (multilingual)'
  - date: '2026-03-03'
    source: 'edit-workflow'
    changes: 'Added phase summary table, multilingual row in journey summary, Phase 1a capability #4, updated risk note'
---

# Product Requirements Document - AreaDiscovery

**Author:** Asifchauhan
**Date:** 2026-03-03

## Executive Summary

AreaDiscovery is an AI-powered area exploration companion that proactively delivers holistic, real-time area portraits to travelers and curious locals. Built with Kotlin Multiplatform (Android first, iOS to follow), the app solves a fundamental gap: existing tools — Google Maps, Yelp, TripAdvisor, travel blogs, even general-purpose AI chatbots — all assume users know what to search for. They surface businesses, not understanding. AreaDiscovery flips this by telling users what they didn't know to ask about the moment they arrive somewhere.

The app assembles a six-bucket area portrait — Safety, Character, What's Happening, Cost, History, and Nearby — synthesized by AI from its training knowledge, public APIs (starting with news), and eventually real-time feeds. It works in two modes: proactive briefings triggered by location changes for passive discovery, and deep AI chat (text in V1, voice in V1.5) for active exploration. An interactive map with AI-generated POI markers provides visual discovery. Offline caching, privacy-first location handling (area names sent to AI, never raw GPS), and confidence tiering on all AI content round out the core experience.

Primary users are globally mobile explorers seeking depth beyond tourist guides and hometown residents wanting to discover their own neighborhoods. Secondary users include vacationing families, digital nomads orienting in new cities, and business travelers needing fast context.

### What Makes This Special

The core differentiator is the "whoa, I didn't know that" moment — delivered within seconds of arriving anywhere, without the user searching for anything. This is possible now because LLMs can aggregate, synthesize, and contextualize scattered area knowledge (history, crime trends, culture, local news, real estate data) into coherent portraits at near-zero marginal cost per area — work that previously would have required a massive editorial team. The area-first approach (holistic portraits vs. individual business listings), combined with proactive delivery and conversational depth, creates a category that no existing tool occupies.

## Project Classification

- **Project Type:** Cross-platform mobile app (Kotlin Multiplatform — Android + iOS)
- **Domain:** Travel / Location Intelligence (general — no regulated industry compliance)
- **Complexity:** Medium — significant technical depth (AI integration, real-time location services, multi-source data aggregation, offline-first architecture) but no regulatory constraints
- **Project Context:** Greenfield — new product, no existing codebase

## Success Criteria

### User Success

- **Instant value delivery:** First area summary appears within 5 seconds of app open with GPS lock — the "whoa, I didn't know that" moment must land before the user has time to question the app's value
- **Exploration depth:** Users who view an area summary proceed to ask 2+ follow-up chat queries per session, indicating the summary sparks genuine curiosity rather than satisfying it
- **Return behavior:** 40%+ D7 retention — users come back within 7 days, proving the app delivers recurring value beyond novelty
- **Worth saving:** 15%+ of sessions include a bookmark, validating that the app surfaces content users consider worth revisiting
- **Hands-free viability (V1.5):** 25%+ of queries via voice once STT/TTS ships, confirming voice-native interaction works for on-the-go use

### Business Success

**3-month validation phase:**
- Few hundred organic users, primarily word-of-mouth — no paid acquisition
- D7 retention above 40% — proof the core experience delivers
- Qualitative signal: users sharing area facts unprompted (organic growth engine)
- Per-area caching model validated — API costs sustainable at scale

**12-month growth phase:**
- Few hundred thousand users with majority from organic/referral channels
- 5%+ freemium conversion rate established
- Premium subscription revenue covering operational costs
- Affiliate revenue stream contributing supplementary income
- International usage validated across multiple languages/regions

### Technical Success

- **Area summary latency:** Streaming response begins within 2 seconds of request; full summary renders within 8 seconds
- **Offline reliability:** Previously viewed area summaries and chat responses available without connectivity; queued questions sync when online
- **Location efficiency:** Geofencing and significant location change APIs — no continuous GPS polling; battery impact imperceptible to users
- **Cache effectiveness:** Majority of area requests for popular locations served from cache — costs scale per-area, not per-user
- **Privacy compliance:** Zero raw GPS coordinates sent to AI APIs; all location history processed on-device

### Measurable Outcomes

| Metric | Target | Measurement Method |
|--------|--------|--------------------|
| Time to first area summary | < 5 seconds from app open | In-app analytics |
| Area summary engagement rate | 60%+ view full summary | Scroll depth tracking |
| Chat queries per session | 2+ average | Session analytics |
| D7 retention | 40%+ | Cohort analysis |
| Bookmark rate | 15%+ of sessions | Event tracking |
| DAU/MAU ratio | 25%+ | Daily/monthly active ratio |
| Organic install rate | Majority of installs | Attribution tracking |
| App store rating | 4.5+ | Store analytics |

## User Journeys

### Journey 1: Asif — First Arrival in a New Neighborhood

**Opening Scene:** Asif lands in Lisbon for a week-long work trip. He's been to Portugal before but never this neighborhood — Alfama. The taxi drops him at his Airbnb on a narrow cobblestone street. He has two hours before his first meeting and zero knowledge of his surroundings. Normally he'd open Google Maps, search "things to do near me," scroll through restaurant listings, and learn nothing about the place itself.

**Rising Action:** He opens AreaDiscovery. Within seconds, the app locks his GPS and begins streaming an area portrait of Alfama. The summary card fills in bucket by bucket: *History* — one of the oldest districts in Lisbon, survived the 1755 earthquake that destroyed most of the city, Moorish origins visible in the winding street layout. *Character* — residential, aging population mixed with growing tourism, fado music birthplace, laundry hanging from windows is a cultural signature, not poverty. *Safety* — generally safe during day, pickpocket-prone near tourist clusters at night, well-lit main routes. *What's Happening* — local fado festival this weekend, construction on Rua de São Miguel causing detours. *Cost* — property values up 40% in 5 years, gentrification tension, average meal €8-12. *Nearby* — Castelo de São Jorge (10 min walk), hidden Miradouro da Graça viewpoint (locals prefer it over the tourist one), Feira da Ladra flea market on Tuesdays and Saturdays.

Asif taps the map. POI markers dot the neighborhood — the castle, three viewpoints, a fado house marked "authentic, not tourist," and a pastelaria a local food blogger called "the best pastel de nata outside Belém." He taps the pastelaria marker and gets a two-line summary with a link to the blog post.

He's intrigued by the earthquake history. He types: "Tell me more about how Alfama survived the 1755 earthquake." The AI responds with a detailed explanation of how the district's Moorish foundations and hilltop position protected it while the lower city was destroyed by the tsunami that followed. It links to a Wikipedia article for deeper reading.

**Climax:** Asif bookmarks the fado festival for Saturday night. He screenshots the earthquake fact and sends it to a friend: "Did you know this??" He's been in Alfama for 4 minutes and already understands the neighborhood better than places he's lived for months.

**Resolution:** Over the week, Asif opens the app each time he moves to a new area of Lisbon. Each neighborhood gets its own portrait. By day three, return visits to Alfama show only what's changed — the flea market is tomorrow, a new restaurant opened on his street. He never searches for "things to do in Lisbon." The app tells him what he didn't know to ask.

**Capabilities revealed:** Proactive area summary, streaming response, six-bucket portrait, interactive map with POI markers, AI chat with source attribution, bookmark, return visit differentiation, share/screenshot flow.

---

### Journey 2: Maya — Discovering Her Own Neighborhood

**Opening Scene:** Maya has lived in Chicago's Pilsen neighborhood for three years. She walks the same route to the L station every morning — past a mural-covered building, a taqueria, a converted warehouse. She knows the taqueria is good. That's about it. She downloaded AreaDiscovery after a friend shared a screenshot of a historical fact about their neighborhood.

**Rising Action:** Maya opens the app on her morning walk. The area portrait for Pilsen loads: *History* — originally a Czech immigrant neighborhood (named after the city of Plzeň), transformed into the heart of Chicago's Mexican-American community in the 1960s-70s, the murals she walks past daily are part of a deliberate cultural preservation movement from the 1970s. *Character* — vibrant arts district, strong community identity, rapidly gentrifying with active resident resistance, 18th Street is the cultural spine. *What's Happening* — community meeting Thursday about the proposed condo development on Ashland, second Friday art walk this week. *Cost* — median home price doubled in 8 years, rent increases displacing longtime residents.

Maya stops walking. The building she passes every morning — the one with the massive mural of a woman holding corn — was painted in 1974 by a collective of Mexican-American artists as a protest against urban renewal that was demolishing Latino neighborhoods. She had no idea.

She types: "What's the story behind the murals on 18th Street?" The AI delivers a rich narrative of the Chicano mural movement in Pilsen, names specific artists, and links to a walking tour guide.

**Climax:** Maya screenshots the mural history and sends it to three friends: "We've walked past this every day for years and never knew." She bookmarks the Friday art walk. For the first time, she feels like she *belongs* to Pilsen rather than just living there.

**Resolution:** The app detects Pilsen as Maya's home area after two weeks of frequent visits. Her experience shifts to the home turf mode — when she opens the app, she sees what's new and changed rather than the full portrait. She discovers a hidden community garden she never noticed, learns the taqueria owner is a second-generation immigrant whose family was part of the original 1960s wave. The neighborhood transforms from backdrop to story.

**Capabilities revealed:** Home area detection, home turf content mode, first visit vs. return visit differentiation, cultural/historical depth, community events surfacing, emotional connection through local knowledge, share flow as growth driver.

---

### Journey 3: The Garcias — Family Vacation in Barcelona

**Opening Scene:** The Garcia family — parents Carlos and Elena, kids Sofia (10) and Marco (7) — are on day three of a Barcelona trip. They've done La Sagrada Familia and Park Güell. The kids are tired of "famous buildings." Carlos is navigating with Google Maps; Elena is scrolling TripAdvisor reviews. Neither feels like they're discovering Barcelona — they're checking boxes.

**Rising Action:** Elena opens AreaDiscovery as they walk through the Gothic Quarter. The area portrait surfaces: *History* — Roman-founded city, these streets follow the original Roman grid, the cathedral was built over a Roman temple. *Nearby* — a hidden courtyard two blocks away with a playground built around Roman ruins where kids can climb (the app notes "kid-friendly, shaded, usually empty in the afternoon"). *Safety* — busy area, watch for pickpockets near Las Ramblas, the side streets east of Via Laietana are quieter and safer. *What's Happening* — free outdoor puppet show in Plaça de Sant Jaume at 5pm today.

Carlos taps the hidden courtyard on the map. The AI adds: "Plaça de Sant Felip Neri — this quiet square has shrapnel marks on the church walls from a 1938 bombing during the Spanish Civil War. The playground sits beside one of Barcelona's most haunting historical sites."

Elena asks via chat: "What's good for lunch nearby that isn't a tourist trap?" The AI responds with three options, noting which ones are on side streets away from tourist foot traffic, with approximate prices and one that has a kids' menu.

**Climax:** They find the courtyard. Sofia plays on the playground while Marco traces the shrapnel marks on the wall. Carlos reads the AI's explanation of the bombing to Elena. It's the most meaningful moment of the trip — history, discovery, and the kids are happy. They never would have found this place. Elena bookmarks it.

They make the 5pm puppet show. Marco loves it. A perfect afternoon that no guidebook planned.

**Resolution:** For the remaining days, Elena opens AreaDiscovery whenever they move to a new part of the city. The family stops following "top 10" lists and starts following the app's lead. Their trip photos shift from famous landmarks to hidden corners. Back home, Elena tells three other parents about the app. "It found things in Barcelona we never would have discovered."

**Capabilities revealed:** POI markers with kid-friendly annotations, emergency info awareness (safety bucket), AI chat for contextual recommendations, bookmark, family use case, organic word-of-mouth growth, emotional engagement beyond transactional utility.

---

### Journey 4: First-Time User — Location Permission Denied

**Opening Scene:** Jamie downloads AreaDiscovery from a friend's recommendation. On first launch, the app requests location permission with context: "AreaDiscovery needs your location to tell you about your surroundings." Jamie instinctively taps "Don't Allow" — years of app permission fatigue.

**Rising Action:** Instead of an empty screen or a nag dialog, the app pivots to manual search. A search bar appears with a prompt: "Search any area to explore it." Jamie types "Shibuya, Tokyo" — a neighborhood they're visiting next month.

The full area portrait loads for Shibuya: *History* — transformation from rural area to the busiest pedestrian crossing in the world, the Hachiko statue story. *Character* — youth culture epicenter, fashion-forward, neon-lit, overwhelming for first-timers but safe. *Safety* — extremely safe at all hours, but sensory overload is the real "hazard." *Cost* — expensive dining near the crossing, significantly cheaper two blocks in any direction. *Nearby* — hidden shrine behind the 109 building, a vinyl record store in a basement that only locals know, the best view of the crossing is from the Starbucks on the second floor of Tsutaya.

Jamie types: "Is it safe to walk around Shibuya at 2am?" The AI responds that Tokyo is one of the safest major cities globally, Shibuya at 2am is busy with nightlife and well-lit, the only real concern is missing the last train at midnight and needing a capsule hotel or taxi.

**Climax:** Jamie realizes the app just gave them more useful, contextualized information about Shibuya in 30 seconds than an hour of Googling. They go to settings and enable location access — the app proved its value first.

**Resolution:** When Jamie lands in Tokyo next month, the app proactively briefs them on Narita Airport, then on each neighborhood as they move through the city. The manual search fallback converted a permission-denier into a fully engaged user by demonstrating value before asking for trust.

**Capabilities revealed:** Manual location search as onboarding fallback, graceful degradation without location, value-first permission strategy, trip planning use case, remote exploration capability, permission conversion funnel.

---

### Journey 5: Offline Explorer — Connectivity Dead Zone

**Opening Scene:** Priya is hiking in rural Iceland, driving the Ring Road between Akureyri and Egilsstaðir. Cell signal dropped 20 minutes ago. She pulls over at a small village — a cluster of houses, a church, and a gas station. She has no idea where she is or if there's anything worth stopping for.

**Rising Action:** Priya opens AreaDiscovery. The app detects no connectivity but has cached data. Earlier that morning in Akureyri, she had browsed the map and viewed area summaries for several points along her planned route. The app had cached those summaries.

The current village isn't in cache — it's too small and she didn't explore it beforehand. The app shows a minimal message: "You're near Dalvík (cached summary available, 25km back) and approaching Ólafsfjörður (cached summary available, 15km ahead). No cached data for current location. Your question will be sent when you're back online."

Priya types: "What is this village known for?" The question enters the offline queue with her GPS coordinates attached.

She drives on. Signal returns near Ólafsfjörður. The cached summary loads instantly — a fishing village with a stunning herring museum, a hiking trail to a hidden waterfall, and a geothermal pool that locals use. She stops. Her queued question also resolves: the small village she passed through has a 19th-century turf church that's a national heritage site. She bookmarks it for the drive back.

**Climax:** The app never showed an empty screen or an error. It gracefully worked with what it had — cached nearby areas, a queue for unanswered questions — and delivered value the moment connectivity returned. Priya never felt stranded.

**Resolution:** Priya learns to tap ahead on the map before driving into remote areas, pre-loading summaries along her route. The offline experience isn't perfect, but it's never broken. She trusts the app in places where every other app fails.

**Capabilities revealed:** Offline cache serving, offline question queue, graceful degradation messaging, nearby cached area suggestions, connectivity restoration sync, pre-loading behavior (user-initiated in V1, predictive in V1.5).

---

### Journey Requirements Summary

| Capability Area | Journeys That Require It |
|----------------|-------------------------|
| Proactive area summary (six-bucket) | Asif, Maya, Garcias, Priya |
| AI chat with source attribution | Asif, Maya, Garcias, Jamie |
| Interactive map with POI markers | Asif, Garcias |
| Bookmark/save areas | Asif, Maya, Garcias, Priya |
| Streaming response | Asif, Jamie |
| Return visit differentiation | Asif, Maya |
| Home area detection + home turf mode | Maya |
| Emergency/safety info | Garcias |
| Manual location search | Jamie |
| Offline cache + question queue | Priya |
| Graceful degradation (data-sparse + offline) | Priya |
| Confidence tiering / source links | Asif, Garcias |
| Multilingual/bilingual responses | Asif, Garcias, Jamie, Priya |
| Share/screenshot flow | Asif, Maya, Garcias |
| Location permission fallback | Jamie |
| First-use onboarding | Jamie |

## Innovation & Novel Patterns

### Detected Innovation Areas

1. **"Area-first" as a new information category** — No existing product treats "the area around you" as the primary information unit. Google Maps is place-first, Wikipedia is article-first, travel guides are destination-first. AreaDiscovery creates a new category: holistic area portraits that synthesize history, safety, culture, cost, news, and POIs into a single coherent view.

2. **Proactive, temporally-aware knowledge delivery** — AI chatbots wait for questions. Maps wait for searches. AreaDiscovery pushes synthesized knowledge triggered by location change — and adapts that knowledge to the current moment. The same area portrait at 8am surfaces morning cafés, commute safety, and today's events; at 11pm it shifts to nightlife, late-night safety, and what's open now. Weekend vs. weekday, seasonal vs. year-round, happening-right-now vs. upcoming — the temporal layer ensures the portrait is always relevant to *this moment*, not just *this place*.

3. **LLM as core architecture, not bolted-on feature** — The AI isn't a chat feature added to a map app. It *is* the product — the aggregation and synthesis engine that transforms scattered public knowledge into structured six-bucket portraits. No editorial team, no manual curation, near-zero marginal cost per area.

4. **Confidence tiering as trust architecture** — AI output differentiated by risk level: fun facts and history are AI-generated freely, safety-critical information (emergency services, crime alerts) requires verified sources. Most AI products treat all output with equal (un)trustworthiness. AreaDiscovery builds trust by being transparent about what it knows confidently vs. approximately.

5. **Implicit behavioral flywheel as moat** — Every area summary viewed, chat query asked, bookmark saved, and dwell time measured creates a proprietary dataset of *what people actually want to know about areas*. This data makes summaries better than raw AI output over time. Google knows where people go; AreaDiscovery knows what people want to *understand* about where they are.

### Validation Approach

- **Category validation (area-first):** Does the "whoa" moment land? Measure area summary engagement rate (target: 60%+ view full summary) and unprompted sharing as proxy for genuine value.
- **Temporal relevance validation:** Compare engagement on time-adapted summaries vs. static summaries. Do users engage more when the portrait reflects their current time context?
- **Proactive vs. pull validation:** Track ratio of proactive summary views vs. manual searches. If proactive dominates, the pattern works.
- **Trust architecture validation:** Monitor thumbs-down rates by confidence tier. Safety-critical info should have near-zero negative feedback.

### Risk Mitigation

- **Area-first might be too broad:** Users may want specific answers, not portraits. Mitigation: AI chat provides the depth layer; the summary is the hook, not the whole product.
- **Temporal context could hallucinate:** AI may fabricate time-specific events. Mitigation: "What's Happening" bucket powered by news API (verified), not AI generation; time-of-day adaptation applied to static knowledge (safety, venue hours) which is lower risk.
- **Behavioral flywheel takes time:** Early summaries are generic AI output without behavioral data. Mitigation: Tier 1 AI knowledge provides a strong baseline; the flywheel improves quality incrementally, not from zero.

## Mobile App Specific Requirements

### Project-Type Overview

AreaDiscovery is a cross-platform mobile app built with Kotlin Multiplatform (KMP) and Compose Multiplatform. Android is the primary target for V1, with iOS following once the Android experience stabilizes. The app is location-centric, AI-driven, and offline-capable — a combination that demands careful attention to platform permissions, battery efficiency, and graceful degradation across connectivity states.

### Platform Requirements

| Requirement | Detail |
|-------------|--------|
| **Framework** | Kotlin Multiplatform + Compose Multiplatform |
| **Android target** | API 26+ (Android 8.0+) — covers 95%+ of active devices |
| **iOS target** | iOS 16+ (post-KMP stabilization) |
| **Architecture** | Shared business logic in `commonMain`, platform-specific implementations for location, TTS/STT, and map rendering |
| **Map SDK** | MapLibre (open-source, no Google dependency, works cross-platform) |
| **AI provider** | Gemini API (streaming support, structured output for six-bucket model) |
| **Local storage** | Room (Android) / SQLDelight (shared) for offline cache and visit history |
| **Networking** | Ktor (KMP-native HTTP client) |
| **DI** | Koin (KMP-compatible) |

### Device Permissions

| Permission | When Needed | Justification | Fallback if Denied |
|-----------|-------------|---------------|-------------------|
| **Fine Location** | V1 — app open | Proactive area summaries, geofencing | Manual search (Journey 4 — Jamie) |
| **Background Location** | V2 — safety alerts | Area change detection while app is backgrounded | Proactive summaries only when app is open |
| **Network State** | V1 — always | Offline/online detection for cache strategy | Assume online, fail gracefully |
| **Microphone** | V1.5 — voice input | Speech-to-text for hands-free queries | Text input only |
| **Internet** | V1 — always | AI API calls, news API | Serve from offline cache |

**Permission strategy:** Value-first. Request location only after explaining the benefit. If denied, manual search demonstrates value before re-prompting. No permission walls — every feature degrades gracefully.

### Offline Mode

**Cache architecture:**
- **Area summaries** cached by area name + time window (valid for days/weeks for static knowledge)
- **Chat responses** cached by area + query hash
- **Map tiles** cached via MapLibre's built-in tile caching
- **Visit history** stored entirely on-device (never synced to server)

**Offline behavior:**
- Previously viewed areas: full summary available from cache
- New areas: "No cached data. Your question will be sent when you're back online."
- Nearby cached areas surfaced as suggestions
- Question queue with GPS coordinates attached, syncs on connectivity restoration
- No pre-caching in V1 (V1.5: predictive pre-caching on WiFi)

**Storage budget:** Target < 100MB cache footprint for typical usage (50-100 cached areas). User-configurable cache clearing.

### Push Notification Strategy

**V1:** No push notifications. App is pull-based — value delivered when user opens the app.

**V1.5:** Home turf weekly digest (opt-in only).
- Single weekly notification with neighborhood updates
- Deep-links to updated area summary
- Respects system notification settings
- No notification spam — one digest per week maximum

**V2:** Safety alerts for significant area changes (opt-in, background location required).

### Store Compliance

| Concern | Approach |
|---------|----------|
| **Location usage justification** | Clear privacy policy explaining on-device processing, area names (not GPS) sent to AI |
| **Content rating** | Crime/safety content may require Teen/12+ rating — verify during submission |
| **AI-generated content disclosure** | Transparent attribution on all AI content; confidence tiering visible to users |
| **Background location (V2)** | Requires App Store/Play Store justification — defer until V2 submission |
| **Data privacy** | GDPR-ready: no PII stored server-side, location history on-device only, user can delete all data |

### Implementation Considerations

- **Battery efficiency:** Geofencing + significant location change APIs instead of continuous GPS polling. Area summaries requested on location change events, not on a timer.
- **Streaming UX:** AI responses stream token-by-token to the UI. Summary card fills progressively. Chat responses appear word-by-word. No loading spinners — content appears as it generates.
- **Network resilience:** All API calls wrapped with timeout, retry with exponential backoff, and offline queue fallback. The app never shows a network error screen — it always has something to show.
- **Memory management:** Area summaries are text-heavy, not media-heavy. Cache is lightweight. Map tiles are the largest memory consumer — leverage MapLibre's built-in tile management.

## Project Scoping & Phased Development

### MVP Strategy & Philosophy

**MVP Approach:** Experience MVP — prove the "whoa, I didn't know that" moment lands within seconds. The fastest path to validated learning is getting the core magic in front of real users, not building a complete feature set.

**Resource Requirements:** Solo developer with Claude Code-assisted development. Android-only for all phases until iOS port.

### Phase Summary

| Phase | Focus | Key Capabilities | Timeline | Validates |
|-------|-------|-------------------|----------|-----------|
| **1a** | Prove the Magic | Proactive area summary, interactive map, simple AI chat, multilingual responses | Week 1–2 | "Whoa" moment lands within seconds |
| **1b** | Complete MVP | Full chat, manual search, bookmarks, offline cache, adaptive intelligence, emergency info | Week 3–5 | Retention, exploration depth, offline viability |
| **V1.5** | Optimize & Expand | Garcias journey, push digest, KMP iOS port, advanced caching | Week 6–10 | Multi-platform, engagement loops |
| **V2** | Scale & Differentiate | Driving mode, real-time events, personalization, social features | Post-V1.5 | Growth, differentiation |

### Phase 1a: Prove the Magic (Week 1-2)

The absolute minimum to test whether area-first, proactive, temporally-aware AI delivery creates the "whoa" moment.

**Core User Journey Supported:** Asif — first arrival in a new area (Journey 1, happy path only).

**Must-Have Capabilities:**

1. **Proactive Area Summary** — Six-bucket portrait (Safety, Character, What's Happening, Cost, History, Nearby) generated on app open via GPS. Streaming response. Time-of-day and day-of-week context passed to AI prompt. Graceful degradation for data-sparse areas.
2. **Interactive Map with AI-Generated POIs** — MapLibre map with AI-generated POI markers. Tap for details.
3. **AI Chat (Simple)** — Single question-and-answer with location context. No conversation history yet. Source attribution and confidence tiering on responses.
4. **Multilingual Responses** — Area summaries and chat responses in the user's preferred language with local language context (place names, cultural terms, key phrases) naturally embedded.

**What this validates:**
- Does the area portrait surprise and delight users?
- Does temporal context make the portrait feel more relevant?
- Do users explore deeper via chat after seeing the summary?
- Is the streaming UX fast enough (< 5 seconds to first content)?

**Go/No-Go for Phase 1b:** Users engage with the summary (60%+ view full content) and ask at least 1 follow-up question.

### Phase 1b: Complete MVP (Week 3-5)

Build out the full V1 experience once the core magic is validated.

**Additional User Journeys Supported:** Maya (Journey 2), Jamie (Journey 4), Priya (Journey 5).

**Capabilities Added:**

4. **AI Chat (Full)** — Conversation context maintained within session. Links to deeper sources.
5. **Manual Location / Remote Exploration** — Search any area by name. Fallback for location-denied users. Trip planning use case.
6. **Bookmark/Save Areas** — One-tap save, accessible from saved list.
7. **Basic Offline Caching** — Cache viewed summaries and chat responses by location. Offline question queue with GPS coordinates, syncs on connectivity.
8. **Adaptive Content Intelligence (Basic)** — Visit counter per area, first visit vs. return visit differentiation, home area detection. Configurable: Always / Only new places / Ask me.
9. **Emergency Info** — AI-surfaced nearest hospitals, police stations, embassies from verified sources.
10. **Privacy & Trust Foundations** — On-device location history, area names (not GPS) to AI, confidence tiering visuals, thumbs up/down feedback.
11. **Onboarding** — Location permission with value context, manual search fallback if denied.

**What this validates:**
- D7 retention (target: 40%+)
- Bookmark rate (target: 15%+ of sessions)
- Organic sharing behavior
- Offline reliability
- Per-area caching cost model

### Phase 2: Voice & Engagement (V1.5, Week 6-8)

**Capabilities Added:**
- Full voice interaction (STT + TTS) with streaming playback
- Push notification digest for home turf mode (weekly, opt-in)
- Predictive pre-caching on WiFi for nearby areas
- News API integration for "What's Happening" bucket (if not included in 1a)

### Phase 3: Depth & Comparison (V2, Month 3-6)

**Capabilities Added:**
- Safety alerts with background location monitoring
- Plan a day — AI-generated exploration itineraries
- Compare areas side-by-side
- Trip summaries
- Local app toolkit
- Tier 2 data API integrations (crime stats, demographics, real estate)

### Phase 4: Expansion (V3, Month 6-12)

**Capabilities Added:**
- Driving mode — hands-free voice loop, simplified UI
- Real-time events and closures (Tier 3 data)
- Advanced personalization — learning user interests
- Social features — share discoveries, follow friends

### Risk Mitigation Strategy

**Technical Risks:**
- *Most challenging:* Streaming AI response + map rendering simultaneously on app open. Mitigation: Prototype this in Phase 1a day 1 — if it's too slow, simplify to sequential loading (summary first, then map populates).
- *AI quality:* Area portraits may be thin for obscure locations. Mitigation: Graceful degradation built into the prompt — AI acknowledges limited knowledge rather than hallucinating.

**Market Risks:**
- *Core value might not land:* Users may not care about area portraits. Mitigation: Phase 1a is a 2-week investment to test this before building the full product. Go/no-go gate before Phase 1b.
- *Retention without push:* Without notifications, users may forget the app. Mitigation: V1.5 adds push digest; V1 relies on location-triggered habit (open app when arriving somewhere new).

**Resource Risks:**
- *Solo developer bottleneck:* If Phase 1a takes longer than 2 weeks, the timeline slips. Mitigation: Phase 1a is deliberately minimal (4 features). If it takes more than 2 weeks, scope Phase 1b down further.
- *API costs at scale:* Gemini API costs could spike with usage. Mitigation: Per-area caching means popular areas are served from cache. Monitor cost per DAU from day 1.

## Functional Requirements

### Area Intelligence

- **FR1:** User can view a proactive area summary organized into six knowledge buckets (Safety, Character, What's Happening, Cost, History, Nearby) based on their current GPS location `[Phase 1a]`
- **FR2:** User can view area summaries that adapt content based on time of day and day of week `[Phase 1a]`
- **FR3:** User can view area summary content as it streams in progressively, without waiting for full completion `[Phase 1a]`
- **FR4:** User can receive an area summary with available information for data-sparse or obscure locations, with the AI acknowledging limited knowledge rather than omitting content `[Phase 1a]`
- **FR5:** User can view differentiated content on return visits to a previously viewed area (only what's changed) `[Phase 1b]`
- **FR6:** User can have their home area automatically detected based on visit frequency `[Phase 1b]`
- **FR7:** User can configure area briefing behavior: Always, Only New Places, or Ask Me `[Phase 1b]`

### AI Conversation

- **FR8:** User can ask a location-aware question via text and receive an AI-generated response with source attribution `[Phase 1a]`
- **FR9:** User can view confidence tiering indicators on all AI-generated content `[Phase 1a]`
- **FR10:** User can maintain a multi-turn conversation within a session with context preserved `[Phase 1b]`
- **FR11:** User can follow outbound links to deeper sources referenced in AI responses `[Phase 1b]`
- **FR12:** User can receive area summaries and chat responses in their preferred language with local language context (place names, cultural terms, key phrases) naturally embedded `[Phase 1a]`
- **FR13:** User can ask questions via voice input and receive spoken responses `[Phase 2]`

### Map & Visual Discovery

- **FR14:** User can view an interactive map displaying AI-generated points of interest as markers `[Phase 1a]`
- **FR15:** User can tap any POI marker to view details including history, significance, and tips `[Phase 1a]`
- **FR16:** User can pan and zoom the map to explore surrounding areas `[Phase 1a]`

### Location & Search

- **FR17:** User can search for any area by name and view its full area portrait without being physically present `[Phase 1b]`
- **FR18:** User can use manual search as a fallback when location permission is denied `[Phase 1b]`
- **FR19:** System can detect significant location changes without continuous GPS polling `[Phase 1a]`

### Bookmarks & Saved Areas

- **FR20:** User can bookmark an area with a single tap `[Phase 1b]`
- **FR21:** User can view and access a list of all bookmarked areas `[Phase 1b]`
- **FR22:** User can navigate to a bookmarked area's summary from the saved list `[Phase 1b]`

### Emergency Information

- **FR23:** User can view nearest hospitals, police stations, and embassies sourced from verified data `[Phase 1b]`
- **FR24:** User can access emergency information within one tap from any screen `[Phase 1b]`

### Offline & Caching

- **FR25:** User can view previously accessed area summaries and chat responses without internet connectivity `[Phase 1b]`
- **FR26:** User can queue questions while offline that automatically send when connectivity returns `[Phase 1b]`
- **FR27:** User can see suggestions for nearby cached areas when current location has no cached data `[Phase 1b]`
- **FR28:** System can cache area summaries by area and time window so repeat requests are served locally `[Phase 1b]`

### Privacy & Trust

- **FR29:** User can provide thumbs up/down feedback on any AI-generated response `[Phase 1b]`
- **FR30:** System processes all location history on-device without sending raw GPS coordinates to external services `[Phase 1a]`
- **FR31:** User can view and delete all stored visit patterns and location history `[Phase 1b]`

### Onboarding & Permissions

- **FR32:** User can view an explanation of why location permission is needed before granting it `[Phase 1b]`
- **FR33:** User can use manual search to view area portraits before granting location permission `[Phase 1b]`

### Engagement & Sharing

- **FR34:** User can share area facts or summaries via the platform share sheet `[Phase 1b]`
- **FR35:** User can receive a weekly home turf digest notification with neighborhood updates `[Phase 2]`

### Advanced Exploration (Post-MVP)

- **FR36:** User can receive safety alerts based on background location monitoring `[Phase 3]`
- **FR37:** User can generate an AI-powered day plan itinerary for an area `[Phase 3]`
- **FR38:** User can compare two or more areas side-by-side `[Phase 3]`
- **FR39:** User can view a summary of areas explored over a time period `[Phase 3]`
- **FR40:** User can receive recommendations for locally relevant apps in their current area/country `[Phase 3]`
- **FR41:** User can use a hands-free driving mode with voice-only interaction `[Phase 4]`

## Non-Functional Requirements

### Performance

- **NFR1:** Area summary streaming must begin rendering content within 2 seconds of location lock
- **NFR2:** Full six-bucket area summary must complete within 8 seconds on a typical mobile connection (4G/LTE)
- **NFR3:** Cached area summaries must load within 500ms (no network dependency)
- **NFR4:** Map with POI markers must render within 3 seconds of area summary request
- **NFR5:** AI chat responses must begin streaming within 1.5 seconds of query submission
- **NFR6:** App background location monitoring must consume less than 2% battery per hour, comparable to standard map app background usage
- **NFR7:** App cold start to first content must complete within 5 seconds (GPS lock + summary stream start)
- **NFR8:** Offline mode detection and cache fallback must engage within 1 second of connectivity loss

### Security

- **NFR9:** AI API keys must never be extractable from the client application binary or interceptable via network traffic inspection
- **NFR10:** All network communication must use TLS 1.2+ encryption
- **NFR11:** Location data sent to AI APIs must be area names only — raw GPS coordinates must never leave the device
- **NFR12:** On-device visit history and behavioral data must be stored in encrypted local storage
- **NFR13:** User must be able to delete all locally stored data (visit history, cache, bookmarks) from within the app

### Scalability

- **NFR14:** Per-area caching must reduce repeat API calls — area summaries cached by area name + time window, not per-user
- **NFR15:** API cost must not exceed $0.01 per DAU per day, decreasing as per-area caching matures
- **NFR16:** Cache storage on device must stay under 100MB for typical usage (50-100 cached areas) with user-configurable clearing
- **NFR17:** API gateway infrastructure must handle 10x concurrent user growth without architectural changes

### Accessibility

- **NFR18:** All UI text must meet WCAG 2.1 AA contrast ratios (4.5:1 for normal text, 3:1 for large text)
- **NFR19:** All interactive elements must have minimum 48dp touch targets per Material 3 guidelines
- **NFR20:** Area summaries and chat responses must be compatible with TalkBack (Android) screen reader
- **NFR21:** Map POI markers must have text-based alternatives accessible via list view for screen reader users

### Integration

- **NFR22:** App must handle AI API provider downtime or rate limiting by showing cached content or informative fallback — never a crash or empty screen
- **NFR23:** News API failures must not block area summary generation — "What's Happening" bucket degrades gracefully to AI knowledge
- **NFR24:** Map tile provider failures must fall back to cached tiles or a minimal base map
- **NFR25:** All external API integrations must implement timeout (10s max) and resilient retry behavior — no single API failure may cascade into app-wide failure

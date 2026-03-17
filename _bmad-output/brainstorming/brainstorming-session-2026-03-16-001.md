---
stepsCompleted: [1, 2, 3, 4]
session_active: false
workflow_completed: true
inputDocuments: []
session_topic: 'Proactive AI Companion — when/why does the app speak up unprompted?'
session_goals: '(1) companion personality + tone, (2) trigger taxonomy (idle, relaunch, proximity, time), (3) nudge types, (4) where it surfaces in UI (reshapes #38), (5) what real data it needs (scopes Theme B data grounding)'
selected_approach: 'ai-recommended'
techniques_used: ['Persona Journey', 'Morphological Analysis', 'Constraint Mapping']
ideas_generated: [1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25,26,27,28,29,30,31,32,33,34,35,36,37,38,39,40,41,42,43,44,45,46,47,48,49,50]
context_file: ''
technique_execution_complete: true
---

# Brainstorming Session Results

**Facilitator:** Asifchauhan
**Date:** 2026-03-16

## Session Overview

**Topic:** Proactive AI Companion — when/why does the app speak up unprompted?

**Goals:**
1. Companion personality + tone
2. Trigger taxonomy (idle, relaunch, proximity, time)
3. Nudge types
4. Where it surfaces in UI (reshapes #38)
5. What real data it needs (scopes Theme B data grounding)

### Context Guidance

5 untracked ideas as input:
- AI Whispers — ambient proactive messages
- Relaunch Greeting — personalized hello on app open
- Concierge card — contextual suggestion card
- AI save acknowledgment — response when user saves a place
- Saves nearby nudge — alert when near a saved place

3 beta features as action surfaces:
- #52 Visit states (WANT_TO_GO / GO_NOW / PLAN_SOON) as triggers
- #53 Itinerary / "Get There" flow as action surface
- #38 FAB/orb as unified entry point

Constraints:
- No backend for beta — all client-side
- No push notifications (require backend FCM/APNs)
- Local notifications OK (AlarmManager / UNUserNotificationCenter)

### Session Setup

Rich topic with clear inputs, defined goals, and hard constraints.

## Technique Selection

**Approach:** AI-Recommended Techniques
**Analysis Context:** Multi-dimensional system design requiring personality + taxonomy + UI + data grounding

**Recommended Techniques:**
- **Persona Journey:** Establish companion identity before system design
- **Morphological Analysis:** Trigger x Nudge x Surface matrix for systematic coverage
- **Constraint Mapping:** Ground ideas in data availability and feasibility tiers

## Technique Execution Results

### Phase 1: Persona Journey — Companion Personality

#### Personality Traits

**[Personality #1]: The Delta Reporter**
_Concept_: Companion monitors saved/visited places and surfaces CHANGES — price shifts, weather windows, safety updates, new transit options, seasonal events. Not static info — temporal deltas.
_Novelty_: Most travel apps show info when you search. This one watches FOR you and reports when something moves.

**[Personality #2]: The Wellness Undertone**
_Concept_: Health and history are always-on lenses, not features. Walking distance, air quality, pollen, altitude, hydration cues woven INTO recommendations, never as separate categories.
_Novelty_: No "health tab" — these are personality traits of the companion itself.

**[Personality #3]: The Pattern Matchmaker**
_Concept_: Every save/visit teaches the companion. "You keep choosing waterfront spots with outdoor seating — here's one in Buenos Aires." Personal pattern recognition, not collaborative filtering.
_Novelty_: Ties directly into #19 AI Mirror as personality expression.

**[Personality #4]: The Anticipation Architect**
_Concept_: Deliberately nurtures planning pleasure (research: planning provides as much pleasure as traveling). Drip-feeds context over days. Keeps the loop OPEN because the open loop IS the pleasure.
_Novelty_: Every travel app tries to close the loop (search→book→go). This one deliberately keeps it open.

**[Personality #5]: The Adaptive Persona (v2)**
_Concept_: UI and tone shift based on accumulated user patterns. The app itself vibes differently for different people.
_Novelty_: Not personalized recommendations — personalized ATMOSPHERE.

**[Personality #6]: The Mirror Communicator**
_Concept_: Mirrors user's communication style. Casual user gets casual companion. Formal gets polished. Two constants: ALWAYS trustworthy, ALWAYS occasionally fun.
_Novelty_: A chameleon with principles.

**[Personality #7]: The Trusted Jester**
_Concept_: Trust is foundation, fun is spice. Humor is observational from YOUR data. "You've saved 6 coffee shops in Porto. At this point I think we can just call it a coffee crawl."
_Novelty_: Humor comes FROM the data, not despite it.

**[Personality #8]: The Nostalgia Curator**
_Concept_: Keeps visited places alive. Resurfaces memories at meaningful moments. "1 year ago you were in that cafe in Porto. They just renovated the terrace." Treats travel history as a living story.
_Novelty_: No travel app does "On This Day" with a VOICE.

#### Personality Spec Summary

**Core identity:** Nameless, first-person. The app itself is the companion.

**Tone:** The Thoughtful Guide — evocative, sensory language that paints pictures.

**Adaptive layer:** Mirrors user's language style (casual/formal/emoji).

**Always-on undertones:** Health (woven in, never preachy) + History (seasoning, not headline).

**Trust floor:** Never hype, never oversell, admits uncertainty.

**Fun ceiling:** Observational humor from user's data, rate-limited, contextual.

**Time orientation:** Backward (nostalgia) + Present (proximity) + Forward (anticipation).

### Phase 2: Morphological Analysis — Trigger x Nudge Matrix

#### Triggers (10)

| # | Trigger | Signal |
|---|---------|--------|
| T1 | App relaunch | onResume/foreground |
| T2 | Idle in-app | No interaction for X seconds |
| T3 | Proximity to saved place | GPS + saved POI coords |
| T4 | Time-based | Local notification |
| T5 | Save/visit action | User taps save or changes visit state |
| T6 | Pattern threshold | N saves in same vibe/area |
| T7 | Vibe switch | User changes category |
| T8 | Search action | User searches |
| T9 | Map pan to new area | Camera beyond distance threshold |
| T10 | Return to same area | isSameArea detection |

#### Nudge Types (11)

| # | Type | Mode |
|---|------|------|
| N1 | Delta report | "X changed since you last looked" |
| N2 | Anticipation drip | Sensory/evocative detail |
| N3 | Wellness hint | Health woven into context |
| N4 | History drop | Cultural/historical enrichment |
| N5 | Pattern insight | "You keep choosing X..." |
| N6 | Similarity match | "Like that place? Try this" |
| N7 | Practical nudge | Directions, hours, cost, weather |
| N8 | Fun observation | Humor from user's behavior |
| N9 | Encouragement | "You've explored 4 cities this month" |
| N10 | Safety/honest warning | "Beautiful but crowded on weekends" |
| N11 | Nostalgia/memory | "Remember when..." + what's new there |

#### All 50 Ideas

**T1: App Relaunch**

[T1xN1 #8]: Welcome Back Delta — On relaunch, surface the most interesting CHANGE across saved POIs.
[T1xN2 #9]: Daydream Resumption — If user has WANT_TO_GO places, drip-feed one sensory detail. Rotates across sessions.
[T1xN5 #10]: Mirror Greeting — Reflects usage patterns. "You always open around 10pm. Late night planning?"
[T1xN8 #11]: Warm Roast — Occasional humor. "12 saved, 0 visited. We should talk." Max once/week.
[T1xN9 #12]: The Streak — Milestone acknowledgment. "First time opening from Japan — welcome to a new continent."
[T1xN11 #25]: Anniversary Relaunch — If today is near a past visit date, lead with it.
[T1xN11+N1 #27]: Living Postcard — Periodically resurface visited place with current update.
[T1xN11+N8 #32]: Nostalgia Roast — "4 gelato shops in one afternoon in Rome. Respect."

**T2: Idle In-App**

[T2xN2 #13]: Ambient Whisper — After 15-20s idle, comment on what user is looking at on the map.
[T2xN6 #14]: Idle Matchmaker — Idle near saved places, suggest nearby unseen one.
[T2xN3 #15]: Gentle Health Nudge — "That route between your 3 saves is 2km — nice morning stretch."
[T2xN4 #16]: History Layer — Idle on a neighborhood, surface history.
[T2xN11 #28]: Idle Memory Lane — Map viewport overlaps with visit history, companion reminisces.

**T3: Proximity to Saved Place**

[T3xN7 #17]: Gentle Proximity Ping — Within ~200m, practical info. "Open until 6pm today."
[T3xN3 #18]: Wellness Proximity — Near outdoor POI, add health context. "Bring water if above 25C."
[T3xN2 #19]: Arrival Anticipation — Shift from daydream to preparation. "Best seats are on the left side."
[T3xN10 #20]: Honest Arrival — Bad conditions? Discourage. "Raining, no cover. Tomorrow at 4pm is clear."
[T3xN11 #30]: Return Visit — Near a previously visited place. "Welcome back. They've added 3 new dishes."

**T4: Time-Based (Local Notifications)**

[T4xN2 #21]: Morning Drip — Daily notification with one evocative detail about a WANT_TO_GO place.
[T4xN1 #22]: Weekly Delta Digest — Weekly summary of changes across all saved places.
[T4xN7 #23]: PLAN_SOON Countdown — Countdown notifications with new details as trip date approaches.
[T4xN3 #24]: Seasonal Wellness Alert — "Allergy season starting — morning walks better for pollen."
[T4xN11 #26]: On This Day — Visit anniversary + current detail. "1 year ago in Shibuya. Cherry blossoms are blooming there now."
[T4xN11+N6 #29]: Nostalgia Matchmaker — Past visit bridges to new discovery. "You loved X, here's Y."
[T4xN11+N3 #31]: Seasonal Memory — "Last winter you hiked Algarve. Wildflowers are blooming there now."

**T5: Save/Visit Action**

[T5xN7 #33]: Smart Acknowledgment — Save = one useful fact. "They're closed Mondays — heads up."
[T5xN6 #34]: Instant Neighbor — On save, suggest 1 nearby same-vibe place.
[T5xN3 #35]: Wellness Save Response — Outdoor POI save gets health context. "4.2km, 200m elevation."
[T5xN4 #36]: History Save Response — Cultural POI save gets one history fact.
[T5xN2 #37]: Anticipation Seed — WANT_TO_GO triggers companion promise. "I'll keep an eye on conditions."
[T5xN6+N11 #38]: Cross-Continental Match — "This reminds me of that cafe in Buenos Aires. You have a type."

**T6: Pattern Threshold**

[T6xN5 #39]: Vibe Profile Reveal — "You've saved 8 outdoor-seating water-view places. I know your vibe."
[T6xN5+N8 #40]: Gentle Callout — "5 coffee shops in 3 days. Serious caffeine person or am I just a cafe finder?"
[T6xN6 #41]: Vibe Expansion — Pattern detected, suggest different vibe. "All waterfront. Tried rooftops?"
[T6xN9 #42]: Explorer Milestone — "Saved places in 5 countries. Building a global map."

**T7: Vibe Switch**

[T7xN4 #43]: Vibe Context — "Nightlife in Bairro Alto doesn't start until midnight."
[T7xN11 #44]: Vibe Memory — "Last time you explored nightlife was in Tokyo. Different energy here."

**T8: Search Action**

[T8xN6 #45]: Search Enhancer — Add serendipity. "Searching Porto? Bolhao market just reopened."
[T8xN3 #46]: Search Wellness Layer — "Cinque Terre trails have steep sections. Monterosso-Vernazza is hardest."

**T9: Map Pan to New Area**

[T9xN4 #47]: Area Introduction — "Trastevere — the old working-class quarter. Louder, cheaper. That's the point."
[T9xN11 #48]: You've Been Near Here — "20 minutes from where you stayed in Rome. Worth exploring."

**T10: Return to Same Area**

[T10xN1 #49]: Area Delta — "Back in Lisbon? 2 saves updated, one changed hours."
[T10xN11 #50]: Warm Return — "Welcome back to Porto. Pick up where you left off, or explore new?"

### Phase 3: Constraint Mapping — Feasibility Tiers

#### Data Available Today (client-side)
- Saved POIs: name, lat/lng, vibe, timestamp, notes
- Visit states: WANT_TO_GO / GO_NOW / PLAN_SOON + visitedAt
- User location: GPS (foreground), geofence (background)
- Map viewport: camera position, zoom, visible area
- App lifecycle: onResume/onPause, session count
- Chat history: past queries and AI responses
- Gemini API: on-demand text generation
- Device clock: local time, timezone
- Local notifications: AlarmManager / UNUserNotificationCenter

#### Tier 1: Buildable Now

| # | Idea | Data Source |
|---|------|-----------|
| 13 | Ambient Whisper | Viewport coords + Gemini |
| 34 | Instant Neighbor | Saved POIs + loaded POIs |
| 37 | Anticipation Seed | Visit state change event |
| 39 | Vibe Profile Reveal | Save count by vibe |
| 8 | Welcome Back Delta | Saved POIs + Gemini |
| 17 | Gentle Proximity Ping | GPS + saved POI coords |

Caveat: #8 and #13 use Gemini as knowledge oracle — good enough for beta but not real-time data.

#### Tier 2: Needs #52 Visit Feature (shipping now)

| # | Idea | Gap |
|---|------|-----|
| 26 | On This Day | visitedAt timestamp |
| 29 | Nostalgia Matchmaker | Visit history + vibe matching |
| 9 | Daydream Resumption | Visit states for filtering |

#### Tier 3: Needs New Data Source (post-beta)

| # | Idea | Missing |
|---|------|---------|
| 20 | Honest Arrival | Live weather API |
| 24 | Seasonal Wellness | Pollen/air quality API |
| 22 | Weekly Delta Digest | Flight prices, POI changes |
| 31 | Seasonal Memory | Weather at remote locations |

#### UI Surfaces

| Surface | Nudge Types | Interruption Level |
|---------|------------|-------------------|
| Ticker slot | Ambient whispers, vibe context, history drops | Low — ephemeral |
| Companion card | Relaunch delta, nostalgia, pattern reveal, save ack | Medium — dismissible |
| Local notification | Time-based drips, anniversaries, proximity | High — outside app |

#### #38 Orb-as-Companion-Mailbox Concept

The orb becomes the companion's PRESENCE on the map:
- Subtle pulse/glow when companion has something to say
- Tap → companion card slides up with latest nudge
- "Tell me more" → opens full chat
- Swipe down → dismiss
- Long press → saved places list (preserves original function)

Unifies: FAB + orb + chat entry + companion nudges into ONE element.

#### Minimum Data for Beta Companion
- visitedAt timestamps (#52 — shipping)
- Save timestamps (have)
- Vibe per POI (have)
- Session open/close timestamps (add to AppLifecycleObserver)
- Interaction idle timer (add to MapScreen)
- Gemini as knowledge oracle (have)

#### Theme B Data Grounding Adds
- Weather API (unlocks Tier 3)
- POI hours validation (partial — deriveStatusFromHours)
- User language style bucket (derive from chat history)
- Notification engagement tracking

## Organization & Decisions

### Key Decisions Made

1. **Companion Identity**: Nameless, first-person. The app IS the companion. No character name.
2. **Tone**: The Thoughtful Guide — evocative, sensory. Mirrors user's language style. Two constants: always trustworthy, occasionally fun.
3. **Always-on undertones**: Health (woven in, never preachy), History (seasoning, not headline). User can toggle both in Settings.
4. **Planning-as-pleasure**: Companion deliberately extends the anticipation phase. Drip-feeds details over days. Does NOT rush to "book now."
5. **Nostalgia Curator**: Keeps visited places alive. Resurfaces memories on anniversaries, connects past visits to new discoveries.
6. **Adaptive persona (v2)**: UI and tone shift based on accumulated patterns. Future version.

### UI Decisions

1. **FAB replaced by Companion Orb** (warm orange/gold gradient) — lives on the map, separate from vibe rail. Pulses when it has something to say.
2. **Companion Card** — slides up from orb tap. Dismissible. "Tell me more" opens full chat.
3. **Ticker** — ambient whispers, history drops, wellness hints. Low interruption. Already exists.
4. **Local Notifications** — morning drips, proximity pings, anniversaries, countdowns. Tap opens app → companion card auto-slides up with same content.
5. **Profile page** — UNCHANGED. Identity strip, vibes, AI Mirror chat. No controls on profile.
6. **Gear icon** added to profile page (top-right, next to X) — opens Settings page.
7. **Settings page** contains: Nudge Notifications (drilldown), Humor toggle, Wellness Hints toggle, History Drops toggle, Language, Units, Export Data, Privacy Policy, Terms, Bug Report, About.
8. **FAB migration**: Saved Places → gold Saved orb (exists). Settings → gear on profile. FAB itself → Companion orb.

### Prototype

Interactive HTML prototype: `_bmad-output/brainstorming/prototype-proactive-companion-v1.html`
- 9 tabs: Overview, Personality, Orb Concept, Ticker, Companion Card, Notifications, A Day With It, Profile + Settings, Build Tiers
- Light themed (#F5F2EF) matching app design
- Interactive: tap scenarios to swap nudge content, toggle between profile/settings views

### Next Steps

1. Quick specs per feature cluster (companion orb + card, ticker nudges, local notifications, settings page)
2. Implement Tier 1 ideas first (6 ideas, all buildable now)
3. Tier 2 unlocks with #52 Visit feature (shipping now)
4. Iteration and testing will refine personality tone and trigger thresholds

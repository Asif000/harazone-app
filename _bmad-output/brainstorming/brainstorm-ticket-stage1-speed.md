# Brainstorm Ticket: Stage 1 Pin Speed

**Created:** 2026-03-13
**Priority:** CRITICAL
**Related:** Bug #30, Feature #31 (3-Pin Progressive Discovery)

## Problem

Stage 1 still takes 15-30s+ even after reducing from 8 to 3 POIs. The 3-pin progressive discovery feature is undermined because the initial 3 pins don't appear fast enough. Both initial load and subsequent searches are equally slow.

The bottleneck is NOT code — it's gemini-2.5-flash latency for the Stage 1 prompt. The prompt asks for vibes + POIs + GPS coordinates in a single call.

## Current Stage 1 Prompt

```
Area: "$areaName". Return JSON only, no other text.
Schema: {"vibes":[...],"pois":[{"n":"Name","t":"type","lat":0.0,"lng":0.0,"v":"Street Art"},...]}
Rules:
- vibes: 4-6 most distinctive dimensions
- pois: 3 best POIs (was 8)
- GPS to 4 decimal places
```

## Brainstorm Axes

### A. Prompt Simplification
- Strip vibes from Stage 1 entirely — return ONLY 3 POIs, fetch vibes in Stage 2
- Remove GPS precision requirement ("4 decimal places") — does it slow generation?
- Shorter schema — minimal fields only (name, lat, lng)
- Remove the example from the prompt (saves tokens in/out)

### B. Model Options
- gemini-2.0-flash (older but potentially faster for simple JSON?)
- gemini-2.0-flash-lite (if available — lower latency tier)
- Test gemini-2.5-flash with `temperature: 0` for deterministic faster output
- Pre-warm with a throwaway request on app start?

### C. Architecture Changes
- **Cache-first**: On area revisit, show cached POIs instantly, refresh in background
- **Streaming first token**: Parse POIs as they stream in (don't wait for full response). Emit first POI as soon as JSON array starts
- **Split Stage 1 into micro-calls**: 1 call for vibes (fast, no GPS), 1 call for 3 POIs (needs GPS). Run in parallel
- **Pre-fetch on camera idle**: Start fetching POIs for the area the user is panning toward, before they commit to a search
- **Local POI seed**: Bundle a small POI database (e.g., OpenStreetMap extract) for instant pins, then enrich with Gemini

### D. Perception Tricks
- Show skeleton/shimmer cards immediately with area name
- Animate map camera to the area during loading (gives feeling of progress)
- Show a "finding hidden gems..." message with progress stages
- Show cached vibes immediately (from previous areas), update when real ones arrive

### E. Cold Start Specific
- The cold start picker ("What excites you") adds to perceived delay on first launch
- Defer cold start picker until AFTER first pins drop
- Or: show cold start picker DURING the initial fetch (user picks while Gemini works)

## Target
- First pin visible in <5s (pass) / <3s (target)
- Must work on cellular (not just WiFi)

## Next Steps
- [ ] Benchmark current Stage 1 latency (log timestamps in GeminiAreaIntelligenceProvider)
- [ ] Test prompt without vibes (POIs-only) — measure time delta
- [ ] Test gemini-2.0-flash vs 2.5-flash latency for same prompt
- [ ] Prototype streaming-first-POI parser
- [ ] Evaluate cache-first architecture for revisited areas

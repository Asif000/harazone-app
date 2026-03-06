# Hero Redesign Tech Spec — Adversarial Review Findings

Source: `_bmad-output/implementation-artifacts/tech-spec-summary-hero-redesign-timeline-right-now.md`
Review date: 2026-03-06

## Findings to fix during implementation

| # | Finding | Severity | Resolution | Status |
|---|---------|----------|------------|--------|
| 1 | `parseTimelineEras` split on `[.!?]` only — missing newline fallback from risk note | M | Add `\n` to sentence split regex | TODO |
| 2 | Year regex matches ZIP codes, addresses, populations — no range guard | H | Restrict to 1000–2099 range | TODO |
| 3 | No fallback when HISTORY completes but no eras parsed — silent empty | M | Show highlightText as fallback if eras empty | TODO |
| 5 | Era sentence text has no maxLines — inconsistent card heights | M | Cap at `maxLines = 3` with ellipsis | TODO |
| 6 | `highlightText.isNotEmpty()` doesn't catch whitespace-only | L | Use `isNotBlank()` | TODO |
| 8 | "More about this place" label shown in Loading/LocationFailed states | M | Gate on at least one bucket having content | TODO |
| 10 | RightNowCard above TimelineCard causes layout shift when HISTORY completes first | H | Render both in fixed order, AnimatedVisibility handles hiding — no shift | TODO |

## Findings deferred (out of scope for this change)

| # | Finding | Reason |
|---|---------|--------|
| 4 | 200.dp hardcoded width violates spacing convention | Spacing tokens don't have a card-width token; 200.dp is reasonable for v1 |
| 7 | liveRegion may re-announce on unrelated recomposition | Low risk; monitor in manual testing |
| 9 | No click-to-scroll interaction on hero cards | Good idea but scope creep; backlog it |
| 11 | No tablet/landscape adaptive layout | Phase 2+ concern |
| 12 | No test for newline-only splitting | Will add when fixing #1 |

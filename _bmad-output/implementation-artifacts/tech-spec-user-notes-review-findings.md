---
title: 'Adversarial Review Findings: User Notes on Saved Cards'
spec: 'tech-spec-user-notes-saved-cards.md'
reviewed: '2026-03-13'
findings_count: 12
resolved_count: 12
resolved_date: '2026-03-13'
---

# Review Findings: User Notes on Saved Cards

## Summary

12 findings. All 12 resolved in spec update 2026-03-13.

---

## Findings

1. **Data loss on fast back-press (critical logic error)** — `onStopEditingNote` cancels `saveNoteJob` without performing an immediate save. The spec's own note claims "there's no data loss on dismiss" because keystrokes trigger `onNoteChanged` — but if the user types and presses back within 500ms, the debounce job is cancelled and the note is never written. Data is silently lost.
   - **RESOLVED**: `onStopEditingNote(finalNote: String)` now takes the current text from the composable and performs an immediate flush before clearing state. Added test case #5 (`onStopEditingNote_flushesImmediately`).

2. **Contradictory `PlatformBackHandler` ordering instructions** — Task 7 says "place it before the existing `PlatformBackHandler`", but the Additional Context note says "last-composed = highest priority." If last-composed is highest priority, placing it *before* makes it *lower* priority than the sheet-dismiss handler — the sheet would close instead of the keyboard. The two instructions directly contradict each other.
   - **RESOLVED**: Both Task 7 and the Notes section now consistently say "AFTER" the sheet-dismiss handler (last-composed = highest priority).

3. **AC 9 references an unimplemented feature** — "Note keeper" achievement tag and `buildDiscoveryStory` are mentioned in Acceptance Criteria but have zero implementation tasks, no file references, and no scope entry. This AC cannot pass. It's either a copy-paste from a future spec or a dangling dependency that will silently fail QA.
   - **RESOLVED**: AC 9 removed. Corresponding smoke test step removed.

4. **`noteText` local state not synced to prop changes** — `remember(poi.id) { mutableStateOf(poi.userNote ?: "") }` only resets when `poi.id` changes. If `poi.userNote` is updated externally (e.g. DB flow emits) and the card recomposes with the new value, the text field still shows the stale local state. The key should be `remember(poi.id, poi.userNote)`.
   - **RESOLVED**: Changed to `remember(poi.id, poi.userNote)` in Task 6 code and Notes section.

5. **Silent DB failure swallows data loss** — `catch (_: Exception) { // Silent }` in `onNoteChanged`. If the DB write fails, the UI shows the note as saved but it isn't. No error state, no retry, no user feedback. Acceptable for v1 only if explicitly noted as a known limitation — it isn't.
   - **RESOLVED**: Documented as Known Limitation (v1) in new "Known Limitations" section. Comment in code updated with "Known limitation (v1)" label and future improvement note.

6. **Char limit enforced in both ViewModel and composable inconsistently** — Composable uses `<= 280` (allows 280); ViewModel uses `> 280` (also allows 280). Functionally identical but the ViewModel guard is dead code since the composable already blocks > 280 characters. Dead defensive code creates maintenance confusion.
   - **RESOLVED**: Removed ViewModel guard. Single enforcement point in composable `onValueChange`. Technical Decisions section updated to reflect single-point enforcement.

7. **`ImeAction.Done` disables newlines on a multi-line field** — `singleLine = false, maxLines = 4` signals a multi-line field, but `ImeAction.Done` replaces the Enter key with a Done button. Users cannot type newlines in a 4-line field. This is a UX design choice with no explicit rationale — should be documented as intentional or changed to `ImeAction.Default`.
   - **RESOLVED**: Changed to `ImeAction.Default` with inline comment explaining rationale (preserves Enter key for newlines; user dismisses via back button, tapping outside, or sheet dismiss).

8. **`Icons.Default.Edit` may not be available** — `androidx.compose.material.icons.filled.Edit` is in `material-icons-extended`, not the core icons bundle. The spec's imports list includes it without checking if the dependency is already present. If the project only includes `compose-material-icons-core`, this will fail to compile.
   - **RESOLVED**: Changed to `Icons.Outlined.Create` (available in core icons). Import list updated. Added inline comment noting fallback options if needed.

9. **Cross-reference error in Testing Strategy** — Line 338: "Unit tests in `SavedPlacesViewModelTest.kt` (4 cases — see Task 8)." Tests are in Task 9, not Task 8. Task 8 is `FakeSavedPoiRepository`. Minor but erodes trust in spec accuracy.
   - **RESOLVED**: Fixed to "see Task 9". Updated count to 5 cases (includes new flush test). Files to Reference table also updated.

10. **No IME inset handling specified** — Opening a keyboard inside a `SavedPlacesScreen` overlay (which renders over the map) is likely to obscure the active text field. No `imeNestedScroll()`, `WindowInsets`, or scroll-to-focused-field behaviour is specified. This is a known Compose/Android pitfall that will show up immediately in manual testing.
    - **RESOLVED**: Added `Modifier.imePadding()` requirement to Task 7. Added smoke test step #8 (keyboard on bottom card scrolls into view).

11. **`onStopEditingNote` does not save immediately before clearing state** — Even if data loss on back-press is "accepted", the spec provides no flush path. A user who closes the sheet via the close button (not back) also hits `onStopEditingNote`, meaning any pending debounce is dropped. There is no way to guarantee the last typed characters reach the DB under normal usage.
    - **RESOLVED**: Same fix as #1. `onStopEditingNote(finalNote)` cancels debounce and performs immediate write.

12. **No handling for list reorder race during active edit** — `editingNotePoiId` is in `UiState`. If a vibe filter change reorders the list while a note is being edited, the `LazyColumn` items recompose and the focused card may jump. `FocusRequester.requestFocus()` is inside `LaunchedEffect(isEditingNote)` — it won't re-fire. The keyboard could detach silently.
    - **RESOLVED**: Added `poi.id` to `LaunchedEffect` key so focus re-fires on recompose. Documented as Known Limitation with mitigation note (auto-close editing on vibe filter change if jarring in testing). Added smoke test step #9.

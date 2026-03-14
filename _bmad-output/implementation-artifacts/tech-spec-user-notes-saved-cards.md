---
title: 'User Notes on Saved Cards'
slug: 'user-notes-saved-cards'
created: '2026-03-13'
status: 'implementation-complete'
stepsCompleted: [1, 2, 3, 4]
tech_stack:
  - 'Kotlin Multiplatform / Compose Multiplatform (commonMain)'
  - 'SQLDelight (multiplatform persistence)'
  - 'Koin (DI)'
  - 'ViewModel + StateFlow (Jetpack lifecycle-viewmodel)'
files_to_modify:
  - 'saved_pois.sq'
  - 'SavedPoiRepository.kt'
  - 'SavedPoiRepositoryImpl.kt'
  - 'SavedPlacesUiState.kt'
  - 'SavedPlacesViewModel.kt'
  - 'SavedPoiCard.kt'
  - 'SavedPlacesScreen.kt'
  - 'FakeSavedPoiRepository.kt'
code_patterns:
  - 'withContext(ioDispatcher) for all DB writes'
  - 'MutableStateFlow<SavedPlacesUiState> + _uiState.update { } pattern'
  - 'BasicTextField with SolidColor cursor — existing pattern in SavedPlacesScreen search bar'
  - 'FocusRequester + LaunchedEffect(isEditing) to show keyboard on edit start'
  - 'Job + delay(500ms) for debounced auto-save in ViewModel; advanceTimeBy(600) in TestScope skips the delay'
test_patterns:
  - 'UnconfinedTestDispatcher + runTest'
  - 'FakeSavedPoiRepository with mutable captured args'
  - 'Assert uiState.value as SavedPlacesUiState'
---

# Tech-Spec: User Notes on Saved Cards

**Created:** 2026-03-13

## Overview

### Problem Statement

Saved POI cards show no personal context. The domain model, database schema, and repository already support `userNote: String?`, but nothing in the UI renders or edits it. The field is completely invisible to users — a shipped but inaccessible feature.

### Solution

Surface the existing `userNote` field on `SavedPoiCard`: show an "Add note…" tap target when empty, and a read/edit inline text field when a note exists. Auto-save on blur/dismiss. 280 character limit with a counter appearing at 240+. Notes appear in both the Saved Places sheet (which renders over the map) and anywhere else `SavedPoiCard` is used.

### Scope

In Scope:
- Add `updateUserNote` SQL query to saved_pois.sq
- Add `updateUserNote(poiId, note)` to repository interface + impl
- Add `editingNotePoiId: String?` to `SavedPlacesUiState`
- Add `onStartEditingNote`, `onNoteChanged` (debounced 500ms), `onStopEditingNote(finalNote)` (immediate flush) to `SavedPlacesViewModel`
- Update `SavedPoiCard` to render note area: "Add note…" placeholder when null; `BasicTextField` when editing; char counter at 240+; edit pencil icon to enter edit mode
- Wire editing state from `SavedPlacesScreen` down to `SavedPoiCard`
- Remove `TODO(BACKLOG-MEDIUM)` at SavedPoiCard line 92

Out of Scope:
- Notes on the map marker tap card (no composable exists for this yet)
- Note search (notes are not indexed in the existing search filter)
- Note export
- Rich text / formatting

## Context for Development

### Codebase Patterns

- All DB writes use `withContext(ioDispatcher)` — see `SavedPoiRepositoryImpl`.
- State is `MutableStateFlow<SavedPlacesUiState>` updated via `_uiState.update { }`.
- `SavedPoiCard` receives all callbacks as parameters — no ViewModel reference inside the composable.
- `BasicTextField` with `SolidColor(Color.White)` cursor already used in `SavedPlacesScreen` search bar (line 173–179) — follow that pattern exactly.
- `FocusRequester` + `LaunchedEffect(isEditingNote) { if (isEditingNote) focusRequester.requestFocus() }` to auto-show keyboard on edit start.
- Debounce pattern: hold a `private var saveNoteJob: Job? = null` in ViewModel; on `onNoteChanged`, cancel previous job and `viewModelScope.launch { delay(500); repo.updateUserNote(...) }`.
- `SavedPlacesScreen` iterates cards with `items(uiState.filteredSaves, key = { it.id })` — pass `isEditingNote = (poi.id == uiState.editingNotePoiId)` per card.

### Files to Reference

| File | Purpose |
| ---- | ------- |
| `composeApp/src/commonMain/sqldelight/com/harazone/data/local/saved_pois.sq` | Add `updateUserNote` query after `deleteById` |
| `composeApp/src/commonMain/kotlin/com/harazone/domain/repository/SavedPoiRepository.kt` | Add `suspend fun updateUserNote(poiId: String, note: String?)` |
| `composeApp/src/commonMain/kotlin/com/harazone/data/repository/SavedPoiRepositoryImpl.kt` | Implement `updateUserNote` using new SQL query |
| `composeApp/src/commonMain/kotlin/com/harazone/ui/saved/SavedPlacesUiState.kt` | Add `editingNotePoiId: String? = null` to `SavedPlacesUiState` |
| `composeApp/src/commonMain/kotlin/com/harazone/ui/saved/SavedPlacesViewModel.kt` | Add 3 note methods; debounced auto-save job |
| `composeApp/src/commonMain/kotlin/com/harazone/ui/saved/components/SavedPoiCard.kt` | Add note area UI — line 92 TODO is the insertion point |
| `composeApp/src/commonMain/kotlin/com/harazone/ui/saved/SavedPlacesScreen.kt` | Pass `isEditingNote` + callbacks to each `SavedPoiCard` |
| `composeApp/src/commonTest/kotlin/com/harazone/fakes/FakeSavedPoiRepository.kt` | Add `updateUserNote` impl + `lastUpdatedPoiId`/`lastUpdatedNote` capture fields |
| `composeApp/src/commonTest/kotlin/com/harazone/ui/saved/SavedPlacesViewModelTest.kt` | Already exists (11 tests) — append 5 note tests |

### Technical Decisions

- Dedicated `updateUserNote` SQL query (not INSERT OR REPLACE) — avoids touching `saved_at`, avoids read-before-write, keeps the write atomic and minimal.
- Editing state (`editingNotePoiId`) lives in `SavedPlacesUiState`, not local Compose state — ensures a single source of truth and makes it testable.
- Auto-save on blur (500ms debounce) — no Save button. Consistent with recommendation: low friction for a short note field.
- 280 char hard cap enforced at a single point: the composable's `onValueChange` (`if (new.length <= 280)`). No redundant ViewModel guard — single enforcement point avoids maintenance confusion. Counter shown only at 240+ to avoid visual noise for short notes.
- Empty note on save: if `note.isBlank()` → call `updateUserNote(poiId, null)` to clear the field rather than persisting an empty string. DB column is nullable TEXT.
- `SavedPoiCard` is a pure composable — all note interactions are callbacks, no direct ViewModel reference.

## Implementation Plan

### Tasks

Tasks ordered by dependency — lowest level first.

---

- [x] **Task 1: saved_pois.sq — add updateUserNote query**
  - File: `composeApp/src/commonMain/sqldelight/com/harazone/data/local/saved_pois.sq`
  - Action: Add after `deleteById`:
    ```sql
    updateUserNote:
    UPDATE saved_pois SET user_note = :user_note WHERE poi_id = :poi_id;
    ```

---

- [x] **Task 2: SavedPoiRepository — add updateUserNote to interface**
  - File: `composeApp/src/commonMain/kotlin/com/harazone/domain/repository/SavedPoiRepository.kt`
  - Action: Add method:
    ```kotlin
    suspend fun updateUserNote(poiId: String, note: String?)
    ```

---

- [x] **Task 3: SavedPoiRepositoryImpl — implement updateUserNote**
  - File: `composeApp/src/commonMain/kotlin/com/harazone/data/repository/SavedPoiRepositoryImpl.kt`
  - Action: Add:
    ```kotlin
    override suspend fun updateUserNote(poiId: String, note: String?) {
        withContext(ioDispatcher) {
            database.saved_poisQueries.updateUserNote(
                user_note = note,
                poi_id = poiId,
            )
        }
    }
    ```

---

- [x] **Task 4: SavedPlacesUiState — add editingNotePoiId**
  - File: `composeApp/src/commonMain/kotlin/com/harazone/ui/saved/SavedPlacesUiState.kt`
  - Action: Add field to `SavedPlacesUiState`:
    ```kotlin
    val editingNotePoiId: String? = null,
    ```

---

- [x] **Task 5: SavedPlacesViewModel — add note editing methods**
  - File: `composeApp/src/commonMain/kotlin/com/harazone/ui/saved/SavedPlacesViewModel.kt`
  - Action 1 — add private var: `private var saveNoteJob: Job? = null`
  - Action 2 — add `fun onStartEditingNote(poiId: String)`:
    ```kotlin
    fun onStartEditingNote(poiId: String) {
        _uiState.update { it.copy(editingNotePoiId = poiId) }
    }
    ```
  - Action 3 — add `fun onNoteChanged(poiId: String, note: String)`:
    ```kotlin
    fun onNoteChanged(poiId: String, note: String) {
        // Char limit is enforced at the composable layer (BasicTextField onValueChange).
        // No redundant ViewModel guard — single enforcement point avoids maintenance confusion.
        val trimmed = if (note.isBlank()) null else note
        saveNoteJob?.cancel()
        saveNoteJob = viewModelScope.launch {
            delay(500)
            try {
                savedPoiRepository.updateUserNote(poiId, trimmed)
            } catch (_: Exception) {
                // Known limitation (v1): silent failure — DB flow will reflect true state on next emit.
                // Future: surface transient error banner or retry.
            }
        }
    }
    ```
  - Action 4 — add `fun onStopEditingNote(finalNote: String)`:
    ```kotlin
    fun onStopEditingNote(finalNote: String) {
        saveNoteJob?.cancel()
        saveNoteJob = null
        val trimmed = if (finalNote.isBlank()) null else finalNote
        val poiId = _uiState.value.editingNotePoiId
        _uiState.update { it.copy(editingNotePoiId = null) }
        if (poiId != null) {
            viewModelScope.launch {
                try {
                    savedPoiRepository.updateUserNote(poiId, trimmed)
                } catch (_: Exception) {
                    // Known limitation (v1): silent failure — DB flow will reflect true state on next emit
                }
            }
        }
    }
    ```
  - Note: `onStopEditingNote` cancels any pending debounce job and performs an immediate flush of the final note value. This prevents data loss when the user presses back or dismisses the sheet within 500ms of the last keystroke. The `finalNote` parameter is passed from the composable's local `noteText` state.

---

- [x] **Task 6: SavedPoiCard — add note UI**
  - File: `composeApp/src/commonMain/kotlin/com/harazone/ui/saved/components/SavedPoiCard.kt`
  - Action 1 — add params (all defaulted, zero impact on existing callers):
    ```kotlin
    isEditingNote: Boolean = false,
    onStartEditingNote: () -> Unit = {},
    onNoteChanged: (String) -> Unit = {},
    onStopEditingNote: (String) -> Unit = {},
    ```
  - Action 2 — add state inside composable:
    ```kotlin
    val focusRequester = remember { FocusRequester() }
    var noteText by remember(poi.id, poi.userNote) { mutableStateOf(poi.userNote ?: "") }
    LaunchedEffect(isEditingNote, poi.id) {
        if (isEditingNote) focusRequester.requestFocus()
    }
    ```
  - Action 3 — replace the TODO comment at line 92 with the note area, inserted AFTER the `areaName` Text and BEFORE `Spacer(Modifier.height(8.dp))`:
    ```kotlin
    // Note area
    Spacer(Modifier.height(6.dp))
    if (isEditingNote) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(
                value = noteText,
                onValueChange = { new ->
                    if (new.length <= 280) {
                        noteText = new
                        onNoteChanged(new)
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester),
                textStyle = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.8f)),
                cursorBrush = SolidColor(Color.White),
                singleLine = false,
                maxLines = 4,
                keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Default),
                // ImeAction.Default preserves the Enter key for newlines in this multi-line field.
                // User dismisses editing via back button, tapping outside, or sheet dismiss.
            )
            if (noteText.length >= 240) {
                Text(
                    text = "${280 - noteText.length}",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (noteText.length >= 270) Color(0xFFFF6B6B) else Color.White.copy(alpha = 0.4f),
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
        }
    } else if (poi.userNote != null) {
        Row(
            modifier = Modifier.clickable { onStartEditingNote() },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = poi.userNote,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.6f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            // Note: Icons.Default.Edit requires material-icons-extended.
            // Check if the project already depends on it; if not, use Icons.Outlined.Create
            // (available in core material-icons) or a simple Text("✏️") as a zero-dependency fallback.
            Icon(
                Icons.Outlined.Create,
                contentDescription = "Edit note",
                tint = Color.White.copy(alpha = 0.3f),
                modifier = Modifier.size(14.dp).padding(start = 4.dp),
            )
        }
    } else {
        Text(
            text = "Add a note…",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.25f),
            modifier = Modifier.clickable { onStartEditingNote() },
        )
    }
    ```
  - Imports to add: `androidx.compose.foundation.text.BasicTextField`, `androidx.compose.material.icons.outlined.Create`, `androidx.compose.runtime.getValue`, `androidx.compose.runtime.mutableStateOf`, `androidx.compose.runtime.remember`, `androidx.compose.runtime.setValue`, `androidx.compose.ui.focus.FocusRequester`, `androidx.compose.ui.focus.focusRequester`, `androidx.compose.ui.graphics.SolidColor`, `androidx.compose.ui.text.input.KeyboardOptions`

---

- [x] **Task 7: SavedPlacesScreen — wire editing state to cards**
  - File: `composeApp/src/commonMain/kotlin/com/harazone/ui/saved/SavedPlacesScreen.kt`
  - Action: In `items(uiState.filteredSaves)` block where `SavedPoiCard` is called, add the new params:
    ```kotlin
    SavedPoiCard(
        poi = poi,
        isPendingUnsave = poi.id in uiState.pendingUnsaveIds,
        isEditingNote = poi.id == uiState.editingNotePoiId,
        onStartEditingNote = { viewModel.onStartEditingNote(poi.id) },
        onNoteChanged = { note -> viewModel.onNoteChanged(poi.id, note) },
        onStopEditingNote = { noteText -> viewModel.onStopEditingNote(noteText) },
        onClick = { onPoiSelected(poi) },
        // ... existing params unchanged
    )
    ```
  - Also add `PlatformBackHandler(enabled = uiState.editingNotePoiId != null) { viewModel.onStopEditingNote(/* pass current noteText from card */) }` — place it AFTER the existing `PlatformBackHandler` for sheet dismiss. In Compose, last-composed = highest priority, so placing it after ensures the note-editing handler fires first on back press (dismissing the keyboard/field), while a second back press hits the sheet-dismiss handler.
  - **IME inset handling**: Ensure the `LazyColumn` (or its parent) applies `Modifier.imePadding()` so the keyboard does not obscure the active text field. If `SavedPlacesScreen` already uses `WindowInsets.ime` or `imePadding()`, verify it covers the card list. If not, add `Modifier.imePadding()` to the `LazyColumn`. Also add smoke test step: open keyboard on a card near the bottom of the list and confirm the card scrolls into view above the keyboard.

---

- [x] **Task 8: FakeSavedPoiRepository — extend for updateUserNote**
  - File: `composeApp/src/commonTest/kotlin/com/harazone/fakes/FakeSavedPoiRepository.kt`
  - Action: Add interface implementation + capture fields:
    ```kotlin
    var lastUpdatedPoiId: String? = null
    var lastUpdatedNote: String? = null

    override suspend fun updateUserNote(poiId: String, note: String?) {
        lastUpdatedPoiId = poiId
        lastUpdatedNote = note
        _pois.value = _pois.value.map {
            if (it.id == poiId) it.copy(userNote = note) else it
        }
    }
    ```

---

- [x] **Task 9: Tests — SavedPlacesViewModelTest**
  - File: `composeApp/src/commonTest/kotlin/com/harazone/ui/saved/SavedPlacesViewModelTest.kt` *(already exists — 11 tests; append only)*
  - Add 5 test cases:
    1. **`onStartEditingNote_setsEditingNotePoiId`**: Call `viewModel.onStartEditingNote("poi-1")`. Assert `uiState.editingNotePoiId == "poi-1"`.
    2. **`onStopEditingNote_clearsEditingNotePoiId`**: Set editing → call `onStopEditingNote("note text")`. Assert `uiState.editingNotePoiId == null`.
    3. **`onNoteChanged_savesAfterDebounce`**: Call `onNoteChanged("poi-1", "Great spot")`. `advanceTimeBy(600)`. Assert `fakeRepo.lastUpdatedNote == "Great spot"` and `fakeRepo.lastUpdatedPoiId == "poi-1"`.
    4. **`onNoteChanged_blankNotesPersistAsNull`**: Call `onNoteChanged("poi-1", "   ")`. `advanceTimeBy(600)`. Assert `fakeRepo.lastUpdatedNote == null`.
    5. **`onStopEditingNote_flushesImmediately`**: Call `onNoteChanged("poi-1", "Fast note")` (starts debounce). Immediately call `onStopEditingNote("Fast note")` (within 500ms). Assert `fakeRepo.lastUpdatedNote == "Fast note"` — proves the immediate flush path saves without waiting for the debounce.

### Acceptance Criteria

- [x] **AC 1**: Given a saved card with no note, when user views the card, then an "Add a note…" placeholder is visible below the place name.
- [x] **AC 2**: Given the "Add a note…" placeholder is visible, when user taps it, then the placeholder is replaced by a focused `BasicTextField` and the keyboard appears.
- [x] **AC 3**: Given the user types a note, when they stop typing for 500ms, then the note is persisted to the database (confirmed by re-opening the saves sheet after dismissing).
- [x] **AC 4**: Given a note field is active and the user presses the Android back button, then the text field closes, the note is immediately flushed to the database (no debounce wait), and the saves sheet remains open.
- [x] **AC 5**: Given a saved card has an existing note, when user views the card, then up to 2 lines of the note are shown with a small pencil icon.
- [x] **AC 6**: Given a note is visible on a card, when user taps it, then the field enters edit mode with the existing text pre-populated.
- [x] **AC 7**: Given user types 240 characters, then a character countdown appears (e.g. "40"). Given user types 270+, then the counter turns red. Given user reaches 280, no further characters are accepted.
- [x] **AC 8**: Given user clears a note completely (blanks it out), when the debounce fires, then `userNote` in the database is set to `null` (not an empty string), and the card reverts to showing "Add a note…".

### Known Limitations (v1)

- **Silent DB failure**: If `updateUserNote` throws, the UI shows the note as saved but it isn't persisted. The DB flow will reflect the true state on next emit (e.g. sheet reopen). Future: surface a transient error banner or retry.
- **List reorder during edit**: If a vibe filter change reorders the list while a note is being edited, the `LaunchedEffect(isEditingNote, poi.id)` will re-fire focus. However, the card may visually jump. If this proves jarring in testing, consider auto-calling `onStopEditingNote` when the vibe filter changes.

## Additional Context

### Dependencies

- No new libraries. `BasicTextField`, `FocusRequester`, `HorizontalPager` — all already in `compose-foundation` (transitive from `compose-material3`).
- No new Koin module registrations — `SavedPoiRepositoryImpl` is already wired.
- No DB migration needed — `user_note TEXT` column already exists in the schema.

### Testing Strategy

Unit tests in `SavedPlacesViewModelTest.kt` (5 cases — see Task 9). `FakeSavedPoiRepository` needs a `var lastUpdatedNote: String? = null` and `var lastUpdatedPoiId: String? = null` fields populated by the fake `updateUserNote` implementation.

Manual smoke checklist:
1. Open Saved Places → verify "Add a note…" appears on all cards
2. Tap placeholder → keyboard appears, text field focused
3. Type a note, wait 1s, dismiss sheet, reopen → note persists
4. Tap existing note → enters edit mode with text pre-filled
5. Type 240 chars → counter appears; 270+ → counter turns red; 281st char rejected
6. Clear note entirely → "Add a note…" placeholder returns after dismiss+reopen
7. Back button while editing → closes field, sheet stays open, note persists
8. Tap "Add a note…" on a card near the bottom of the list → keyboard opens, card scrolls into view above keyboard
9. While editing a note, change vibe filter → editing closes gracefully, no crash

### Notes

- `SavedPoiCard` note area sits between `areaName` text and the existing `Spacer(8.dp)`. Card height grows dynamically — no fixed height is set on the card body Column, so note area expansion is free.
- `noteText` local state is keyed on `poi.id` and `poi.userNote` via `remember(poi.id, poi.userNote)` — ensures text resets correctly if the list reorders or if the note is updated externally (e.g. DB flow emits a new value).
- `SavedPlacesScreen` already has a `PlatformBackHandler` for sheet dismiss. The note-editing handler must be composed AFTER the sheet-dismiss handler — in Compose, last-composed = highest priority. This ensures back press dismisses the keyboard/field first, then a second back press dismisses the sheet.
- The "map-side" context: `SavedPlacesScreen` is already rendered as a full-screen overlay on `MapScreen` (line 503 of MapScreen.kt, `AnimatedVisibility`). Updating `SavedPoiCard` covers both the dedicated Saved Places tab and the map overlay — no duplicate work needed.

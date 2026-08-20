# Scribe Editor — Current Shortcomings & Root Cause Analysis

## Overview
This document details the architectural and implementation flaws identified in the current Scribe codebase that cause functional breakage and performance regressions during large document editing.

---

## 1. Multi-Paragraph Selection Breakage (LazyColumn Virtualization Flaw)
- **Current Implementation**: `ScribeEditor` wraps each paragraph in its own `ScribeEditorLine` inside a `LazyColumn`.
- **Root Cause**: In Jetpack Compose (and Android in general), every `BasicTextField` / `Text` composable is an isolated Layout Node with its own localized text selection bounds and gesture detectors.
- **Consequence**:
  - Dragging a selection handle cannot cross item boundaries in a `LazyColumn`.
  - When a user tries to drag a selection handle past the first or last character of a line, the selection is clamped to `[0, lineLength]`.
  - Triple-tap (select paragraph) or long-press drag across multiple paragraphs is structurally impossible.
  - Clipboard operations (Copy, Cut, Share) cannot capture multi-paragraph spans through native text handles.

---

## 2. Keyboard Churn, IME Destruction & Focus Flickering
- **Current Implementation**: Only the active paragraph renders a live `BasicTextField`, while all other paragraphs render `Text()`. When the user taps a different line, `activeLineIndex` is updated, destroying the previous line's `BasicTextField` and instantiating a new one on the tapped line.
- **Root Cause**:
  - In Android's Input Method Framework (IMF), `InputConnection` is bound to a single focused View / FocusTargetNode.
  - When the active line index shifts, the existing `BasicTextField` loses focus and unmounts its `InputConnection`. The new `BasicTextField` requests focus and invokes `LocalSoftwareKeyboardController.show()` or `InputMethodManager.restartInput()`.
- **Consequence**:
  - The software keyboard flickers, closes, and reopens on every line transition.
  - Ongoing IME composition states (Gboard predictive text, swipe typing, Pinyin/Japanese input, autocorrect spans) are abruptly cancelled, losing candidate words.
  - Noticeable input lag / stutter on every tap to navigate paragraphs.

---

## 3. Broken Word Count, Character Count, and Database Autosave
- **Current Implementation**: In `MainEditorScreen.kt`, word and character count updates and database synchronization rely on:
  ```kotlin
  LaunchedEffect(engine) {
      snapshotFlow { engine.exportPlainText() }
          .debounce(500)
          .collect { text ->
              editorVm.onContentChanged(text)
          }
  }
  ```
- **Root Cause**:
  - `snapshotFlow` only detects reads of Compose `State` objects (such as `mutableStateOf`, `mutableIntStateOf`).
  - `engine.exportPlainText()` reads directly from `buffer.asString()`, which is a raw Piece Table in memory and not a Compose `State`.
  - Because no Compose `State` is read inside the lambda, `snapshotFlow` never emits after its initial evaluation.
- **Consequence**:
  - `editorVm.onContentChanged(text)` is never called during user typing.
  - `editorVm.wordCount` and `editorVm.charCount` remain static.
  - `editorVm.activeNote` content is not persisted to the Room database during active writing sessions.

---

## 4. Disconnected State & Format Bar Failure
- **Current Implementation**:
  - The formatting bars (Bold, Italic, Heading, Quote, Bullet list) and menu actions in `MainEditorScreen.kt` query `engine.textFieldState.selection` and call `engine.textFieldState.edit { ... }`.
  - However, in the virtualized line model, typing edits occur inside per-line local `rememberTextFieldState` instances inside `ScribeEditorLine`.
- **Root Cause**:
  - `engine.textFieldState` is not the active input buffer while the user is typing in `ScribeEditorLine`.
- **Consequence**:
  - Formatting toolbar buttons operate on stale selection or empty ranges.
  - Selection queries in `MainEditorScreen` (e.g., `hasSelection`, `activeSelection`) always read `TextRange(0, 0)` or outdated offsets.

---

## 5. Performance Regressions in the Initial Implementation
- **Piece Table Self-Destruction**: In the initial `ScribeEditorEngine.init`, a `snapshotFlow` was deleting and re-inserting the entire document into the piece table on every keystroke (`buffer.delete(0, buffer.length())` followed by `buffer.insert(0, currentText)`), causing $O(N)$ string allocations and garbage collection pressure.
- **Global Span Scanning**: `ScribeOutputTransformation` was performing full scans over all format spans and search matches for the whole document on every redraw.

---

## Summary of Critical Fix Requirements
1. Unified input connection and global selection range that natively spans multiple paragraphs without IME tear-down.
2. Direct reactive connection between document mutations, Compose snapshot state, `EditorViewModel.wordCount`, and Room persistence.
3. Fast $O(\log N)$ or viewport-bounded span and layout queries so large documents (50k–100k+ words) render smoothly at 60/120 FPS.
4. Seamless integration with formatting toolbars, undo/redo stacks, and find-replace engine.

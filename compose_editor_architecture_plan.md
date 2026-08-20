# Jetpack Compose Prose Editor Architecture Plan

## 1. Executive Summary
To achieve the fluid, lag-free performance of Sora Editor while fixing the functional regressions (multi-paragraph selection failure, keyboard blinking/churn, broken word counts, disconnected toolbar state), Scribe will adopt a **Unified Core Input & Spatial Transformation Architecture** in pure Jetpack Compose.

---

## 2. Architectural Comparison: Sora Editor vs. Compose Implementation

| Feature | Sora Editor (Custom Android View) | Scribe Compose Native Equivalent |
|---|---|---|
| **Input Connection** | Single persistent `EditorInputConnection` across entire document | Unified `BasicTextField` with Foundation `TextFieldState` |
| **Selection Across Paragraphs** | Global continuous character offsets `[selStart, selEnd]` | Native Compose `TextFieldState.selection` across all lines |
| **Keyboard / IME Lifecycle** | Zero IME teardown on cursor movement | Single FocusTargetNode; keyboard stays open and fluid |
| **Formatting & Spans** | Line-bounded syntax token query during `onDraw` | `OutputTransformation` with interval-bounded `SpanStyle` resolution |
| **Large Doc Performance** | Incremental piece table + layout cache | High-performance gap-buffer in `TextFieldState` + debounced background analysis |
| **Word Count & Stats** | Async worker thread on document mutation | Reactive `snapshotFlow { textFieldState.text }` on `Dispatchers.Default` |
| **Room DB Autosave** | Debounced file sync on background thread | Reactive `snapshotFlow` triggering `EditorViewModel.onContentChanged()` |

---

## 3. Detailed Component Plan

### A. Unified Text Engine (`ScribeEditorEngine.kt`)
1. **Single Source of Truth**:
   - `textFieldState: TextFieldState` holds the active editing text, selection range, and IME composition.
   - `DocumentBuffer` (Piece Table) is synchronized incrementally without full-buffer deletions or full-string reallocations.
2. **Reactive Mutation & Stats Engine**:
   - Listen to `snapshotFlow { textFieldState.text }` with debouncing (300ms for word/char count, 500ms for outline and database autosave).
   - Offload word counting regex and markdown outline parsing to `Dispatchers.Default`.
3. **Robust Undo/Redo**:
   - Record differential edits (`Edit.Insert`, `Edit.Delete`, `Edit.Compound`) and cursor positions in `UndoManager`.
   - Undo/Redo updates `textFieldState` atomically via `textFieldState.edit { }`.

### B. High-Performance OutputTransformation (`ScribeOutputTransformation.kt`)
1. **Interval-Filtered Spans**:
   - Query `FormatRegistry` for active spans with efficient interval intersection checks.
   - Apply typography hierarchy (`FontWeight`, `FontStyle`, `FontSize`, `TextDecoration`, `Color`, `LineHeight`) cleanly.
2. **Search Match Highlighting**:
   - Highlight active match with golden glow (`#FFFFD54F`) and secondary matches with soft amber (`#66FFE082`).

### C. Unified Viewport & Scroll Container (`ScribeEditor.kt`)
1. **Single BasicTextField in Scrollable Container**:
   - A single `BasicTextField` composable configured with `lineLimits = TextFieldLineLimits.MultiLine(minHeightInLines = 1)`.
   - Smooth vertical scrolling with `rememberScrollState()`.
   - Native multi-paragraph selection handles, magnifier, and context menu.
2. **Floating Quick Actions & Selection Context Menu**:
   - Seamless Copy, Cut, Paste, Select All, and Prose Formatting.
3. **Keyboard & IME Reliability**:
   - `KeyboardOptions` configured with `capitalization = Sentences`, `autoCorrectEnabled = true`, `imeAction = Default`.

### D. Main Screen & ViewModel Synchronization (`MainEditorScreen.kt`)
1. **Live Word Count & Autosave**:
   - Bind `editorVm.wordCount` directly or sync from `engine.wordCount`.
   - Ensure `snapshotFlow { engine.textFieldState.text.toString() }` triggers `editorVm.onContentChanged()` cleanly.
2. **Toolbar & Format Bar Integration**:
   - Connect Bold, Italic, Underline, Heading 1-3, Quote, and List buttons directly to `engine.toggleFormat()` / `engine.applyFormatWrap()` using the active `engine.textFieldState.selection`.

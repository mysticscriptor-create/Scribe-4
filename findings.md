# Scribe Editor Architectural Audit & Optimization Findings (August 2026)

This document contains a file-by-file audit of all editor and engine components in Scribe, identifying architectural bottlenecks, edge-case bugs, GC/memory pressures, and outlining state-of-the-art solutions based on modern high-performance mobile text editors (Sora-Editor, Markor, Monaco/VS Code Piece Tree, Compose 1.12+ virtualization).

---

## 1. `DocumentBuffer.kt` (Core Piece Table Buffer)
### Current Implementation Analysis
- **Structure**: Piece Table with `original: String`, `appendBuf: StringBuilder`, and `pieces: MutableList<Piece>`.
- **Strengths**: Provides constant-time append insertions and non-destructive piecewise slicing.

### Issues Identified
1. **Unused Locality Cache in Piece Lookup**: Lines 40-41 declare `cachedPieceIndex` and `cachedPieceOffset`, but `findPieceAt(pos: Int)` always executes an $O(N)$ linear loop starting from `currentOffset = 0`. With 5,000+ pieces, this causes unnecessary loop iterations on every `charAt` and `substring`.
2. **Full Document String Allocation in Search**: `search()` calls `val fullText = asString()`, which materializes a full concatenated String in memory. On a 100k+ word manuscript, this allocates 600 KB to 2 MB of heap memory per search invocation.
3. **Absence of `CharSequence` Interface**: `DocumentBuffer` does not implement `CharSequence`, preventing zero-copy integration with standard regex matchers, string utilities, and text indexers.
4. **Allocation Churn on Deletions**: `delete()` constructs multiple intermediate `ArrayList` collections (`newPieces`, `remainingStarts`, `keptIndices`, `newKeys`) on every backspace or cut operation.

### Modern Solution (August 2026 Standards)
- Implement `CharSequence` directly on `DocumentBuffer` with direct piecewise indexing ($O(1)$ amortized access).
- Enhance `findPieceAt` with directional locality caching: if `pos >= cachedPieceOffset`, start scanning from `cachedPieceIndex` instead of index 0.
- Eliminate full-text allocations in search by streaming over line chunks or using a virtual `CharSequence` regex matcher.
- Optimize `delete` and `insert` line cache updates with zero-allocation primitive array shifts.

---

## 2. `FastLineIndexer.kt` (Zero-Allocation Line Indexer)
### Current Implementation Analysis
- **Structure**: Primitive `IntArray` growing by $2\times$ factor with binary search lookups (`lineIndexForOffset`).

### Issues Identified
1. **Full Rescan on Incremental Typing**: `index(text: CharSequence)` re-scans the entire document from offset 0 to `text.length`. For huge texts (100k+ words), typing a character at the end re-traverses all previous 500,000 characters unnecessarily.
2. **Missing In-Place Update API**: Needs an incremental update method (`update(offset, deleteLen, insertLen, newText)`) to shift subsequent line offsets in $O(\text{remaining lines})$ without scanning unchanged preceding lines.

### Modern Solution (August 2026 Standards)
- Implement fast-path incremental updates for single-line character additions/deletions.
- Retain primitive binary search for $O(\log L)$ line resolution.
- Guard against concurrent modification when accessed across background workers and the UI layout pass.

---

## 3. `FindReplaceEngine.kt` (Reactive Find & Replace)
### Current Implementation Analysis
- **Structure**: Reactive search engine utilizing `DocumentBuffer.forEachLine`.

### Issues Identified
1. **Compose `TextFieldState` Desynchronization on Replacements**: `replaceAll` and `replaceCurrent` modify `buffer.delete(...)` and `buffer.insert(...)` directly on the `DocumentBuffer`, but the UI editor is driven by Compose's `TextFieldState`. Without atomic state editing, the canvas UI could lag or fail to reflect batch replacements.
2. **Cross-Line Pattern Matches**: Line-by-line searching fails for multiline regex queries (e.g. queries matching `\n` or cross-paragraph patterns).
3. **Snapshot Mutation Flooding**: Clearing and iteratively populating `mutableStateListOf` triggers multiple snapshot notifications.

### Modern Solution (August 2026 Standards)
- Route all text modifications through `ScribeEditorEngine.state.edit { ... }` or provide bidirectional atomic sync.
- Perform batch search results updates with a single immutable list snapshot.
- Support multiline matching when requested.

---

## 4. `FormatRegistry.kt` (Prose Typography & Formatting Spans)
### Current Implementation Analysis
- **Structure**: Span-based formatting registry supporting `BOLD`, `ITALIC`, `UNDERLINE`, `STRIKETHROUGH`, `H1`, `H2`, `H3`, `BLOCKQUOTE`, and `SCENE_SEPARATOR`.

### Issues Identified
1. **Per-Frame GC Pressure in `spansIn`**: `spansIn(start, end)` allocates a new `ArrayList<FormatSpan>` on every call. During 120Hz scrolling across 40 visible lines, this creates up to 4,800 list allocations per second.
2. **Fragmented Adjacent Spans**: Adjacent spans of identical type (e.g. `BOLD [0..5]` and `BOLD [5..10]`) are not coalesced, causing redundant span segments during measuring and rendering.
3. **Scene Separator Styling**: `FormatType.SCENE_SEPARATOR` returns an empty `SpanStyle()`, leaving line layout without visual separation.

### Modern Solution (August 2026 Standards)
- Add zero-allocation inline visitation: `inline fun forEachSpanIn(start: Int, end: Int, action: (FormatSpan) -> Unit)`.
- Coalesce adjacent and overlapping spans of the same type after insert/delete operations.
- Render scene breaks with stylized literary ornament dividers (`* * *`) and subtle geometry in the canvas.

---

## 5. `ProseAnalysisEngine.kt` (Deep Literary & Readability Analysis)
### Current Implementation Analysis
- **Structure**: Background analysis engine computing Flesch-Kincaid, Gunning Fog, Coleman-Liau, Lexical Diversity, Dialogue vs. Narrative ratios, and Monotony metrics.

### Issues Identified
1. **Lack of Cooperative Cancellation in Loops**: Background analysis on 100k+ words does not check `coroutineContext.ensureActive()` inside tokenization and n-gram loops. When the user types quickly, cancelled jobs continue consuming CPU cycles in the background.
2. **Excessive String Token Allocations**: `splitSentences` and `extractWords` allocate new `String` objects for every word and sentence.

### Modern Solution (August 2026 Standards)
- Insert `coroutineContext.ensureActive()` inside outer loops to guarantee instantaneous cancellation on new keystrokes.
- Use index-based sentence and word scanners that inspect character ranges without allocating substrings unless necessary.

---

## 6. `ScribeEditorEngine.kt` (Unified Engine Architecture)
### Current Implementation Analysis
- **Structure**: Bridges Compose `TextFieldState`, `DocumentBuffer`, `FastLineIndexer`, `FormatRegistry`, `UndoManager`, and background analysis workers.

### Issues Identified
1. **String Allocation on Keystroke Debounce**: `snapshotFlow { state.text.toString() }` calls `.toString()` on every keystroke, allocating 100k+ word strings into the heap before debouncing.
2. **Disconnected Undo for Formatting**: `UndoManager` records text edits (`Edit`), but formatting operations (`FormatEdit`) are not tracked in the undo history. Toggling Bold/Heading and pressing Undo only affects text, not formatting spans.
3. **Full Outline Recalculation**: Outline extraction iterates through all lines on every debounce cycle without line skipping.

### Modern Solution (August 2026 Standards)
- In `snapshotFlow`, inspect `state.text` as `CharSequence` directly and only convert to String when necessary.
- Unify `UndoEntry` to record both `bufferEdit: Edit?` and `formatEdit: FormatEdit?`, enabling seamless text and formatting undo/redo.
- Add `coroutineContext.ensureActive()` to outline and analysis pipelines.

---

## 7. `SerializedDocument.kt` (Document Persistence)
### Current Implementation Analysis
- **Structure**: KotlinX Serialization schema for documents (`version = 2`, `plainText`, `spans`).

### Issues Identified
1. **Boundary Clamping on Corrupted Imports**: If spans from external sources exceed `plainText.length`, index bounds errors could occur if not sanitized.

### Modern Solution (August 2026 Standards)
- Sanitize and clamp all span boundaries (`start.coerceIn(0, plainText.length)`, `end.coerceIn(start, plainText.length)`) upon loading.

---

## 8. `UndoManager.kt` (Time-Grouped History Manager)
### Current Implementation Analysis
- **Structure**: 200-step dual-deque undo/redo manager with 500ms typing grouping.

### Issues Identified
1. **Missing Format Span Undo/Redo**: Format modifications cannot be undone or redone.
2. **Grouping Overwrites Across Format Changes**: Rapid typing followed immediately by a format change should not merge across the format boundary.

### Modern Solution (August 2026 Standards)
- Add `formatEdit: FormatEdit? = null` to `UndoEntry`.
- Execute inverse and forward format operations in `undo()` and `redo()`.

---

## 9. `ScribeEditor.kt` (Virtualized Canvas Prose Renderer)
### Current Implementation Analysis
- **Structure**: Viewport-bounded Canvas editor using `LineLayoutTracker`, `ScribeLineCache`, `rememberTextMeasurer`, and virtual scrolling.

### Issues Identified
1. **Cumulative Coordinate Shifts During Live Typing**: When a line height is updated in `LineLayoutTracker`, `dirty = true` causes subsequent line positions to require offset recalculation.
2. **Cursor Blink State Reset**: When the user moves the cursor or types, `cursorVisible` should immediately reset to `true` to provide instant feedback.
3. **Double-Tap Word Selection Boundary**: Punctuation and quotes attached to words should be trimmed cleanly for intuitive word selection.
4. **Drag Selection Viewport Bounds**: Long-press drag selection needs smooth boundary clamping across line bounds.

### Modern Solution (August 2026 Standards)
- Ensure `LineLayoutTracker.recomputeOffsets()` is called before line drawing passes.
- Reset cursor visibility phase on selection changes for responsive cursor rendering.
- Render rich prose headings, blockquotes, and scene breaks with distinct typographical hierarchy and visual cues.

---

## 10. `ScribeInputTransformation.kt` (Typographical Input Processing)
### Current Implementation Analysis
- **Structure**: Smart curly quote transformation and bracket auto-pairing.

### Issues Identified
1. **Missing Standard Prose Em-Dash & Ellipsis Auto-Conversions**: Double hyphens (`-- `) and triple dots (`...`) are common prose conventions that should convert to em-dash (`—`) and ellipsis (`…`).

### Modern Solution (August 2026 Standards)
- Add smart transformations for `-- ` $\to$ `— ` and `...` $\to$ `…`.
- Keep single and double smart quote transformations accurate and instant.

---

## 11. `ScribeLineCache.kt` (Per-Line TextLayoutResult Cache)
### Current Implementation Analysis
- **Structure**: `LinkedHashMap` LRU cache keyed on `(lineIndex, contentHash)`.

### Issues Identified
1. **Span Hash Precision**: `contentHash` should compute quickly without boxing.

### Modern Solution (August 2026 Standards)
- Optimize `contentHash` with fast primitive hash combinations.
- Keep cache capacity bounded (1024 entries) to avoid memory leaks while guaranteeing 60fps/120fps scrolling.

---

## 12. `MainEditorScreen.kt` & `EditorViewModel.kt` (Editor Host & State Synchronization)
### Current Implementation Analysis
- **Structure**: Orchestrates top bar, bottom bar, companion drawer, word count pill, and auto-save.

### Issues Identified
1. **Debounced Auto-Save Efficiency**: Content sync between `ScribeEditorEngine` and `EditorViewModel` should use fast revision counters without redundant object serialization.
2. **Panel Gesture Smoothness**: `AnchoredDraggableState` gestures must not conflict with vertical scrolling in the editor canvas.

### Modern Solution (August 2026 Standards)
- Ensure debounced content change emissions pass cleanly to `EditorViewModel.onContentChanged`.
- Maintain decoupled background auto-save and statistics computation.

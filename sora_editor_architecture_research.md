# Sora Editor Architecture & Optimization Deep-Dive

## 1. Overview of Sora Editor (io.github.rosemoe.sora)
Sora Editor is a high-performance Android code/prose editor capable of smoothly editing and rendering documents exceeding hundreds of thousands of words (and millions of characters) at 60/120 FPS. It achieves this by bypassing standard Android multi-widget hierarchies and implementing a custom single-view text rendering engine.

---

## 2. Core Architectural Pillars

### A. Data Layer: `Content` & `ContentLine`
- **Line-Indexed Piece Storage**:
  - Instead of storing a monolithic `String` or `StringBuilder`, Sora Editor maintains a structured line table (`Content` containing `ContentLine` objects or indexed character buffers).
  - Lines are indexed such that character offset to `(line, column)` conversions and reverse lookups run in $O(\log L)$ time using binary search over prefix lengths.
- **Incremental Text Mutation**:
  - Inserting or deleting text only mutates the affected `ContentLine`s and updates downstream line offset indices incrementally.
  - No complete document string copying or re-parsing occurs on single keystrokes.

### B. Layout & Measurement Engine: `AbstractLayout` / `LineBreakLayout`
- **Cached Line Heights and Break Points**:
  - In word-wrap mode, lines that wrap into multiple display rows are broken and cached in a layout structure (`LineBreakLayout`).
  - Line heights and display row indices are stored in a Fenwick tree (Binary Indexed Tree) or segment tree, enabling:
    - Vertical pixel $Y \to \text{Line Index}$ lookup in $O(\log N)$ time.
    - Line Index $\to$ Vertical pixel $Y$ computation in $O(\log N)$ time.
- **Lazy & Incremental Measurement**:
  - Only lines that undergo text mutations or style changes have their width/height re-measured. Unchanged lines reuse cached dimensions.

### C. Viewport-Only Rendering Pipeline (`onDraw`)
- **Spatial Clipping (Frustum Culling for Text)**:
  - When Android calls `onDraw(Canvas)` on the single `CodeEditor` View:
    1. It reads `scrollY` and `viewportHeight`.
    2. It computes the **first visible line** and **last visible line** using the layout index:
       $$\text{firstVisibleLine} = \text{layout.getLineIndexForY}(\text{scrollY})$$
       $$\text{lastVisibleLine} = \text{layout.getLineIndexForY}(\text{scrollY} + \text{viewportHeight})$$
    3. It iterates **ONLY** from `firstVisibleLine` to `lastVisibleLine` (typically 30–60 lines on screen).
    4. For each visible line, it resolves syntax/prose spans and draws text directly via `canvas.drawText()` / `TextLayout`.
    5. It draws selection rectangles (`canvas.drawRect`) and cursor lines only if they fall within the visible region.
  - Zero memory is allocated for off-screen lines, and zero draw calls are issued for invisible content.

### D. Single Persistent Input Connection (`EditorInputConnection`)
- **No IME Churn**:
  - The editor is a single Android `View`. The `InputConnection` created with the software keyboard (Gboard, Samsung Keyboard, etc.) remains **alive and permanent** throughout the entire editing session.
  - Moving the cursor across lines, paragraphs, or pages simply sends cursor updates (`updateSelection`) to the IME rather than tearing down and reconstructing the input connection.
  - Keyboard never blinks, closes, or stutters.

### E. Unified Global Selection & Touch Gestures (`EditorTouchEventHandler`)
- **Continuous Document Coordinates**:
  - Touch coordinates $(X, Y)$ are mapped directly to global document character offsets:
    $$\text{offset} = \text{layout.getOffsetForPosition}(X + \text{scrollX}, Y + \text{scrollY})$$
  - Text selection is defined as a continuous character range `[selectionStart, selectionEnd]`.
  - Dragging selection handles seamlessly spans across multiple lines, paragraphs, and screen bounds with auto-scrolling at viewport edges.

---

## 3. Key Takeaways for High-Performance Prose Editing
1. **Never split a document into separate interactive text fields** (e.g., one field per paragraph). Doing so forfeits continuous text selection, breaks IME lifecycle, and introduces massive recomposition churn.
2. **Decouple Document State from Display Text**: Keep a fast data model for document manipulation, undo/redo, and search, while feeding an optimized single input system.
3. **Bound Span Calculations to What is Active / Transformed**: Eliminate $O(N)$ document traversals on every keystroke.
4. **Asynchronous Background Processing**: Compute word counts, statistics, and outline trees on background coroutine dispatchers with debouncing.

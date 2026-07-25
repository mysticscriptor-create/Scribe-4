# Scribe Android modernization plan

Reference: `IMPLEMENTATION_REFERENCE.md`

## Section 1 — dependency and build modernization

- [x] Update AGP 9.1.1, Kotlin 2.4.10, KSP 2.3.9, Gradle 9.5.1, Java 21, SDK 36, and CI toolchain (JDK 21 + Gradle 9.5.1).
- [x] Update Compose BOM 2026.06.00, Activity 1.13.0, AndroidX libs, coroutines 1.10.2, Room 2.8.4, Coil 3.5.0, Lottie 6.7.1, Gson 2.13.2, minSdk 23.
- [x] Remove kotlin-android plugin, forced stdlib resolution, room-ktx, and `[plugins]` section from libs.versions.toml.
- [x] Update all Coil imports to coil3.*; verify no coil2 imports remain.
- [x] Fix build errors: missing setValue imports, IntRect→Rect conversion, Coil3 BitmapImage API, Canvas DrawScope color extraction.

## Section 2 — UI fixes and visual refinement

- [x] Format bar shown only when keyboard (IME) is visible.
- [x] Format bar uses frosted Surface with pill-style buttons (no OutlinedButton borders).
- [x] Editor activity uses adjustPan — background image does not shift with keyboard.
- [x] Dropdown menus use semi-opaque surface (0.96f alpha) for readability over backgrounds.
- [x] ModalDrawerSheet uses semi-opaque surface container color.
- [x] Auto text/accent color fallback based on background image luminance (ScribeTheme.kt).
- [x] Gradient overlays removed from Books and Notes list tabs.
- [x] NavigationBar: 60dp height, explicit theme accent for indicator/selected icon via LocalAppTheme.
- [x] SegmentedButton in Statistics and Wordmap tabs: explicit theme accent color via LocalAppTheme.
- [x] SecondaryTabRow indicator uses theme accent color.
- [x] Stat cards, chart card, and daily goal progress bar use themed surface colors.
- [x] goalProgress wired from EditorViewModel (was hardcoded to 500-word goal).

## Section 3 — live theme updates

- [x] ScribeComposeTheme resolves themes from reactive DataStore flows (collectAsState).
- [x] EditorViewModel observes active theme flow via collectLatest (no more one-shot loadTheme).
- [x] ThemeListActivity onResume reload removed (now redundant).

## Section 4 — background-aware adaptive text

- [x] LocalBgAnalysisBitmap and LocalScreenSize CompositionLocals added to ScribeTheme.
- [x] 32×32 analysis bitmap decoded asynchronously per background URI in ScribeComposeTheme.
- [x] AdaptiveColor.kt: regionLuminance + contrastingTextColor helpers + rememberAdaptiveTextColor composable.
- [x] Applied in HomeScreen (title, icons), MainEditorScreen (top bar title), MainStatisticsTabContent (tab labels), BookScreen (heading).

## Section 5 — audit and delivery

- [x] Deprecated Icons.Default.ArrowBack → Icons.AutoMirrored.Filled.ArrowBack across all screens.
- [x] Deprecated Divider() → HorizontalDivider() in SettingsScreen.
- [x] Null-safe rename dialog in MainEditorScreen (captured val, no !! dereference).
- [x] goalProgress sourced from EditorViewModel.goalProgress LiveData (removed hardcoded 500).
- [x] Verified: no old coil imports, no room-ktx, no kotlin-android plugin, no resolutionStrategy block, no [plugins] in toml, Java 21 throughout.
- [x] All changes committed to built-in git; pushed to GitHub.

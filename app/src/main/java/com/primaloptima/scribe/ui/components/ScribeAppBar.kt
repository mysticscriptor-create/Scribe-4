package com.primaloptima.scribe.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.primaloptima.scribe.ui.theme.FrostedBarContent
import com.primaloptima.scribe.ui.theme.LocalAccentColor
import com.primaloptima.scribe.ui.theme.LocalBarBlurBitmap
import com.primaloptima.scribe.ui.theme.LocalHazeState
import com.primaloptima.scribe.ui.theme.LocalOneShotBitmap
import com.primaloptima.scribe.ui.theme.frostedBar
import com.primaloptima.scribe.ui.theme.rememberAdaptiveTextColor


// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// SCRIBE APP BAR SYSTEM  —  ScribeAppBar.kt
//
// One file. Every top bar and bottom bar in the app comes from here.
// Change a token in ScribeBarTokens and it updates every screen at once.
//
// Architecture (three layers — mirrors ScribeFab.kt and Cards.kt):
//
//   LAYER 1 — ScribeBarTokens
//       All sizing, spacing, and visual constants in one place.
//       Change bar height, content padding, border style here.
//
//   LAYER 2 — ScribeTopBarSurface / ScribeBottomBarSurface
//       The raw frosted-glass + border surfaces.
//       Handle all visual chrome: frosted glass, gradient border, top shine.
//       Callers never touch hazeState, accentColor, or blur bitmaps.
//
//   LAYER 3 — Ready-to-use bar variants (all built on the surfaces above)
//
//     Top bars:
//       ScribeTopBar           Standard screen bar — title + optional nav icon + actions
//       ScribeEditorTopBar     Editor bar — clickable title + always-present nav + actions
//
//     Bottom bars:
//       ScribeNavBar           Home screen tab bar — list of ScribeNavItem entries
//
// How screens use this:
//
//   Every screen passes ScribeTopBar (or ScribeEditorTopBar) as the topBar
//   slot of its Scaffold.  HomeScreen also passes ScribeNavBar as bottomBar.
//   The screen only supplies WHAT to show (title string, icon list, onClick
//   callbacks).  All sizing, frosted glass, borders, and inset handling live
//   here — invisible to the caller.
//
// Inset strategy:
//   Bars use windowInsets = WindowInsets(0.dp) on the underlying M3 component
//   so M3 never adds its own status/nav bar padding.  The surfaces manually
//   add .statusBarsPadding() / .navigationBarsPadding() OUTSIDE the fixed
//   content-height box.  This is the same approach used by HomeScreen's bars
//   and is required to keep the frosted glass correctly sized:
//     • The glass region = BarHeight (content) + system inset (transparent area above/below)
//     • Setting a fixed height ONLY on the content Row keeps the tap area correct
//       while still letting the surface extend behind the status/nav bar.
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━


// ── LAYER 1 — Design tokens ───────────────────────────────────────────────────

object ScribeBarTokens {

    // ── Sizing ────────────────────────────────────────────────────────────────

    /** Height of the actual content row (icons + title text). System insets are added on top. */
    val TopBarContentHeight = 48.dp

    /** Height of the navigation bar content row. System inset is added below. */
    val NavBarContentHeight = 52.dp

    /**
     * Horizontal padding inside the bar — gap between the bar edge and the
     * first/last icon.  Smaller than M3 default (which adds 4dp per IconButton
     * plus internal padding), giving a tighter, more compact feel.
     */
    val ContentPaddingHorizontal = 4.dp

    /**
     * Padding between the title text and the navigation icon on its left.
     * Keeps the title visually close to the back button without crowding it.
     */
    val TitleStartPadding = 2.dp

    // ── Shape ─────────────────────────────────────────────────────────────────

    /** Bars are full-width rectangles — no corner rounding needed. */
    val Shape = RoundedCornerShape(0.dp)

    // ── Border — matches FAB and Card accent border exactly ───────────────────

    /**
     * Thickness of the gradient border line.
     * Same as ScribeFab (0.7.dp) and ScribeCard (0.7.dp).
     */
    val BorderWidth: Dp = 0.7.dp

    /**
     * Alpha of the accent colour at the bright end of the border gradient.
     * Top bar uses a bottom border (bright → transparent downward).
     * Nav bar uses a top border (bright → transparent upward).
     * Same value as ScribeCard.AccentBorderAlpha.
     */
    const val BorderAccentAlpha = 0.22f

    // ── Shine — same as Cards.kt ShineAlpha ──────────────────────────────────

    /** Very faint white sheen across the top of the bar surface. */
    const val ShineAlpha = 0.06f

    // ── Icon sizes ────────────────────────────────────────────────────────────

    /** Nav icon and action icon size inside the top bar. */
    val IconSize = 22.dp

    /** Navigation-tab icon size inside the bottom nav bar. */
    val NavTabIconSize = 20.dp

    /** Label font size for bottom nav tab labels. */
    val NavTabLabelSize = 9.sp
}


// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// LAYER 2A — ScribeTopBarSurface  (raw frosted top bar surface)
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

/**
 * The frosted glass surface that every top bar in the app sits on.
 *
 * Handles:
 *  • Frosted glass (Haze on API 31+, pre-blurred bitmap on older devices)
 *  • Gradient accent border along the bottom edge
 *  • Faint white shine along the top edge
 *  • Status-bar inset: a transparent region above [contentHeight] lets the
 *    frosted glass extend behind the status bar without pushing content up
 *  • LocalContentColor set via FrostedBarContent so icons/text inherit colour
 *
 * Callers put their content (title row, icons) in the [content] slot.
 * They never touch hazeState, bitmap locals, or border/shine code.
 */
@Composable
private fun ScribeTopBarSurface(
    contentHeight: Dp = ScribeBarTokens.TopBarContentHeight,
    content: @Composable RowScope.() -> Unit,
) {
    val hazeState   = LocalHazeState.current
    val accentColor = LocalAccentColor.current

    // Wire LocalOneShotBitmap → LocalBarBlurBitmap so frostedBar picks up the
    // pre-blurred wallpaper on pre-API-31 devices (same pattern as HomeScreen).
    CompositionLocalProvider(LocalOneShotBitmap provides LocalBarBlurBitmap.current) {
        FrostedBarContent {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    // Frosted glass covers status bar + content height
                    .frostedBar(hazeState)
                    // Gradient border along the bottom edge of the bar
                    .border(
                        width = ScribeBarTokens.BorderWidth,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                accentColor.copy(alpha = ScribeBarTokens.BorderAccentAlpha)
                            )
                        ),
                        shape = ScribeBarTokens.Shape
                    )
            ) {
                // Subtle top shine — identical to Cards.kt and ScribeFab.kt
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = ScribeBarTokens.ShineAlpha),
                                    Color.Transparent
                                )
                            )
                        )
                )

                // Content row: status bar space above, then the fixed-height content row
                Column {
                    // Transparent spacer that matches the status bar height —
                    // the frosted glass underneath shows through it correctly.
                    Spacer(modifier = Modifier.statusBarsPadding())

                    // Fixed-height content row — this is the only part that has tap targets
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(contentHeight)
                            .padding(horizontal = ScribeBarTokens.ContentPaddingHorizontal),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        content()
                    }
                }
            }
        }
    }
}


// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// LAYER 2B — ScribeBottomBarSurface  (raw frosted bottom bar surface)
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

/**
 * The frosted glass surface that every bottom bar in the app sits on.
 *
 * Mirrors [ScribeTopBarSurface] but for the bottom:
 *  • Gradient border along the TOP edge (bright at top, fades downward)
 *  • Navigation-bar inset: a transparent region below [contentHeight] lets
 *    the frosted glass extend behind the navigation bar
 *  • Shine along the top edge (same as top bar)
 */
@Composable
private fun ScribeBottomBarSurface(
    contentHeight: Dp = ScribeBarTokens.NavBarContentHeight,
    content: @Composable RowScope.() -> Unit,
) {
    val hazeState   = LocalHazeState.current
    val accentColor = LocalAccentColor.current

    CompositionLocalProvider(LocalOneShotBitmap provides LocalBarBlurBitmap.current) {
        FrostedBarContent {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .frostedBar(hazeState)
                    // Gradient border along the top edge of the bar
                    .border(
                        width = ScribeBarTokens.BorderWidth,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                accentColor.copy(alpha = ScribeBarTokens.BorderAccentAlpha),
                                Color.Transparent
                            )
                        ),
                        shape = ScribeBarTokens.Shape
                    )
            ) {
                // Subtle top shine
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = ScribeBarTokens.ShineAlpha),
                                    Color.Transparent
                                )
                            )
                        )
                )

                // Fixed-height content row, then nav bar transparent spacer below
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(contentHeight)
                            .padding(horizontal = ScribeBarTokens.ContentPaddingHorizontal),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        content()
                    }

                    // Transparent spacer that matches the navigation bar height
                    Spacer(modifier = Modifier.navigationBarsPadding())
                }
            }
        }
    }
}


// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// LAYER 3A — ScribeTopBar  (standard screen top bar)
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

/**
 * Data class for a single action icon in the top bar's trailing area.
 *
 * @param icon               The icon to display.
 * @param contentDescription Accessibility label.
 * @param onClick            What happens when the user taps it.
 */
data class ScribeBarAction(
    val icon: ImageVector,
    val contentDescription: String,
    val onClick: () -> Unit,
)

/**
 * Standard top bar used by most screens in the app.
 *
 * Slots:
 *  [title]          — String shown in the bar. Bold, single line, ellipsized.
 *  [navigationIcon] — Optional icon on the left (typically a back arrow or hamburger).
 *                     Pass null on root screens that have no back navigation.
 *  [onNavigationClick] — Called when [navigationIcon] is tapped.
 *  [actions]        — List of [ScribeBarAction] trailing icons (right side). Empty by default.
 *  [extraContent]   — Optional composable slot placed below the title row inside the
 *                     bar surface. Use for progress bars, find-bar, tab rows, etc.
 *                     The surface expands to fit it — no separate padding needed.
 *
 * Usage in a Scaffold:
 * ```kotlin
 * Scaffold(
 *     contentWindowInsets = WindowInsets.systemBars,
 *     topBar = {
 *         ScribeTopBar(
 *             title = "Settings",
 *             navigationIcon = Icons.AutoMirrored.Filled.ArrowBack,
 *             onNavigationClick = onBack,
 *             actions = listOf(
 *                 ScribeBarAction(Icons.Default.Search, "Search") { ... }
 *             )
 *         )
 *     }
 * ) { padding -> ... }
 * ```
 */
@Composable
fun ScribeTopBar(
    title: String,
    navigationIcon: ImageVector? = null,
    onNavigationClick: () -> Unit = {},
    actions: List<ScribeBarAction> = emptyList(),
    extraContent: (@Composable () -> Unit)? = null,
    titleContent: (@Composable (titleModifier: Modifier) -> Unit)? = null,
) {
    val (titleColor, titleModifier) = rememberAdaptiveTextColor(
        fallback = MaterialTheme.colorScheme.onSurface
    )

    ScribeTopBarSurface {
        // ── Navigation icon ──────────────────────────────────────────────────
        if (navigationIcon != null) {
            IconButton(
                onClick = onNavigationClick,
                modifier = Modifier.size(40.dp)
            ) {
                val (iconColor, iconMod) = rememberAdaptiveTextColor(
                    fallback = MaterialTheme.colorScheme.primary
                )
                Icon(
                    imageVector        = navigationIcon,
                    contentDescription = "Navigate back",
                    tint               = iconColor,
                    modifier           = iconMod.size(ScribeBarTokens.IconSize)
                )
            }
        }

        // ── Title ────────────────────────────────────────────────────────────
        val baseTitleModifier = titleModifier
            .weight(1f)
            .padding(
                start = if (navigationIcon != null) ScribeBarTokens.TitleStartPadding else 12.dp,
                end   = 4.dp
            )
        if (titleContent != null) {
            titleContent(baseTitleModifier)
        } else {
            Text(
                text       = title,
                fontWeight = FontWeight.Bold,
                fontSize   = 17.sp,
                maxLines   = 1,
                overflow   = TextOverflow.Ellipsis,
                color      = titleColor,
                modifier   = baseTitleModifier
            )
        }

        // ── Action icons ─────────────────────────────────────────────────────
        actions.forEach { action ->
            IconButton(
                onClick  = action.onClick,
                modifier = Modifier.size(40.dp)
            ) {
                val (iconColor, iconMod) = rememberAdaptiveTextColor(
                    fallback = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Icon(
                    imageVector        = action.icon,
                    contentDescription = action.contentDescription,
                    tint               = iconColor,
                    modifier           = iconMod.size(ScribeBarTokens.IconSize)
                )
            }
        }
    }

    // Extra content slot (progress bar, find bar, etc.) — rendered directly
    // below the surface, outside the fixed-height row but still inside the
    // overall topBar composable so Scaffold accounts for its height.
    extraContent?.invoke()
}


// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// LAYER 3B — ScribeEditorTopBar  (editor screen top bar)
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

/**
 * Specialised top bar for the editor screen.
 *
 * Differences from [ScribeTopBar]:
 *  • [title] is tappable (triggers note rename). Pass null to show "Scribe Editor".
 *  • Navigation icon is always shown (the left-drawer hamburger).
 *  • Supports a progress bar below the title row via [extraContent].
 *  • Has a visibility toggle: pass [visible] = false to collapse entirely (Zen mode).
 *
 * Usage:
 * ```kotlin
 * ScribeEditorTopBar(
 *     title        = activeNote?.name,
 *     onTitleClick = { showRenameDialog = true },
 *     onNavClick   = { scope.launch { drawerState.open() } },
 *     actions      = listOf(...),
 *     visible      = !zenMode,
 *     extraContent = {
 *         LinearProgressIndicator(progress = { goalProgress }, modifier = Modifier.fillMaxWidth().height(3.dp))
 *     }
 * )
 * ```
 */
@Composable
fun ScribeEditorTopBar(
    title: String?,
    onNavClick: () -> Unit,
    onTitleClick: () -> Unit = {},
    actions: List<ScribeBarAction> = emptyList(),
    visible: Boolean = true,
    navigationIcon: ImageVector,
    extraContent: (@Composable () -> Unit)? = null,
) {
    if (!visible) return

    val (titleColor, titleModifier) = rememberAdaptiveTextColor(
        fallback = MaterialTheme.colorScheme.onSurface
    )

    ScribeTopBarSurface {
        // ── Hamburger / drawer toggle ────────────────────────────────────────
        IconButton(
            onClick  = onNavClick,
            modifier = Modifier.size(40.dp)
        ) {
            val (iconColor, iconMod) = rememberAdaptiveTextColor(
                fallback = MaterialTheme.colorScheme.primary
            )
            Icon(
                imageVector        = navigationIcon,
                contentDescription = "Vault Explorer",
                tint               = iconColor,
                modifier           = iconMod.size(ScribeBarTokens.IconSize)
            )
        }

        // ── Tappable note title ──────────────────────────────────────────────
        Text(
            text       = title ?: "Scribe Editor",
            fontWeight = FontWeight.Bold,
            fontSize   = 17.sp,
            maxLines   = 1,
            overflow   = TextOverflow.Ellipsis,
            color      = titleColor,
            modifier   = titleModifier
                .weight(1f)
                .padding(start = ScribeBarTokens.TitleStartPadding, end = 4.dp)
                .clip(RoundedCornerShape(6.dp))
                .then(
                    if (title != null)
                        Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    else Modifier
                )
                .let { m ->
                    if (title != null) m else m
                }
        )

        // ── Action icons ─────────────────────────────────────────────────────
        actions.forEach { action ->
            IconButton(
                onClick  = action.onClick,
                modifier = Modifier.size(40.dp)
            ) {
                val (iconColor, iconMod) = rememberAdaptiveTextColor(
                    fallback = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Icon(
                    imageVector        = action.icon,
                    contentDescription = action.contentDescription,
                    tint               = iconColor,
                    modifier           = iconMod.size(ScribeBarTokens.IconSize)
                )
            }
        }
    }

    extraContent?.invoke()
}


// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// LAYER 3C — ScribeNavBar  (home bottom navigation bar)
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

/**
 * Data class for a single tab in [ScribeNavBar].
 *
 * @param icon               Icon shown in the tab.
 * @param label              Text label below the icon.
 * @param contentDescription Accessibility label (defaults to [label]).
 */
data class ScribeNavItem(
    val icon: ImageVector,
    val label: String,
    val contentDescription: String = label,
)

/**
 * Bottom navigation bar for the Home screen.
 *
 * Renders a row of tab items. The currently selected tab shows its icon tinted
 * with the accent colour; unselected tabs use a muted surface-variant colour.
 * No text labels are shown — just icons — keeping the bar compact.
 *
 * @param items         List of [ScribeNavItem] tab entries (3–5 items recommended).
 * @param selectedIndex Index of the currently active tab.
 * @param onTabSelected Called with the new index when a tab is tapped.
 *
 * Usage in a Scaffold:
 * ```kotlin
 * Scaffold(
 *     bottomBar = {
 *         ScribeNavBar(
 *             items = listOf(
 *                 ScribeNavItem(Icons.Default.Dashboard, "Dashboard"),
 *                 ScribeNavItem(Icons.Default.Book,      "Books"),
 *                 ScribeNavItem(Icons.Filled.StickyNote2,"Notes"),
 *                 ScribeNavItem(Icons.Default.BarChart,  "Stats"),
 *             ),
 *             selectedIndex = selectedNavTab,
 *             onTabSelected = { shellVm.selectTab(it) }
 *         )
 *     }
 * ) { padding -> ... }
 * ```
 */
@Composable
fun ScribeNavBar(
    items: List<ScribeNavItem>,
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
) {
    val accentColor = LocalAccentColor.current

    ScribeBottomBarSurface {
        items.forEachIndexed { index, item ->
            val isSelected = index == selectedIndex
            val iconColor  = if (isSelected) accentColor
                             else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(10.dp))
                    .then(
                        if (isSelected) Modifier.background(accentColor.copy(alpha = 0.10f))
                        else Modifier
                    )
                    .padding(0.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(10.dp))
                        .then(
                            Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
                        )
                ) {
                    IconButton(
                        onClick  = { onTabSelected(index) },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector        = item.icon,
                            contentDescription = item.contentDescription,
                            tint               = iconColor,
                            modifier           = Modifier.size(ScribeBarTokens.NavTabIconSize)
                        )
                    }
                    Text(
                        text     = item.label,
                        fontSize = ScribeBarTokens.NavTabLabelSize,
                        color    = iconColor,
                        maxLines = 1,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}

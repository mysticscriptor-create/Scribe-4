package com.primaloptima.scribe.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.primaloptima.scribe.ui.theme.LocalAccentColor
import com.primaloptima.scribe.ui.theme.LocalHazeState
import com.primaloptima.scribe.ui.theme.LocalSolidSurface
import com.primaloptima.scribe.ui.theme.frostedContainerColor
import com.primaloptima.scribe.ui.theme.frostedFab
import com.primaloptima.scribe.ui.theme.localHasBgImage


// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// SCRIBE FAB SYSTEM  —  ScribeFab.kt
//
// One file. Every FAB in the app comes from here.
//
// Architecture (three layers):
//
//   LAYER 1 — ScribeFabTokens
//       Design constants: sizes, radii, animation specs, elevation.
//
//   LAYER 2 — ScribeFab  (the raw base)
//       A single frosted, press-animated FAB button. Handles all
//       visual chrome — frosted glass, accent border, shine, press scale.
//       Callers supply icon + optional label + onClick. That's it.
//
//   LAYER 3 — Ready-to-use variants (all built on ScribeFab)
//
//     • ScribeSingleFab        Plain icon FAB (standard M3 FAB)
//     • ScribeExtendedFab      Icon + text row (M3 ExtendedFAB equivalent)
//     • ScribeSpeedDialFab     One FAB that expands into a staggered menu card
//
// Usage examples:
//
//   // Simple — just an icon
//   ScribeSingleFab(icon = Icons.Default.Add, contentDescription = "Add") { ... }
//
//   // Extended — icon + label
//   ScribeExtendedFab(icon = Icons.Default.Edit, label = "Quick Note") { ... }
//
//   // Speed-dial — one FAB, many actions
//   ScribeSpeedDialFab(
//       items = listOf(
//           SpeedDialItem(Icons.Default.Add,  "New Book",  { ... }),
//           SpeedDialItem(Icons.Default.Edit, "New Sheet", { ... }),
//       )
//   )
//
// The scrim (dimmed background) is owned by the caller's screen so it can be
// placed correctly in the z-order (above the pager, below system chrome).
// ScribeSpeedDialFab exposes `expanded` and `onExpandedChange` for exactly
// that reason. See HomeScreen for the canonical usage pattern.
//
// Frosted glass: every surface reads from LocalHazeState / frostedFab /
// frostedContainerColor exactly as Cards.kt and the rest of the design system.
// Callers never need to touch hazeState themselves.
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━


// ── LAYER 1 — Design tokens ───────────────────────────────────────────────────

object ScribeFabTokens {

    // Sizes
    val SizeDefault  = 56.dp   // standard M3 FAB footprint
    val SizeSmall    = 40.dp   // small variant (secondary action)

    // Corner radii — squircle feel, not full circle
    val RadiusDefault = 16.dp
    val RadiusSmall   = 12.dp

    // Elevation — always 0; frosted glass provides depth
    val Elevation = 0.dp

    // Speed-dial card
    val SpeedDialWidth   = 210.dp
    val SpeedDialRadius  = 20.dp
    val ItemHeight       = 52.dp

    // Animation — press scale
    val PressScale = 0.93f

    // Animation specs (named so they can be tuned in one place)
    val PressScaleSpec: AnimationSpec<Float> = tween(110, easing = FastOutSlowInEasing)

    // FAB entry/exit when tab switches (used by AnimatedContent in callers)
    val TabSwitchEnter: EnterTransition =
        scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)) +
        fadeIn(tween(180))
    val TabSwitchExit: ExitTransition =
        scaleOut(tween(130)) + fadeOut(tween(130))

    // Speed-dial expand — springs from bottom-right origin
    val ExpandEnter: EnterTransition =
        scaleIn(
            spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
            transformOrigin = TransformOrigin(1f, 1f)
        ) + fadeIn(tween(160))
    val ExpandExit: ExitTransition =
        scaleOut(tween(170), targetScale = 0.80f, transformOrigin = TransformOrigin(1f, 1f)) +
        fadeOut(tween(140))

    // Speed-dial collapse
    val CollapseEnter: EnterTransition =
        scaleIn(tween(130), initialScale = 0.80f, transformOrigin = TransformOrigin(1f, 1f)) +
        fadeIn(tween(110))
    val CollapseExit: ExitTransition =
        scaleOut(tween(170), transformOrigin = TransformOrigin(1f, 1f)) +
        fadeOut(tween(170))

    // Stagger delay between speed-dial items (ms)
    const val ItemStaggerMs = 55

    // Icon rotation: + → X morph
    const val IconRotationCollapsed =  0f
    const val IconRotationExpanded  = 45f  // rotates + to ×
}


// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// LAYER 2 — ScribeFab  (base building block)
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

/**
 * The single base FAB for the entire Scribe app.
 *
 * Handles frosted glass, accent border, top-shine, and press-scale animation.
 * Callers supply a [content] slot — typically an Icon, or an Icon + Text row.
 *
 * All higher-level FAB variants ([ScribeSingleFab], [ScribeExtendedFab],
 * [ScribeSpeedDialFab]) are built on top of this.
 *
 * @param onClick         Called when the FAB is tapped.
 * @param modifier        Applied to the outer button. Use for size / positioning.
 * @param cornerRadius    Shape radius. Default: [ScribeFabTokens.RadiusDefault].
 * @param contentDescription Accessibility label for the FAB.
 * @param content         Icon / icon+label to render inside.
 */
@Composable
fun ScribeFab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = ScribeFabTokens.RadiusDefault,
    contentDescription: String = "",
    content: @Composable BoxScope.() -> Unit,
) {
    val hazeState   = LocalHazeState.current
    val accentColor = LocalAccentColor.current
    val shape       = RoundedCornerShape(cornerRadius)

    // Press-scale — interaction source drives both the scale and suppresses
    // the default ripple (glass surfaces use scale feedback instead).
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue   = if (isPressed) ScribeFabTokens.PressScale else 1f,
        animationSpec = ScribeFabTokens.PressScaleSpec,
        label         = "fab-press-scale"
    )

    // Frosted container color — reads hasBgImage internally
    val containerColor = frostedContainerColor(
        fallback = accentColor
    )

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            // Frosted glass modifier — no-ops gracefully when no bg image
            .frostedFab(hazeState, shape = shape)
            // Hairline accent border — "light-catch" depth cue
            .border(
                width = 0.7.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        accentColor.copy(alpha = 0.28f),
                        Color.Transparent
                    )
                ),
                shape = shape
            )
            .clip(shape)
            .clickable(
                interactionSource = interactionSource,
                indication        = null,   // scale replaces ripple on glass
                onClickLabel      = contentDescription
            ) { onClick() }
            .semantics {
                this.contentDescription = contentDescription
            },
        contentAlignment = Alignment.Center
    ) {
        // Frosted container fill
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(containerColor)
        )

        // Top-shine gradient — same as Cards.kt ShineAlpha
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        colors  = listOf(Color.White.copy(alpha = 0.10f), Color.Transparent),
                        startY  = 0f,
                        endY    = Float.MAX_VALUE
                    )
                )
        )

        // Caller content (icon, icon+label…)
        content()
    }
}


// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// LAYER 3A — ScribeSingleFab  (plain icon FAB)
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

/**
 * Standard icon FAB. Equivalent to M3's [FloatingActionButton].
 *
 * Frosted glass + accent border + press scale are automatic.
 *
 * @param icon               The icon to display.
 * @param contentDescription Accessibility label.
 * @param modifier           Size / position. Defaults to [ScribeFabTokens.SizeDefault].
 * @param iconTint           Icon colour. Defaults to [Color.White] (reads through frosted glass).
 * @param onClick            Action.
 */
@Composable
fun ScribeSingleFab(
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    iconTint: Color = Color.White,
    onClick: () -> Unit,
) {
    ScribeFab(
        onClick            = onClick,
        modifier           = modifier.size(ScribeFabTokens.SizeDefault),
        contentDescription = contentDescription,
    ) {
        Icon(
            imageVector        = icon,
            contentDescription = null,
            tint               = iconTint,
            modifier           = Modifier.size(24.dp)
        )
    }
}


// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// LAYER 3B — ScribeExtendedFab  (icon + label row)
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

/**
 * Extended FAB with an icon and a text label side-by-side.
 * Equivalent to M3's [ExtendedFloatingActionButton].
 *
 * Height is fixed at [ScribeFabTokens.SizeDefault]; width wraps content.
 *
 * @param icon               Icon on the left.
 * @param label              Text label.
 * @param contentDescription Accessibility label (defaults to [label]).
 * @param modifier           Applied to the outer FAB.
 * @param iconTint           Icon colour. Default: [Color.White].
 * @param onClick            Action.
 */
@Composable
fun ScribeExtendedFab(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    contentDescription: String = label,
    iconTint: Color = Color.White,
    onClick: () -> Unit,
) {
    ScribeFab(
        onClick            = onClick,
        modifier           = modifier.height(ScribeFabTokens.SizeDefault),
        contentDescription = contentDescription,
    ) {
        Row(
            modifier            = Modifier.padding(horizontal = 20.dp),
            verticalAlignment   = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector        = icon,
                contentDescription = null,
                tint               = iconTint,
                modifier           = Modifier.size(20.dp)
            )
            Text(
                text       = label,
                fontSize   = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color      = Color.White
            )
        }
    }
}


// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// LAYER 3C — ScribeSpeedDialFab  (expandable multi-action FAB)
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

/**
 * One action item inside a [ScribeSpeedDialFab] menu.
 *
 * @param icon               Icon displayed on the left.
 * @param label              Text label.
 * @param contentDescription Accessibility label (defaults to [label]).
 * @param onClick            Called when the row is tapped.
 */
data class SpeedDialItem(
    val icon: ImageVector,
    val label: String,
    val contentDescription: String = label,
    val onClick: () -> Unit,
)

/**
 * Speed-dial FAB — a single collapsed FAB that morphs into a frosted menu card
 * when tapped, revealing [items] as staggered animated rows.
 *
 * Motion design:
 *  • Collapsed FAB scales in/out from the bottom-right corner (spring).
 *  • Menu card scales in from the same origin with a medium-bounce spring.
 *  • Menu items stagger in with [ScribeFabTokens.ItemStaggerMs] delays.
 *  • The + icon rotates 45° to a × as the menu opens (spring-damped).
 *  • BackHandler collapses the menu on back press.
 *
 * Scrim / dimming: the caller is responsible for rendering a scrim Box behind
 * the FAB when [expanded] is true. This keeps z-ordering correct (scrim above
 * the pager, FAB above the scrim, nav bar above the FAB).
 *
 * @param items              The actions to show in the expanded menu (2–5 recommended).
 * @param expanded           Whether the menu is open. Hoist this state in the caller.
 * @param onExpandedChange   Called with the new expanded value when the FAB is tapped or
 *                           the menu is dismissed via back-press.
 * @param modifier           Applied to the FAB column layout.
 * @param collapsedIcon      Icon shown when the FAB is collapsed. Default: [Icons.Default.Add].
 * @param collapsedContentDescription  Accessibility label for the collapsed state.
 */
@Composable
fun ScribeSpeedDialFab(
    items: List<SpeedDialItem>,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    collapsedIcon: ImageVector = Icons.Default.Add,
    collapsedContentDescription: String = "Open actions",
) {
    // Collapse on system back press when expanded
    BackHandler(enabled = expanded) { onExpandedChange(false) }

    // Icon rotation: 0° (collapsed) → 45° (expanded), spring-damped
    val iconRotation by animateFloatAsState(
        targetValue   = if (expanded) ScribeFabTokens.IconRotationExpanded
                        else          ScribeFabTokens.IconRotationCollapsed,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness    = Spring.StiffnessMedium
        ),
        label = "fab-icon-rotation"
    )

    AnimatedContent(
        targetState  = expanded,
        transitionSpec = {
            if (targetState) {
                // Expanding
                ScribeFabTokens.ExpandEnter togetherWith ScribeFabTokens.ExpandExit
            } else {
                // Collapsing
                ScribeFabTokens.CollapseEnter togetherWith ScribeFabTokens.CollapseExit
            }
        },
        label        = "fab-morph",
        modifier     = modifier
    ) { isExpanded ->
        if (isExpanded) {
            // ── Expanded: frosted speed-dial card ────────────────────────────
            SpeedDialCard(
                items   = items,
                iconRotation = iconRotation,
                collapsedIcon = collapsedIcon,
                onCollapse = { onExpandedChange(false) }
            )
        } else {
            // ── Collapsed: single FAB button ──────────────────────────────
            ScribeFab(
                onClick            = { onExpandedChange(true) },
                modifier           = Modifier.size(ScribeFabTokens.SizeDefault),
                contentDescription = collapsedContentDescription,
            ) {
                Icon(
                    imageVector        = collapsedIcon,
                    contentDescription = null,
                    tint               = Color.White,
                    modifier           = Modifier
                        .size(24.dp)
                        .graphicsLayer { rotationZ = iconRotation }
                )
            }
        }
    }
}

/**
 * The expanded speed-dial card rendered by [ScribeSpeedDialFab].
 * Private — callers always go through [ScribeSpeedDialFab].
 */
@Composable
private fun SpeedDialCard(
    items: List<SpeedDialItem>,
    iconRotation: Float,
    collapsedIcon: ImageVector,
    onCollapse: () -> Unit,
) {
    val hazeState   = LocalHazeState.current
    val accentColor = LocalAccentColor.current
    val hasBgImage  = localHasBgImage()
    val solidSurface = LocalSolidSurface.current
    val shape       = RoundedCornerShape(ScribeFabTokens.SpeedDialRadius)

    // Stagger trigger: flip to true after the card enters so items animate in
    var showItems by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { showItems = true }

    val containerColor = frostedContainerColor(
        fallback = if (hasBgImage) solidSurface.copy(alpha = 0.92f)
                   else MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
    )

    Surface(
        shape  = shape,
        color  = containerColor,
        tonalElevation = ScribeFabTokens.Elevation,
        modifier = Modifier
            .width(ScribeFabTokens.SpeedDialWidth)
            .frostedFab(hazeState, shape = shape)
            // Accent border — top-glow cue same as ScribeCard
            .border(
                width = 0.7.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        accentColor.copy(alpha = 0.22f),
                        Color.Transparent
                    )
                ),
                shape = shape
            )
    ) {
        Column {
            items.forEachIndexed { index, item ->
                val delayMs = index * ScribeFabTokens.ItemStaggerMs

                AnimatedVisibility(
                    visible = showItems,
                    enter   = fadeIn(tween(160, delayMillis = delayMs)) +
                              slideInVertically(
                                  initialOffsetY = { 28 },
                                  animationSpec  = tween(220, delayMillis = delayMs)
                              )
                ) {
                    SpeedDialRow(
                        item        = item,
                        accentColor = accentColor,
                        onCollapse  = onCollapse
                    )
                }

                // Divider between rows — not after the last item
                if (index < items.lastIndex) {
                    HorizontalDivider(
                        thickness = 0.5.dp,
                        color     = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f),
                        modifier  = Modifier.padding(horizontal = 12.dp)
                    )
                }
            }

            // Bottom row: the collapse button, matching the icon rotation
            HorizontalDivider(
                thickness = 0.5.dp,
                color     = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f),
            )
            AnimatedVisibility(
                visible = showItems,
                enter   = fadeIn(tween(160, delayMillis = items.size * ScribeFabTokens.ItemStaggerMs)) +
                          slideInVertically(
                              initialOffsetY = { 28 },
                              animationSpec  = tween(220, delayMillis = items.size * ScribeFabTokens.ItemStaggerMs)
                          )
            ) {
                // Close / collapse row — small FAB style inline
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(ScribeFabTokens.ItemHeight)
                        .clickable(
                            indication        = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) { onCollapse() }
                        .semantics { contentDescription = "Close actions" }
                        .padding(horizontal = 16.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector        = collapsedIcon,
                        contentDescription = null,
                        modifier           = Modifier
                            .size(18.dp)
                            .graphicsLayer { rotationZ = iconRotation },
                        tint               = accentColor
                    )
                }
            }
        }
    }
}

/**
 * One row inside the speed-dial card. Private.
 */
@Composable
private fun SpeedDialRow(
    item: SpeedDialItem,
    accentColor: Color,
    onCollapse: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val rowAlpha by animateFloatAsState(
        targetValue   = if (isPressed) 0.70f else 1f,
        animationSpec = tween(80),
        label         = "row-press-alpha"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(ScribeFabTokens.ItemHeight)
            .clickable(
                interactionSource = interactionSource,
                indication        = null
            ) {
                onCollapse()
                item.onClick()
            }
            .graphicsLayer { alpha = rowAlpha }
            .semantics { contentDescription = item.contentDescription }
            .padding(horizontal = 16.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Accent icon box — same pattern as ScribeStripCard
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(ScribeCardTokens.RadiusTiny))
                .background(accentColor.copy(alpha = 0.12f))
                .border(
                    width = 0.6.dp,
                    color = accentColor.copy(alpha = 0.22f),
                    shape = RoundedCornerShape(ScribeCardTokens.RadiusTiny)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector        = item.icon,
                contentDescription = null,
                modifier           = Modifier.size(15.dp),
                tint               = accentColor
            )
        }

        Text(
            text       = item.label,
            fontSize   = 14.sp,
            fontWeight = FontWeight.Medium,
            color      = MaterialTheme.colorScheme.onSurface
        )
    }
}


// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// USAGE NOTES FOR CALLERS
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
//
// ── Scrim (dimmed overlay behind speed-dial) ─────────────────────────────────
//
// The scrim is NOT inside ScribeFab — it belongs in the screen's Box so it sits
// at the right layer. Canonical pattern (same as the old HomeScreen):
//
//   // In the Box that wraps the Scaffold content:
//   AnimatedVisibility(
//       visible = fabExpanded && selectedNavTab == 1,
//       enter   = fadeIn(tween(200)),
//       exit    = fadeOut(tween(200))
//   ) {
//       Box(
//           modifier = Modifier
//               .fillMaxSize()
//               .background(Color.Black.copy(alpha = 0.38f))
//               .clickable { fabExpanded = false }
//       )
//   }
//
// ── Tab-switching animation ───────────────────────────────────────────────────
//
// Wrap the entire floatingActionButton slot in AnimatedContent keyed on the
// selected tab, using ScribeFabTokens.TabSwitchEnter / TabSwitchExit as the
// transition spec. Each tab returns a different variant or Box(Modifier):
//
//   floatingActionButton = {
//       AnimatedContent(
//           targetState  = selectedNavTab,
//           transitionSpec = { ScribeFabTokens.TabSwitchEnter togetherWith ScribeFabTokens.TabSwitchExit },
//           label = "fab-tab-switch"
//       ) { tab ->
//           when (tab) {
//               0 -> Box(Modifier) // Dashboard — no FAB
//               1 -> ScribeSpeedDialFab(
//                       items = listOf(
//                           SpeedDialItem(Icons.Default.Add,          "New Book",  { ... }),
//                           SpeedDialItem(Icons.Outlined.Book,        "New Sheet", { ... }),
//                       ),
//                       expanded         = fabExpanded,
//                       onExpandedChange = { fabExpanded = it },
//                   )
//               2 -> ScribeExtendedFab(
//                       icon    = Icons.Default.Edit,
//                       label   = "Quick Note",
//                       onClick = { ... }
//                   )
//               else -> Box(Modifier)
//           }
//       }
//   }
//
// ── Adding a new FAB to a new screen ─────────────────────────────────────────
//
// Need a plain FAB on a new screen?
//   ScribeSingleFab(icon = Icons.Default.Add, contentDescription = "Add note") { ... }
//
// Need an extended FAB on a settings screen?
//   ScribeExtendedFab(icon = Icons.Default.Save, label = "Save Changes") { ... }
//
// Need a speed-dial on a future screen?
//   ScribeSpeedDialFab(
//       items = listOf(
//           SpeedDialItem(Icons.Default.PhotoCamera, "Take Photo",   { camera() }),
//           SpeedDialItem(Icons.Default.AttachFile,  "Attach File",  { picker() }),
//           SpeedDialItem(Icons.Default.Link,        "Insert Link",  { linkDlg() }),
//       ),
//       expanded         = dialExpanded,
//       onExpandedChange = { dialExpanded = it },
//   )
//
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

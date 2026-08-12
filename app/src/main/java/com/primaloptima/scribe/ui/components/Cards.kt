package com.primaloptima.scribe.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.primaloptima.scribe.ui.theme.FrostedCardContent
import com.primaloptima.scribe.ui.theme.LocalAccentColor
import com.primaloptima.scribe.ui.theme.LocalHazeState
import com.primaloptima.scribe.ui.theme.LocalSolidSurface
import com.primaloptima.scribe.ui.theme.frostedCard
import com.primaloptima.scribe.ui.theme.frostedContainerColor
import com.primaloptima.scribe.ui.theme.localHasBgImage

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// SCRIBE CARD DESIGN SYSTEM
//
// Three layers:
//   1. ScribeCard          — the raw base. Every card in the app uses this.
//   2. Card variants       — opinionated wrappers with named slots
//   3. Sub-components      — parts that go inside cards
//
// Slot API pattern:
//   Each card variant defines a STRUCTURE (layout, spacing, frosted glass).
//   The caller fills named SLOTS with whatever content they need.
//   No new card type should ever be created for a new screen — instead,
//   use the slots to adapt an existing variant.
//
// Variants:
//   ScribeCard             ← base, raw slot — just a frosted container
//   ScribeContentCard      ← section card: header + optional footer + body slot
//   ScribeStripCard        ← list row: leading + text block + trailing slots
//   ScribeActionTile       ← square action tile: icon + label + badge slot
//   ScribeStatColumn       ← stat display column (not a card, lives inside one)
//
// Sub-components (use inside any card):
//   ScribeCardDivider      ← hairline divider between sections
//   ScribeSectionLabel     ← overline label (uppercase, accent tinted)
//   ScribePill             ← small badge / status chip
//   ScribeProgressBar      ← animated thin progress bar
//   ScribeVerticalDivider  ← vertical line between stat columns
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━


// ── Design tokens ─────────────────────────────────────────────────────────────

object ScribeCardTokens {
    // Corner radii
    val RadiusLarge  = 20.dp   // content cards, project cards
    val RadiusMedium = 16.dp   // secondary cards, strip rows
    val RadiusSmall  = 12.dp   // action tiles, pills
    val RadiusTiny   = 8.dp    // icon boxes, sub-elements

    // Elevation — always 0 because frosted glass handles depth visually
    val Elevation = 0.dp

    // Padding presets
    val PaddingOuter      = 16.dp  // card → screen edge
    val PaddingInner      = 16.dp  // content inside card
    val PaddingInnerTight = 12.dp

    // Accent border — the subtle glow line on top of premium cards
    val AccentBorderAlpha = 0.18f

    // Gradient overlay — very subtle top-to-transparent sheen
    val ShineAlpha = 0.06f
}


// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// LAYER 1 — ScribeCard (the base)
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

/**
 * The single base card for the entire app.
 *
 * All other card variants build on top of this. Callers who need full
 * control (e.g. a very unique one-off card on a specific screen) can use
 * this directly and put anything inside the [content] slot.
 *
 * Premium touches built in automatically:
 *  • Subtle press-scale animation (97%) when [onClick] is provided
 *  • Hairline accent-coloured top border (the "light catch" effect)
 *  • Faint white shine gradient on the upper half
 *  • Frosted glass on themed backgrounds, solid surface otherwise
 *
 * @param modifier      Applied to the outer card. Use for size and padding.
 * @param cornerRadius  Corner rounding. Default: RadiusLarge.
 * @param onClick       Optional. Enables press animation and ripple.
 * @param accentBorder  Whether to show the hairline accent border. Default true.
 * @param shine         Whether to show the subtle top shine. Default true.
 * @param content       Card body — a BoxScope slot. Put anything here.
 */
@Composable
fun ScribeCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = ScribeCardTokens.RadiusLarge,
    onClick: (() -> Unit)? = null,
    accentBorder: Boolean = true,
    shine: Boolean = true,
    content: @Composable BoxScope.() -> Unit
) {
    val hazeState    = LocalHazeState.current
    val accentColor  = LocalAccentColor.current
    val hasBgImage   = localHasBgImage()
    val solidSurface = LocalSolidSurface.current
    val shape        = RoundedCornerShape(cornerRadius)

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue   = if (isPressed && onClick != null) 0.97f else 1f,
        animationSpec = tween(120, easing = FastOutSlowInEasing),
        label         = "card-press-scale"
    )

    val containerColor = frostedContainerColor(
        fallback = if (hasBgImage) solidSurface.copy(alpha = 0.82f)
                   else MaterialTheme.colorScheme.surface
    )

    Box(
        modifier = modifier
            .scale(scale)
            // Border BEFORE clip — sits on the edge itself, catches light like the FAB
            .then(
                if (accentBorder) Modifier.border(
                    width = 0.7.dp,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            accentColor.copy(alpha = 0.28f),
                            Color.Transparent
                        )
                    ),
                    shape = shape
                ) else Modifier
            )
            .clip(shape)
            // Solid fallback fill — transparent when frosted glass is active
            .background(containerColor)
            // Haze blur on a plain Box, no Surface intercepting it
            .frostedCard(hazeState, shape = shape)
            .then(
                if (onClick != null) Modifier.clickable(
                    interactionSource = interactionSource,
                    indication        = null
                ) { onClick() } else Modifier
            )
    ) {
        FrostedCardContent {
            Box(modifier = Modifier.fillMaxWidth()) {
                content()
                if (shine) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.White.copy(alpha = ScribeCardTokens.ShineAlpha),
                                        Color.Transparent
                                    ),
                                    startY = 0f,
                                    endY   = Float.MAX_VALUE
                                )
                            )
                    )
                }
            }
        }
    }
}


// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// LAYER 2A — ScribeContentCard
// Section card: header bar + body + optional footer
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

/**
 * Standard section card with a structured header, body slot, and optional footer.
 *
 * Used for: Progress card, Recent Chapters, any labelled content block,
 * Guide sections, Book stats tab, or any card that needs a title at the top.
 *
 * Slots:
 *  [headerLeading]  — composable on the LEFT of the header title (e.g. a small icon).
 *                     Optional. If null, only the title text is shown.
 *  [headerTrailing] — composable on the RIGHT of the header (e.g. "See All ›" link,
 *                     a chip, or an icon button). Optional.
 *  [footer]         — composable shown BELOW the body, separated by a divider.
 *                     Optional. Use for timestamps, tags, action rows, etc.
 *  [content]        — the main body of the card. Rendered below the header divider.
 *
 * @param title          Header title text.
 * @param modifier       Applied to the outer card.
 * @param cornerRadius   Default: RadiusLarge.
 * @param onClick        Makes the whole card tappable. Optional.
 * @param headerLeading  Optional slot — left of the title in the header.
 * @param headerTrailing Optional slot — right side of the header.
 * @param footer         Optional slot — below the body, above card bottom edge.
 * @param content        Main body slot.
 */
@Composable
fun ScribeContentCard(
    title: String,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = ScribeCardTokens.RadiusLarge,
    onClick: (() -> Unit)? = null,
    // Legacy parameters — kept so DashboardScreen does not need changes.
    // For new screens, use headerTrailing instead.
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    // Slot API parameters — preferred for new usage
    headerLeading: (@Composable () -> Unit)? = null,
    headerTrailing: (@Composable () -> Unit)? = null,
    footer: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val accentColor = LocalAccentColor.current
    val onSurface = MaterialTheme.colorScheme.onSurface
    val outline   = MaterialTheme.colorScheme.outline

    // If the caller uses the legacy actionLabel/onAction, convert them into
    // a headerTrailing slot automatically so the layout is identical.
    val resolvedTrailing: (@Composable () -> Unit)? = when {
        headerTrailing != null -> headerTrailing
        actionLabel != null && onAction != null -> ({
            Text(
                text       = actionLabel,
                fontSize   = 12.sp,
                fontWeight = FontWeight.Medium,
                color      = accentColor,
                modifier   = Modifier.clickable(
                    indication        = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { onAction() }
            )
        })
        else -> null
    }

    ScribeCard(
        modifier     = modifier,
        cornerRadius = cornerRadius,
        onClick      = onClick,
        accentBorder = true,
        shine        = true,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {

            // ── Header ────────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ScribeCardTokens.PaddingInner, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                // Left side: optional icon + title
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier              = Modifier.weight(1f)
                ) {
                    headerLeading?.invoke()
                    Text(
                        text       = title,
                        fontSize   = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color      = onSurface,
                        maxLines   = 1,
                        overflow   = TextOverflow.Ellipsis
                    )
                }
                // Right side: any trailing composable (action link, chip, icon…)
                resolvedTrailing?.invoke()
            }

            HorizontalDivider(color = outline.copy(alpha = 0.10f))

            // ── Body ──────────────────────────────────────────────────────────
            Column(modifier = Modifier.fillMaxWidth()) {
                content()
            }

            // ── Footer ────────────────────────────────────────────────────────
            if (footer != null) {
                HorizontalDivider(color = outline.copy(alpha = 0.07f))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = ScribeCardTokens.PaddingInner, vertical = 10.dp)
                ) {
                    footer()
                }
            }
        }
    }
}


// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// LAYER 2B — ScribeStripCard
// List row card: leading slot + text block + trailing slot
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

/**
 * A horizontal strip card — one item in a list.
 *
 * This is the most flexible card variant. Every kind of list row in the app
 * (note rows, chapter rows, world entry rows, history rows, setting rows)
 * should use this instead of building a raw Card from scratch.
 *
 * Layout:
 *   [ leading ] [ title / subtitle / preview / footerLines ] [ trailing ]
 *
 * Slots:
 *  [leading]     — composable on the FAR LEFT (e.g. icon box, avatar, color dot,
 *                  book cover thumbnail). Optional. When null, text starts at edge.
 *  [trailing]    — composable on the FAR RIGHT (e.g. 3-dot menu, chevron, timestamp,
 *                  checkbox). Optional.
 *
 * Text block (all optional except title):
 *  [title]        — primary bold text (always shown).
 *  [subtitle]     — second line, dimmed (e.g. "340 words · /Chapters").
 *  [preview]      — italic preview text. Controls how many lines with [previewMaxLines].
 *  [footerLines]  — list of small dimmed lines at the bottom (e.g. timestamps).
 *                   Each string in the list gets its own line.
 *
 * Wrapping:
 *  [wrapInCard]   — true (default): wraps the strip in a ScribeCard with frosted glass.
 *                   false: renders as a plain row, for use INSIDE a ScribeContentCard
 *                   (which is already a card — no need to double-wrap).
 *
 * @param title          Primary text. Always shown.
 * @param modifier       Applied to the outer container.
 * @param subtitle       Optional second line below title.
 * @param preview        Optional italic preview text.
 * @param previewMaxLines How many lines the preview can expand to. Default 1.
 * @param footerLines    Optional list of small lines at the bottom (timestamps, tags…).
 * @param leading        Optional slot — left side of the row.
 * @param trailing       Optional slot — right side of the row.
 * @param onClick        Makes the whole strip tappable.
 * @param showDivider    Draw a faint divider below. Use when wrapInCard = false
 *                       and this strip lives inside a ScribeContentCard list.
 * @param wrapInCard     True → standalone frosted card. False → plain row (inside a card).
 * @param cornerRadius   Only used when wrapInCard = true. Default: RadiusMedium.
 */
@Composable
fun ScribeStripCard(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    preview: String? = null,
    previewMaxLines: Int = 1,
    footerLines: List<String>? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    showDivider: Boolean = false,
    wrapInCard: Boolean = true,
    cornerRadius: Dp = ScribeCardTokens.RadiusMedium
) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val outline   = MaterialTheme.colorScheme.outline

    // The inner row — shared between both wrapInCard modes
    @Composable
    fun StripRow() {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (onClick != null) Modifier.clickable(
                            indication        = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) { onClick() } else Modifier
                    )
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Leading slot (icon box, avatar, thumbnail…)
                leading?.invoke()

                // Text block
                Column(
                    modifier            = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text       = title,
                        fontWeight = FontWeight.SemiBold,
                        fontSize   = 13.sp,
                        color      = onSurface,
                        maxLines   = 1,
                        overflow   = TextOverflow.Ellipsis
                    )
                    if (subtitle != null) {
                        Text(
                            text     = subtitle,
                            fontSize = 11.sp,
                            color    = onSurface.copy(alpha = 0.50f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (preview != null) {
                        Text(
                            text      = preview,
                            fontSize  = 12.sp,
                            color     = onSurface.copy(alpha = 0.55f),
                            maxLines  = previewMaxLines,
                            overflow  = TextOverflow.Ellipsis,
                            fontStyle = FontStyle.Italic
                        )
                    }
                    if (!footerLines.isNullOrEmpty()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        footerLines.forEach { line ->
                            Text(
                                text     = line,
                                fontSize = 11.sp,
                                color    = onSurface.copy(alpha = 0.38f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // Trailing slot (menu button, chevron, checkbox…)
                trailing?.invoke()
            }

            if (showDivider) {
                HorizontalDivider(
                    modifier = Modifier.padding(
                        start = if (leading != null) 66.dp else 14.dp,
                        end   = 14.dp
                    ),
                    color = outline.copy(alpha = 0.08f)
                )
            }
        }
    }

    if (wrapInCard) {
        ScribeCard(
            modifier     = modifier,
            cornerRadius = cornerRadius,
            onClick      = onClick,
            accentBorder = true,
            shine        = true
        ) {
            // When wrapInCard = true, ScribeCard already handles the click,
            // so we pass null to StripRow to avoid double-handling
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    leading?.invoke()

                    Column(
                        modifier            = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text       = title,
                            fontWeight = FontWeight.SemiBold,
                            fontSize   = 13.sp,
                            color      = onSurface,
                            maxLines   = 1,
                            overflow   = TextOverflow.Ellipsis
                        )
                        if (subtitle != null) {
                            Text(
                                text     = subtitle,
                                fontSize = 11.sp,
                                color    = onSurface.copy(alpha = 0.50f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (preview != null) {
                            Text(
                                text      = preview,
                                fontSize  = 12.sp,
                                color     = onSurface.copy(alpha = 0.55f),
                                maxLines  = previewMaxLines,
                                overflow  = TextOverflow.Ellipsis,
                                fontStyle = FontStyle.Italic
                            )
                        }
                        if (!footerLines.isNullOrEmpty()) {
                            Spacer(modifier = Modifier.height(2.dp))
                            footerLines.forEach { line ->
                                Text(
                                    text     = line,
                                    fontSize = 11.sp,
                                    color    = onSurface.copy(alpha = 0.38f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    trailing?.invoke()
                }
            }
        }
    } else {
        // Plain row — no card wrapper (used inside ScribeContentCard)
        Box(modifier = modifier) {
            StripRow()
        }
    }
}


// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// LAYER 2C — ScribeActionTile
// Compact square tile for quick actions
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

/**
 * A compact action tile — square or near-square.
 * Used in the Quick Actions row on the Dashboard and anywhere else
 * a grid of tappable actions is needed.
 *
 * Slots:
 *  [badge]   — optional composable rendered in the TOP-RIGHT corner of the tile.
 *              Use for notification dots, counts, or "New" chips.
 *
 * @param icon      Icon shown centred in the tile.
 * @param label     Short label below the icon.
 * @param onClick   Tap handler.
 * @param modifier  Applied to the tile. Caller controls size/weight.
 * @param isPrimary True → filled with accent color (e.g. the "Write" tile).
 *                  False → frosted glass style.
 * @param badge     Optional slot — top-right corner overlay (notification dot, count…).
 */
@Composable
fun ScribeActionTile(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isPrimary: Boolean = false,
    badge: (@Composable BoxScope.() -> Unit)? = null
) {
    val accentColor  = LocalAccentColor.current
    val hazeState    = LocalHazeState.current
    val hasBgImage   = localHasBgImage()
    val solidSurface = LocalSolidSurface.current
    val shape        = RoundedCornerShape(ScribeCardTokens.RadiusMedium)

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue   = if (isPressed) 0.95f else 1f,
        animationSpec = tween(100, easing = FastOutSlowInEasing),
        label         = "tile-press"
    )

    val containerColor = if (isPrimary) accentColor
    else frostedContainerColor(
        fallback = if (hasBgImage) solidSurface.copy(alpha = 0.80f)
                   else MaterialTheme.colorScheme.surface
    )

    Box(
        modifier = modifier
            .scale(scale)
            // Border before clip — same light-catch edge as FAB and ScribeCard
            .then(
                if (isPrimary) Modifier else Modifier.border(
                    width = 0.7.dp,
                    brush = Brush.verticalGradient(
                        colors = listOf(accentColor.copy(alpha = 0.28f), Color.Transparent)
                    ),
                    shape = shape
                )
            )
            .clip(shape)
            .background(containerColor)
            .then(if (!isPrimary) Modifier.frostedCard(hazeState, shape = shape) else Modifier)
            .clickable(
                interactionSource = interactionSource,
                indication        = null
            ) { onClick() }
    ) {
        val tileContent: @Composable () -> Unit = {
            Column(
                modifier              = Modifier.fillMaxSize(),
                horizontalAlignment   = Alignment.CenterHorizontally,
                verticalArrangement   = Arrangement.Center
            ) {
                Box(
                    modifier = if (isPrimary) Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.15f))
                    else Modifier.size(36.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector        = icon,
                        contentDescription = label,
                        modifier           = Modifier.size(20.dp),
                        tint               = if (isPrimary) Color.White else accentColor
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text       = label,
                    fontSize   = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color      = if (isPrimary) Color.White
                                 else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                    maxLines   = 1
                )
            }
        }

        // Tile body + optional badge overlay
        Box(modifier = Modifier.fillMaxSize()) {
            if (isPrimary) {
                FrostedCardContent {
                    Box(modifier = Modifier.fillMaxSize()) {
                        tileContent()
                        // Shine on primary tile
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(Color.White.copy(alpha = 0.12f), Color.Transparent)
                                    )
                                )
                        )
                    }
                }
            } else {
                FrostedCardContent { tileContent() }
            }

            // Badge slot — top-right corner
            if (badge != null) {
                Box(
                    modifier         = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp),
                    content          = badge
                )
            }
        }
    }
}


// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// LAYER 2D — ScribeStatColumn
// One stat display column — lives inside another card's Row
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

/**
 * A single stat display column.
 * Does NOT wrap itself in a ScribeCard — it lives inside another card's Row.
 *
 * Used in: Writing Progress card, Statistics screen, Book stats tab.
 *
 * Slots:
 *  [extra]   — optional composable rendered BELOW the badge line.
 *              Use for StreakDots, a thin progress bar, or any extra visual.
 *
 * @param label      Small label above the value (e.g. "Today").
 * @param value      Large bold number/text (e.g. "1.2k").
 * @param modifier   Applied to the column.
 * @param subLabel   Small label below the value (e.g. "/ 2k words").
 * @param icon       Icon shown in a small accent circle above the label.
 * @param iconTint   Override icon tint. Defaults to accentColor.
 * @param badge      Optional small text badge below subLabel (e.g. "Goal reached! 🎉").
 * @param extra      Optional slot — below the badge (StreakDots, ThinProgressBar…).
 */
@Composable
fun ScribeStatColumn(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    subLabel: String? = null,
    icon: ImageVector? = null,
    iconTint: Color? = null,
    badge: String? = null,
    extra: (@Composable () -> Unit)? = null
) {
    val accentColor = LocalAccentColor.current
    val onSurface   = MaterialTheme.colorScheme.onSurface
    val tint        = iconTint ?: accentColor

    Column(
        modifier            = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        if (icon != null) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(tint.copy(alpha = 0.13f))
                    .border(0.6.dp, tint.copy(alpha = 0.20f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector        = icon,
                    contentDescription = null,
                    modifier           = Modifier.size(14.dp),
                    tint               = tint
                )
            }
        }
        Text(
            text       = label,
            fontSize   = 10.sp,
            fontWeight = FontWeight.Medium,
            color      = onSurface.copy(alpha = 0.50f)
        )
        Text(
            text       = value,
            fontSize   = 26.sp,
            fontWeight = FontWeight.Bold,
            color      = onSurface
        )
        if (subLabel != null) {
            Text(
                text     = subLabel,
                fontSize = 11.sp,
                color    = onSurface.copy(alpha = 0.45f)
            )
        }
        if (badge != null) {
            Text(
                text       = badge,
                fontSize   = 10.sp,
                color      = accentColor,
                fontWeight = FontWeight.SemiBold
            )
        }
        extra?.invoke()
    }
}


// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// LAYER 3 — Sub-components (parts that go inside any card)
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

/**
 * Hairline divider used between sections inside any card.
 * Slightly softer than M3's default HorizontalDivider.
 */
@Composable
fun ScribeCardDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier,
        color    = MaterialTheme.colorScheme.outline.copy(alpha = 0.10f)
    )
}

/**
 * Section label — overline style: uppercase, small, accent-tinted.
 * Use inside cards or as a standalone screen section header.
 *
 * @param text      Label text (uppercased automatically).
 * @param modifier  Optional padding or alignment.
 */
@Composable
fun ScribeSectionLabel(
    text: String,
    modifier: Modifier = Modifier
) {
    val accentColor = LocalAccentColor.current
    Text(
        text          = text.uppercase(),
        fontSize      = 10.sp,
        fontWeight    = FontWeight.Bold,
        letterSpacing = 1.2.sp,
        color         = accentColor.copy(alpha = 0.80f),
        modifier      = modifier
    )
}

/**
 * Small pill / badge — streak count, word count highlight, status indicator.
 *
 * @param text   Pill content.
 * @param color  Background tint. Defaults to accentColor.
 */
@Composable
fun ScribePill(
    text: String,
    modifier: Modifier = Modifier,
    color: Color? = null
) {
    val accentColor = LocalAccentColor.current
    val bg = color ?: accentColor
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(bg.copy(alpha = 0.13f))
            .border(0.6.dp, bg.copy(alpha = 0.22f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text       = text,
            fontSize   = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color      = bg
        )
    }
}

/**
 * Animated thin progress bar.
 * Used inside cards for per-project or monthly progress.
 *
 * @param progress  0f to 1f. Animates automatically when the value changes.
 * @param modifier  Controls size — caller sets width and height.
 * @param color     Bar color. Defaults to accentColor.
 */
@Composable
fun ScribeProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color? = null
) {
    val accentColor = LocalAccentColor.current
    val barColor    = color ?: accentColor
    val animated by animateFloatAsState(
        targetValue   = progress.coerceIn(0f, 1f),
        animationSpec = tween(600, easing = FastOutSlowInEasing),
        label         = "scribe-progress"
    )
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(animated)
                .fillMaxHeight()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(barColor.copy(alpha = 0.80f), barColor)
                    )
                )
        )
    }
}

/**
 * Vertical divider used between stat columns inside a card.
 *
 * @param height  Height of the line. Default 80.dp.
 */
@Composable
fun ScribeVerticalDivider(
    modifier: Modifier = Modifier,
    height: Dp = 80.dp
) {
    Box(
        modifier = modifier
            .width(0.8.dp)
            .height(height)
            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
    )
}


// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// QUICK REFERENCE — How to use each slot
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
//
// NoteListRow  (BookScreen) → ScribeStripCard(
//     title         = note.title,
//     subtitle      = "${note.wordCount} words · ${note.folder}",
//     preview       = note.previewText,
//     previewMaxLines = 3,
//     footerLines   = listOf("Created: ${note.createdAt}", "Modified: ${note.modifiedAt}"),
//     leading       = { ScribeIconBox(icon = Icons.Outlined.Description) },
//     trailing      = { NoteMenuButton(note) },
//     wrapInCard    = true
// )
//
// WorldEntryCard (SheetsScreen) → ScribeStripCard(
//     title         = entry.name,
//     subtitle      = entry.type,
//     preview       = entry.summary,
//     leading       = { EntryAvatar(entry) },
//     trailing      = { Icon(Icons.Default.ChevronRight) },
//     wrapInCard    = true
// )
//
// GuideSection (GuideScreen) → ScribeContentCard(
//     title   = section.title,
//     content = { Text(section.description) }
// )
//
// StatCard (BookScreen stats tab) → ScribeCard {
//     ScribeStatColumn(label = "Words", value = "12,450")
// }
//
// ActionTile with badge → ScribeActionTile(
//     icon  = Icons.Outlined.Notifications,
//     label = "Alerts",
//     badge = { ScribePill(text = "3") },
//     onClick = { }
// )
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

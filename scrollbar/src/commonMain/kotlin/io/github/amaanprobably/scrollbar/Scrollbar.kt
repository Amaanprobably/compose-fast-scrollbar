package io.github.amaanprobably.scrollbar

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.stopScroll
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.mandatorySystemGestures
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource
import androidx.compose.ui.graphics.lerp as lerpColor

/**
 * Which side of the cross axis the scrollbar sits on.
 *
 * - Vertical lists: START is the leading side (left in LTR, right in RTL), END the trailing side.
 * - Horizontal lists: START is the top, END is the bottom.
 */
enum class FastScrollEdge { START, END }

/**
 * How the thumb's length is chosen.
 *
 * Lazy lists don't know their true content size, so a proportional thumb can't be fully stable
 * on variable-height content. [Fixed] sidesteps that and is the default, like the platform
 * fast scrollers.
 */
@Immutable
sealed interface ThumbSizing {
    /** Constant length regardless of content size. */
    @Immutable
    class Fixed(val length: Dp = 40.dp) : ThumbSizing {
        init {
            require(length > 0.dp) { "length must be positive" }
        }
    }

    /**
     * Length follows viewport / content. With [smoothed], changes in the estimated content
     * size are animated instead of snapping, which hides jitter on variable-height items.
     */
    @Immutable
    class Proportional(
        val minLength: Dp = 48.dp,
        val maxLengthFraction: Float = 0.9f,
        val smoothed: Boolean = true,
    ) : ThumbSizing {
        init {
            require(maxLengthFraction in 0f..1f) {
                "maxLengthFraction must be between 0f and 1f"
            }
        }
    }
}

/**
 * What the draggable thumb looks like.
 */
sealed interface ThumbAppearance {
    /**
     * The built-in touch look: a translucent capsule with a thin thumb inside it. It uses the
     * style's thickness, color, capsule and [ThumbSizing] parameters.
     */
    data object Default : ThumbAppearance

    /**
     * A single rounded bar with no capsule, like desktop scrollbars. The default on desktop, and
     * available everywhere. It uses the style's thumb thickness, color and [ThumbSizing]
     * parameters; the capsule parameters are ignored. It widens from [ScrollbarStyle.thumbThickness]
     * to [ScrollbarStyle.pressedThumbThickness] when hovered or dragged.
     */
    data object Bar : ThumbAppearance

    /**
     * Replaces the whole capsule and thumb with your own icon, for example a brand logo.
     *
     * - [size] is required (an icon's own intrinsic size is ignored). Along the scroll axis it
     *   becomes the thumb length, so [ThumbSizing] and the style's capsule, thickness and color
     *   parameters are ignored while an icon is used.
     * - [idle] is shown at rest and after release. [dragging] is faded in while the thumb is
     *   held. Leave it out to use one icon for both states.
     * - [tint] recolors the icons. Leave it [Color.Unspecified] to keep their own colors, which
     *   is what you want for multicolor logos.
     * - [draggingScale] grows the icon slightly while it is held. Use `1f` to turn it off.
     * - The icon is never mirrored or rotated, only positioned. For right-to-left layouts, set
     *   `autoMirror` on a vector yourself if it should flip.
     * - Touches anywhere on the icon grab it. The touch target is never smaller than
     *   [ScrollbarStyle.touchTargetThickness].
     *
     * Painters should be created with `remember` (as `painterResource` and `rememberVectorPainter`
     * already do). A painter created on every recomposition is replaced mid-scroll.
     *
     * Animated painters can work, because the icon is drawn every frame while the bar is
     * visible, but they are not tested yet. A painter that animates forever keeps redrawing for as
     * long as the bar is visible.
     */
    @Stable
    class Icon(
        val idle: Painter,
        val size: DpSize,
        val dragging: Painter = idle,
        val tint: Color = Color.Unspecified,
        val draggingScale: Float = 1.1f,
    ) : ThumbAppearance {
        init {
            require(size.width > 0.dp && size.height > 0.dp) {
                "size must be specified and positive"
            }
            require(draggingScale > 0f) { "draggingScale must be positive" }
        }

        // Built once, not per frame.
        internal val colorFilter: ColorFilter? =
            if (tint.isSpecified) ColorFilter.tint(tint) else null
    }
}

/**
 * How much of the track [TrackAppearance] draws.
 */
enum class TrackExtent {
    /** The whole track, from where the thumb can start to where it can end. */
    Full,

    /**
     * From the start of the list up to the middle of the thumb, so the track grows as you scroll,
     * like a rope the thumb hangs from (or Wukong's growing staff). For `reverseLayout`
     * (and right-to-left rows), the start is the bottom (or right), where the list starts.
     */
    UpToThumb,
}

/** How [TrackAppearance.Image] fills the track along the scroll axis. */
enum class TrackFit {
    /** One copy of the image, stretched to the track's length. Good for lines, webs and ropes. */
    Stretch,

    /**
     * Copies of the image placed end to end, keeping their proportions, for patterns that would
     * look smeared when stretched, like a ladder, a chain or a brick tower.
     */
    Repeat,
}

/**
 * What the track (the path the thumb moves along) looks like. It's drawn under the thumb, centered
 * on it across the scroll axis, and fades in and out with it. It is only decoration: touches on
 * it behave exactly as without it.
 */
@Immutable
sealed interface TrackAppearance {
    /** No visible track. The default. */
    data object None : TrackAppearance

    /** A plain rounded line. */
    @Immutable
    class Line(
        val color: Color = Color(0x33808080),
        val thickness: Dp = 2.dp,
        val extent: TrackExtent = TrackExtent.Full,
    ) : TrackAppearance {
        init {
            require(thickness > 0.dp) { "thickness must be positive" }
        }
    }

    /**
     * Your own drawing as the track, for example a web for a spider thumb or a pole for a
     * character sliding down it.
     *
     * - [thickness] is its size across the scroll axis.
     * - With [TrackFit.Repeat], [tileLength] is the length of one copy along the scroll axis.
     *   Leave it unspecified to keep the painter's own proportions (or a square, when the painter
     *   has no intrinsic size).
     * - [tint] recolors it. Leave it [Color.Unspecified] to keep its own colors.
     * - It's never rotated or mirrored, only positioned: draw it upright for vertical lists and
     *   sideways for horizontal ones.
     * - A track wider than the space between the thumb and the list's edge is moved inward, so it
     *   never draws outside the list.
     *
     * Painters should be created with `remember` (as `painterResource` and `rememberVectorPainter`
     * already do). Animated painters work, because the track is drawn every frame while visible.
     */
    @Stable
    class Image(
        val painter: Painter,
        val thickness: Dp,
        val extent: TrackExtent = TrackExtent.Full,
        val fit: TrackFit = TrackFit.Stretch,
        val tileLength: Dp = Dp.Unspecified,
        val tint: Color = Color.Unspecified,
    ) : TrackAppearance {
        init {
            require(thickness > 0.dp) { "thickness must be positive" }
            require(tileLength.value.isNaN() || tileLength > 0.dp) { "tileLength must be positive" }
        }

        // Built once, not per frame.
        internal val colorFilter: ColorFilter? =
            if (tint.isSpecified) ColorFilter.tint(tint) else null
    }
}

/**
 * When the scrollbar is visible. Either way it only shows when the list can actually scroll and
 * has more than `minItemsToShow` items.
 */
enum class ScrollbarVisibility {
    /** Fades in while scrolling or dragging and fades out after [ScrollbarStyle.hideDelay]. */
    AutoHide,

    /** Always visible, so users can see where they are and grab the thumb at any time. */
    Always,
}

/**
 * Look of the bubble that [ScrollbarLabel] shows next to the thumb while it is dragged.
 */
@Immutable
class ScrollbarLabelStyle(
    val textStyle: TextStyle = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Medium),
    val textColor: Color = Color.White,
    val bubbleColor: Color = Color(0xE6212121),
    val cornerRadius: Dp = 14.dp,
    val horizontalPadding: Dp = 14.dp,
    val verticalPadding: Dp = 8.dp,
    /** Distance between the thumb and the bubble. */
    val gap: Dp = 8.dp,
)

/**
 * A bubble that follows the thumb while it is dragged and shows where you are in the list, for
 * example the first letter of a contact or a date. It sits on the inner side of the thumb.
 *
 * [provider] gets the index of the item the list is jumping to. For grids this is the first item
 * of the target row or column. Return the text to show, or `null` to hide the bubble for that item.
 * It runs on the main thread every time the drag moves to a different item, so keep it cheap.
 *
 * For sections (an A-Z list, for example), return the same text for every item of a section.
 * With [sectionHaptics], the device ticks whenever the text changes, so a per-item label like
 * "Item 1234" would tick constantly. Turn [sectionHaptics] off for labels like that.
 */
@Stable
class ScrollbarLabel(
    val sectionHaptics: Boolean = true,
    val style: ScrollbarLabelStyle = ScrollbarLabelStyle(),
    val provider: (itemIndex: Int) -> String?,
)

/**
 * Visual and timing configuration for [scrollbar].
 *
 * The "capsule" is the translucent, rounded background that wraps the thumb and moves with it.
 * Create instances with [ScrollbarDefaults.style].
 *
 * The capsule parameters only apply to [ThumbAppearance.Default]. [ThumbAppearance.Bar] uses the
 * thumb thickness, color and [ThumbSizing] parameters. [ThumbAppearance.Icon] replaces all of them.
 *
 * [trackAppearance] adds an optional visible track under the thumb, with any look. It's
 * [TrackAppearance.None] (invisible) by default.
 */
@Stable
class ScrollbarStyle internal constructor(
    val thumbThickness: Dp,
    val pressedThumbThickness: Dp,
    val thumbSizing: ThumbSizing,
    val thumbAppearance: ThumbAppearance,
    val visibility: ScrollbarVisibility,
    val touchTargetThickness: Dp,
    val trackPadding: Dp,
    val edgePadding: Dp,
    val capsuleThickness: Dp,
    val capsuleExtraLength: Dp,
    val hideDelay: Duration,
    val fadeInDuration: Duration,
    val fadeOutDuration: Duration,
    val thumbColor: Color,
    val pressedThumbColor: Color,
    val capsuleColor: Color,
    val trackAppearance: TrackAppearance,
)

/** Where the library is running. Each target sets it in androidMain, iosMain or desktopMain. */
internal enum class Platform { Android, Ios, Desktop }

internal expect val currentPlatform: Platform

/** The look and size defaults, which differ per platform. */
internal class StyleDefaults(
    val thumbAppearance: ThumbAppearance,
    val thumbThickness: Dp,
    val pressedThumbThickness: Dp,
    val thumbLength: Dp,
    val touchTargetThickness: Dp,
    val trackPadding: Dp,
    val edgePadding: Dp,
    val capsuleThickness: Dp,
    val capsuleExtraLength: Dp,
)

/**
 * Android: a capsule with a thumb inside, like the platform fast scroller. The capsule is 24dp
 * wide and 64dp long (40dp thumb + 24dp), close to Google's own 24dp x 63dp drag handle, so it
 * covers little content. The 48dp touch strip keeps it easy to grab.
 */
internal val AndroidDefaults = StyleDefaults(
    thumbAppearance = ThumbAppearance.Default,
    thumbThickness = 5.dp,
    pressedThumbThickness = 8.dp,
    thumbLength = 40.dp,
    touchTargetThickness = 48.dp,
    trackPadding = 8.dp,
    edgePadding = 4.dp,
    capsuleThickness = 24.dp,
    capsuleExtraLength = 24.dp,
)

/**
 * iOS: a thin bar that widens when grabbed, like the system scroll indicator, with the same
 * finger-sized grab area as Android.
 */
internal val IosDefaults = StyleDefaults(
    thumbAppearance = ThumbAppearance.Bar,
    thumbThickness = 3.dp,
    pressedThumbThickness = 8.dp,
    thumbLength = 40.dp,
    touchTargetThickness = 48.dp,
    trackPadding = 6.dp,
    edgePadding = 3.dp,
    capsuleThickness = 24.dp, // unused by Bar, same as Android for apps that switch to Default
    capsuleExtraLength = 24.dp,
)

/** Mouse-sized: a single thin bar, like desktop scrollbars. */
internal val CompactDefaults = StyleDefaults(
    thumbAppearance = ThumbAppearance.Bar,
    thumbThickness = 8.dp,
    pressedThumbThickness = 12.dp,
    thumbLength = 40.dp,
    touchTargetThickness = 16.dp,
    trackPadding = 4.dp,
    edgePadding = 2.dp,
    capsuleThickness = 16.dp,
    capsuleExtraLength = 8.dp,
)

/** The defaults for the platform the library is running on. */
internal val PlatformDefaults: StyleDefaults
    get() = when (currentPlatform) {
        Platform.Android -> AndroidDefaults
        Platform.Ios -> IosDefaults
        Platform.Desktop -> CompactDefaults
    }

object ScrollbarDefaults {
    /**
     * The default style. It follows the platform: a finger-sized capsule
     * ([ThumbAppearance.Default]) on Android, a thin [ThumbAppearance.Bar] that widens when
     * grabbed on iOS, and a mouse-sized bar on desktop. Use [compactStyle] for the desktop look
     * everywhere, or pass `thumbAppearance` to pick a look yourself.
     *
     * It is a plain function, so it can be hoisted into a top-level `val` and shared.
     */
    fun style(
        thumbThickness: Dp = PlatformDefaults.thumbThickness,
        pressedThumbThickness: Dp = PlatformDefaults.pressedThumbThickness,
        thumbSizing: ThumbSizing = ThumbSizing.Fixed(PlatformDefaults.thumbLength),
        thumbAppearance: ThumbAppearance = PlatformDefaults.thumbAppearance,
        visibility: ScrollbarVisibility = ScrollbarVisibility.AutoHide,
        touchTargetThickness: Dp = PlatformDefaults.touchTargetThickness,
        trackPadding: Dp = PlatformDefaults.trackPadding,
        edgePadding: Dp = PlatformDefaults.edgePadding,
        capsuleThickness: Dp = PlatformDefaults.capsuleThickness,
        capsuleExtraLength: Dp = PlatformDefaults.capsuleExtraLength,
        hideDelay: Duration = 1_500.milliseconds,
        fadeInDuration: Duration = 150.milliseconds,
        fadeOutDuration: Duration = 300.milliseconds,
        thumbColor: Color = Color(0x80808080),
        pressedThumbColor: Color = Color(0xFF2196F3),
        capsuleColor: Color = Color(0x2E808080),
        trackAppearance: TrackAppearance = TrackAppearance.None,
    ): ScrollbarStyle = ScrollbarStyle(
        thumbThickness = thumbThickness,
        pressedThumbThickness = pressedThumbThickness,
        thumbSizing = thumbSizing,
        thumbAppearance = thumbAppearance,
        visibility = visibility,
        touchTargetThickness = touchTargetThickness,
        trackPadding = trackPadding,
        edgePadding = edgePadding,
        capsuleThickness = capsuleThickness,
        capsuleExtraLength = capsuleExtraLength,
        hideDelay = hideDelay,
        fadeInDuration = fadeInDuration,
        fadeOutDuration = fadeOutDuration,
        thumbColor = thumbColor,
        pressedThumbColor = pressedThumbColor,
        capsuleColor = capsuleColor,
        trackAppearance = trackAppearance,
    )

    /**
     * Picks the style for the platform the app runs on, so a multiplatform app can set every look
     * in one place, without its own platform checks. Each argument is a full style, so anything
     * can differ per platform. Pass only the platforms you want to change; the others keep their
     * built-in [style]:
     *
     * ```
     * val scrollbarStyle = ScrollbarDefaults.platformStyle(
     *     android = ScrollbarDefaults.style(
     *         thumbAppearance = ThumbAppearance.Bar,
     *         visibility = ScrollbarVisibility.Always,
     *     ),
     *     ios = ScrollbarDefaults.style(
     *         thumbAppearance = ThumbAppearance.Icon(idle = logoPainter, size = DpSize(32.dp, 32.dp)),
     *     ),
     *     desktop = ScrollbarDefaults.compactStyle(
     *         thumbSizing = ThumbSizing.Proportional(),
     *         hideDelay = 3.seconds,
     *     ),
     * )
     * ```
     */
    fun platformStyle(
        android: ScrollbarStyle = style(),
        ios: ScrollbarStyle = style(),
        desktop: ScrollbarStyle = style(),
    ): ScrollbarStyle = when (currentPlatform) {
        Platform.Android -> android
        Platform.Ios -> ios
        Platform.Desktop -> desktop
    }

    /**
     * The desktop look on every platform: a single thin bar with a mouse-sized grab area. It's
     * what [style] uses on desktop. The grab area is harder to hit with a finger, so on touch
     * screens prefer `style(thumbAppearance = ThumbAppearance.Bar)`.
     */
    fun compactStyle(
        thumbThickness: Dp = CompactDefaults.thumbThickness,
        pressedThumbThickness: Dp = CompactDefaults.pressedThumbThickness,
        thumbSizing: ThumbSizing = ThumbSizing.Fixed(CompactDefaults.thumbLength),
        thumbAppearance: ThumbAppearance = CompactDefaults.thumbAppearance,
        visibility: ScrollbarVisibility = ScrollbarVisibility.AutoHide,
        touchTargetThickness: Dp = CompactDefaults.touchTargetThickness,
        trackPadding: Dp = CompactDefaults.trackPadding,
        edgePadding: Dp = CompactDefaults.edgePadding,
        capsuleThickness: Dp = CompactDefaults.capsuleThickness,
        capsuleExtraLength: Dp = CompactDefaults.capsuleExtraLength,
        hideDelay: Duration = 1_500.milliseconds,
        fadeInDuration: Duration = 150.milliseconds,
        fadeOutDuration: Duration = 300.milliseconds,
        thumbColor: Color = Color(0x80808080),
        pressedThumbColor: Color = Color(0xFF2196F3),
        capsuleColor: Color = Color(0x2E808080),
        trackAppearance: TrackAppearance = TrackAppearance.None,
    ): ScrollbarStyle = style(
        thumbThickness = thumbThickness,
        pressedThumbThickness = pressedThumbThickness,
        thumbSizing = thumbSizing,
        thumbAppearance = thumbAppearance,
        visibility = visibility,
        touchTargetThickness = touchTargetThickness,
        trackPadding = trackPadding,
        edgePadding = edgePadding,
        capsuleThickness = capsuleThickness,
        capsuleExtraLength = capsuleExtraLength,
        hideDelay = hideDelay,
        fadeInDuration = fadeInDuration,
        fadeOutDuration = fadeOutDuration,
        thumbColor = thumbColor,
        pressedThumbColor = pressedThumbColor,
        capsuleColor = capsuleColor,
        trackAppearance = trackAppearance,
    )
}

/** One shared instance, so calls that don't pass a style neither allocate nor look "changed". */
internal val DefaultScrollbarStyle: ScrollbarStyle = ScrollbarDefaults.style()

/**
 * Draggable fast scroller for a [androidx.compose.foundation.lazy.LazyColumn] or
 * [androidx.compose.foundation.lazy.LazyRow].
 *
 * Apply it to the lazy list itself. Only touches that land on the thumb are taken;
 * every other touch reaches the list untouched.
 *
 * With a mouse (desktop, or a mouse on Android and iPad), hovering near the bar reveals it and
 * highlights the thumb, hovering the track previews the [label] for that position, and clicking
 * the track jumps there. Mouse wheel and trackpad scrolling always reach the list. Touch
 * behavior is unaffected by any of this.
 *
 * **Position accuracy:** lazy lists only know the size of items they've measured, so dragging
 * maps the thumb to an item using the average item size. With uniform items it's exact, with
 * variable sizes the list can land slightly off the thumb.
 *
 * @param contentPadding Pass the same padding the list uses (including system insets) so the
 * track stays inside it. Only the padding along the scroll axis is applied; use
 * [ScrollbarStyle.edgePadding] to move the bar away from the cross-axis edge.
 * @param hasMore Set to `true` for feeds that load more items as you scroll. While it is `true`
 * the thumb stops short of the very end, because the list is not really finished. When it
 * becomes `false` the thumb glides to the end. Leave it `false` for lists with a known size.
 * @param label Shows a bubble next to the thumb while it is dragged. See [ScrollbarLabel].
 * @param scrollToStartLabel Accessibility label for a "scroll to start" action. No action is
 * added when `null`.
 * @param scrollToEndLabel Accessibility label for a "scroll to end" action. No action is added
 * when `null`.
 * @param onEdgeChange Lets users move the bar to the other side: hold the thumb still (half a
 * second, or the system long-press timeout if that's longer), drag it across, and it snaps to the
 * nearest edge. Store the new edge and pass it back
 * as [edge], like any setting. Touch only. Off when `null`.
 */
@Composable
fun Modifier.scrollbar(
    listState: LazyListState,
    edge: FastScrollEdge = FastScrollEdge.END,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    minItemsToShow: Int = 10,
    hasMore: Boolean = false,
    style: ScrollbarStyle = DefaultScrollbarStyle,
    label: ScrollbarLabel? = null,
    scrollToStartLabel: String? = null,
    scrollToEndLabel: String? = null,
    onEdgeChange: ((FastScrollEdge) -> Unit)? = null,
): Modifier {
    val target = remember(listState) { ListScrollbarTarget(listState) }
    return scrollbarFor(
        target, edge, contentPadding, minItemsToShow, hasMore, style, label,
        scrollToStartLabel, scrollToEndLabel, onEdgeChange,
    )
}

/**
 * Draggable fast scroller for [androidx.compose.foundation.lazy.grid.LazyVerticalGrid] and
 * [androidx.compose.foundation.lazy.grid.LazyHorizontalGrid].
 *
 * Grids don't expose a span count, so items per line is estimated from the visible items when
 * the thumb is grabbed. Grids with uneven spans or full-span headers may land slightly off
 * the thumb position while dragging. See the list overload for the other parameters.
 */
@Composable
fun Modifier.scrollbar(
    gridState: LazyGridState,
    edge: FastScrollEdge = FastScrollEdge.END,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    minItemsToShow: Int = 10,
    hasMore: Boolean = false,
    style: ScrollbarStyle = DefaultScrollbarStyle,
    label: ScrollbarLabel? = null,
    scrollToStartLabel: String? = null,
    scrollToEndLabel: String? = null,
    onEdgeChange: ((FastScrollEdge) -> Unit)? = null,
): Modifier {
    val target = remember(gridState) { GridScrollbarTarget(gridState) }
    return scrollbarFor(
        target, edge, contentPadding, minItemsToShow, hasMore, style, label,
        scrollToStartLabel, scrollToEndLabel, onEdgeChange,
    )
}

// Targets: what the scrollbar needs from a lazy layout, so lists and grids share one code path.

internal interface ScrollbarTarget {
    val state: ScrollableState
    val isVertical: Boolean
    val isReversed: Boolean
    val totalItemsCount: Int

    /** Items that share one scroll line: 1 for lists, the estimated span count for grids. */
    val itemsPerLine: Int

    /** First visible item and how far it's scrolled, in list order whatever the layout. */
    val firstVisibleItemIndex: Int
    val firstVisibleItemScrollOffset: Int

    fun requestScrollToItem(index: Int, offset: Int)
}

internal class ListScrollbarTarget(override val state: LazyListState) : ScrollbarTarget {
    override val isVertical: Boolean
        get() = state.layoutInfo.orientation == Orientation.Vertical
    override val isReversed: Boolean
        get() = state.layoutInfo.reverseLayout
    override val totalItemsCount: Int
        get() = state.layoutInfo.totalItemsCount
    override val itemsPerLine: Int
        get() = 1
    override val firstVisibleItemIndex: Int
        get() = state.firstVisibleItemIndex
    override val firstVisibleItemScrollOffset: Int
        get() = state.firstVisibleItemScrollOffset

    override fun requestScrollToItem(index: Int, offset: Int) =
        state.requestScrollToItem(index, offset)
}

private class GridScrollbarTarget(override val state: LazyGridState) : ScrollbarTarget {
    override val isVertical: Boolean
        get() = state.layoutInfo.orientation == Orientation.Vertical
    override val isReversed: Boolean
        get() = state.layoutInfo.reverseLayout
    override val totalItemsCount: Int
        get() = state.layoutInfo.totalItemsCount

    // The span count only changes when the items or the grid's size change, but the draw phase
    // asks every frame, so it's cached on those two.
    private var cachedItemsPerLine = 1
    private var cachedItemCount = -1
    private var cachedViewportSize = IntSize.Zero

    /**
     * The most items found in any visible line. Visible items come in index order, so the items
     * of one line are next to each other and a single pass is enough, with no allocation.
     */
    override val itemsPerLine: Int
        get() {
            val layoutInfo = state.layoutInfo
            if (layoutInfo.totalItemsCount == cachedItemCount && layoutInfo.viewportSize == cachedViewportSize) {
                return cachedItemsPerLine
            }
            val visibleItems = layoutInfo.visibleItemsInfo
            val vertical = layoutInfo.orientation == Orientation.Vertical
            var mostInOneLine = 1
            var itemsInCurrentLine = 0
            var currentLine = Int.MIN_VALUE
            for (itemIndex in visibleItems.indices) {
                val item = visibleItems[itemIndex]
                val line = if (vertical) item.row else item.column
                if (line == currentLine) {
                    itemsInCurrentLine++
                } else {
                    currentLine = line
                    itemsInCurrentLine = 1
                }
                if (itemsInCurrentLine > mostInOneLine) mostInOneLine = itemsInCurrentLine
            }
            if (visibleItems.isNotEmpty()) { // don't cache a layout with nothing measured yet
                cachedItemsPerLine = mostInOneLine
                cachedItemCount = layoutInfo.totalItemsCount
                cachedViewportSize = layoutInfo.viewportSize
            }
            return mostInOneLine
        }
    override val firstVisibleItemIndex: Int
        get() = state.firstVisibleItemIndex
    override val firstVisibleItemScrollOffset: Int
        get() = state.firstVisibleItemScrollOffset

    override fun requestScrollToItem(index: Int, offset: Int) =
        state.requestScrollToItem(index, offset)
}

// METRICS AND GEOMETRY

/** Pins the ends exactly, because the list's own estimate drifts slightly. */
private fun ScrollableState.pinnedProgress(offset: Float, maxScroll: Float): Float = when {
    !canScrollBackward -> 0f
    !canScrollForward -> 1f
    else -> (offset / maxScroll).coerceIn(0f, 1f)
}

/**
 * How far the list is scrolled, in pixels from its start in list order, estimated from the first
 * visible item. Built from the same average line length that dragging maps through, so the thumb
 * and the drag always agree.
 *
 * The list's own scroll indicator offset isn't used here: in a reversed layout it runs in screen
 * order, so mirroring it again for reverseLayout flipped the thumb to the wrong end.
 */
internal fun logicalScrollOffset(
    firstVisibleItemIndex: Int,
    firstVisibleItemScrollOffset: Int,
    itemsPerLine: Int,
    averageLineLength: Float,
): Float = (firstVisibleItemIndex / itemsPerLine.coerceAtLeast(1)) * averageLineLength +
        firstVisibleItemScrollOffset

/** Progress is logical (0 = start of the list). Mirrored layouts show it flipped on screen. */
internal fun visualProgress(progress: Float, isMirrored: Boolean): Float =
    if (isMirrored) 1f - progress else progress

/** Mutable and reusable. Snapshot taken at grab time and used for the whole drag. */
internal class ScrollMetrics {
    var viewportLength = 1f
    var contentLength = 1f
    var averageLineLength = 1f
    var progress = 0f
    var itemCount = 0
    var itemsPerLine = 1
    var lineCount = 0

    val maxScroll: Float get() = (contentLength - viewportLength).coerceAtLeast(1f)
    val thumbFraction: Float
        get() = if (contentLength <= 0f) 1f else (viewportLength / contentLength).coerceIn(0f, 1f)

    fun update(target: ScrollbarTarget): Boolean {
        val indicator = target.state.scrollIndicatorState ?: return false
        val count = target.totalItemsCount
        val content = indicator.contentSize.toFloat()
        val viewport = indicator.viewportSize.toFloat()
        if (count <= 0 || content <= 0f || viewport <= 0f) return false

        val perLine = target.itemsPerLine.coerceAtLeast(1)
        itemCount = count
        itemsPerLine = perLine
        lineCount = (count + perLine - 1) / perLine
        contentLength = content
        viewportLength = viewport
        averageLineLength = (content / lineCount).coerceAtLeast(1f)
        val offset = logicalScrollOffset(
            target.firstVisibleItemIndex, target.firstVisibleItemScrollOffset, perLine, averageLineLength,
        )
        progress = target.state.pinnedProgress(offset, maxScroll)
        return true
    }
}

/** Thumb position along the scroll axis. Works for both orientations. */
internal class ThumbGeometry {
    var start = 0f
    var length = 0f
    var trackLength = 0f

    /** Where the thumb's travel begins along the scroll axis (its start at progress 0). */
    var trackStart = 0f

    fun set(
        density: Density,
        containerLength: Float,
        startPadding: Float,
        endPadding: Float,
        sizing: ThumbSizing,
        fraction: Float,
        visualProgress: Float,
        lengthOverride: Float,
    ) {
        val usable = (containerLength - startPadding - endPadding).coerceAtLeast(0f)
        trackStart = startPadding
        if (usable <= 0f) {
            start = startPadding; length = 0f; trackLength = 0f
            return
        }
        length = if (lengthOverride >= 0f) {
            lengthOverride.coerceAtMost(usable)
        } else {
            density.resolveThumbLength(sizing, usable, fraction)
        }
        trackLength = (usable - length).coerceAtLeast(0f)
        start = startPadding + trackLength * visualProgress
    }
}

internal fun Density.resolveThumbLength(
    sizing: ThumbSizing,
    usableLength: Float,
    fraction: Float,
): Float = when (sizing) {
    is ThumbSizing.Fixed -> sizing.length.toPx().coerceAtMost(usableLength)
    is ThumbSizing.Proportional -> {
        val minLength = sizing.minLength.toPx()
        // coerceAtLeast().coerceAtMost() can't throw, unlike coerceIn() with min > max.
        val maxLength = (usableLength * sizing.maxLengthFraction)
            .coerceAtLeast(minLength)
            .coerceAtMost(usableLength)
        (usableLength * fraction).coerceAtLeast(minLength).coerceAtMost(maxLength)
    }
}

private const val NO_LENGTH_OVERRIDE = -1f

/**
 * Offset used to reach the end of the list. Asking for "last item, offset 0" only lines up the
 * last item's top with the top of the list, which is a valid stopping point when that item is
 * taller than the list, so its bottom could never be reached. An offset far past any item makes
 * the list clamp to its true end instead. Small enough to never overflow inside the list.
 */
internal const val SCROLL_TO_END_OFFSET = 1_000_000_000

/**
 * No bounce, settles in about 300 ms. The threshold is far below a pixel: the default (0.01) would
 * end the glide 1% of the track early and snap the last few pixels.
 */
private val GLIDE_SPRING = spring(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessMediumLow,
    visibilityThreshold = 0.0001f,
)

/** Marks that the draw phase has no previous frame to compare with. */
private const val NO_BASELINE = -1

/**
 * While more items can still load, the thumb stops here instead of at the very end, because the
 * real end isn't known yet.
 */
private const val HAS_MORE_END_CAP = 0.95f

/**
 * Shortest hold that picks the thumb up to move it to the other edge: 500 ms, the long-press
 * default on iOS and in Flutter (including its drag-to-move `LongPressDraggable`). Android's
 * system long press is 400 ms, which is tuned for quick menus and too easy to hit by accident on
 * a thumb people also drag.
 */
private const val EDGE_MOVE_MIN_HOLD_MILLIS = 500L

/** Move hint shown next to a picked-up thumb: circle radius and its gap from the thumb. */
private val MOVE_HINT_RADIUS = 14.dp
private val MOVE_HINT_GAP = 8.dp

/** Below this fade the bar counts as hidden: it can't be grabbed and doesn't block system gestures. */
private const val GRABBABLE_ALPHA = 0.05f

/**
 * Extra length kept protected from system edge gestures past each end of the thumb, so the
 * protected area only needs updating every so often while the thumb moves.
 */
private val EDGE_GUARD_SLACK = 24.dp

/** How far a hovered thumb moves toward its pressed look (0 = none, 1 = fully pressed). */
private const val HOVER_HIGHLIGHT = 0.5f

/** An icon decides the thumb length itself. Returns [NO_LENGTH_OVERRIDE] for the default look. */
private fun Density.iconMainAxisLength(style: ScrollbarStyle, isVertical: Boolean): Float {
    val icon = style.thumbAppearance as? ThumbAppearance.Icon ?: return NO_LENGTH_OVERRIDE
    return (if (isVertical) icon.size.height else icon.size.width).toPx()
}

/**
 * Maps a progress (0..1) to the item and pixel offset to scroll to, packed into one Long so
 * nothing is allocated while dragging. Read it back with [keyIndex] and [keyOffset].
 */
internal fun scrollTargetKey(metrics: ScrollMetrics, progress: Float): Long {
    val index: Int
    val offset: Int
    when {
        progress <= 0f -> { index = 0; offset = 0 }
        progress >= 1f -> { index = metrics.itemCount - 1; offset = SCROLL_TO_END_OFFSET }
        else -> {
            val targetOffset = progress * metrics.maxScroll
            val line = (targetOffset / metrics.averageLineLength).toInt()
                .coerceIn(0, metrics.lineCount - 1)
            index = (line * metrics.itemsPerLine).coerceAtMost(metrics.itemCount - 1)
            offset = (targetOffset - line * metrics.averageLineLength).roundToInt().coerceAtLeast(0)
        }
    }
    return (index.toLong() shl 32) or (offset.toLong() and 0xFFFFFFFFL)
}

internal fun keyIndex(key: Long): Int = (key ushr 32).toInt()

internal fun keyOffset(key: Long): Int = key.toInt()

/**
 * Non-suspending, so it's safe from the pointer-input scope. Skips the request when the
 * target is unchanged. Returns the key of the request.
 */
private fun ScrollbarTarget.scrollToProgress(
    metrics: ScrollMetrics,
    progress: Float,
    lastKey: Long,
): Long {
    val key = scrollTargetKey(metrics, progress)
    if (key != lastKey) requestScrollToItem(keyIndex(key), keyOffset(key))
    return key
}

// SYSTEM GESTURES

/**
 * Keeps system edge gestures (Android's back swipe) from taking touches that start on the thumb.
 * Android implements it with system gesture exclusion; other platforms have no such API and use
 * [NoEdgeGestureGuard].
 */
internal interface EdgeGestureGuard {
    /** Protects this area, in the coordinates of the Compose root (the hosting view). */
    fun protect(left: Float, top: Float, right: Float, bottom: Float)

    /** Stops protecting anything. */
    fun clear()
}

internal object NoEdgeGestureGuard : EdgeGestureGuard {
    override fun protect(left: Float, top: Float, right: Float, bottom: Float) = Unit
    override fun clear() = Unit
}

@Composable
internal expect fun rememberEdgeGestureGuard(): EdgeGestureGuard

/** True when the protected span still covers the thumb, so it doesn't need updating. */
internal fun guardCoversThumb(guardStart: Float, guardEnd: Float, thumbStart: Float, thumbEnd: Float): Boolean =
    !guardStart.isNaN() && thumbStart >= guardStart && thumbEnd <= guardEnd

/** The span to protect around the thumb: the thumb plus [slack] on both ends, kept inside the list. */
internal fun guardSpanStart(thumbStart: Float, slack: Float): Float = (thumbStart - slack).coerceAtLeast(0f)

internal fun guardSpanEnd(thumbEnd: Float, slack: Float, containerLength: Float): Float =
    (thumbEnd + slack).coerceAtMost(containerLength)

/**
 * How much of the list overlaps the screen's top and bottom system gesture zones (where the
 * system's gestures always win, like Android's home swipe), in pixels. Updated after layout.
 */
internal class GestureZoneOverlap {
    var top = 0f
    var bottom = 0f
}

/** Overlap of a list starting at [listTop] with a top gesture zone [zoneHeight] tall. */
internal fun topGestureZoneOverlap(listTop: Float, listHeight: Float, zoneHeight: Float): Float =
    (zoneHeight - listTop).coerceIn(0f, listHeight.coerceAtLeast(0f))

/** Overlap of a list with a bottom gesture zone [zoneHeight] tall, at the bottom of the window. */
internal fun bottomGestureZoneOverlap(
    listTop: Float,
    listHeight: Float,
    rootHeight: Float,
    zoneHeight: Float,
): Float = (listTop + listHeight - (rootHeight - zoneHeight)).coerceIn(0f, listHeight.coerceAtLeast(0f))

// CONTROLLER AND CONFIG

/**
 * Owns all scrollbar state, so nothing is captured by a stale closure.
 * [style] is refreshed on every recomposition.
 */
private class ScrollbarController(
    private val scope: CoroutineScope,
    var style: ScrollbarStyle,
) {
    var dragging by mutableStateOf(false)
    var dragProgress by mutableFloatStateOf(0f)
    var settleFrom by mutableFloatStateOf(0f)

    val frozen = ScrollMetrics()
    val geometry = ThumbGeometry()
    val grabGeometry = ThumbGeometry()

    val fade = Animatable(0f)
    val press = Animatable(0f)
    val settle = Animatable(0f) // 1 = at release position, 0 = at live position

    /**
     * Offset added to the drawn thumb position that decays to 0. It hides jumps when the item
     * count changes (pages loading, prepends) and when the drag is re-anchored.
     */
    val glide = Animatable(0f)
    private var glideJob: Job? = null

    /**
     * A new glide offset waiting for [glide] to take it over, or NaN. `snapTo` can't apply until a
     * cancelled glide has let go, which can be a frame later, and that frame would draw the jump
     * the glide exists to hide. Plain field: it's only read together with [glide].
     */
    private var pendingGlide = Float.NaN

    /** The offset to draw. Reads [glide] every time, so the draw keeps observing it. */
    val glideOffset: Float
        get() {
            val animated = glide.value
            return if (pendingGlide.isNaN()) animated else pendingGlide
        }

    /** Shows [offset] from the very next frame, then runs [then] once [glide] holds it. */
    private fun replaceGlide(offset: Float, lowerBound: Float, upperBound: Float, then: suspend () -> Unit) {
        glideJob?.cancel()
        pendingGlide = offset
        // UNDISPATCHED, so it applies immediately when nothing else holds the glide.
        glideJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            // Set before snapTo, which clamps to the current bounds.
            glide.updateBounds(lowerBound = lowerBound, upperBound = upperBound)
            glide.snapTo(offset)
            pendingGlide = Float.NaN
            then()
        }
    }

    // What the previous non-drag frame drew. Plain fields on purpose: nothing observes them.
    var lastDrawnCount = NO_BASELINE
    var lastDrawnEndCap = 1f
    var lastDrawnProgress = 0f

    /** Text of the label bubble. Written while dragging, read by the draw phase. */
    var label by mutableStateOf<String?>(null)
    val labelLayoutCache = LabelLayoutCache()

    /** Smoothed viewport/content ratio. Only used by [ThumbSizing.Proportional] with smoothing. */
    val smoothedFraction = Animatable(1f)

    /** Fraction shown at grab time, held for the whole drag so the thumb never resizes. */
    var frozenFraction = 1f

    fun displayedFraction(sizing: ThumbSizing, rawFraction: Float): Float =
        if (sizing is ThumbSizing.Proportional && sizing.smoothed) smoothedFraction.value
        else rawFraction

    /** Where the list sits in the Compose root, for system gesture exclusion. Set after layout. */
    var positionInRoot = Offset.Zero

    /** Overlap with the top and bottom system gesture zones. Set after layout. */
    val gestureZones = GestureZoneOverlap()

    // The area currently protected from system edge gestures, in list coordinates (NaN = none).
    private var guardedMainStart = Float.NaN
    private var guardedMainEnd = Float.NaN
    private var guardedCrossStart = Float.NaN
    private var guardedCrossEnd = Float.NaN
    private var guardedOrigin = Offset.Unspecified

    /**
     * Protects the thumb's touch area from system edge gestures. Cheap to call every frame: the
     * guard is only updated when the thumb leaves the protected span, the strip changes, or the
     * list moves.
     */
    fun protectThumb(
        guard: EdgeGestureGuard,
        isVertical: Boolean,
        thumbStart: Float,
        thumbEnd: Float,
        crossStart: Float,
        crossEnd: Float,
        containerLength: Float,
        slack: Float,
    ) {
        val origin = positionInRoot
        if (guardCoversThumb(guardedMainStart, guardedMainEnd, thumbStart, thumbEnd) &&
            crossStart == guardedCrossStart && crossEnd == guardedCrossEnd && origin == guardedOrigin
        ) {
            return
        }
        val mainStart = guardSpanStart(thumbStart, slack)
        val mainEnd = guardSpanEnd(thumbEnd, slack, containerLength)
        guardedMainStart = mainStart
        guardedMainEnd = mainEnd
        guardedCrossStart = crossStart
        guardedCrossEnd = crossEnd
        guardedOrigin = origin
        if (isVertical) {
            guard.protect(origin.x + crossStart, origin.y + mainStart, origin.x + crossEnd, origin.y + mainEnd)
        } else {
            guard.protect(origin.x + mainStart, origin.y + crossStart, origin.x + mainEnd, origin.y + crossEnd)
        }
    }

    /** Hidden or not drawn: the edge belongs to the system again. */
    fun releaseThumb(guard: EdgeGestureGuard) {
        if (guardedMainStart.isNaN()) return
        guardedMainStart = Float.NaN
        guardedMainEnd = Float.NaN
        guardedCrossStart = Float.NaN
        guardedCrossEnd = Float.NaN
        guardedOrigin = Offset.Unspecified
        guard.clear()
    }

    // Mouse hover. None of this is used for touch.
    /** True while the mouse is over the bar's strip. Keeps the bar visible, like a drag does. */
    var stripHovered = false
        private set
    private var thumbHovered = false
    private var thumbHoverJob: Job? = null
    private var previewJob: Job? = null

    /** 0..1, how highlighted the thumb is because the mouse is over it. */
    val thumbHover = Animatable(0f)

    /** 0..1, fade of the hover preview bubble. */
    val previewAmount = Animatable(0f)

    /** Text and center (along the scroll axis) of the hover preview bubble. */
    var previewLabel by mutableStateOf<String?>(null)
    var previewCenter by mutableFloatStateOf(0f)

    // Moving the bar to the other edge (opt-in, touch only).
    /** True while the thumb is picked up and follows the finger across. */
    var movingEdge by mutableStateOf(false)
        private set

    /** Center of the thumb across the scroll axis while it is picked up. */
    var moveCrossCenter by mutableFloatStateOf(0f)

    /** 0 = at the drop position, 1 = settled at its edge. Animates after a drop. */
    val edgeSnap = Animatable(1f)
    private var edgeSnapFrom = 0f // read together with edgeSnap, which drives the redraws
    private var edgeSnapJob: Job? = null

    /** 0..1, fade of the move hint (a two-way arrow) shown while the thumb is picked up. */
    val moveHint = Animatable(0f)
    private var moveHintJob: Job? = null

    /** Which side of the thumb the hint is on: 0 = left (or top), 1 = right (or bottom). */
    val moveHintSide = Animatable(1f)
    private var moveHintSideJob: Job? = null

    /** Moves the hint to the side with more room. Does nothing if it's already there or heading there. */
    fun setMoveHintSide(onEndSide: Boolean, animate: Boolean = true) {
        val target = if (onEndSide) 1f else 0f
        if (moveHintSide.targetValue == target) return
        moveHintSideJob?.cancel()
        moveHintSideJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            if (animate) moveHintSide.animateTo(target, tween(150)) else moveHintSide.snapTo(target)
        }
    }

    private fun fadeMoveHint(target: Float) {
        moveHintJob?.cancel()
        moveHintJob = scope.launch { moveHint.animateTo(target, tween(150)) }
    }

    private val events = Channel<Unit>(Channel.CONFLATED)
    private var fadeJob: Job? = null
    private var lastScrollPosition = Long.MIN_VALUE
    private var appliedVisibility = ScrollbarVisibility.AutoHide

    /**
     * Called by the draw phase with the scroll position it just read (first visible item and its
     * offset, packed into one Long). Signals activity only on real movement, so no separate
     * watcher is needed: the draw phase already re-runs on every scroll.
     */
    fun onScrollPositionDrawn(position: Long) {
        when {
            lastScrollPosition == Long.MIN_VALUE -> {
                lastScrollPosition = position // baseline: don't show just because we attached
            }

            position != lastScrollPosition -> {
                lastScrollPosition = position
                signalActivity()
            }
        }
    }

    fun signalActivity() {
        // Always visible: nothing to show or hide, applyVisibility() already faded the bar in.
        if (style.visibility == ScrollbarVisibility.Always) return

        events.trySend(Unit)

        val job = fadeJob
        if (job != null && job.isActive && fade.targetValue != 0f) return

        job?.cancel() // also restarts immediately if we were mid fade-out
        fadeJob = scope.launch {
            fade.animateTo(1f, tween(style.fadeInDuration.inWholeMilliseconds.toInt()))
            while (true) {
                events.tryReceive() // drop stale events
                if (dragging || stripHovered) {
                    // Held or hovered: stay visible until that ends and sends an event.
                    events.receiveCatching().getOrNull() ?: return@launch
                    continue
                }
                val next = withTimeoutOrNull(style.hideDelay) {
                    events.receiveCatching().getOrNull()
                }
                if (next == null) break
            }
            if (!dragging && !stripHovered) {
                fade.animateTo(0f, tween(style.fadeOutDuration.inWholeMilliseconds.toInt()))
            }
        }
    }

    /**
     * Called when the visibility mode changes. The initial auto-hide value does nothing, so the
     * bar doesn't flash when it first appears.
     */
    fun applyVisibility(visibility: ScrollbarVisibility) {
        if (visibility == appliedVisibility) return
        appliedVisibility = visibility
        fadeJob?.cancel()
        fadeJob = null
        if (visibility == ScrollbarVisibility.Always) {
            fadeJob = scope.launch {
                fade.animateTo(1f, tween(style.fadeInDuration.inWholeMilliseconds.toInt()))
            }
        } else {
            signalActivity() // back to auto-hide: fades out after the usual delay
        }
    }

    /**
     * Starts a glide that hides a jump of [delta] (old position minus new position). A spring
     * carries over the running glide's speed, so jumps arriving in quick succession (a burst of
     * inserts) blend into one smooth movement instead of restarting from a standstill each time.
     */
    fun startGlide(delta: Float) {
        if (abs(delta) < 0.002f) return
        // Read before cancelling: a cancelled animation forgets its velocity.
        val velocity = glide.velocity
        val offset = (glideOffset + delta).coerceIn(-1f, 1f)
        // Only carry speed that heads toward the real position. Speed the other way (left over
        // from a change in the opposite direction) would push the thumb backwards first.
        val towardZero = if (velocity * offset < 0f) velocity else 0f
        // A spring with carried speed can overshoot. Bounding it between the offset and 0 stops it
        // on the real position instead, so the thumb never passes it and comes back.
        replaceGlide(offset, lowerBound = min(offset, 0f), upperBound = max(offset, 0f)) {
            glide.animateTo(0f, GLIDE_SPRING, initialVelocity = towardZero)
        }
    }

    /**
     * Called every non-drag frame with what is about to be drawn. When the item count or the end
     * cap changed since the last frame, the thumb would jump, so glide it instead.
     */
    fun noteShownProgress(itemCount: Int, endCap: Float, shownProgress: Float) {
        if (lastDrawnCount != NO_BASELINE && (itemCount != lastDrawnCount || endCap != lastDrawnEndCap)) {
            startGlide(lastDrawnProgress - shownProgress)
        }
        lastDrawnCount = itemCount
        lastDrawnEndCap = endCap
        lastDrawnProgress = shownProgress
    }

    fun setStripHovered(hovered: Boolean) {
        if (hovered == stripHovered) return
        stripHovered = hovered
        signalActivity() // shows the bar on enter, restarts the hide delay on exit
    }

    fun setThumbHovered(hovered: Boolean) {
        if (hovered == thumbHovered) return
        thumbHovered = hovered
        thumbHoverJob?.cancel()
        thumbHoverJob = scope.launch { thumbHover.animateTo(if (hovered) 1f else 0f, tween(120)) }
    }

    /** Shows the preview bubble, or hides it when [text] is null. Animates only on a change. */
    fun setPreview(text: String?, center: Float) {
        if (text == null) {
            hidePreview()
            return
        }
        previewLabel = text
        previewCenter = center
        if (previewAmount.targetValue != 1f) {
            previewJob?.cancel()
            previewJob = scope.launch { previewAmount.animateTo(1f, tween(120)) }
        }
    }

    fun hidePreview() {
        if (previewAmount.targetValue == 0f) return
        previewJob?.cancel()
        previewJob = scope.launch { previewAmount.animateTo(0f, tween(120)) }
    }

    /** Cheap to call repeatedly: every setter returns early when nothing changes. */
    fun endHover() {
        setStripHovered(false)
        setThumbHovered(false)
        hidePreview()
    }

    fun beginEdgeMove(center: Float, hintOnEndSide: Boolean) {
        setMoveHintSide(hintOnEndSide, animate = false) // no slide on pickup
        edgeSnapJob?.cancel()
        edgeSnapJob = scope.launch(start = CoroutineStart.UNDISPATCHED) { edgeSnap.snapTo(1f) }
        moveCrossCenter = center
        label = null // the bubble would sit on the wrong side while moving
        movingEdge = true
        fadeMoveHint(1f)
    }

    /** Glides the thumb from where it was dropped to its edge (new or unchanged). */
    fun endEdgeMove() {
        if (!movingEdge) return
        edgeSnapFrom = moveCrossCenter
        edgeSnapJob?.cancel()
        // UNDISPATCHED so the snap starts at the drop position on the very next frame.
        edgeSnapJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            edgeSnap.snapTo(0f)
            edgeSnap.animateTo(1f, spring(stiffness = Spring.StiffnessMediumLow))
        }
        movingEdge = false
        fadeMoveHint(0f)
    }

    /** Where the thumb is drawn across the scroll axis, given where it rests at its edge. */
    fun displayedCrossStart(restingCrossStart: Float, thumbCrossLength: Float): Float {
        if (movingEdge) return moveCrossCenter - thumbCrossLength / 2f
        val snap = edgeSnap.value
        if (snap >= 1f) return restingCrossStart
        return lerp(edgeSnapFrom - thumbCrossLength / 2f, restingCrossStart, snap)
    }

    fun beginDrag(progress: Float) {
        replaceGlide(0f, lowerBound = 0f, upperBound = 0f) {}
        lastDrawnCount = NO_BASELINE
        dragProgress = progress
        dragging = true
        signalActivity()
    }

    fun endDrag() {
        settleFrom = (dragProgress + glideOffset).coerceIn(0f, 1f)
        replaceGlide(0f, lowerBound = 0f, upperBound = 0f) {}
        lastDrawnCount = NO_BASELINE
        // UNDISPATCHED so settle is 1f before the next draw (no one-frame flash).
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            settle.snapTo(1f)
            settle.animateTo(0f, tween(150))
        }
        dragging = false // before signaling, so the fade job sees the drag has ended
        signalActivity()
    }

    fun close() {
        fadeJob?.cancel()
        glideJob?.cancel()
        thumbHoverJob?.cancel()
        previewJob?.cancel()
        edgeSnapJob?.cancel()
        moveHintJob?.cancel()
        moveHintSideJob?.cancel()
        fadeJob = null
        events.close()
    }
}

/**
 * Keeps the last measured label, so an unchanged label isn't measured again on every frame while
 * dragging. Only used from the draw phase.
 */
private class LabelLayoutCache {
    private var text: String? = null
    private var maxWidth = -1
    private var textStyle: TextStyle? = null
    private var layoutDirection: LayoutDirection? = null
    private var density = 0f
    private var fontScale = 0f
    private var layout: TextLayoutResult? = null

    fun measure(
        textMeasurer: TextMeasurer,
        text: String,
        textStyle: TextStyle,
        maxWidth: Int,
        layoutDirection: LayoutDirection,
        density: Density,
    ): TextLayoutResult {
        val cached = layout
        if (cached != null &&
            text == this.text &&
            maxWidth == this.maxWidth &&
            textStyle == this.textStyle &&
            layoutDirection == this.layoutDirection &&
            density.density == this.density &&
            density.fontScale == this.fontScale
        ) {
            return cached
        }
        val result = textMeasurer.measure(
            text = text,
            style = textStyle,
            overflow = TextOverflow.Ellipsis,
            softWrap = false,
            maxLines = 1,
            constraints = Constraints(maxWidth = maxWidth),
            layoutDirection = layoutDirection,
            density = density,
        )
        this.text = text
        this.maxWidth = maxWidth
        this.textStyle = textStyle
        this.layoutDirection = layoutDirection
        this.density = density.density
        this.fontScale = density.fontScale
        layout = result
        return result
    }
}

/** Everything the draw and pointer code read from the call site, bundled for one-shot updates. */
internal class ScrollbarConfig(
    val style: ScrollbarStyle,
    val contentPadding: PaddingValues,
    val minItemsToShow: Int,
    val edge: FastScrollEdge,
    val layoutDirection: LayoutDirection,
    val label: ScrollbarLabel?,
    val hasMore: Boolean,
    val onEdgeChange: ((FastScrollEdge) -> Unit)?,
    val gestureZones: GestureZoneOverlap = GestureZoneOverlap(),
) {
    /** Largest thumb progress. Below 1 while more items can still load. */
    val endCap: Float get() = if (hasMore) HAS_MORE_END_CAP else 1f

    /** True when the bar sits at the left (vertical) or top (horizontal) of the list. */
    fun isCrossAxisAtOrigin(isVertical: Boolean): Boolean =
        if (isVertical) (edge == FastScrollEdge.START) == (layoutDirection == LayoutDirection.Ltr)
        else edge == FastScrollEdge.START

    /** The edge whose side is the left (vertical) or top (horizontal) when [atOrigin]. */
    fun edgeAt(isVertical: Boolean, atOrigin: Boolean): FastScrollEdge {
        val start = if (isVertical) atOrigin == (layoutDirection == LayoutDirection.Ltr) else atOrigin
        return if (start) FastScrollEdge.START else FastScrollEdge.END
    }

    /** True when scroll progress runs against the physical axis (reverseLayout, or RTL rows). */
    fun isMirrored(target: ScrollbarTarget): Boolean =
        target.isReversed != (!target.isVertical && layoutDirection == LayoutDirection.Rtl)

    /**
     * How far the thumb stops from each end of the track. For the capsule look it's at least half
     * the capsule's extra length, so the capsule never has to be pushed back inside the list at
     * the ends, which would leave the thumb off-center in it.
     */
    val thumbEndPadding: Dp
        get() = when (style.thumbAppearance) {
            ThumbAppearance.Default -> maxOf(style.trackPadding, style.capsuleExtraLength / 2)
            ThumbAppearance.Bar, is ThumbAppearance.Icon -> style.trackPadding
        }

    /**
     * Where the track starts, in pixels: past the content padding and, for vertical lists, past
     * the top system gesture zone. The larger of the two is used, not the sum, so passing the
     * system insets as content padding doesn't push the track in twice.
     */
    fun mainAxisStartPaddingPx(density: Density, isVertical: Boolean): Float = with(density) {
        val content = (
                if (isVertical) contentPadding.calculateTopPadding()
                else contentPadding.calculateLeftPadding(layoutDirection)
                ).toPx()
        val zone = if (isVertical) gestureZones.top else 0f
        max(content, zone) + thumbEndPadding.toPx()
    }

    /** Where the track ends: the same rule at the bottom (or right). */
    fun mainAxisEndPaddingPx(density: Density, isVertical: Boolean): Float = with(density) {
        val content = (
                if (isVertical) contentPadding.calculateBottomPadding()
                else contentPadding.calculateRightPadding(layoutDirection)
                ).toPx()
        val zone = if (isVertical) gestureZones.bottom else 0f
        max(content, zone) + thumbEndPadding.toPx()
    }

    /**
     * Lifts a horizontal bar out of the top or bottom system gesture zone, so grabbing it can't
     * trigger Android's home swipe. Vertical bars sit at the side edges, where the edge guard
     * handles the back gesture instead.
     */
    fun crossZoneInsetPx(isVertical: Boolean): Float = when {
        isVertical -> 0f
        isCrossAxisAtOrigin(isVertical = false) -> gestureZones.top
        else -> gestureZones.bottom
    }
}

// TRACK: where the optional visible track goes. Pure functions, so they're unit tested.

/** Where the visible part of the track starts along the scroll axis, in screen order. */
internal fun trackDrawStart(extent: TrackExtent, geometry: ThumbGeometry, isMirrored: Boolean): Float =
    when {
        extent == TrackExtent.UpToThumb && isMirrored -> geometry.start + geometry.length / 2f
        else -> geometry.trackStart
    }

/** Where the visible part of the track ends along the scroll axis, in screen order. */
internal fun trackDrawEnd(extent: TrackExtent, geometry: ThumbGeometry, isMirrored: Boolean): Float =
    when {
        extent == TrackExtent.UpToThumb && !isMirrored -> geometry.start + geometry.length / 2f
        else -> geometry.trackStart + geometry.trackLength + geometry.length
    }

/** Centers the track on the thumb across the scroll axis, moved inward if it would leave the list. */
internal fun trackCrossStart(thickness: Float, thumbCrossCenter: Float, crossLength: Float): Float =
    (thumbCrossCenter - thickness / 2f).coerceIn(0f, (crossLength - thickness).coerceAtLeast(0f))

/**
 * Length of one repeated copy along the scroll axis: [tileLength] when given (> 0), otherwise the
 * painter's own proportions at [thickness], otherwise a square. Pass NaN for unknown sizes.
 */
internal fun repeatTileLength(
    tileLength: Float,
    intrinsicMain: Float,
    intrinsicCross: Float,
    thickness: Float,
): Float {
    val length = when {
        tileLength > 0f -> tileLength
        intrinsicMain > 0f && intrinsicCross > 0f && intrinsicMain.isFinite() && intrinsicCross.isFinite() ->
            thickness * intrinsicMain / intrinsicCross
        else -> thickness
    }
    return length.coerceAtLeast(MIN_TRACK_TILE_LENGTH)
}

/** Keeps a tiny tile from turning one frame into thousands of draws. */
internal const val MIN_TRACK_TILE_LENGTH = 2f

// INPUT HELPERS: shared by touch, mouse press and mouse hover, so they agree on where the thumb is.

/** How wide the strip along the bar's edge that accepts input is, across the scroll axis. */
internal fun Density.barStripThickness(style: ScrollbarStyle, isVertical: Boolean): Float {
    // An icon wider than the touch target must still be grabbable across its whole width.
    val iconCrossExtent = (style.thumbAppearance as? ThumbAppearance.Icon)
        ?.let { icon ->
            (if (isVertical) icon.size.width else icon.size.height).toPx() + style.edgePadding.toPx()
        } ?: 0f
    return max(style.touchTargetThickness.toPx(), iconCrossExtent)
}

/** True when [crossPosition] is inside the strip along the bar's edge that accepts input. */
internal fun Density.isInBarStrip(
    config: ScrollbarConfig,
    isVertical: Boolean,
    crossLength: Float,
    crossPosition: Float,
): Boolean {
    val reach = config.crossZoneInsetPx(isVertical) + barStripThickness(config.style, isVertical)
    return if (config.isCrossAxisAtOrigin(isVertical)) crossPosition <= reach
    else crossPosition >= crossLength - reach
}

/** Size of the thumb across the scroll axis: the capsule, or the icon. */
internal fun Density.crossLengthOfThumb(style: ScrollbarStyle, isVertical: Boolean): Float =
    when (val appearance = style.thumbAppearance) {
        is ThumbAppearance.Icon ->
            (if (isVertical) appearance.size.width else appearance.size.height).toPx()
        ThumbAppearance.Bar -> style.pressedThumbThickness.toPx() // room to widen in place
        ThumbAppearance.Default -> style.capsuleThickness.toPx()
    }

/** Where the thumb rests across the scroll axis, at its edge. */
internal fun Density.restingCrossStart(
    config: ScrollbarConfig,
    isVertical: Boolean,
    crossLength: Float,
    thumbCrossLength: Float,
): Float {
    val inset = config.style.edgePadding.toPx() + config.crossZoneInsetPx(isVertical)
    return if (config.isCrossAxisAtOrigin(isVertical)) inset else crossLength - thumbCrossLength - inset
}

/** The hint goes on the thumb's end side (right or bottom) while the thumb is in the first half. */
internal fun moveHintOnEndSide(thumbCrossCenter: Float, crossLength: Float): Boolean =
    thumbCrossCenter < crossLength / 2f

/**
 * Center of the move hint across the scroll axis. [endSide] is 0 for the start side (left or top
 * of the thumb), 1 for the end side, and in between while it slides over. Kept inside the list.
 */
internal fun moveHintCrossCenter(
    thumbCrossStart: Float,
    thumbCrossLength: Float,
    crossLength: Float,
    hintRadius: Float,
    gap: Float,
    endSide: Float,
): Float {
    val startSideCenter = thumbCrossStart - gap - hintRadius
    val endSideCenter = thumbCrossStart + thumbCrossLength + gap + hintRadius
    val center = lerp(startSideCenter, endSideCenter, endSide)
    return center.coerceAtLeast(hintRadius).coerceAtMost((crossLength - hintRadius).coerceAtLeast(hintRadius))
}

/**
 * How far the hint moves along the scroll axis to arc around the thumb while it slides from one
 * side to the other: 0 at either side, half the thumb + gap + radius halfway.
 */
internal fun moveHintArcOffset(endSide: Float, thumbHalfLength: Float, gap: Float, hintRadius: Float): Float =
    sin(PI.toFloat() * endSide.coerceIn(0f, 1f)) * (thumbHalfLength + gap + hintRadius)

/**
 * Lays out the thumb where it is drawn right now, into [metrics] and [geometry]. Returns false
 * when no thumb is drawn (too few items, nothing to scroll, or no room for a track).
 */
private fun Density.layoutThumbForInput(
    target: ScrollbarTarget,
    config: ScrollbarConfig,
    controller: ScrollbarController,
    metrics: ScrollMetrics,
    geometry: ThumbGeometry,
    isVertical: Boolean,
    containerLength: Float,
): Boolean {
    if (!metrics.update(target) || metrics.itemCount <= config.minItemsToShow) return false
    // Nothing to scroll means nothing is drawn, so nothing may be grabbed either. This matters
    // in Always mode, where the bar's alpha stays at 1.
    if (metrics.contentLength <= metrics.viewportLength) return false
    val style = config.style
    geometry.set(
        density = this,
        containerLength = containerLength,
        startPadding = config.mainAxisStartPaddingPx(this, isVertical),
        endPadding = config.mainAxisEndPaddingPx(this, isVertical),
        sizing = style.thumbSizing,
        fraction = controller.displayedFraction(style.thumbSizing, metrics.thumbFraction),
        visualProgress = visualProgress(metrics.progress * config.endCap, config.isMirrored(target)),
        lengthOverride = iconMainAxisLength(style, isVertical),
    )
    return geometry.trackLength > 0f
}

/**
 * Thumb position (0..endCap) when the thumb is centered on [mainPosition]. Mouse track clicks
 * and the hover preview both use it, so a click lands exactly where the preview pointed.
 */
internal fun centeredProgress(
    geometry: ThumbGeometry,
    startPadding: Float,
    mainPosition: Float,
    isMirrored: Boolean,
): Float {
    val onScreen = ((mainPosition - geometry.length / 2f - startPadding) / geometry.trackLength)
        .coerceIn(0f, 1f)
    return visualProgress(onScreen, isMirrored)
}

// MODIFIER

@Composable
private fun Modifier.scrollbarFor(
    target: ScrollbarTarget,
    edge: FastScrollEdge,
    contentPadding: PaddingValues,
    minItemsToShow: Int,
    hasMore: Boolean,
    style: ScrollbarStyle,
    label: ScrollbarLabel?,
    // Accessibility labels
    scrollToStartLabel: String?,
    scrollToEndLabel: String?,
    onEdgeChange: ((FastScrollEdge) -> Unit)?,
): Modifier {
    val haptics = LocalHapticFeedback.current
    val layoutDirection = LocalLayoutDirection.current
    val scope = rememberCoroutineScope()
    val textMeasurer = rememberTextMeasurer()

    val controller = remember(scope) { ScrollbarController(scope, style) }

    val config = ScrollbarConfig(
        style, contentPadding, minItemsToShow, edge, layoutDirection, label, hasMore, onEdgeChange,
        gestureZones = controller.gestureZones,
    )
    val latestConfig by rememberUpdatedState(config)

    // Android: keeps the back gesture off the visible thumb. A no-op elsewhere.
    val edgeGuard = rememberEdgeGestureGuard()
    // Where the system's gestures always win (Android's home swipe, for example).
    val gestureInsets = WindowInsets.mandatorySystemGestures
    val density = LocalDensity.current
    val windowInfo = LocalWindowInfo.current

    SideEffect { controller.style = style }
    DisposableEffect(controller) { onDispose { controller.close() } }
    DisposableEffect(controller, edgeGuard) { onDispose { controller.releaseThumb(edgeGuard) } }

    // Switches between auto-hide and always-visible without restarting anything else.
    LaunchedEffect(controller, style.visibility) {
        controller.applyVisibility(style.visibility)
    }

    // snapshotFlow instead of keying on `dragging`: no recomposition at drag start/end.
    LaunchedEffect(controller) {
        snapshotFlow { controller.dragging }.collectLatest { isDragging ->
            controller.press.animateTo(if (isDragging) 1f else 0f, tween(120))
        }
    }

    // Proportional sizing only: animates the estimated ratio so content-size jitter is hidden.
    // The default fixed thumb doesn't launch it, so it doesn't re-run on every scroll frame.
    val sizingOption = style.thumbSizing
    val smoothingEnabled = sizingOption is ThumbSizing.Proportional && sizingOption.smoothed
    LaunchedEffect(controller, target, smoothingEnabled) {
        if (!smoothingEnabled) return@LaunchedEffect
        var hasBaseline = false
        snapshotFlow {
            val sizing = latestConfig.style.thumbSizing
            val indicator = target.state.scrollIndicatorState
            if (sizing is ThumbSizing.Proportional && sizing.smoothed &&
                indicator != null && indicator.contentSize > 0
            ) {
                (indicator.viewportSize.toFloat() / indicator.contentSize).coerceIn(0f, 1f)
            } else {
                null
            }
        }.collectLatest { rawFraction ->
            if (rawFraction == null) return@collectLatest
            if (hasBaseline) {
                controller.smoothedFraction.animateTo(
                    rawFraction,
                    spring(stiffness = Spring.StiffnessMediumLow),
                )
            } else {
                controller.smoothedFraction.snapTo(rawFraction)
                hasBaseline = true
            }
        }
    }

    val pointerModifier = Modifier.pointerInput(target) {
        val slop = 8.dp.toPx()

        awaitEachGesture {
            // Initial pass: we see the touch before the list does.
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)

            // Not visible means not grabbable.
            if (controller.fade.value < GRABBABLE_ALPHA) return@awaitEachGesture

            val currentConfig = latestConfig
            val isVertical = target.isVertical
            val crossLength = (if (isVertical) size.width else size.height).toFloat()
            val downMain = if (isVertical) down.position.y else down.position.x
            val downCross = if (isVertical) down.position.x else down.position.y

            // Cheapest check first: most touches on the list are nowhere near the bar, and they
            // must not pay for the metrics below.
            if (!isInBarStrip(currentConfig, isVertical, crossLength, downCross)) {
                return@awaitEachGesture
            }

            // Mouse: only the primary button grabs. Touch never takes this branch.
            val isMouse = down.type == PointerType.Mouse
            if (isMouse && !currentEvent.buttons.isPrimaryPressed) return@awaitEachGesture

            val endCap = currentConfig.endCap
            val metrics = controller.frozen
            val geometry = controller.grabGeometry
            val containerLength = (if (isVertical) size.height else size.width).toFloat()
            if (!layoutThumbForInput(
                    target, currentConfig, controller, metrics, geometry, isVertical, containerLength,
                )
            ) {
                return@awaitEachGesture
            }
            val isMirrored = currentConfig.isMirrored(target)
            val startPadding = currentConfig.mainAxisStartPaddingPx(this, isVertical)
            val trackLength = geometry.trackLength
            val shownFraction =
                controller.displayedFraction(currentConfig.style.thumbSizing, metrics.thumbFraction)

            val onThumb =
                downMain in (geometry.start - slop)..(geometry.start + geometry.length + slop)

            // Touch off the thumb: consume nothing, the list handles it. A mouse click on the
            // track jumps there instead and then drags like a grab.
            if (!onThumb && !isMouse) return@awaitEachGesture

            // On the thumb, keep the finger's position inside it so it never jumps. On the
            // track, center the thumb under the cursor (same mapping as the hover preview).
            var grabOffset =
                if (onThumb) (downMain - geometry.start).coerceIn(0f, geometry.length)
                else geometry.length / 2f

            /** Thumb position (0..endCap) for a finger position. Not yet divided by [endCap]. */
            fun progressAt(mainPosition: Float): Float {
                val onScreen = ((mainPosition - grabOffset - startPadding) / trackLength)
                    .coerceIn(0f, 1f)
                return visualProgress(onScreen, isMirrored)
            }

            val startProgress =
                if (onThumb) metrics.progress * endCap
                else centeredProgress(geometry, startPadding, downMain, isMirrored).coerceAtMost(endCap)

            val labelConfig = currentConfig.label
            var lastLabelIndex = Int.MIN_VALUE
            var countWatcher: Job? = null
            try {
                controller.frozenFraction = shownFraction
                controller.endHover() // a press replaces any hover preview
                if (labelConfig != null) {
                    lastLabelIndex = keyIndex(
                        scrollTargetKey(metrics, (startProgress / endCap).coerceIn(0f, 1f)),
                    )
                    controller.label = labelConfig.provider(lastLabelIndex)
                } else {
                    controller.label = null
                }
                controller.beginDrag(startProgress)
                if (!isMouse) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                down.consume()
                scope.launch { target.state.stopScroll() } // cancel any running fling

                var lastKey = Long.MIN_VALUE
                var lastMainPosition = downMain

                // Track click: jump right away, before the mouse moves.
                if (!onThumb) {
                    lastKey = target.scrollToProgress(
                        metrics,
                        (startProgress / endCap).coerceIn(0f, 1f),
                        lastKey,
                    )
                }

                // The item count changed mid-drag (a page loaded, items were prepended). Refresh
                // the metrics and continue from where the list really is, with the finger kept
                // on the thumb's new position, so there is no fake end and no jump.
                fun rebaseToLiveMetrics() {
                    val shownBefore = controller.dragProgress
                    if (!metrics.update(target)) return
                    val shownNow = metrics.progress * endCap
                    grabOffset = lastMainPosition - startPadding -
                            trackLength * visualProgress(shownNow, isMirrored)
                    controller.dragProgress = shownNow
                    controller.startGlide(shownBefore - shownNow)
                    lastKey = Long.MIN_VALUE
                }

                // Covers a finger held still while a page loads. Exists only during a drag.
                countWatcher = scope.launch {
                    snapshotFlow { target.totalItemsCount }.collect { count ->
                        if (count != metrics.itemCount) rebaseToLiveMetrics()
                    }
                }

                // Edge move: a long press on the thumb without moving picks it up. Moving first
                // makes it a normal drag for the rest of the gesture.
                val edgeMoveEnabled = currentConfig.onEdgeChange != null && onThumb && !isMouse
                val grabMark = TimeSource.Monotonic.markNow()
                // At least 500 ms, and never shorter than the system long press, so people who set
                // a slower touch-and-hold in accessibility settings keep their longer delay.
                val holdMillis = max(viewConfiguration.longPressTimeoutMillis, EDGE_MOVE_MIN_HOLD_MILLIS)
                val touchSlop = viewConfiguration.touchSlop
                var movedSinceDown = false
                val crossThumbLength = crossLengthOfThumb(currentConfig.style, isVertical)
                val restingCenter = restingCrossStart(
                    currentConfig, isVertical, crossLength, crossThumbLength,
                ) + crossThumbLength / 2f

                while (true) {
                    val waitingForLongPress =
                        edgeMoveEnabled && !movedSinceDown && !controller.movingEdge
                    val event = if (waitingForLongPress) {
                        val remaining = holdMillis - grabMark.elapsedNow().inWholeMilliseconds
                        if (remaining <= 0L) null
                        else withTimeoutOrNull(remaining) { awaitPointerEvent(PointerEventPass.Initial) }
                    } else {
                        awaitPointerEvent(PointerEventPass.Initial)
                    }
                    if (event == null) {
                        // Held still long enough: pick the thumb up.
                        controller.beginEdgeMove(
                            restingCenter,
                            hintOnEndSide = moveHintOnEndSide(restingCenter, crossLength),
                        )
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        continue
                    }
                    var change: PointerInputChange? = null
                    for (changeIndex in event.changes.indices) {
                        if (event.changes[changeIndex].id == down.id) {
                            change = event.changes[changeIndex]
                            break
                        }
                    }
                    if (change == null) break
                    if (!change.pressed) {
                        change.consume()
                        if (controller.movingEdge) {
                            // Dropped: the nearest edge wins. The app stores it and passes it
                            // back as `edge`; until then the thumb glides toward the old edge.
                            val newEdge = currentConfig.edgeAt(
                                isVertical,
                                atOrigin = controller.moveCrossCenter < crossLength / 2f,
                            )
                            controller.endEdgeMove()
                            if (newEdge != currentConfig.edge) currentConfig.onEdgeChange?.invoke(newEdge)
                        }
                        break
                    }
                    if (!movedSinceDown && (change.position - down.position).getDistance() > touchSlop) {
                        movedSinceDown = true
                    }
                    if (controller.movingEdge) {
                        // Picked up: follow the finger across, no scrolling.
                        val cross = if (isVertical) change.position.x else change.position.y
                        val halfThumb = crossThumbLength / 2f
                        controller.moveCrossCenter = (restingCenter + cross - downCross)
                            .coerceAtLeast(halfThumb)
                            .coerceAtMost((crossLength - halfThumb).coerceAtLeast(halfThumb))
                        // Crossing the middle slides the hint around to the side with more room.
                        controller.setMoveHintSide(moveHintOnEndSide(controller.moveCrossCenter, crossLength))
                        change.consume()
                        continue
                    }
                    val mainPosition = if (isVertical) change.position.y else change.position.x
                    lastMainPosition = mainPosition
                    if (target.totalItemsCount != metrics.itemCount) rebaseToLiveMetrics()
                    // Capped while more can load, so the thumb never claims the real end.
                    controller.dragProgress = progressAt(mainPosition).coerceAtMost(endCap)
                    lastKey = target.scrollToProgress(
                        metrics,
                        (controller.dragProgress / endCap).coerceIn(0f, 1f),
                        lastKey,
                    )
                    if (labelConfig != null) {
                        val labelIndex = keyIndex(lastKey)
                        if (labelIndex != lastLabelIndex) {
                            lastLabelIndex = labelIndex
                            val newText = labelConfig.provider(labelIndex)
                            if (newText != controller.label) {
                                if (labelConfig.sectionHaptics && newText != null && !isMouse) {
                                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                }
                                controller.label = newText
                            }
                        }
                    }
                    change.consume()
                }
            } finally {
                // Runs on lift, cancel, and pointer-input restart, so no stuck state.
                countWatcher?.cancel()
                controller.endEdgeMove() // canceled while picked up: glide back
                controller.endDrag()
            }
        }
    }
    // Mouse hover: reveal, thumb highlight and track preview. Watches events without consuming
    // any, so wheel and trackpad scrolling and the list's own hover handling are unaffected.
    // Touch events only reach endHover(), which returns early when nothing is hovered.
    val hoverModifier = Modifier.pointerInput(target) {
        val hoverMetrics = ScrollMetrics()
        val hoverGeometry = ThumbGeometry()
        awaitPointerEventScope {
            var lastPreviewIndex = Int.MIN_VALUE
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull() ?: continue
                val isMouseHover = change.type == PointerType.Mouse && !change.pressed
                if (!isMouseHover || event.type == PointerEventType.Exit || controller.dragging) {
                    controller.endHover()
                    lastPreviewIndex = Int.MIN_VALUE
                    continue
                }
                // Scroll events count too: the thumb moves under a resting cursor.
                if (event.type != PointerEventType.Move &&
                    event.type != PointerEventType.Enter &&
                    event.type != PointerEventType.Scroll
                ) {
                    continue
                }

                val config = latestConfig
                val isVertical = target.isVertical
                val crossLength = (if (isVertical) size.width else size.height).toFloat()
                val main = if (isVertical) change.position.y else change.position.x
                val cross = if (isVertical) change.position.x else change.position.y
                if (!isInBarStrip(config, isVertical, crossLength, cross)) {
                    controller.endHover()
                    lastPreviewIndex = Int.MIN_VALUE
                    continue
                }
                controller.setStripHovered(true)

                val containerLength = (if (isVertical) size.height else size.width).toFloat()
                if (!layoutThumbForInput(
                        target, config, controller, hoverMetrics, hoverGeometry,
                        isVertical, containerLength,
                    )
                ) {
                    controller.setThumbHovered(false)
                    controller.hidePreview()
                    lastPreviewIndex = Int.MIN_VALUE
                    continue
                }

                val onThumb = main in hoverGeometry.start..(hoverGeometry.start + hoverGeometry.length)
                controller.setThumbHovered(onThumb)

                val labelConfig = config.label
                if (onThumb || labelConfig == null) {
                    controller.hidePreview()
                    lastPreviewIndex = Int.MIN_VALUE
                    continue
                }
                val startPadding = config.mainAxisStartPaddingPx(this, isVertical)
                val isMirrored = config.isMirrored(target)
                val shown = centeredProgress(hoverGeometry, startPadding, main, isMirrored)
                val index = keyIndex(
                    scrollTargetKey(hoverMetrics, (shown / config.endCap).coerceIn(0f, 1f)),
                )
                // Center of the thumb if it were moved here, so the bubble clamps at the ends
                // exactly like the thumb would.
                val center = startPadding +
                        hoverGeometry.trackLength * visualProgress(shown, isMirrored) +
                        hoverGeometry.length / 2f
                when {
                    index != lastPreviewIndex -> {
                        lastPreviewIndex = index
                        controller.setPreview(labelConfig.provider(index), center)
                    }

                    controller.previewLabel != null -> controller.previewCenter = center
                }
            }
        }
    }

    val accessibilityModifier = remember(target, scrollToStartLabel, scrollToEndLabel) {
        val actions = listOfNotNull(
            scrollToStartLabel?.let { label ->
                CustomAccessibilityAction(label) {
                    target.requestScrollToItem(0, 0)
                    true
                }
            },
            scrollToEndLabel?.let { label ->
                CustomAccessibilityAction(label) {
                    // The list clamps this to its true end, even past a tall last item.
                    target.requestScrollToItem((target.totalItemsCount - 1).coerceAtLeast(0), SCROLL_TO_END_OFFSET)
                    true
                }
            },
        )
        if (actions.isEmpty()) Modifier else Modifier.semantics { customActions = actions }
    }
    // After layout: where the list sits (for the edge guard) and how much of it overlaps the top
    // and bottom system gesture zones (to keep the track out of them).
    // Insets are measured from the window's edges, so the overlap uses the list's position in the
    // window (a Compose view may not start at the top of the window). The edge guard uses the
    // position in the Compose root instead, because that's the hosting view's coordinate space.
    val positionModifier = Modifier.onGloballyPositioned { coordinates ->
        controller.positionInRoot = coordinates.positionInRoot()
        val listTop = coordinates.positionInWindow().y
        val listHeight = coordinates.size.height.toFloat()
        controller.gestureZones.top = topGestureZoneOverlap(
            listTop = listTop,
            listHeight = listHeight,
            zoneHeight = gestureInsets.getTop(density).toFloat(),
        )
        controller.gestureZones.bottom = bottomGestureZoneOverlap(
            listTop = listTop,
            listHeight = listHeight,
            rootHeight = windowInfo.containerSize.height.toFloat(),
            zoneHeight = gestureInsets.getBottom(density).toFloat(),
        )
    }

    return this
        .then(accessibilityModifier)
        .then(positionModifier)
        .then(pointerModifier)
        .then(hoverModifier)
        .drawWithContent {
            drawContent()

            val isVertical = target.isVertical
            val isMirrored = config.isMirrored(target)
            val alpha: Float
            val fraction: Float
            val progress: Float

            if (controller.dragging) {
                // The thumb follows the finger, not the live scroll offset, while dragging.
                alpha = 1f
                fraction = controller.frozenFraction
                progress = (controller.dragProgress + controller.glideOffset).coerceIn(0f, 1f)
            } else {
                val indicator = target.state.scrollIndicatorState ?: run {
                    controller.releaseThumb(edgeGuard)
                    return@drawWithContent
                }
                val content = indicator.contentSize.toFloat()
                val viewport = indicator.viewportSize.toFloat()
                val itemCount = target.totalItemsCount
                if (viewport <= 0f || content <= viewport || itemCount <= config.minItemsToShow) {
                    controller.lastDrawnCount = NO_BASELINE // nothing drawn, nothing to compare
                    controller.releaseThumb(edgeGuard)
                    return@drawWithContent
                }

                // Read before the alpha check so scrolling always invalidates the draw, even while
                // the bar is hidden. That's also how a hidden bar notices scrolling and wakes up.
                val firstIndex = target.firstVisibleItemIndex
                val firstOffset = target.firstVisibleItemScrollOffset
                controller.onScrollPositionDrawn((firstIndex.toLong() shl 32) or (firstOffset.toLong() and 0xFFFFFFFFL))

                val itemsPerLine = target.itemsPerLine.coerceAtLeast(1)
                val lineCount = (itemCount + itemsPerLine - 1) / itemsPerLine
                val offset = logicalScrollOffset(
                    firstIndex,
                    firstOffset,
                    itemsPerLine,
                    averageLineLength = (content / lineCount).coerceAtLeast(1f),
                )

                val endCap = config.endCap
                val live = target.state.pinnedProgress(offset, (content - viewport).coerceAtLeast(1f)) * endCap
                // Runs even while hidden, so a bar fading in later has a fresh baseline.
                controller.noteShownProgress(itemCount, endCap, live)

                alpha = controller.fade.value
                if (alpha <= 0f) {
                    controller.releaseThumb(edgeGuard)
                    return@drawWithContent
                }

                fraction = controller.displayedFraction(style.thumbSizing, viewport / content)
                // After release, glide from where the finger left to the real position.
                // The glide offset hides jumps from item count changes.
                progress = (
                        lerp(live, controller.settleFrom, controller.settle.value) +
                                controller.glideOffset
                        ).coerceIn(0f, 1f)
            }

            val containerLength = if (isVertical) size.height else size.width
            val crossLength = if (isVertical) size.width else size.height

            val geometry = controller.geometry
            geometry.set(
                density = this,
                containerLength = containerLength,
                startPadding = config.mainAxisStartPaddingPx(this, isVertical),
                endPadding = config.mainAxisEndPaddingPx(this, isVertical),
                sizing = style.thumbSizing,
                fraction = fraction,
                visualProgress = visualProgress(progress, isMirrored),
                lengthOverride = this.iconMainAxisLength(style, isVertical),
            )
            if (geometry.length <= 0f) {
                controller.releaseThumb(edgeGuard)
                return@drawWithContent
            }

            val pressed = controller.press.value
            // Hovering the thumb with a mouse shows part of the pressed look.
            val highlight = max(pressed, controller.thumbHover.value * HOVER_HIGHLIGHT)
            val atOrigin = config.isCrossAxisAtOrigin(isVertical)
            val appearance = style.thumbAppearance

            // Where the thumb sits across the scroll axis, shared by both looks and the label.
            // While it is picked up or snapping to an edge, it follows that instead.
            val thumbCrossLength = crossLengthOfThumb(style, isVertical)
            val thumbCrossStart = controller.displayedCrossStart(
                restingCrossStart = restingCrossStart(config, isVertical, crossLength, thumbCrossLength),
                thumbCrossLength = thumbCrossLength,
            )

            // While the thumb can be grabbed, keep system edge gestures (Android's back swipe) off
            // its touch strip. Hidden, the edge belongs to the system again.
            if (alpha >= GRABBABLE_ALPHA) {
                val reach = config.crossZoneInsetPx(isVertical) + barStripThickness(style, isVertical)
                controller.protectThumb(
                    guard = edgeGuard,
                    isVertical = isVertical,
                    thumbStart = geometry.start,
                    thumbEnd = geometry.start + geometry.length,
                    crossStart = if (atOrigin) 0f else (crossLength - reach).coerceAtLeast(0f),
                    crossEnd = if (atOrigin) reach.coerceAtMost(crossLength) else crossLength,
                    containerLength = containerLength,
                    slack = EDGE_GUARD_SLACK.toPx(),
                )
            } else {
                controller.releaseThumb(edgeGuard)
            }

            // The optional track goes under the thumb, centered on it, so it follows an edge move.
            drawTrack(
                track = style.trackAppearance,
                isVertical = isVertical,
                geometry = geometry,
                isMirrored = isMirrored,
                thumbCrossCenter = thumbCrossStart + thumbCrossLength / 2f,
                alpha = alpha,
            )

            when (appearance) {
                is ThumbAppearance.Icon -> {
                    drawThumbIcon(
                        icon = appearance,
                        isVertical = isVertical,
                        geometry = geometry,
                        crossStart = thumbCrossStart,
                        alpha = alpha,
                        pressed = pressed,
                        highlight = highlight,
                    )
                }

                ThumbAppearance.Bar -> {
                    drawBar(
                        isVertical = isVertical,
                        geometry = geometry,
                        alpha = alpha,
                        color = lerpColor(style.thumbColor, style.pressedThumbColor, highlight),
                        thickness = lerp(
                            style.thumbThickness.toPx(),
                            style.pressedThumbThickness.toPx(),
                            highlight,
                        ),
                        slotCrossStart = thumbCrossStart,
                        slotThickness = thumbCrossLength,
                    )
                }

                ThumbAppearance.Default -> {
                    drawScrollbar(
                        isVertical = isVertical,
                        geometry = geometry,
                        alpha = alpha,
                        thumbColor = lerpColor(style.thumbColor, style.pressedThumbColor, highlight),
                        thumbThickness = lerp(
                            style.thumbThickness.toPx(),
                            style.pressedThumbThickness.toPx(),
                            highlight,
                        ),
                        capsuleCrossStart = thumbCrossStart,
                        capsuleThickness = thumbCrossLength,
                        capsuleExtraLength = style.capsuleExtraLength.toPx(),
                        capsuleColor = style.capsuleColor,
                    )
                }
            }

            // The bubble follows the press animation, so it fades in on grab and out on release.
            val labelText = controller.label
            if (label != null && labelText != null && pressed > 0f) {
                drawLabelBubble(
                    textMeasurer = textMeasurer,
                    layoutCache = controller.labelLayoutCache,
                    text = labelText,
                    labelStyle = label.style,
                    isVertical = isVertical,
                    thumbStart = geometry.start,
                    thumbLength = geometry.length,
                    thumbCrossStart = thumbCrossStart,
                    thumbCrossLength = thumbCrossLength,
                    thumbAtOrigin = atOrigin,
                    alpha = alpha * pressed,
                )
            }

            // Move hint: shows the picked-up thumb can be dragged across to the other edge.
            val moveHintAmount = controller.moveHint.value
            if (moveHintAmount > 0f) {
                val hintRadius = MOVE_HINT_RADIUS.toPx()
                val hintGap = MOVE_HINT_GAP.toPx()
                val endSide = controller.moveHintSide.value
                // Arcs toward the start of the list while sliding over, so it goes around the
                // thumb instead of through it.
                val mainCenter = geometry.start + geometry.length / 2f -
                        moveHintArcOffset(endSide, geometry.length / 2f, hintGap, hintRadius)
                drawMoveHint(
                    isVertical = isVertical,
                    mainCenter = mainCenter.coerceAtLeast(hintRadius),
                    crossCenter = moveHintCrossCenter(
                        thumbCrossStart, thumbCrossLength, crossLength, hintRadius, hintGap, endSide,
                    ),
                    radius = hintRadius,
                    bubbleColor = label?.style?.bubbleColor ?: ScrollbarLabelStyle().bubbleColor,
                    arrowColor = label?.style?.textColor ?: ScrollbarLabelStyle().textColor,
                    alpha = alpha * moveHintAmount,
                )
            }

            // Mouse hover preview: the label for the hovered track position, where a click jumps.
            if (label != null && !controller.dragging) {
                val previewText = controller.previewLabel
                val previewAmount = controller.previewAmount.value
                if (previewText != null && previewAmount > 0f) {
                    drawLabelBubble(
                        textMeasurer = textMeasurer,
                        layoutCache = controller.labelLayoutCache,
                        text = previewText,
                        labelStyle = label.style,
                        isVertical = isVertical,
                        thumbStart = controller.previewCenter,
                        thumbLength = 0f,
                        thumbCrossStart = thumbCrossStart,
                        thumbCrossLength = thumbCrossLength,
                        thumbAtOrigin = atOrigin,
                        alpha = alpha * previewAmount,
                    )
                }
            }
        }
}

private fun pointOf(isVertical: Boolean, main: Float, cross: Float): Offset =
    if (isVertical) Offset(cross, main) else Offset(main, cross)

private fun sizeOf(isVertical: Boolean, mainLength: Float, crossLength: Float): Size =
    if (isVertical) Size(crossLength, mainLength) else Size(mainLength, crossLength)

/** Draws the visible track, if any: a line, or a painter stretched or repeated along it. */
private fun DrawScope.drawTrack(
    track: TrackAppearance,
    isVertical: Boolean,
    geometry: ThumbGeometry,
    isMirrored: Boolean,
    thumbCrossCenter: Float,
    alpha: Float,
) {
    val extent: TrackExtent
    val thickness: Float
    when (track) {
        TrackAppearance.None -> return
        is TrackAppearance.Line -> {
            extent = track.extent
            thickness = track.thickness.toPx()
        }
        is TrackAppearance.Image -> {
            extent = track.extent
            thickness = track.thickness.toPx()
        }
    }
    val from = trackDrawStart(extent, geometry, isMirrored)
    val length = trackDrawEnd(extent, geometry, isMirrored) - from
    if (length <= 0f) return

    val crossLength = if (isVertical) size.width else size.height
    val crossStart = trackCrossStart(thickness, thumbCrossCenter, crossLength)
    val topLeft = pointOf(isVertical, from, crossStart)
    val trackSize = sizeOf(isVertical, length, thickness)

    when (track) {
        TrackAppearance.None -> Unit
        is TrackAppearance.Line -> drawRoundRect(
            color = track.color,
            topLeft = topLeft,
            size = trackSize,
            cornerRadius = CornerRadius(thickness / 2f),
            alpha = alpha,
        )
        is TrackAppearance.Image -> when (track.fit) {
            TrackFit.Stretch -> translate(left = topLeft.x, top = topLeft.y) {
                drawPainter(track.painter, trackSize, alpha, track.colorFilter)
            }
            TrackFit.Repeat -> {
                val intrinsic = track.painter.intrinsicSize
                val hasIntrinsic = intrinsic != Size.Unspecified
                val tile = repeatTileLength(
                    tileLength = if (track.tileLength.value.isNaN()) Float.NaN else track.tileLength.toPx(),
                    intrinsicMain = if (!hasIntrinsic) Float.NaN else if (isVertical) intrinsic.height else intrinsic.width,
                    intrinsicCross = if (!hasIntrinsic) Float.NaN else if (isVertical) intrinsic.width else intrinsic.height,
                    thickness = thickness,
                )
                val tileSize = sizeOf(isVertical, tile, thickness)
                // Anchored at the list's start, so the pattern stays still while an UpToThumb
                // track grows and shrinks. That start is at the far end for mirrored lists.
                val step = if (isMirrored) -tile else tile
                var position = if (isMirrored) from + length - tile else from
                clipRect(
                    left = topLeft.x,
                    top = topLeft.y,
                    right = topLeft.x + trackSize.width,
                    bottom = topLeft.y + trackSize.height,
                ) {
                    repeat(ceil(length / tile).toInt()) {
                        val tileTopLeft = pointOf(isVertical, position, crossStart)
                        translate(left = tileTopLeft.x, top = tileTopLeft.y) {
                            drawPainter(track.painter, tileSize, alpha, track.colorFilter)
                        }
                        position += step
                    }
                }
            }
        }
    }
}

/**
 * Draws the icon centered on the thumb position. The idle and dragging icons crossfade with the
 * press animation, and a single icon (idle === dragging) is drawn once, so it never dims halfway.
 */
private fun DrawScope.drawThumbIcon(
    icon: ThumbAppearance.Icon,
    isVertical: Boolean,
    geometry: ThumbGeometry,
    crossStart: Float,
    alpha: Float,
    pressed: Float,
    highlight: Float,
) {
    val iconWidth = icon.size.width.toPx()
    val iconHeight = icon.size.height.toPx()
    val mainLength = if (isVertical) iconHeight else iconWidth
    val mainStart = geometry.start + (geometry.length - mainLength) / 2f
    val topLeft = pointOf(isVertical, mainStart, crossStart)
    val iconSize = Size(iconWidth, iconHeight)
    val scale = lerp(1f, icon.draggingScale, highlight) // grows a little on hover too

    withTransform({
        translate(left = topLeft.x, top = topLeft.y)
        scale(scaleX = scale, scaleY = scale, pivot = Offset(iconWidth / 2f, iconHeight / 2f))
    }) {
        if (icon.dragging === icon.idle) {
            drawPainter(icon.idle, iconSize, alpha, icon.colorFilter)
        } else {
            val idleAlpha = alpha * (1f - pressed)
            val draggingAlpha = alpha * pressed
            if (idleAlpha > 0f) drawPainter(icon.idle, iconSize, idleAlpha, icon.colorFilter)
            if (draggingAlpha > 0f) drawPainter(icon.dragging, iconSize, draggingAlpha, icon.colorFilter)
        }
    }
}

private fun DrawScope.drawPainter(
    painter: Painter,
    size: Size,
    alpha: Float,
    colorFilter: ColorFilter?,
) {
    with(painter) { draw(size = size, alpha = alpha, colorFilter = colorFilter) }
}

/**
 * Draws the label bubble on the inner side of the thumb, centered on it along the scroll axis and
 * kept inside the list. Text measurements are cached by the [TextMeasurer], so a repeated label
 * costs a lookup, not a new layout.
 */
private fun DrawScope.drawLabelBubble(
    textMeasurer: TextMeasurer,
    layoutCache: LabelLayoutCache,
    text: String,
    labelStyle: ScrollbarLabelStyle,
    isVertical: Boolean,
    thumbStart: Float,
    thumbLength: Float,
    thumbCrossStart: Float,
    thumbCrossLength: Float,
    thumbAtOrigin: Boolean,
    alpha: Float,
) {
    val horizontalPadding = labelStyle.horizontalPadding.toPx()
    val verticalPadding = labelStyle.verticalPadding.toPx()
    val gap = labelStyle.gap.toPx()
    val containerLength = if (isVertical) size.height else size.width
    val crossLength = if (isVertical) size.width else size.height

    // Long labels are cut with an ellipsis instead of growing past the list.
    val maxTextWidth = (
            (if (isVertical) crossLength else containerLength) - 2f * horizontalPadding
            ).coerceAtLeast(1f)
    val layout = layoutCache.measure(
        textMeasurer = textMeasurer,
        text = text,
        textStyle = labelStyle.textStyle,
        maxWidth = maxTextWidth.toInt(),
        layoutDirection = layoutDirection,
        density = this,
    )

    val bubbleWidth = layout.size.width + 2f * horizontalPadding
    val bubbleHeight = layout.size.height + 2f * verticalPadding
    val bubbleMainLength = if (isVertical) bubbleHeight else bubbleWidth
    val bubbleCrossLength = if (isVertical) bubbleWidth else bubbleHeight

    // coerceIn() with an upper bound floored at 0 can't throw on an empty range.
    val mainStart = (thumbStart + thumbLength / 2f - bubbleMainLength / 2f)
        .coerceIn(0f, (containerLength - bubbleMainLength).coerceAtLeast(0f))
    val crossStart = (
            if (thumbAtOrigin) thumbCrossStart + thumbCrossLength + gap
            else thumbCrossStart - gap - bubbleCrossLength
            ).coerceIn(0f, (crossLength - bubbleCrossLength).coerceAtLeast(0f))
    val topLeft = pointOf(isVertical, mainStart, crossStart)

    drawRoundRect(
        color = labelStyle.bubbleColor,
        topLeft = topLeft,
        size = Size(bubbleWidth, bubbleHeight),
        cornerRadius = CornerRadius(
            min(labelStyle.cornerRadius.toPx(), min(bubbleWidth, bubbleHeight) / 2f),
        ),
        alpha = alpha,
    )
    drawText(
        textLayoutResult = layout,
        color = labelStyle.textColor,
        topLeft = Offset(topLeft.x + horizontalPadding, topLeft.y + verticalPadding),
        alpha = alpha,
    )
}

/**
 * A circle with a two-way arrow across the scroll axis (left-right for vertical lists, up-down for
 * horizontal ones), because the picked-up thumb can only move that way.
 */
private fun DrawScope.drawMoveHint(
    isVertical: Boolean,
    mainCenter: Float,
    crossCenter: Float,
    radius: Float,
    bubbleColor: Color,
    arrowColor: Color,
    alpha: Float,
) {
    val center = pointOf(isVertical, mainCenter, crossCenter)
    drawCircle(color = bubbleColor, radius = radius, center = center, alpha = alpha)

    val halfArrow = radius * 0.5f
    val head = radius * 0.25f
    val stroke = (radius * 0.14f).coerceAtLeast(1f)
    val startTip = pointOf(isVertical, mainCenter, crossCenter - halfArrow)
    val endTip = pointOf(isVertical, mainCenter, crossCenter + halfArrow)
    fun line(from: Offset, to: Offset) =
        drawLine(arrowColor, from, to, strokeWidth = stroke, cap = StrokeCap.Round, alpha = alpha)

    line(startTip, endTip)
    // Arrowheads: two short strokes back from each tip.
    line(startTip, pointOf(isVertical, mainCenter - head, crossCenter - halfArrow + head))
    line(startTip, pointOf(isVertical, mainCenter + head, crossCenter - halfArrow + head))
    line(endTip, pointOf(isVertical, mainCenter - head, crossCenter + halfArrow - head))
    line(endTip, pointOf(isVertical, mainCenter + head, crossCenter + halfArrow - head))
}

/** A single rounded bar, centered in its slot so it widens in place. */
private fun DrawScope.drawBar(
    isVertical: Boolean,
    geometry: ThumbGeometry,
    alpha: Float,
    color: Color,
    thickness: Float,
    slotCrossStart: Float,
    slotThickness: Float,
) {
    val crossStart = slotCrossStart + (slotThickness - thickness) / 2f
    drawRoundRect(
        color = color,
        topLeft = pointOf(isVertical, geometry.start, crossStart),
        size = sizeOf(isVertical, geometry.length, thickness),
        cornerRadius = CornerRadius(thickness / 2f),
        alpha = alpha,
    )
}

private fun DrawScope.drawScrollbar(
    isVertical: Boolean,
    geometry: ThumbGeometry,
    alpha: Float,
    thumbColor: Color,
    thumbThickness: Float,
    capsuleCrossStart: Float,
    capsuleThickness: Float,
    capsuleExtraLength: Float,
    capsuleColor: Color,
) {
    val containerLength = if (isVertical) size.height else size.width
    val capsuleLength = (geometry.length + capsuleExtraLength).coerceAtMost(containerLength)
    val capsuleMainStart = (geometry.start + geometry.length / 2f - capsuleLength / 2f)
        .coerceIn(0f, (containerLength - capsuleLength).coerceAtLeast(0f))

    drawRoundRect(
        color = capsuleColor,
        topLeft = pointOf(isVertical, capsuleMainStart, capsuleCrossStart),
        size = sizeOf(isVertical, capsuleLength, capsuleThickness),
        cornerRadius = CornerRadius(capsuleThickness / 2f),
        alpha = alpha,
    )

    val thumbCrossStart = capsuleCrossStart + (capsuleThickness - thumbThickness) / 2f
    drawRoundRect(
        color = thumbColor,
        topLeft = pointOf(isVertical, geometry.start, thumbCrossStart),
        size = sizeOf(isVertical, geometry.length, thumbThickness),
        cornerRadius = CornerRadius(thumbThickness / 2f),
        alpha = alpha,
    )
}
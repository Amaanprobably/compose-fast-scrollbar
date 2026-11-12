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
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
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
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
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
    class Fixed(val length: Dp = 48.dp) : ThumbSizing {
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
     * The built-in look: a translucent capsule with a thin thumb inside it. It uses the style's
     * thickness, color and [ThumbSizing] parameters.
     */
    data object Default : ThumbAppearance

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
 * The capsule, thumb thickness, thumb color and [ThumbSizing] parameters only apply to
 * [ThumbAppearance.Default]. [ThumbAppearance.Icon] replaces all of them.
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
)

object ScrollbarDefaults {
    @Composable
    fun style(
        thumbThickness: Dp = 5.dp,
        pressedThumbThickness: Dp = 8.dp,
        thumbSizing: ThumbSizing = ThumbSizing.Fixed(),
        thumbAppearance: ThumbAppearance = ThumbAppearance.Default,
        visibility: ScrollbarVisibility = ScrollbarVisibility.AutoHide,
        touchTargetThickness: Dp = 48.dp,
        trackPadding: Dp = 8.dp,
        edgePadding: Dp = 4.dp,
        capsuleThickness: Dp = 38.dp,
        capsuleExtraLength: Dp = 20.dp,
        hideDelay: Duration = 1_500.milliseconds,
        fadeInDuration: Duration = 150.milliseconds,
        fadeOutDuration: Duration = 300.milliseconds,
        thumbColor: Color = Color(0x80808080),
        pressedThumbColor: Color = Color(0xFF2196F3),
        capsuleColor: Color = Color(0x2E808080),
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
    )
}

/**
 * Draggable fast scroller for a [androidx.compose.foundation.lazy.LazyColumn] or
 * [androidx.compose.foundation.lazy.LazyRow].
 *
 * Apply it to the lazy list itself. Only touches that land on the thumb are taken;
 * every other touch reaches the list untouched.
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
 */
@Composable
fun Modifier.scrollbar(
    listState: LazyListState,
    edge: FastScrollEdge = FastScrollEdge.END,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    minItemsToShow: Int = 10,
    hasMore: Boolean = false,
    style: ScrollbarStyle = ScrollbarDefaults.style(),
    label: ScrollbarLabel? = null,
    scrollToStartLabel: String? = null,
    scrollToEndLabel: String? = null,
): Modifier {
    val target = remember(listState) { ListScrollbarTarget(listState) }
    return scrollbarFor(
        target, edge, contentPadding, minItemsToShow, hasMore, style, label,
        scrollToStartLabel, scrollToEndLabel,
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
    style: ScrollbarStyle = ScrollbarDefaults.style(),
    label: ScrollbarLabel? = null,
    scrollToStartLabel: String? = null,
    scrollToEndLabel: String? = null,
): Modifier {
    val target = remember(gridState) { GridScrollbarTarget(gridState) }
    return scrollbarFor(
        target, edge, contentPadding, minItemsToShow, hasMore, style, label,
        scrollToStartLabel, scrollToEndLabel,
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

    fun requestScrollToItem(index: Int, offset: Int)
}

private class ListScrollbarTarget(override val state: LazyListState) : ScrollbarTarget {
    override val isVertical: Boolean
        get() = state.layoutInfo.orientation == Orientation.Vertical
    override val isReversed: Boolean
        get() = state.layoutInfo.reverseLayout
    override val totalItemsCount: Int
        get() = state.layoutInfo.totalItemsCount
    override val itemsPerLine: Int
        get() = 1

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

    /** The most items found in any visible line. Only called when the thumb is grabbed. */
    override val itemsPerLine: Int
        get() {
            val visibleItems = state.layoutInfo.visibleItemsInfo
            val vertical = isVertical
            val countPerLine = HashMap<Int, Int>()
            var mostInOneLine = 1
            for (itemIndex in visibleItems.indices) {
                val item = visibleItems[itemIndex]
                val line = if (vertical) item.row else item.column
                val count = (countPerLine[line] ?: 0) + 1
                countPerLine[line] = count
                if (count > mostInOneLine) mostInOneLine = count
            }
            return mostInOneLine
        }

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
        progress = target.state.pinnedProgress(indicator.scrollOffset.toFloat(), maxScroll)
        return true
    }
}

/** Thumb position along the scroll axis. Works for both orientations. */
internal class ThumbGeometry {
    var start = 0f
    var length = 0f
    var trackLength = 0f

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

/** Marks that the draw phase has no previous frame to compare with. */
private const val NO_BASELINE = -1

/**
 * While more items can still load, the thumb stops here instead of at the very end, because the
 * real end isn't known yet.
 */
private const val HAS_MORE_END_CAP = 0.95f

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
        progress >= 1f -> { index = metrics.itemCount - 1; offset = 0 } // list clamps to the true end
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

    // What the previous non-drag frame drew. Plain fields on purpose: nothing observes them.
    var lastDrawnCount = NO_BASELINE
    var lastDrawnEndCap = 1f
    var lastDrawnProgress = 0f

    /** Text of the label bubble. Written while dragging, read by the draw phase. */
    var label by mutableStateOf<String?>(null)

    /** Smoothed viewport/content ratio. Only used by [ThumbSizing.Proportional] with smoothing. */
    val smoothedFraction = Animatable(1f)

    /** Fraction shown at grab time, held for the whole drag so the thumb never resizes. */
    var frozenFraction = 1f

    fun displayedFraction(sizing: ThumbSizing, rawFraction: Float): Float =
        if (sizing is ThumbSizing.Proportional && sizing.smoothed) smoothedFraction.value
        else rawFraction

    private val events = Channel<Unit>(Channel.CONFLATED)
    private var fadeJob: Job? = null
    private var lastScrollOffset = Int.MIN_VALUE
    private var appliedVisibility = ScrollbarVisibility.AutoHide

    /** Fed by a snapshotFlow. Signals activity only on real movement. */
    fun onScrollOffsetChanged(offset: Int) {
        if (lastScrollOffset == Int.MIN_VALUE) {
            lastScrollOffset = offset // baseline: don't show just because we attached
        } else if (offset != lastScrollOffset) {
            lastScrollOffset = offset
            signalActivity()
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
                if (dragging) {
                    // Held: stay visible until the drag ends and sends an event.
                    events.receiveCatching().getOrNull() ?: return@launch
                    continue
                }
                val next = withTimeoutOrNull(style.hideDelay) {
                    events.receiveCatching().getOrNull()
                }
                if (next == null) break
            }
            if (!dragging) fade.animateTo(0f, tween(style.fadeOutDuration.inWholeMilliseconds.toInt()))
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

    /** Starts a glide that hides a jump of [delta] (old position minus new position). */
    fun startGlide(delta: Float) {
        if (abs(delta) < 0.002f) return
        glideJob?.cancel()
        // UNDISPATCHED so the offset is in place before the next draw reads it.
        glideJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            glide.snapTo((glide.value + delta).coerceIn(-1f, 1f))
            glide.animateTo(0f, tween(250))
        }
    }

    /**
     * Called every non-drag frame with what is about to be drawn. When the item count or the end
     * cap changed since the last frame, the thumb would jump, so glide it instead.
     */
    fun noteShownProgress(itemCount: Int, endCap: Float, shownProgress: Float) {
        if (lastDrawnCount != NO_BASELINE && (itemCount != lastDrawnCount || endCap != lastDrawnEndCap)) {
            startGlide(lastDrawnProgress + glide.value - shownProgress)
        }
        lastDrawnCount = itemCount
        lastDrawnEndCap = endCap
        lastDrawnProgress = shownProgress
    }

    fun beginDrag(progress: Float) {
        glideJob?.cancel()
        glideJob = scope.launch(start = CoroutineStart.UNDISPATCHED) { glide.snapTo(0f) }
        lastDrawnCount = NO_BASELINE
        dragProgress = progress
        dragging = true
        signalActivity()
    }

    fun endDrag() {
        settleFrom = (dragProgress + glide.value).coerceIn(0f, 1f)
        glideJob?.cancel()
        lastDrawnCount = NO_BASELINE
        // UNDISPATCHED so settle is 1f before the next draw (no one-frame flash).
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            glide.snapTo(0f)
            settle.snapTo(1f)
            settle.animateTo(0f, tween(150))
        }
        dragging = false // before signaling, so the fade job sees the drag has ended
        signalActivity()
    }

    fun close() {
        fadeJob?.cancel()
        glideJob?.cancel()
        fadeJob = null
        events.close()
    }
}

/** Everything the draw and pointer code read from the call site, bundled for one-shot updates. */
private class ScrollbarConfig(
    val style: ScrollbarStyle,
    val contentPadding: PaddingValues,
    val minItemsToShow: Int,
    val edge: FastScrollEdge,
    val layoutDirection: LayoutDirection,
    val label: ScrollbarLabel?,
    val hasMore: Boolean,
) {
    /** Largest thumb progress. Below 1 while more items can still load. */
    val endCap: Float get() = if (hasMore) HAS_MORE_END_CAP else 1f

    /** True when the bar sits at the left (vertical) or top (horizontal) of the list. */
    fun isCrossAxisAtOrigin(isVertical: Boolean): Boolean =
        if (isVertical) (edge == FastScrollEdge.START) == (layoutDirection == LayoutDirection.Ltr)
        else edge == FastScrollEdge.START

    /** True when scroll progress runs against the physical axis (reverseLayout, or RTL rows). */
    fun isMirrored(target: ScrollbarTarget): Boolean =
        target.isReversed != (!target.isVertical && layoutDirection == LayoutDirection.Rtl)

    fun mainAxisStartPadding(isVertical: Boolean): Dp =
        (if (isVertical) contentPadding.calculateTopPadding()
        else contentPadding.calculateLeftPadding(layoutDirection)) + style.trackPadding

    fun mainAxisEndPadding(isVertical: Boolean): Dp =
        (if (isVertical) contentPadding.calculateBottomPadding()
        else contentPadding.calculateRightPadding(layoutDirection)) + style.trackPadding
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
): Modifier {
    val haptics = LocalHapticFeedback.current
    val layoutDirection = LocalLayoutDirection.current
    val scope = rememberCoroutineScope()
    val textMeasurer = rememberTextMeasurer()

    val config = ScrollbarConfig(style, contentPadding, minItemsToShow, edge, layoutDirection, label, hasMore)
    val latestConfig by rememberUpdatedState(config)

    val controller = remember(scope) { ScrollbarController(scope, style) }

    SideEffect { controller.style = style }
    DisposableEffect(controller) { onDispose { controller.close() } }

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

    // Wakes the bar on real scroll movement, even while it is hidden.
    LaunchedEffect(controller, target) {
        snapshotFlow { target.state.scrollIndicatorState?.scrollOffset }
            .collect { offset -> if (offset != null) controller.onScrollOffsetChanged(offset) }
    }

    // Proportional sizing only: animates the estimated ratio so content-size jitter is hidden.
    LaunchedEffect(controller, target) {
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
            if (controller.fade.value < 0.05f) return@awaitEachGesture

            val currentConfig = latestConfig
            val currentStyle = currentConfig.style
            val endCap = currentConfig.endCap
            val metrics = controller.frozen
            if (!metrics.update(target) || metrics.itemCount <= currentConfig.minItemsToShow) {
                return@awaitEachGesture
            }
            // Nothing to scroll means nothing is drawn, so nothing may be grabbed either. This
            // matters in Always mode, where the bar's alpha stays at 1.
            if (metrics.contentLength <= metrics.viewportLength) return@awaitEachGesture

            val isVertical = target.isVertical
            val isMirrored = currentConfig.isMirrored(target)
            val startPadding = currentConfig.mainAxisStartPadding(isVertical).toPx()
            val endPadding = currentConfig.mainAxisEndPadding(isVertical).toPx()
            val containerLength = (if (isVertical) size.height else size.width).toFloat()
            val crossLength = (if (isVertical) size.width else size.height).toFloat()

            val sizing = currentStyle.thumbSizing
            val shownFraction = controller.displayedFraction(sizing, metrics.thumbFraction)
            val geometry = controller.grabGeometry
            geometry.set(
                density = this,
                containerLength = containerLength,
                startPadding = startPadding,
                endPadding = endPadding,
                sizing = sizing,
                fraction = shownFraction,
                visualProgress = visualProgress(metrics.progress * endCap, isMirrored),
                lengthOverride = this.iconMainAxisLength(currentStyle, isVertical),
            )
            val trackLength = geometry.trackLength
            if (trackLength <= 0f) return@awaitEachGesture

            // An icon wider than the touch target must still be grabbable across its whole width.
            val iconCrossExtent = (currentStyle.thumbAppearance as? ThumbAppearance.Icon)
                ?.let { icon ->
                    (if (isVertical) icon.size.width else icon.size.height).toPx() +
                            currentStyle.edgePadding.toPx()
                } ?: 0f
            val touchThickness = max(currentStyle.touchTargetThickness.toPx(), iconCrossExtent)
            val downMain = if (isVertical) down.position.y else down.position.x
            val downCross = if (isVertical) down.position.x else down.position.y
            val insideCrossAxis =
                if (currentConfig.isCrossAxisAtOrigin(isVertical)) downCross <= touchThickness
                else downCross >= crossLength - touchThickness
            val insideMainAxis =
                downMain in (geometry.start - slop)..(geometry.start + geometry.length + slop)

            // Not on the thumb: consume nothing, the list handles it.
            if (!insideCrossAxis || !insideMainAxis) return@awaitEachGesture

            // Keep the finger's position inside the thumb so it never jumps.
            var grabOffset = (downMain - geometry.start).coerceIn(0f, geometry.length)

            /** Thumb position (0..endCap) for a finger position. Not yet divided by [endCap]. */
            fun progressAt(mainPosition: Float): Float {
                val onScreen = ((mainPosition - grabOffset - startPadding) / trackLength)
                    .coerceIn(0f, 1f)
                return visualProgress(onScreen, isMirrored)
            }

            val labelConfig = currentConfig.label
            var lastLabelIndex = Int.MIN_VALUE
            var countWatcher: Job? = null
            try {
                controller.frozenFraction = shownFraction
                if (labelConfig != null) {
                    lastLabelIndex = keyIndex(scrollTargetKey(metrics, metrics.progress))
                    controller.label = labelConfig.provider(lastLabelIndex)
                } else {
                    controller.label = null
                }
                controller.beginDrag(metrics.progress * endCap)
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                down.consume()
                scope.launch { target.state.stopScroll() } // cancel any running fling

                var lastKey = Long.MIN_VALUE
                var lastMainPosition = downMain

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

                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
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
                        break
                    }
                    val mainPosition = if (isVertical) change.position.y else change.position.x
                    lastMainPosition = mainPosition
                    if (target.totalItemsCount != metrics.itemCount) rebaseToLiveMetrics()
                    controller.dragProgress = progressAt(mainPosition)
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
                                if (labelConfig.sectionHaptics && newText != null) {
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
                controller.endDrag()
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
                    // The list clamps this to the true end.
                    target.requestScrollToItem((target.totalItemsCount - 1).coerceAtLeast(0), 0)
                    true
                }
            },
        )
        if (actions.isEmpty()) Modifier else Modifier.semantics { customActions = actions }
    }
    return this
        .then(accessibilityModifier)
        .then(pointerModifier)
        .drawWithContent {
            drawContent()

            val isVertical = target.isVertical
            val isMirrored = config.isMirrored(target)
            val alpha: Float
            val fraction: Float
            val progress: Float

            if (controller.dragging) {
                // Frozen snapshot only: no live layoutInfo reads while dragging.
                alpha = 1f
                fraction = controller.frozenFraction
                progress = (controller.dragProgress + controller.glide.value).coerceIn(0f, 1f)
            } else {
                val indicator = target.state.scrollIndicatorState ?: return@drawWithContent
                val content = indicator.contentSize.toFloat()
                val viewport = indicator.viewportSize.toFloat()
                val itemCount = target.totalItemsCount
                if (viewport <= 0f || content <= viewport || itemCount <= config.minItemsToShow) {
                    controller.lastDrawnCount = NO_BASELINE // nothing drawn, nothing to compare
                    return@drawWithContent
                }

                // Read before the alpha check so scrolling always invalidates the draw.
                val offset = indicator.scrollOffset

                val endCap = config.endCap
                val live = target.state.pinnedProgress(
                    offset.toFloat(),
                    (content - viewport).coerceAtLeast(1f),
                ) * endCap
                // Runs even while hidden, so a bar fading in later has a fresh baseline.
                controller.noteShownProgress(itemCount, endCap, live)

                alpha = controller.fade.value
                if (alpha <= 0f) return@drawWithContent

                fraction = controller.displayedFraction(style.thumbSizing, viewport / content)
                // After release, glide from where the finger left to the real position.
                // The glide offset hides jumps from item count changes.
                progress = (
                        lerp(live, controller.settleFrom, controller.settle.value) +
                                controller.glide.value
                        ).coerceIn(0f, 1f)
            }

            val containerLength = if (isVertical) size.height else size.width
            val crossLength = if (isVertical) size.width else size.height

            val geometry = controller.geometry
            geometry.set(
                density = this,
                containerLength = containerLength,
                startPadding = config.mainAxisStartPadding(isVertical).toPx(),
                endPadding = config.mainAxisEndPadding(isVertical).toPx(),
                sizing = style.thumbSizing,
                fraction = fraction,
                visualProgress = visualProgress(progress, isMirrored),
                lengthOverride = this.iconMainAxisLength(style, isVertical),
            )
            if (geometry.length <= 0f) return@drawWithContent

            val pressed = controller.press.value
            val atOrigin = config.isCrossAxisAtOrigin(isVertical)
            val appearance = style.thumbAppearance

            // Where the thumb sits across the scroll axis, shared by both looks and the label.
            val thumbCrossLength: Float
            val thumbCrossStart: Float

            if (appearance is ThumbAppearance.Icon) {
                thumbCrossLength =
                    (if (isVertical) appearance.size.width else appearance.size.height).toPx()
                thumbCrossStart =
                    if (atOrigin) style.edgePadding.toPx()
                    else crossLength - thumbCrossLength - style.edgePadding.toPx()
                drawThumbIcon(
                    icon = appearance,
                    isVertical = isVertical,
                    geometry = geometry,
                    crossStart = thumbCrossStart,
                    alpha = alpha,
                    pressed = pressed,
                )
            } else {
                thumbCrossLength = style.capsuleThickness.toPx()
                thumbCrossStart =
                    if (atOrigin) style.edgePadding.toPx()
                    else crossLength - thumbCrossLength - style.edgePadding.toPx()
                drawScrollbar(
                    isVertical = isVertical,
                    geometry = geometry,
                    alpha = alpha,
                    thumbColor = lerpColor(style.thumbColor, style.pressedThumbColor, pressed),
                    thumbThickness = lerp(
                        style.thumbThickness.toPx(),
                        style.pressedThumbThickness.toPx(),
                        pressed,
                    ),
                    capsuleCrossStart = thumbCrossStart,
                    capsuleThickness = thumbCrossLength,
                    capsuleExtraLength = style.capsuleExtraLength.toPx(),
                    capsuleColor = style.capsuleColor,
                )
            }

            // The bubble follows the press animation, so it fades in on grab and out on release.
            val labelText = controller.label
            if (label != null && labelText != null && pressed > 0f) {
                drawLabelBubble(
                    textMeasurer = textMeasurer,
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
        }
}

private fun pointOf(isVertical: Boolean, main: Float, cross: Float): Offset =
    if (isVertical) Offset(cross, main) else Offset(main, cross)

private fun sizeOf(isVertical: Boolean, mainLength: Float, crossLength: Float): Size =
    if (isVertical) Size(crossLength, mainLength) else Size(mainLength, crossLength)

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
) {
    val iconWidth = icon.size.width.toPx()
    val iconHeight = icon.size.height.toPx()
    val mainLength = if (isVertical) iconHeight else iconWidth
    val mainStart = geometry.start + (geometry.length - mainLength) / 2f
    val topLeft = pointOf(isVertical, mainStart, crossStart)
    val iconSize = Size(iconWidth, iconHeight)
    val scale = lerp(1f, icon.draggingScale, pressed)

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
    val layout = textMeasurer.measure(
        text = text,
        style = labelStyle.textStyle,
        overflow = TextOverflow.Ellipsis,
        softWrap = false,
        maxLines = 1,
        constraints = Constraints(maxWidth = maxTextWidth.toInt()),
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
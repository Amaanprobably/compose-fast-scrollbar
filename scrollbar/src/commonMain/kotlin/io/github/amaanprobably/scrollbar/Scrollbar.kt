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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
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
 * Visual and timing configuration for [scrollbar].
 *
 * The "capsule" is the translucent rounded background that wraps the thumb and moves with it.
 * Create instances with [ScrollbarDefaults.style].
 */
@Immutable
class ScrollbarStyle internal constructor(
    val thumbThickness: Dp,
    val pressedThumbThickness: Dp,
    val thumbSizing: ThumbSizing,
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
 */
@Composable
fun Modifier.scrollbar(
    listState: LazyListState,
    edge: FastScrollEdge = FastScrollEdge.END,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    minItemsToShow: Int = 10,
    style: ScrollbarStyle = ScrollbarDefaults.style(),
    scrollToStartLabel: String? = null,
    scrollToEndLabel: String? = null,
): Modifier {
    val target = remember(listState) { ListScrollbarTarget(listState) }
    return scrollbarFor(target, edge, contentPadding, minItemsToShow, style, scrollToStartLabel, scrollToEndLabel)
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
    style: ScrollbarStyle = ScrollbarDefaults.style(),
    scrollToStartLabel: String? = null,
    scrollToEndLabel: String? = null,
): Modifier {
    val target = remember(gridState) { GridScrollbarTarget(gridState) }
    return scrollbarFor(target, edge, contentPadding, minItemsToShow, style, scrollToStartLabel, scrollToEndLabel)
}

// Targets: what the scrollbar needs from a lazy layout, so lists and grids share one code path.

private interface ScrollbarTarget {
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
private fun visualProgress(progress: Float, isMirrored: Boolean): Float =
    if (isMirrored) 1f - progress else progress

/** Mutable and reusable. Snapshot taken at grab time and used for the whole drag. */
private class ScrollMetrics {
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
private class ThumbGeometry {
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
    ) {
        val usable = (containerLength - startPadding - endPadding).coerceAtLeast(0f)
        if (usable <= 0f) {
            start = startPadding; length = 0f; trackLength = 0f
            return
        }
        length = density.resolveThumbLength(sizing, usable, fraction)
        trackLength = (usable - length).coerceAtLeast(0f)
        start = startPadding + trackLength * visualProgress
    }
}

private fun Density.resolveThumbLength(
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

/**
 * Non-suspending, so it's safe from the pointer-input scope. Skips the request when the
 * target is unchanged. Returns the key of the request.
 */
private fun ScrollbarTarget.scrollToProgress(
    metrics: ScrollMetrics,
    progress: Float,
    lastKey: Long,
): Long {
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
    val key = (index.toLong() shl 32) or (offset.toLong() and 0xFFFFFFFFL)
    if (key != lastKey) requestScrollToItem(index, offset)
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

    fun beginDrag(progress: Float) {
        dragProgress = progress
        dragging = true
        signalActivity()
    }

    fun endDrag() {
        settleFrom = dragProgress
        // UNDISPATCHED so settle is 1f before the next draw (no one-frame flash).
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            settle.snapTo(1f)
            settle.animateTo(0f, tween(150))
        }
        dragging = false // before signalling, so the fade job sees the drag has ended
        signalActivity()
    }

    fun close() {
        fadeJob?.cancel()
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
) {
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
    style: ScrollbarStyle,
    // Accessibility labels
    scrollToStartLabel: String? = null,
    scrollToEndLabel: String? = null,
): Modifier {
    val haptics = LocalHapticFeedback.current
    val layoutDirection = LocalLayoutDirection.current
    val scope = rememberCoroutineScope()

    val config = ScrollbarConfig(style, contentPadding, minItemsToShow, edge, layoutDirection)
    val latestConfig by rememberUpdatedState(config)

    val controller = remember(scope) { ScrollbarController(scope, style) }

    SideEffect { controller.style = style }
    DisposableEffect(controller) { onDispose { controller.close() } }

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
            val metrics = controller.frozen
            if (!metrics.update(target) || metrics.itemCount <= currentConfig.minItemsToShow) {
                return@awaitEachGesture
            }

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
                visualProgress = visualProgress(metrics.progress, isMirrored),
            )
            val trackLength = geometry.trackLength
            if (trackLength <= 0f) return@awaitEachGesture

            val touchThickness = currentStyle.touchTargetThickness.toPx()
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
            val grabOffset = (downMain - geometry.start).coerceIn(0f, geometry.length)
            fun progressAt(mainPosition: Float): Float {
                val onScreen = ((mainPosition - grabOffset - startPadding) / trackLength)
                    .coerceIn(0f, 1f)
                return visualProgress(onScreen, isMirrored)
            }

            try {
                controller.frozenFraction = shownFraction
                controller.beginDrag(metrics.progress)
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                down.consume()
                scope.launch { target.state.stopScroll() } // cancel any running fling

                var lastKey = Long.MIN_VALUE
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
                    controller.dragProgress = progressAt(mainPosition)
                    lastKey = target.scrollToProgress(metrics, controller.dragProgress, lastKey)
                    change.consume()
                }
            } finally {
                // Runs on lift, cancel, and pointer-input restart, so no stuck state.
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
                    target.requestScrollToItem(target.totalItemsCount - 1, 0)
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
                progress = controller.dragProgress
            } else {
                val indicator = target.state.scrollIndicatorState ?: return@drawWithContent
                val content = indicator.contentSize.toFloat()
                val viewport = indicator.viewportSize.toFloat()
                if (viewport <= 0f || content <= viewport) return@drawWithContent
                if (target.totalItemsCount <= config.minItemsToShow) return@drawWithContent

                // Read before the alpha check so scrolling always invalidates the draw.
                val offset = indicator.scrollOffset

                alpha = controller.fade.value
                if (alpha <= 0f) return@drawWithContent

                fraction = controller.displayedFraction(style.thumbSizing, viewport / content)
                val live = target.state.pinnedProgress(
                    offset.toFloat(),
                    (content - viewport).coerceAtLeast(1f),
                )
                // After release, glide from where the finger left to the real position.
                progress = lerp(live, controller.settleFrom, controller.settle.value)
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
            )
            if (geometry.length <= 0f) return@drawWithContent

            val pressed = controller.press.value
            val capsuleThickness = style.capsuleThickness.toPx()
            val capsuleCrossStart =
                if (config.isCrossAxisAtOrigin(isVertical)) style.edgePadding.toPx()
                else crossLength - capsuleThickness - style.edgePadding.toPx()

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
                capsuleCrossStart = capsuleCrossStart,
                capsuleThickness = capsuleThickness,
                capsuleExtraLength = style.capsuleExtraLength.toPx(),
                capsuleColor = style.capsuleColor,
            )
        }
}

private fun pointOf(isVertical: Boolean, main: Float, cross: Float): Offset =
    if (isVertical) Offset(cross, main) else Offset(main, cross)

private fun sizeOf(isVertical: Boolean, mainLength: Float, crossLength: Float): Size =
    if (isVertical) Size(crossLength, mainLength) else Size(mainLength, crossLength)

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
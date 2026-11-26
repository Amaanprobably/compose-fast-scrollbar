package io.github.amaanprobably.scrollbar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.mandatorySystemGestures
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.TouchInjectionScope
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val TOUCH_TAG = "touchList"

// The same geometry as the other UI tests: the thumb is 40 dp long with 8 dp of track padding, so
// at rest its center is 28 dp from the top, over the first row.
private val touchTestStyle = ScrollbarDefaults.style(
    thumbAppearance = ThumbAppearance.Capsule,
    thumbSizing = ThumbSizing.Fixed(40.dp),
    trackPadding = 8.dp,
    touchTargetThickness = 48.dp,
    edgePadding = 4.dp,
    capsuleThickness = 32.dp,
    capsuleExtraLength = 16.dp,
    visibility = ScrollbarVisibility.Always,
)

private val touchTestAutoHideStyle = ScrollbarDefaults.style(
    thumbAppearance = ThumbAppearance.Capsule,
    thumbSizing = ThumbSizing.Fixed(40.dp),
    trackPadding = 8.dp,
    touchTargetThickness = 48.dp,
    edgePadding = 4.dp,
    capsuleThickness = 32.dp,
    capsuleExtraLength = 16.dp,
    visibility = ScrollbarVisibility.AutoHide,
)

private val tenThousandIds = List(10_000) { it }

/**
 * What a gesture did. Rows count their clicks by id. The bar asks for its label once when it grabs
 * the thumb, so no label requests means the thumb was never grabbed.
 */
private class TouchProbe {
    val clicks = mutableMapOf<Int, Int>()
    var labelRequests = 0
    val edgeChanges = mutableListOf<ScrollbarEdge>()
    val label = ScrollbarLabel(sectionHaptics = false) { index ->
        labelRequests++
        "#$index"
    }
}

/** A 300 x 600 dp list of clickable 50 dp rows, keyed by [ids], with edge moving turned on. */
@Composable
private fun TouchList(
    state: LazyListState,
    probe: TouchProbe,
    ids: List<Int> = tenThousandIds,
    style: ScrollbarStyle = touchTestStyle,
) {
    LazyColumn(
        state = state,
        modifier = Modifier
            // Clear of system bars and gesture zones, so the track geometry is the same on a
            // desktop window and an edge-to-edge phone.
            .windowInsetsPadding(WindowInsets.safeDrawing.union(WindowInsets.mandatorySystemGestures))
            .size(300.dp, 600.dp)
            .testTag(TOUCH_TAG)
            .scrollbar(
                state,
                style = style,
                label = probe.label,
                onEdgeChange = { probe.edgeChanges += it },
            ),
    ) {
        items(ids.size, key = { ids[it] }) { index ->
            val id = ids[index]
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .clickable { probe.clicks[id] = (probe.clicks[id] ?: 0) + 1 },
            )
        }
    }
}

/** Where the thumb rests at the top: on the bar's strip, 28 dp down, over the first row. */
private fun TouchInjectionScope.thumbAtTop() = Offset(right - 10f, 28.dp.toPx())

private fun TouchInjectionScope.moveInSteps(from: Offset, to: Offset, steps: Int = 20) {
    for (step in 1..steps) moveTo(lerp(from, to, step / steps.toFloat()))
}

/** A finger held still on the thumb this long grabs it: three quarters of the long press. */
private fun ViewConfiguration.grabHoldMillis() = longPressTimeoutMillis * 3 / 4

/** A finger held still on the thumb this long, counted from the touch, picks it up to move sides. */
private fun ViewConfiguration.edgeHoldMillis() = max(longPressTimeoutMillis, 500L)

/**
 * A finger on the thumb only grabs it once it's clearly a drag or a hold, so a quick tap still
 * reaches the row under the thumb.
 */
@OptIn(ExperimentalTestApi::class)
class ScrollbarTouchTest {

    @Test
    fun aTapOnTheThumbClicksTheRowUnderIt() = runComposeUiTest {
        val probe = TouchProbe()
        lateinit var state: LazyListState
        setContent {
            state = rememberLazyListState()
            TouchList(state, probe)
        }
        onNodeWithTag(TOUCH_TAG).performTouchInput { click(thumbAtTop()) }
        waitForIdle()
        assertEquals(1, probe.clicks[0], "the row under the thumb should get the tap")
        assertEquals(0, probe.labelRequests, "a tap must not grab the thumb")
        assertEquals(0, state.firstVisibleItemIndex, "a tap must not scroll")
        assertEquals(0, state.firstVisibleItemScrollOffset, "a tap must not scroll")
    }

    @Test
    fun aDragFromTheThumbGoesOnFromWhereItWasClaimedWithoutAJump() = runComposeUiTest {
        val probe = TouchProbe()
        lateinit var state: LazyListState
        setContent {
            state = rememberLazyListState()
            TouchList(state, probe)
        }
        val list = onNodeWithTag(TOUCH_TAG)
        var claimedAt = Offset.Zero
        list.performTouchInput {
            val start = thumbAtTop()
            claimedAt = start + Offset(0f, viewConfiguration.touchSlop + 1f)
            down(start)
            moveTo(claimedAt)
        }
        waitForIdle()
        assertTrue(probe.labelRequests > 0, "moving past the touch slop along the bar grabs the thumb")
        assertEquals(0, state.firstVisibleItemIndex, "grabbing must not move the list")
        assertEquals(0, state.firstVisibleItemScrollOffset, "grabbing must not move the list")

        // From there the thumb follows the finger: a tenth of the track is a tenth of the list.
        // Had it gone on from where the finger landed, the list would also jump by the slop.
        list.performTouchInput {
            val track = height - 56.dp.toPx() // height - 2 * 8 dp padding - 40 dp thumb
            moveInSteps(claimedAt, claimedAt + Offset(0f, track * 0.1f))
            up()
        }
        waitForIdle()
        val landed = state.firstVisibleItemIndex
        assertTrue(landed in 950..1_050, "landed at $landed, expected about 1,000")
        assertTrue(probe.clicks.isEmpty(), "a drag must not click the row it started on")
    }

    @Test
    fun holdingStillOnTheThumbGrabsItWithoutClickingTheRow() = runComposeUiTest {
        val probe = TouchProbe()
        lateinit var config: ViewConfiguration
        setContent {
            config = LocalViewConfiguration.current
            TouchList(rememberLazyListState(), probe)
        }
        waitForIdle()
        mainClock.autoAdvance = false
        val list = onNodeWithTag(TOUCH_TAG)
        list.performTouchInput { down(thumbAtTop()) }
        mainClock.advanceTimeBy(config.grabHoldMillis() - 50)
        assertEquals(0, probe.labelRequests, "not yet: it could still be a tap")
        mainClock.advanceTimeBy(100)
        assertTrue(probe.labelRequests > 0, "held still long enough: the thumb is grabbed")

        list.performTouchInput { up() }
        mainClock.autoAdvance = true
        waitForIdle()
        assertTrue(probe.clicks.isEmpty(), "lifting after a hold must not click the row")
    }

    @Test
    fun pickingUpToMoveSidesIsTimedFromTheTouchNotFromTheGrab() = runComposeUiTest {
        // The hold grabs first (300 ms with Android's defaults). Picking up must still come at
        // 500 ms from the touch, not 500 ms after the grab.
        val probe = TouchProbe()
        lateinit var config: ViewConfiguration
        setContent {
            config = LocalViewConfiguration.current
            TouchList(rememberLazyListState(), probe)
        }
        waitForIdle()
        val list = onNodeWithTag(TOUCH_TAG)
        var grabbedAt = Offset.Zero

        // Just short of the pick up: grabbed, but moving across doesn't move the bar. One jump
        // across, so the finger has clearly moved before the pick up would come.
        mainClock.autoAdvance = false
        list.performTouchInput {
            grabbedAt = thumbAtTop()
            down(grabbedAt)
        }
        mainClock.advanceTimeBy(config.edgeHoldMillis() - 80)
        assertTrue(probe.labelRequests > 0, "the hold should have grabbed the thumb by now")
        list.performTouchInput {
            moveTo(Offset(left + 10f, grabbedAt.y))
            up()
        }
        mainClock.autoAdvance = true
        waitForIdle()
        assertTrue(probe.edgeChanges.isEmpty(), "too short to pick the bar up")

        // Just past it: picked up, and moving across moves the bar.
        mainClock.autoAdvance = false
        list.performTouchInput { down(grabbedAt) }
        mainClock.advanceTimeBy(config.edgeHoldMillis() + 50)
        list.performTouchInput {
            moveInSteps(grabbedAt, Offset(left + 10f, grabbedAt.y))
            up()
        }
        mainClock.autoAdvance = true
        waitForIdle()
        assertEquals(listOf(ScrollbarEdge.Start), probe.edgeChanges)
    }

    @Test
    fun aTouchOnTheThumbWhileTheListFlingsGrabsItRightAway() = runComposeUiTest {
        val probe = TouchProbe()
        lateinit var state: LazyListState
        setContent {
            state = rememberLazyListState()
            TouchList(state, probe)
        }
        waitForIdle()
        mainClock.autoAdvance = false
        val list = onNodeWithTag(TOUCH_TAG)
        // A quick swipe up in the middle starts a fling toward the end. It only moves a few rows
        // out of 10,000, so the thumb is still at the top.
        list.performTouchInput {
            swipeUp(startY = centerY + 200f, endY = centerY - 200f, durationMillis = 50)
        }
        mainClock.advanceTimeBy(48)
        assertTrue(state.isScrollInProgress, "the fling should be running")

        list.performTouchInput { down(thumbAtTop()) }
        assertTrue(probe.labelRequests > 0, "during a fling a touch on the thumb grabs it at once")
        mainClock.advanceTimeBy(100)
        assertFalse(state.isScrollInProgress, "grabbing stops the fling")

        list.performTouchInput { up() }
        mainClock.autoAdvance = true
        waitForIdle()
        assertTrue(probe.clicks.isEmpty(), "a touch that stops a fling isn't a click")
    }

    @Test
    fun aSecondFingerLetsTheThumbGo() = runComposeUiTest {
        val probe = TouchProbe()
        setContent { TouchList(rememberLazyListState(), probe) }
        waitForIdle()
        mainClock.autoAdvance = false
        val list = onNodeWithTag(TOUCH_TAG)
        list.performTouchInput {
            down(0, thumbAtTop())
            down(1, center)
        }
        mainClock.advanceTimeBy(1_000) // well past the hold and the pick up
        list.performTouchInput {
            up(1)
            up(0)
        }
        mainClock.autoAdvance = true
        waitForIdle()
        assertEquals(0, probe.labelRequests, "two fingers are a different gesture: the thumb stays put")
        assertTrue(probe.edgeChanges.isEmpty())
    }

    @Test
    fun lettingGoAfterAFastThumbDragDoesNotFlingTheList() = runComposeUiTest {
        // The list saw the finger land, then lost it to the thumb. If it still tracked the
        // finger's speed, it would fling on release and carry the list past where the thumb was let go.
        val probe = TouchProbe()
        lateinit var state: LazyListState
        setContent {
            state = rememberLazyListState()
            TouchList(state, probe)
        }
        waitForIdle()
        mainClock.autoAdvance = false
        onNodeWithTag(TOUCH_TAG).performTouchInput {
            val start = thumbAtTop()
            val grabbedAt = start + Offset(0f, viewConfiguration.touchSlop + 1f)
            down(start)
            moveTo(grabbedAt)
            // A third of the track in four quick moves, about 64 ms: fast enough to fling a list.
            moveInSteps(grabbedAt, grabbedAt + Offset(0f, (height - 56.dp.toPx()) / 3f), steps = 4)
            up()
        }
        mainClock.advanceTimeBy(100) // the last jump is applied on the next layout
        val landedIndex = state.firstVisibleItemIndex
        val landedOffset = state.firstVisibleItemScrollOffset
        assertTrue(landedIndex > 1_000, "the thumb drag should have moved the list, landed at $landedIndex")

        mainClock.advanceTimeBy(2_000)
        assertFalse(state.isScrollInProgress, "nothing should be scrolling after the release")
        assertEquals(landedIndex, state.firstVisibleItemIndex, "the list must stay where the thumb left it")
        assertEquals(landedOffset, state.firstVisibleItemScrollOffset, "the list must stay where the thumb left it")
        mainClock.autoAdvance = true
    }

    @Test
    fun aShakyFingerUnderTheSlopStillCountsAsAHold() = runComposeUiTest {
        val probe = TouchProbe()
        lateinit var config: ViewConfiguration
        setContent {
            config = LocalViewConfiguration.current
            TouchList(rememberLazyListState(), probe)
        }
        waitForIdle()
        mainClock.autoAdvance = false
        val list = onNodeWithTag(TOUCH_TAG)
        var start = Offset.Zero
        var wobble = 0f
        list.performTouchInput {
            start = thumbAtTop()
            wobble = viewConfiguration.touchSlop / 3f
            down(start)
        }
        // A real finger is never perfectly still: a small move every 50 ms, all under the slop,
        // stopping short of the hold time.
        val wobbles = ((config.grabHoldMillis() - 60) / 50).toInt()
        for (wobbleIndex in 0 until wobbles) {
            mainClock.advanceTimeBy(50, ignoreFrameDuration = true) // exactly 50 ms, not rounded up to whole frames
            val sideways = if (wobbleIndex % 2 == 0) wobble else -wobble
            list.performTouchInput { moveTo(start + Offset(sideways, wobble), delayMillis = 0) }
        }
        assertEquals(0, probe.labelRequests, "not yet: the hold time hasn't passed")

        // The hold is timed from the touch, so the wobbles don't restart it.
        mainClock.advanceTimeBy(config.grabHoldMillis() - wobbles * 50L + 50)
        assertTrue(probe.labelRequests > 0, "a shaky finger held long enough should grab the thumb")

        list.performTouchInput { up() }
        mainClock.autoAdvance = true
        waitForIdle()
        assertTrue(probe.clicks.isEmpty(), "lifting after the hold must not click the row")
    }

    @Test
    fun aShakyQuickTapStillClicksTheRow() = runComposeUiTest {
        val probe = TouchProbe()
        setContent { TouchList(rememberLazyListState(), probe) }
        waitForIdle()
        mainClock.autoAdvance = false
        val list = onNodeWithTag(TOUCH_TAG)
        var start = Offset.Zero
        var wobble = 0f
        list.performTouchInput {
            start = thumbAtTop()
            wobble = viewConfiguration.touchSlop / 3f
            down(start)
        }
        for (wobbleIndex in 0 until 2) { // 100 ms of small moves, then lift
            mainClock.advanceTimeBy(50)
            val sideways = if (wobbleIndex % 2 == 0) wobble else -wobble
            list.performTouchInput { moveTo(start + Offset(sideways, wobble), delayMillis = 0) }
        }
        list.performTouchInput { up() }
        mainClock.autoAdvance = true
        waitForIdle()
        assertEquals(1, probe.clicks[0], "a tap that wobbled under the slop is still a tap")
        assertEquals(0, probe.labelRequests, "and it must not grab the thumb")
    }

    @Test
    fun aSwipeAcrossTheThumbIsLeftAlone() = runComposeUiTest {
        // Across the bar is the other direction: a pager or a swipe-to-dismiss row may want it.
        val probe = TouchProbe()
        setContent { TouchList(rememberLazyListState(), probe) }
        waitForIdle()
        val list = onNodeWithTag(TOUCH_TAG)
        list.performTouchInput {
            val start = thumbAtTop()
            down(start)
            moveTo(start - Offset(viewConfiguration.touchSlop * 2f, 0f))
        }
        mainClock.advanceTimeBy(1_000) // past the hold: a finger that went across never grabs
        list.performTouchInput { up() }
        waitForIdle()
        assertEquals(0, probe.labelRequests, "moving across first must not grab the thumb")
        assertTrue(probe.edgeChanges.isEmpty())
    }

    @Test
    fun aRestingFingerKeepsAnAutoHideBarUpAndTheTapRestartsTheHideDelay() = runComposeUiTest {
        val probe = TouchProbe()
        lateinit var state: LazyListState
        lateinit var config: ViewConfiguration
        setContent {
            config = LocalViewConfiguration.current
            state = rememberLazyListState()
            TouchList(state, probe, style = touchTestAutoHideStyle)
        }
        waitForIdle()
        mainClock.autoAdvance = false
        // Scrolling one row wakes the bar: it fades in for 150 ms, stays 1.5 s, then fades out
        // over 300 ms, so without a finger it would be gone about 2 s from now.
        runOnUiThread { state.requestScrollToItem(1) }
        mainClock.advanceTimeBy(1_500)

        // A finger lands on the thumb just before the fade would start and lifts as a tap after it
        // would have started. Row 1 is at the top now, under the thumb.
        val list = onNodeWithTag(TOUCH_TAG)
        list.performTouchInput { down(thumbAtTop()) }
        mainClock.advanceTimeBy(config.grabHoldMillis() - 50)
        list.performTouchInput { up() }

        // Past when the bar would have finished fading without the finger. The tap restarted the
        // hide delay, so the bar is still up and the thumb can be grabbed.
        mainClock.advanceTimeBy(400)
        list.performTouchInput {
            val start = thumbAtTop()
            down(start)
            moveInSteps(start, Offset(start.x, bottom * 2))
            up()
        }
        mainClock.autoAdvance = true
        waitForIdle()
        assertEquals(1, probe.clicks[1], "the tap should have reached the row under the thumb")
        assertFalse(state.canScrollForward, "the bar should have stayed up and grabbable")
    }

    @Test
    fun theThumbMovingWhileTheFingerWaitsStillGivesAWorkingDrag() = runComposeUiTest {
        val probe = TouchProbe()
        lateinit var state: LazyListState
        val ids = mutableStateListOf<Int>().apply { addAll(0 until 100) }
        setContent {
            state = rememberLazyListState(initialFirstVisibleItemIndex = 50)
            TouchList(state, probe, ids = ids)
        }
        waitForIdle()
        mainClock.autoAdvance = false
        val list = onNodeWithTag(TOUCH_TAG)
        // 100 rows of 50 dp in a 600 dp list, at row 50: offset 2,500 of 4,400, so the thumb's
        // center is at 8 + 544 * 0.568 + 20 = about 337 dp.
        var downAt = Offset.Zero
        list.performTouchInput {
            downAt = Offset(right - 10f, 337.dp.toPx())
            down(downAt)
        }
        // New rows arrive above while the finger waits. With keys the list stays on the same row,
        // now 100 places further down, so the thumb moves away from under the finger.
        runOnUiThread { ids.addAll(0, List(100) { -1 - it }) }
        mainClock.advanceTimeByFrame()
        mainClock.advanceTimeByFrame()
        assertEquals(150, state.firstVisibleItemIndex)

        list.performTouchInput {
            moveInSteps(downAt, Offset(downAt.x, bottom * 3))
            up()
        }
        mainClock.autoAdvance = true
        waitForIdle()
        assertTrue(probe.labelRequests > 0, "the drag should have grabbed the thumb")
        assertFalse(state.canScrollForward, "the drag should still reach the end")
    }
}

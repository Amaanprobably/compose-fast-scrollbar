package io.github.amaanprobably.scrollbar

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.mandatorySystemGestures
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.TouchInjectionScope
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val LIST_TAG = "list"

// Explicit, always-visible style: no fade to wait for, and the same geometry on every platform.
// Thumb is 40 dp long with 8 dp of track padding, so at rest its center is 28 dp from the edge.
private val uiTestStyle = ScrollbarDefaults.style(
    thumbAppearance = ThumbAppearance.Capsule,
    thumbSizing = ThumbSizing.Fixed(40.dp),
    trackPadding = 8.dp,
    touchTargetThickness = 48.dp,
    edgePadding = 4.dp,
    capsuleThickness = 32.dp,
    capsuleExtraLength = 16.dp, // half is 8 dp, so the thumb's end padding stays 8 dp
    visibility = ScrollbarVisibility.Always,
)

// Same geometry, but hidden until the list scrolls.
private val uiTestAutoHideStyle = ScrollbarDefaults.style(
    thumbAppearance = ThumbAppearance.Capsule,
    thumbSizing = ThumbSizing.Fixed(40.dp),
    trackPadding = 8.dp,
    touchTargetThickness = 48.dp,
    edgePadding = 4.dp,
    capsuleThickness = 32.dp,
    capsuleExtraLength = 16.dp, // half is 8 dp, so the thumb's end padding stays 8 dp
    visibility = ScrollbarVisibility.AutoHide,
)

@Composable
private fun TestList(
    state: LazyListState,
    itemCount: Int,
    style: ScrollbarStyle = uiTestStyle,
    reverseLayout: Boolean = false,
    hasMore: Boolean = false,
    minItemsToShow: Int = 10,
    onEdgeChange: ((ScrollbarEdge) -> Unit)? = null,
    onComposed: () -> Unit = {},
) {
    SideEffect { onComposed() }
    LazyColumn(
        state = state,
        reverseLayout = reverseLayout,
        modifier = Modifier
            // Clear of system bars and gesture zones, so the track geometry is the same on a
            // desktop window and an edge-to-edge phone (zones would shorten the track).
            .windowInsetsPadding(WindowInsets.safeDrawing.union(WindowInsets.mandatorySystemGestures))
            .size(300.dp, 600.dp)
            .testTag(LIST_TAG)
            .scrollbar(
                state,
                minItemsToShow = minItemsToShow,
                hasMore = hasMore,
                style = style,
                onEdgeChange = onEdgeChange,
            ),
    ) {
        items(itemCount) { Box(Modifier.fillMaxWidth().height(50.dp)) }
    }
}

/** Like [TestList], but keyed by [ids], so inserting at the top keeps the list on the same item. */
@Composable
private fun KeyedTestList(state: LazyListState, ids: List<Int>) {
    LazyColumn(
        state = state,
        modifier = Modifier
            .windowInsetsPadding(WindowInsets.safeDrawing.union(WindowInsets.mandatorySystemGestures))
            .size(300.dp, 600.dp)
            .testTag(LIST_TAG)
            .scrollbar(state, style = uiTestStyle),
    ) {
        items(ids.size, key = { ids[it] }) { Box(Modifier.fillMaxWidth().height(50.dp)) }
    }
}

@Composable
private fun TestGrid(state: LazyGridState, itemCount: Int) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        state = state,
        modifier = Modifier
            // Clear of system bars and gesture zones, so the track geometry is the same on a
            // desktop window and an edge-to-edge phone (zones would shorten the track).
            .windowInsetsPadding(WindowInsets.safeDrawing.union(WindowInsets.mandatorySystemGestures))
            .size(300.dp, 600.dp)
            .testTag(LIST_TAG)
            .scrollbar(state, style = uiTestStyle),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) { Box(Modifier.fillMaxWidth().height(80.dp)) }
        items(itemCount) { Box(Modifier.fillMaxWidth().height(50.dp)) }
    }
}

/** Where the thumb is at rest at the top: on the bar's strip, 28 dp down. */
private fun TouchInjectionScope.thumbAtTop() = Offset(right - 10f, 28.dp.toPx())

/** Same, for a reversed list, where the thumb rests at the bottom. */
private fun TouchInjectionScope.thumbAtBottom() = Offset(right - 10f, bottom - 28.dp.toPx())

private fun TouchInjectionScope.moveInSteps(from: Offset, to: Offset, steps: Int = 20) {
    for (step in 1..steps) moveTo(lerp(from, to, step / steps.toFloat()))
}

private fun TouchInjectionScope.drag(from: Offset, to: Offset) {
    down(from)
    moveInSteps(from, to)
    up()
}

@OptIn(ExperimentalTestApi::class)
class ScrollbarUiTest {

    @Test
    fun draggingTheThumbToTheBottomReachesTheEnd() = runComposeUiTest {
        lateinit var state: LazyListState
        setContent {
            state = rememberLazyListState()
            TestList(state, itemCount = 10_000)
        }
        onNodeWithTag(LIST_TAG).performTouchInput {
            drag(thumbAtTop(), Offset(right - 10f, bottom * 2))
        }
        waitForIdle()
        assertFalse(state.canScrollForward, "should be at the end")
    }

    @Test
    fun aVisibleTrackDoesNotChangeDragging() = runComposeUiTest {
        // A repeated image track that grows with the thumb: exercises the whole track drawing
        // path on every frame of the drag, and must leave the input exactly as without it.
        val trackStyle = ScrollbarDefaults.style(
            thumbAppearance = ThumbAppearance.Capsule,
            thumbSizing = ThumbSizing.Fixed(40.dp),
            trackPadding = 8.dp,
            touchTargetThickness = 48.dp,
            edgePadding = 4.dp,
            capsuleThickness = 32.dp,
            capsuleExtraLength = 16.dp,
            visibility = ScrollbarVisibility.Always,
            trackAppearance = TrackAppearance.Image(
                painter = ColorPainter(Color.Red),
                thickness = 12.dp,
                extent = TrackExtent.UpToThumb,
                fit = TrackFit.Repeat,
            ),
        )
        lateinit var state: LazyListState
        setContent {
            state = rememberLazyListState()
            TestList(state, itemCount = 10_000, style = trackStyle)
        }
        onNodeWithTag(LIST_TAG).performTouchInput {
            drag(thumbAtTop(), Offset(right - 10f, bottom * 2))
        }
        waitForIdle()
        assertFalse(state.canScrollForward, "should be at the end")
    }

    @Test
    fun aLastItemTallerThanTheListCanBeDraggedToItsBottom() = runComposeUiTest {
        lateinit var state: LazyListState
        setContent {
            state = rememberLazyListState()
            LazyColumn(
                state = state,
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.safeDrawing.union(WindowInsets.mandatorySystemGestures))
                    .size(300.dp, 600.dp)
                    .testTag(LIST_TAG)
                    .scrollbar(state, style = uiTestStyle),
            ) {
                items(500) { Box(Modifier.fillMaxWidth().height(50.dp)) }
                item { Box(Modifier.fillMaxWidth().height(2_000.dp)) } // more than three screens tall
            }
        }
        onNodeWithTag(LIST_TAG).performTouchInput {
            drag(thumbAtTop(), Offset(right - 10f, bottom * 2))
        }
        waitForIdle()
        assertFalse(state.canScrollForward, "should reach the very bottom of the tall last item")
    }

    @Test
    fun draggingToTheMiddleLandsNearTheMiddle() = runComposeUiTest {
        lateinit var state: LazyListState
        setContent {
            state = rememberLazyListState()
            TestList(state, itemCount = 10_000)
        }
        onNodeWithTag(LIST_TAG).performTouchInput {
            // Moving the thumb by half the track length: track = height - 2 * 8 dp - 40 dp.
            val halfTrack = (height - 56.dp.toPx()) / 2f
            val start = thumbAtTop()
            drag(start, start + Offset(0f, halfTrack))
        }
        waitForIdle()
        assertTrue(state.firstVisibleItemIndex in 4_900..5_100, "landed at ${state.firstVisibleItemIndex}")
    }

    @Test
    fun aHiddenBarWakesUpWhenTheListScrollsAndCanThenBeGrabbed() = runComposeUiTest {
        lateinit var state: LazyListState
        setContent {
            state = rememberLazyListState()
            TestList(state, itemCount = 10_000, style = uiTestAutoHideStyle)
        }
        waitForIdle()
        val list = onNodeWithTag(LIST_TAG)
        // A normal swipe in the middle scrolls the list a little, which must reveal the bar.
        list.performTouchInput {
            drag(Offset(centerX, bottom * 0.7f), Offset(centerX, bottom * 0.4f))
        }
        // Let frames run so the draw phase sees the scroll and fades the bar in (150 ms), well
        // before it would hide again (1.5 s). Events in one input block get no frames in between.
        mainClock.advanceTimeBy(300)
        // The thumb, still near the top, can now be grabbed and dragged to the end.
        list.performTouchInput {
            drag(thumbAtTop(), Offset(right - 10f, bottom * 2))
        }
        waitForIdle()
        assertFalse(state.canScrollForward, "the revealed thumb should have been grabbed")
    }

    @Test
    fun aHiddenBarCannotBeGrabbed() = runComposeUiTest {
        lateinit var state: LazyListState
        setContent {
            state = rememberLazyListState()
            TestList(state, itemCount = 10_000, style = uiTestAutoHideStyle)
        }
        waitForIdle()
        onNodeWithTag(LIST_TAG).performTouchInput {
            drag(thumbAtTop(), Offset(right - 10f, bottom * 2))
        }
        waitForIdle()
        assertTrue(state.firstVisibleItemIndex < 100, "a hidden thumb isn't grabbable, so this is just a swipe")
    }

    @Test
    fun touchesOnTheTrackAwayFromTheThumbStillScrollTheList() = runComposeUiTest {
        lateinit var state: LazyListState
        setContent {
            state = rememberLazyListState()
            TestList(state, itemCount = 10_000)
        }
        onNodeWithTag(LIST_TAG).performTouchInput {
            // On the bar's strip, but far below the thumb: a normal swipe up.
            drag(Offset(right - 10f, bottom * 0.8f), Offset(right - 10f, bottom * 0.3f))
        }
        waitForIdle()
        assertTrue(state.firstVisibleItemIndex > 0, "the list should have scrolled")
        assertTrue(state.firstVisibleItemIndex < 100, "a swipe, not a fast-scroll jump")
    }

    @Test
    fun nothingIsGrabbedWhenContentFits() = runComposeUiTest {
        lateinit var state: LazyListState
        setContent {
            state = rememberLazyListState()
            TestList(state, itemCount = 3, minItemsToShow = 0)
        }
        onNodeWithTag(LIST_TAG).performTouchInput {
            drag(thumbAtTop(), Offset(right - 10f, bottom))
        }
        waitForIdle()
        assertEquals(0, state.firstVisibleItemIndex)
        assertEquals(0, state.firstVisibleItemScrollOffset)
    }

    @Test
    fun aListWithExactlyMinItemsToShowItemsCanBeGrabbed() = runComposeUiTest {
        // 20 items of 50 dp scroll in a 600 dp list. minItemsToShow = 20 means 20 is enough.
        lateinit var state: LazyListState
        setContent {
            state = rememberLazyListState()
            TestList(state, itemCount = 20, minItemsToShow = 20)
        }
        onNodeWithTag(LIST_TAG).performTouchInput {
            drag(thumbAtTop(), Offset(right - 10f, bottom * 2))
        }
        waitForIdle()
        assertFalse(state.canScrollForward, "the bar should show and drag to the end")
    }

    @Test
    fun aListWithFewerThanMinItemsToShowItemsHasNoBar() = runComposeUiTest {
        lateinit var state: LazyListState
        setContent {
            state = rememberLazyListState()
            TestList(state, itemCount = 19, minItemsToShow = 20)
        }
        onNodeWithTag(LIST_TAG).performTouchInput {
            drag(thumbAtTop(), Offset(right - 10f, bottom * 2))
        }
        waitForIdle()
        // No bar to grab: the swipe goes to the list, which is already at the top.
        assertEquals(0, state.firstVisibleItemIndex)
    }

    @Test
    fun reversedListDragsUpToReachTheEnd() = runComposeUiTest {
        lateinit var state: LazyListState
        setContent {
            state = rememberLazyListState()
            TestList(state, itemCount = 10_000, reverseLayout = true)
        }
        onNodeWithTag(LIST_TAG).performTouchInput {
            drag(thumbAtBottom(), Offset(right - 10f, -bottom))
        }
        waitForIdle()
        assertFalse(state.canScrollForward, "should be at the end")
    }

    @Test
    fun progressFollowsListOrderInANormalList() = runComposeUiTest {
        lateinit var state: LazyListState
        setContent {
            state = rememberLazyListState()
            TestList(state, itemCount = 10_000)
        }
        runOnIdle { state.requestScrollToItem(2_000) }
        waitForIdle()
        val metrics = ScrollMetrics()
        assertTrue(metrics.update(ListScrollbarTarget(state)))
        assertEquals(0.2f, metrics.progress, 0.01f)
    }

    @Test
    fun progressFollowsListOrderInAReversedList() = runComposeUiTest {
        // Regression: the thumb used to jump to the wrong end after release in reversed lists.
        lateinit var state: LazyListState
        setContent {
            state = rememberLazyListState()
            TestList(state, itemCount = 10_000, reverseLayout = true)
        }
        runOnIdle { state.requestScrollToItem(2_000) }
        waitForIdle()
        val metrics = ScrollMetrics()
        assertTrue(metrics.update(ListScrollbarTarget(state)))
        assertEquals(0.2f, metrics.progress, 0.01f, "item 2000 of 10000 is 20% from the start")
    }

    @Test
    fun reversedListGrabAfterScrollingContinuesFromTheRightPlace() = runComposeUiTest {
        lateinit var state: LazyListState
        setContent {
            state = rememberLazyListState()
            TestList(state, itemCount = 10_000, reverseLayout = true)
        }
        runOnIdle { state.requestScrollToItem(2_000) }
        waitForIdle()
        var expectedIndex = 0
        onNodeWithTag(LIST_TAG).performTouchInput {
            // The thumb must be drawn 20% up from the bottom. Grab it there and move it up by a
            // tenth of the track: a fast-scroll to about 30%. If the thumb were drawn at the
            // wrong end, this touch would miss it and only nudge the list.
            val track = height - 56.dp.toPx()
            val thumbCenter = Offset(right - 10f, bottom - 28.dp.toPx() - track * 0.2f)
            expectedIndex = 3_000
            drag(thumbCenter, thumbCenter + Offset(0f, -track * 0.1f))
        }
        waitForIdle()
        val landed = state.firstVisibleItemIndex
        assertTrue(landed in expectedIndex - 300..expectedIndex + 300, "landed at $landed, expected about $expectedIndex")
    }

    @Test
    fun gridWithAFullSpanHeaderReachesTheEnd() = runComposeUiTest {
        lateinit var state: LazyGridState
        setContent {
            state = rememberLazyGridState()
            TestGrid(state, itemCount = 3_000)
        }
        onNodeWithTag(LIST_TAG).performTouchInput {
            drag(thumbAtTop(), Offset(right - 10f, bottom * 2))
        }
        waitForIdle()
        assertFalse(state.canScrollForward, "should be at the end")
    }

    @Test
    fun itemsAppendedMidDragAreReachableInTheSameDrag() = runComposeUiTest {
        lateinit var state: LazyListState
        var itemCount by mutableStateOf(100)
        setContent {
            state = rememberLazyListState()
            TestList(state, itemCount = itemCount)
        }
        val list = onNodeWithTag(LIST_TAG)
        var fingerAt = Offset.Zero
        list.performTouchInput {
            val start = thumbAtTop()
            fingerAt = Offset(right - 10f, bottom)
            down(start)
            moveInSteps(start, fingerAt)
        }
        waitForIdle()

        // A page loads while the finger is still down.
        itemCount = 200
        waitForIdle()

        list.performTouchInput {
            val farBelow = Offset(fingerAt.x, bottom * 3)
            moveInSteps(fingerAt, farBelow)
            up()
        }
        waitForIdle()
        assertEquals(200, state.layoutInfo.totalItemsCount)
        assertFalse(state.canScrollForward, "the drag should continue into the new items")
    }

    @Test
    fun itemsInsertedAtTheTopMidDragAreReachableInTheSameDrag() = runComposeUiTest {
        lateinit var state: LazyListState
        val ids = mutableStateListOf<Int>().apply { addAll(0 until 100) }
        setContent {
            state = rememberLazyListState()
            KeyedTestList(state, ids)
        }
        val list = onNodeWithTag(LIST_TAG)
        var fingerAt = Offset.Zero
        list.performTouchInput {
            val start = thumbAtTop()
            fingerAt = Offset(right - 10f, bottom / 2)
            down(start)
            moveInSteps(start, fingerAt)
        }
        waitForIdle()

        // New items arrive above, like an inbox, while the finger is still down.
        ids.addAll(0, List(100) { -1 - it })
        waitForIdle()

        list.performTouchInput {
            val farBelow = Offset(fingerAt.x, bottom * 3)
            moveInSteps(fingerAt, farBelow)
            up()
        }
        waitForIdle()
        assertEquals(200, state.layoutInfo.totalItemsCount)
        assertFalse(state.canScrollForward, "the drag should still reach the end")
    }

    @Test
    fun theThumbStaysGrabbableAfterItemsAreInsertedAtTheTop() = runComposeUiTest {
        lateinit var state: LazyListState
        val ids = mutableStateListOf<Int>().apply { addAll(0 until 100) }
        setContent {
            state = rememberLazyListState(initialFirstVisibleItemIndex = 50)
            KeyedTestList(state, ids)
        }
        waitForIdle()

        ids.addAll(0, List(100) { -1 - it })
        waitForIdle()
        // With keys, the list stays on the same item, which is now 100 places further down.
        assertEquals(150, state.firstVisibleItemIndex)

        // 200 items of 50 dp in a 600 dp list: offset 7,500 of 9,400, so progress is about 0.8.
        // The track is 600 - 2 * 8 = 584 dp, minus the 40 dp thumb leaves 544 dp of travel, so the
        // thumb's center is at 8 + 544 * 0.798 + 20 = about 462 dp. If the thumb were drawn
        // elsewhere, this touch would miss it and swipe the list instead.
        onNodeWithTag(LIST_TAG).performTouchInput {
            drag(Offset(right - 10f, 462.dp.toPx()), Offset(right - 10f, bottom * 2))
        }
        waitForIdle()
        assertFalse(state.canScrollForward, "grabbing the thumb where it's drawn should reach the end")
    }

    @Test
    fun withHasMoreDraggingToTheBottomReachesTheLastLoadedItem() = runComposeUiTest {
        lateinit var state: LazyListState
        setContent {
            state = rememberLazyListState()
            TestList(state, itemCount = 500, hasMore = true)
        }
        onNodeWithTag(LIST_TAG).performTouchInput {
            drag(thumbAtTop(), Offset(right - 10f, bottom * 2))
        }
        waitForIdle()
        assertFalse(state.canScrollForward, "the capped thumb still maps to the last loaded item")
    }

    @Test
    fun mouseClickOnTheTrackJumpsThere() = runComposeUiTest {
        lateinit var state: LazyListState
        setContent {
            state = rememberLazyListState()
            TestList(state, itemCount = 10_000)
        }
        onNodeWithTag(LIST_TAG).performMouseInput {
            // The track has equal padding at both ends, so its middle is the list's middle.
            moveTo(Offset(right - 5f, bottom / 2f))
            press()
            release()
        }
        waitForIdle()
        assertTrue(state.firstVisibleItemIndex in 4_900..5_100, "landed at ${state.firstVisibleItemIndex}")
    }

    @Test
    fun longPressAndDragAcrossMovesTheBarToTheOtherEdge() = runComposeUiTest {
        lateinit var state: LazyListState
        val edgeChanges = mutableListOf<ScrollbarEdge>()
        setContent {
            state = rememberLazyListState()
            TestList(state, itemCount = 10_000, onEdgeChange = { edgeChanges += it })
        }
        val list = onNodeWithTag(LIST_TAG)
        var grabbedAt = Offset.Zero
        list.performTouchInput {
            grabbedAt = thumbAtTop()
            down(grabbedAt)
        }
        mainClock.advanceTimeBy(3_000) // past the hold even with a slow system touch-and-hold setting
        list.performTouchInput {
            moveInSteps(grabbedAt, Offset(left + 10f, grabbedAt.y))
            up()
        }
        waitForIdle()
        assertEquals(listOf(ScrollbarEdge.Start), edgeChanges)
        assertEquals(0, state.firstVisibleItemIndex, "moving the bar must not scroll")
    }

    @Test
    fun aShortHoldDoesNotPickUpTheBar() = runComposeUiTest {
        lateinit var state: LazyListState
        val edgeChanges = mutableListOf<ScrollbarEdge>()
        setContent {
            state = rememberLazyListState()
            TestList(state, itemCount = 10_000, onEdgeChange = { edgeChanges += it })
        }
        val list = onNodeWithTag(LIST_TAG)
        var grabbedAt = Offset.Zero
        list.performTouchInput {
            grabbedAt = thumbAtTop()
            down(grabbedAt)
        }
        // Shorter than the 500 ms minimum hold.
        mainClock.advanceTimeBy(300)
        list.performTouchInput {
            moveInSteps(grabbedAt, Offset(left + 10f, grabbedAt.y))
            up()
        }
        waitForIdle()
        assertTrue(edgeChanges.isEmpty(), "a hesitant grab must not start a move")
    }

    @Test
    fun aQuickGrabAndDragScrollsInsteadOfPickingUpTheBar() = runComposeUiTest {
        lateinit var state: LazyListState
        val edgeChanges = mutableListOf<ScrollbarEdge>()
        setContent {
            state = rememberLazyListState()
            TestList(state, itemCount = 10_000, onEdgeChange = { edgeChanges += it })
        }
        onNodeWithTag(LIST_TAG).performTouchInput {
            drag(thumbAtTop(), Offset(right - 10f, bottom * 2))
        }
        waitForIdle()
        assertTrue(edgeChanges.isEmpty(), "a moving finger never picks the bar up")
        assertFalse(state.canScrollForward, "it should have been a normal drag")
    }

    @Test
    fun draggingDoesNotRecomposeTheList() = runComposeUiTest {
        var compositions = 0
        setContent {
            TestList(rememberLazyListState(), itemCount = 10_000, onComposed = { compositions++ })
        }
        waitForIdle()
        val before = compositions
        onNodeWithTag(LIST_TAG).performTouchInput {
            drag(thumbAtTop(), Offset(right - 10f, bottom * 0.6f))
        }
        waitForIdle()
        assertEquals(before, compositions, "scrollbar state must only be read in the draw phase")
    }
}
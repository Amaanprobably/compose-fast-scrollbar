package io.github.amaanprobably.scrollbar

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.mandatorySystemGestures
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.TouchInjectionScope
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.sign
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val SCROLL_TAG = "scroll"
private const val TO_START = "Scroll to top"
private const val TO_END = "Scroll to bottom"

// Same always-visible geometry as the list UI tests: the thumb's center rests 28 dp from the start.
private val scrollStateTestStyle = ScrollbarDefaults.style(
    thumbAppearance = ThumbAppearance.Capsule,
    thumbSizing = ThumbSizing.Fixed(40.dp),
    trackPadding = 8.dp,
    touchTargetThickness = 48.dp,
    edgePadding = 4.dp,
    capsuleThickness = 32.dp,
    capsuleExtraLength = 16.dp,
    visibility = ScrollbarVisibility.Always,
)

/** Clear of system bars and gesture zones, so the geometry is the same on desktop and phones. */
@Composable
private fun Modifier.testArea(): Modifier =
    windowInsetsPadding(WindowInsets.safeDrawing.union(WindowInsets.mandatorySystemGestures))
        .size(300.dp, 600.dp)
        .testTag(SCROLL_TAG)

/** A Column of [boxCount] boxes, 50 dp each, with the scrollbar before verticalScroll. */
@Composable
private fun TestColumn(state: ScrollState, boxCount: Int = 200, reverseScrolling: Boolean = false) {
    Column(
        Modifier
            .testArea()
            .scrollbar(
                state,
                reverseScrolling = reverseScrolling,
                style = scrollStateTestStyle,
                scrollToStartLabel = TO_START,
                scrollToEndLabel = TO_END,
            )
            .verticalScroll(state, reverseScrolling = reverseScrolling),
    ) {
        repeat(boxCount) { Box(Modifier.fillMaxWidth().height(50.dp)) }
    }
}

/** A Row of 200 boxes, 50 dp each, scrolled horizontally. */
@Composable
private fun TestRow(state: ScrollState) {
    Row(
        Modifier
            .testArea()
            .scrollbar(state, orientation = Orientation.Horizontal, style = scrollStateTestStyle)
            .horizontalScroll(state),
    ) {
        repeat(200) { Box(Modifier.fillMaxHeight().width(50.dp)) }
    }
}

private fun TouchInjectionScope.moveInSteps(from: Offset, to: Offset, steps: Int = 20) {
    for (step in 1..steps) moveTo(lerp(from, to, step / steps.toFloat()))
}

private fun TouchInjectionScope.drag(from: Offset, to: Offset) {
    down(from)
    moveInSteps(from, to)
    up()
}

/**
 * Grabs the thumb at [from] by moving just past the touch slop, then moves it by [by] along the
 * bar and lifts. The thumb follows the finger from where it was grabbed, so it moves by exactly [by].
 */
private fun TouchInjectionScope.grabAndMove(from: Offset, by: Float) {
    val grabbedAt = from + Offset(0f, sign(by) * (viewConfiguration.touchSlop + 1f))
    down(from)
    moveTo(grabbedAt)
    moveInSteps(grabbedAt, grabbedAt + Offset(0f, by))
    up()
}

/** Runs the custom accessibility action named [label] on the scroll area. */
@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.runAccessibilityAction(label: String) {
    val node = onNodeWithTag(SCROLL_TAG).fetchSemanticsNode()
    val action = node.config[SemanticsActions.CustomActions].first { it.label == label }
    runOnUiThread { action.action() }
}

@OptIn(ExperimentalTestApi::class)
class ScrollbarScrollStateTest {

    @Test
    fun draggingTheThumbToTheBottomReachesTheEnd() = runComposeUiTest {
        lateinit var state: ScrollState
        setContent {
            state = rememberScrollState()
            TestColumn(state)
        }
        onNodeWithTag(SCROLL_TAG).performTouchInput {
            drag(Offset(right - 10f, 28.dp.toPx()), Offset(right - 10f, bottom * 2))
        }
        waitForIdle()
        assertEquals(state.maxValue, state.value, "should be at the end")
    }

    @Test
    fun draggingToTheMiddleLandsInTheMiddle() = runComposeUiTest {
        lateinit var state: ScrollState
        setContent {
            state = rememberScrollState()
            TestColumn(state)
        }
        onNodeWithTag(SCROLL_TAG).performTouchInput {
            // Half the thumb's travel: track = height - 2 * 8 dp - 40 dp.
            val halfTrack = (height - 56.dp.toPx()) / 2f
            grabAndMove(Offset(right - 10f, 28.dp.toPx()), halfTrack)
        }
        waitForIdle()
        val half = state.maxValue / 2
        assertTrue(abs(state.value - half) <= state.maxValue / 100, "landed at ${state.value}, half is $half")
    }

    @Test
    fun theBarStaysHiddenWhenTheContentFits() = runComposeUiTest {
        lateinit var state: ScrollState
        setContent {
            state = rememberScrollState()
            TestColumn(state, boxCount = 5) // 250 dp in a 600 dp area
        }
        onNodeWithTag(SCROLL_TAG).performTouchInput {
            drag(Offset(right - 10f, 28.dp.toPx()), Offset(right - 10f, bottom * 2))
        }
        waitForIdle()
        assertEquals(0, state.maxValue)
        assertEquals(0, state.value)
    }

    @Test
    fun reverseScrollingStartsAtTheBottomAndDragsUpToTheEnd() = runComposeUiTest {
        lateinit var state: ScrollState
        setContent {
            state = rememberScrollState()
            TestColumn(state, reverseScrolling = true)
        }
        onNodeWithTag(SCROLL_TAG).performTouchInput {
            // With reverseScrolling, value 0 is the bottom, so the thumb rests there.
            drag(Offset(right - 10f, bottom - 28.dp.toPx()), Offset(right - 10f, -bottom))
        }
        waitForIdle()
        assertEquals(state.maxValue, state.value, "dragging up should reach the end")
    }

    @Test
    fun horizontalDraggingReachesTheEnd() = runComposeUiTest {
        lateinit var state: ScrollState
        setContent {
            state = rememberScrollState()
            TestRow(state)
        }
        onNodeWithTag(SCROLL_TAG).performTouchInput {
            // The bar sits along the bottom; the thumb rests at the start, on the left.
            drag(Offset(28.dp.toPx(), bottom - 10f), Offset(right * 2, bottom - 10f))
        }
        waitForIdle()
        assertEquals(state.maxValue, state.value, "should be at the end")
    }

    @Test
    fun horizontalInRightToLeftStartsOnTheRightAndDragsLeftToTheEnd() = runComposeUiTest {
        lateinit var state: ScrollState
        setContent {
            state = rememberScrollState()
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                TestRow(state)
            }
        }
        onNodeWithTag(SCROLL_TAG).performTouchInput {
            // In RTL the start is on the right, so the thumb rests there and the end is on the left.
            drag(Offset(right - 28.dp.toPx(), bottom - 10f), Offset(-right, bottom - 10f))
        }
        waitForIdle()
        assertEquals(state.maxValue, state.value, "dragging left should reach the end")
    }

    @Test
    fun contentThatGrowsMidDragIsReachableInTheSameDrag() = runComposeUiTest {
        lateinit var state: ScrollState
        var boxCount by mutableStateOf(100)
        setContent {
            state = rememberScrollState()
            TestColumn(state, boxCount = boxCount)
        }
        val column = onNodeWithTag(SCROLL_TAG)
        var fingerAt = Offset.Zero
        column.performTouchInput {
            val start = Offset(right - 10f, 28.dp.toPx())
            fingerAt = Offset(right - 10f, bottom / 2)
            down(start)
            moveInSteps(start, fingerAt)
        }
        waitForIdle()

        // More content appears while the finger is still down.
        boxCount = 200
        waitForIdle()

        column.performTouchInput {
            moveInSteps(fingerAt, Offset(fingerAt.x, bottom * 3))
            up()
        }
        waitForIdle()
        assertEquals(state.maxValue, state.value, "the drag should reach the end of the grown content")
    }

    @Test
    fun accessibilityActionsJumpToBothEnds() = runComposeUiTest {
        lateinit var state: ScrollState
        setContent {
            state = rememberScrollState()
            TestColumn(state)
        }
        waitForIdle()
        runAccessibilityAction(TO_END)
        waitForIdle()
        assertEquals(state.maxValue, state.value)
        runAccessibilityAction(TO_START)
        waitForIdle()
        assertEquals(0, state.value)
    }

    @Test
    fun scrollToEndDuringAFlingStaysAtTheEnd() = runComposeUiTest {
        lateinit var state: ScrollState
        setContent {
            state = rememberScrollState(initial = 10_000)
            TestColumn(state)
        }
        waitForIdle()
        mainClock.autoAdvance = false
        // A quick swipe down starts a fling back toward the top.
        onNodeWithTag(SCROLL_TAG).performTouchInput {
            swipeDown(startY = centerY - 200f, endY = centerY + 200f, durationMillis = 50)
        }
        mainClock.advanceTimeBy(48)
        val beforeJump = state.value
        assertTrue(state.isScrollInProgress, "the fling should be running")

        runAccessibilityAction(TO_END)
        mainClock.autoAdvance = true
        waitForIdle()
        assertTrue(state.value > beforeJump)
        assertEquals(state.maxValue, state.value, "the fling must not pull it back from the end")
    }

    @Test
    fun accessibilityActionsStillWorkOnLazyLists() = runComposeUiTest {
        // The actions now stop a running fling first; lists must still reach both ends.
        lateinit var state: LazyListState
        setContent {
            state = rememberLazyListState()
            LazyColumn(
                state = state,
                modifier = Modifier
                    .testArea()
                    .scrollbar(
                        state,
                        style = scrollStateTestStyle,
                        scrollToStartLabel = TO_START,
                        scrollToEndLabel = TO_END,
                    ),
            ) {
                items(1_000) { Box(Modifier.fillMaxWidth().height(50.dp)) }
            }
        }
        waitForIdle()
        runAccessibilityAction(TO_END)
        waitForIdle()
        assertFalse(state.canScrollForward, "should be at the end")
        runAccessibilityAction(TO_START)
        waitForIdle()
        assertFalse(state.canScrollBackward, "should be back at the start")
    }
}

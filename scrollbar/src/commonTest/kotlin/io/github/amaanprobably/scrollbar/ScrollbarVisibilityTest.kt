package io.github.amaanprobably.scrollbar

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.TouchInjectionScope
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val VISIBILITY_TAG = "visibilityList"

// The same geometry as the other UI tests: a 40 dp thumb with 8 dp of track padding, so at rest its
// center is 28 dp from the top. Auto-hiding: 150 ms in, 1.5 s up, 300 ms out.
private val visibilityTestStyle = ScrollbarDefaults.style(
    thumbAppearance = ThumbAppearance.Capsule,
    thumbSizing = ThumbSizing.Fixed(40.dp),
    trackPadding = 8.dp,
    touchTargetThickness = 48.dp,
    edgePadding = 4.dp,
    capsuleThickness = 32.dp,
    capsuleExtraLength = 16.dp,
    visibility = ScrollbarVisibility.AutoHide,
)

/** A 300 x 600 dp list of 50 dp rows with an auto-hiding bar. */
@Composable
private fun VisibilityList(state: LazyListState, itemCount: Int) {
    LazyColumn(
        state = state,
        modifier = Modifier
            .windowInsetsPadding(WindowInsets.safeDrawing.union(WindowInsets.mandatorySystemGestures))
            .size(300.dp, 600.dp)
            .testTag(VISIBILITY_TAG)
            .scrollbar(state, style = visibilityTestStyle),
    ) {
        items(itemCount) { Box(Modifier.fillMaxWidth().height(50.dp)) }
    }
}

private fun TouchInjectionScope.thumbAtTop() = Offset(right - 10f, 28.dp.toPx())

private fun TouchInjectionScope.moveInSteps(from: Offset, to: Offset, steps: Int = 20) {
    for (step in 1..steps) moveTo(lerp(from, to, step / steps.toFloat()))
}

private fun TouchInjectionScope.drag(from: Offset, to: Offset) {
    down(from)
    moveInSteps(from, to)
    up()
}

/**
 * When an auto-hiding bar shows and hides. Whether the thumb can be grabbed tells whether the bar
 * is showing: a hidden thumb can't be grabbed, so a drag from it is a plain swipe.
 */
@OptIn(ExperimentalTestApi::class)
class ScrollbarVisibilityTest {

    @Test
    fun anAutoHideBarShowsOnceWhenTheListAppears() = runComposeUiTest {
        // So people can see the list goes on before they scroll. It hides again after the usual
        // delay; aHiddenBarCannotBeGrabbed in ScrollbarUiTest checks that side.
        lateinit var state: LazyListState
        setContent {
            state = rememberLazyListState()
            VisibilityList(state, itemCount = 10_000)
        }
        waitForIdle()
        onNodeWithTag(VISIBILITY_TAG).performTouchInput { drag(thumbAtTop(), Offset(right - 10f, bottom * 2)) }
        waitForIdle()
        assertFalse(state.canScrollForward, "the bar showed when the list appeared, so the thumb could be grabbed")
    }

    @Test
    fun aListThatGrowsPastTheScreenShowsTheBarThen() = runComposeUiTest {
        // A list that fits has nothing to show. When items arrive and it can scroll, that's the
        // moment to show the bar, even long after the list appeared.
        lateinit var state: LazyListState
        var itemCount by mutableIntStateOf(5)
        setContent {
            state = rememberLazyListState()
            VisibilityList(state, itemCount)
        }
        waitForIdle()
        mainClock.advanceTimeBy(3_000) // longer than a first show would last
        runOnIdle { itemCount = 10_000 }
        waitForIdle()
        onNodeWithTag(VISIBILITY_TAG).performTouchInput { drag(thumbAtTop(), Offset(right - 10f, bottom * 2)) }
        waitForIdle()
        assertFalse(state.canScrollForward, "the bar should have shown once the list could scroll")
    }

    @Test
    fun aThumbThatWasLetGoHidesSoonerThanAfterAScroll() = runComposeUiTest {
        // 1.2 s after a drag instead of 1.5 s, like RecyclerView's fast scroller.
        lateinit var state: LazyListState
        setContent {
            state = rememberLazyListState()
            VisibilityList(state, itemCount = 10_000)
        }
        waitForIdle()
        mainClock.autoAdvance = false
        val list = onNodeWithTag(VISIBILITY_TAG)
        var thumbNowAt = Offset.Zero
        list.performTouchInput {
            // Grab past the slop, then move the thumb half the track: it ends up centered there.
            val start = thumbAtTop()
            val grabbedAt = start + Offset(0f, viewConfiguration.touchSlop + 1f)
            val halfTrack = (height - 56.dp.toPx()) / 2f
            thumbNowAt = start + Offset(0f, halfTrack)
            down(start)
            moveTo(grabbedAt)
            moveInSteps(grabbedAt, grabbedAt + Offset(0f, halfTrack))
            up()
        }
        // 1.2 s and the 300 ms fade, with some to spare: gone. With the 1.5 s delay of a scroll it
        // would only be halfway through fading out, and still grabbable.
        mainClock.advanceTimeBy(1_650)
        list.performTouchInput { drag(thumbNowAt, Offset(thumbNowAt.x, bottom * 2)) }
        mainClock.autoAdvance = true
        waitForIdle()
        assertTrue(state.canScrollForward, "the bar should have hidden: this was a swipe, not a grab")
    }
}

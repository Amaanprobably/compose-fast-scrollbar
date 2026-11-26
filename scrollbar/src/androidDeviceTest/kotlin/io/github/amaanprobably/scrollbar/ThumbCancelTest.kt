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
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val CANCEL_TAG = "cancelList"

// The same geometry as ScrollbarTouchTest: the thumb's center rests 28 dp from the top.
private val cancelTestStyle = ScrollbarDefaults.style(
    thumbAppearance = ThumbAppearance.Capsule,
    thumbSizing = ThumbSizing.Fixed(40.dp),
    trackPadding = 8.dp,
    touchTargetThickness = 48.dp,
    edgePadding = 4.dp,
    capsuleThickness = 32.dp,
    capsuleExtraLength = 16.dp,
    visibility = ScrollbarVisibility.Always,
)

/** Rows count their clicks by index. The bar asks for its label once when it grabs the thumb. */
private class CancelProbe {
    val clicks = mutableMapOf<Int, Int>()
    var labelRequests = 0
    val edgeChanges = mutableListOf<ScrollbarEdge>()
    val label = ScrollbarLabel(sectionHaptics = false) { index ->
        labelRequests++
        "#$index"
    }
}

/** A 300 x 600 dp list of 10,000 clickable 50 dp rows, with edge moving turned on. */
@Composable
private fun CancelList(state: LazyListState, probe: CancelProbe) {
    LazyColumn(
        state = state,
        modifier = Modifier
            .windowInsetsPadding(WindowInsets.safeDrawing.union(WindowInsets.mandatorySystemGestures))
            .size(300.dp, 600.dp)
            .testTag(CANCEL_TAG)
            .scrollbar(
                state,
                style = cancelTestStyle,
                label = probe.label,
                onEdgeChange = { probe.edgeChanges += it },
            ),
    ) {
        items(10_000) { index ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .clickable { probe.clicks[index] = (probe.clicks[index] ?: 0) + 1 },
            )
        }
    }
}

private fun TouchInjectionScope.thumbAtTop() = Offset(right - 10f, 28.dp.toPx())

private fun TouchInjectionScope.moveInSteps(from: Offset, to: Offset, steps: Int = 20) {
    for (step in 1..steps) moveTo(lerp(from, to, step / steps.toFloat()))
}

/** A finger held still on the thumb this long, counted from the touch, picks it up to move sides. */
private fun ViewConfiguration.edgeHoldMillis() = max(longPressTimeoutMillis, 500L)

/**
 * Android only: touch cancels. The desktop and iOS test input sends nothing for cancel(), so
 * these can't run there. A cancel is what Android sends when the system or a parent takes the
 * touch, like the notification shade or a parent that starts its own scroll.
 */
@OptIn(ExperimentalTestApi::class)
class ThumbCancelTest {

    @Test
    fun aCanceledTouchNeitherGrabsNorClicks() = runComposeUiTest {
        val probe = CancelProbe()
        setContent { CancelList(rememberLazyListState(), probe) }
        waitForIdle()
        onNodeWithTag(CANCEL_TAG).performTouchInput {
            down(thumbAtTop())
            cancel()
        }
        mainClock.advanceTimeBy(1_000)
        waitForIdle()
        assertEquals(0, probe.labelRequests, "a canceled touch must not grab the thumb")
        assertTrue(probe.clicks.isEmpty(), "a canceled touch must not click the row")
    }

    @Test
    fun aDragCanceledMidwayLeavesNothingStuck() = runComposeUiTest {
        val probe = CancelProbe()
        lateinit var state: LazyListState
        setContent {
            state = rememberLazyListState()
            CancelList(state, probe)
        }
        val list = onNodeWithTag(CANCEL_TAG)
        var thumbNowAt = Offset.Zero
        list.performTouchInput {
            val start = thumbAtTop()
            val grabbedAt = start + Offset(0f, viewConfiguration.touchSlop + 1f)
            val tenthOfTrack = (height - 56.dp.toPx()) / 10f
            thumbNowAt = start + Offset(0f, tenthOfTrack)
            down(start)
            moveTo(grabbedAt)
            moveInSteps(grabbedAt, grabbedAt + Offset(0f, tenthOfTrack))
            cancel()
        }
        waitForIdle()
        assertTrue(probe.labelRequests > 0, "the drag should have grabbed the thumb")
        assertTrue(probe.clicks.isEmpty(), "a canceled drag must not click the row")

        // The bar let go: the thumb sits where the list is, a tenth down, and grabs again.
        list.performTouchInput {
            down(thumbNowAt)
            moveInSteps(thumbNowAt, Offset(thumbNowAt.x, bottom * 2))
            up()
        }
        waitForIdle()
        assertFalse(state.canScrollForward, "a new drag after the cancel should work as usual")
    }

    @Test
    fun aTouchCanceledAfterBeingPickedUpChangesNothing() = runComposeUiTest {
        val probe = CancelProbe()
        lateinit var config: ViewConfiguration
        setContent {
            config = LocalViewConfiguration.current
            CancelList(rememberLazyListState(), probe)
        }
        waitForIdle()
        mainClock.autoAdvance = false
        val list = onNodeWithTag(CANCEL_TAG)
        list.performTouchInput { down(thumbAtTop()) }
        mainClock.advanceTimeBy(config.edgeHoldMillis() + 50) // grabbed by the hold, then picked up
        list.performTouchInput {
            moveTo(Offset(left + 10f, thumbAtTop().y)) // carried to the other side...
            cancel() // ...and canceled before it's dropped
        }
        mainClock.autoAdvance = true
        waitForIdle()
        assertTrue(probe.edgeChanges.isEmpty(), "a cancel while picked up must not move the bar")
        assertTrue(probe.clicks.isEmpty(), "a cancel must not click the row")

        // Nothing is left stuck: a tap on the thumb reaches the row again and doesn't grab.
        val requestsBefore = probe.labelRequests
        list.performTouchInput { click(thumbAtTop()) }
        waitForIdle()
        assertEquals(1, probe.clicks[0], "after the cancel, a tap on the thumb should reach the row")
        assertEquals(requestsBefore, probe.labelRequests, "and it must not grab the thumb")
    }
}

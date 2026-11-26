package io.github.amaanprobably.scrollbar

import android.os.Build
import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Android only: checks the thumb is excluded from the back gesture, and only while visible. */
@OptIn(ExperimentalTestApi::class)
class EdgeGestureGuardTest {

    @Composable
    private fun FullScreenList(visibility: ScrollbarVisibility) {
        val state = rememberLazyListState()
        LazyColumn(
            state = state,
            modifier = Modifier
                .fillMaxSize()
                .scrollbar(state, style = ScrollbarDefaults.style(visibility = visibility)),
        ) {
            items(1_000) { Box(Modifier.fillMaxWidth().height(50.dp)) }
        }
    }

    /** Two lists stacked on one screen, both with the bar on the right, sharing one View. */
    @Composable
    private fun TwoLists(topVisibility: ScrollbarVisibility, showBottom: Boolean) {
        Column(Modifier.fillMaxSize()) {
            val topState = rememberLazyListState()
            LazyColumn(
                state = topState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .scrollbar(topState, style = ScrollbarDefaults.style(visibility = topVisibility)),
            ) {
                items(1_000) { Box(Modifier.fillMaxWidth().height(50.dp)) }
            }
            if (showBottom) {
                val bottomState = rememberLazyListState()
                LazyColumn(
                    state = bottomState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .scrollbar(bottomState, style = ScrollbarDefaults.style(visibility = ScrollbarVisibility.Always)),
                ) {
                    items(1_000) { Box(Modifier.fillMaxWidth().height(50.dp)) }
                }
            }
        }
    }

    @Test
    fun twoListsOnOneScreenEachProtectTheirOwnThumb() = runComposeUiTest {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return@runComposeUiTest
        lateinit var view: View
        setContent {
            view = LocalView.current
            TwoLists(topVisibility = ScrollbarVisibility.Always, showBottom = true)
        }
        waitForIdle()

        val rects = view.systemGestureExclusionRects.sortedBy { it.top }
        assertEquals(2, rects.size, "one protected area per thumb, neither replacing the other")
        assertTrue(rects[0].top < view.height / 2, "the first is the top list's thumb")
        assertTrue(rects[1].top >= view.height / 2, "the second is the bottom list's thumb")
        assertTrue(rects.all { it.right == view.width }, "both hug the right edge")
    }

    @Test
    fun hidingOneListKeepsTheOthersProtection() = runComposeUiTest {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return@runComposeUiTest
        lateinit var view: View
        var topVisibility by mutableStateOf(ScrollbarVisibility.Always)
        setContent {
            view = LocalView.current
            TwoLists(topVisibility = topVisibility, showBottom = true)
        }
        waitForIdle()
        assertEquals(2, view.systemGestureExclusionRects.size)

        // Back to auto-hide: the top bar fades out after its delay and releases its edge.
        topVisibility = ScrollbarVisibility.AutoHide
        mainClock.advanceTimeBy(3_000)
        waitForIdle()

        val rects = view.systemGestureExclusionRects
        assertEquals(1, rects.size, "only the visible bottom thumb is still protected")
        assertTrue(rects.single().top >= view.height / 2)
    }

    @Test
    fun removingOneListKeepsTheOthersProtection() = runComposeUiTest {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return@runComposeUiTest
        lateinit var view: View
        var showBottom by mutableStateOf(true)
        setContent {
            view = LocalView.current
            TwoLists(topVisibility = ScrollbarVisibility.Always, showBottom = showBottom)
        }
        waitForIdle()
        assertEquals(2, view.systemGestureExclusionRects.size)

        showBottom = false // the bottom list leaves the screen, like switching tabs
        waitForIdle()

        val rects = view.systemGestureExclusionRects
        assertEquals(1, rects.size, "the removed list cleans up only its own area")
        assertTrue(rects.single().top < view.height / 2, "the top list's thumb is still protected")
    }

    @Test
    fun visibleThumbIsExcludedFromTheBackGesture() = runComposeUiTest {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return@runComposeUiTest // no gesture nav API
        lateinit var view: View
        setContent {
            view = LocalView.current
            FullScreenList(ScrollbarVisibility.Always)
        }
        waitForIdle()

        val rects = view.systemGestureExclusionRects
        assertEquals(1, rects.size, "one protected area, around the thumb")
        val rect = rects.single()
        assertEquals(view.width, rect.right, "it hugs the right edge, where the bar is")
        assertTrue(rect.width() > 0 && rect.height() > 0)
        // The thumb rests at the top, so the protected area is near the top, and stays far under
        // Android's 200 dp per-edge limit.
        assertTrue(rect.top < view.height / 4, "top ${rect.top} px of ${view.height}")
        val density = view.resources.displayMetrics.density
        assertTrue(rect.height() < 200 * density, "height ${rect.height()} px")
    }

    @Test
    fun hiddenBarLeavesTheEdgeToTheSystem() = runComposeUiTest {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return@runComposeUiTest
        lateinit var view: View
        setContent {
            view = LocalView.current
            FullScreenList(ScrollbarVisibility.AutoHide)
        }
        waitForIdle()
        mainClock.advanceTimeBy(2_500) // past the bar's first show: hidden until the list scrolls
        waitForIdle()
        assertTrue(view.systemGestureExclusionRects.isEmpty(), "a hidden bar must not block the back gesture")
    }
}
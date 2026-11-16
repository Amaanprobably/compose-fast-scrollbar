package io.github.amaanprobably.scrollbar

import android.os.Build
import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
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
            FullScreenList(ScrollbarVisibility.AutoHide) // hidden until the list scrolls
        }
        waitForIdle()
        assertTrue(view.systemGestureExclusionRects.isEmpty(), "a hidden bar must not block the back gesture")
    }
}
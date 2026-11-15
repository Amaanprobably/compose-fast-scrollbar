package io.github.amaanprobably.scrollbar

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private val testDensity = Density(1f) // 1 dp == 1 px

// Explicit values, so these tests don't depend on the platform defaults.
private val testStyle = ScrollbarDefaults.style(
    thumbAppearance = ThumbAppearance.Default,
    thumbThickness = 5.dp,
    pressedThumbThickness = 12.dp,
    touchTargetThickness = 48.dp,
    edgePadding = 4.dp,
    capsuleThickness = 32.dp,
)

private fun config(
    edge: FastScrollEdge = FastScrollEdge.END,
    layoutDirection: LayoutDirection = LayoutDirection.Ltr,
    style: ScrollbarStyle = testStyle,
) = ScrollbarConfig(
    style = style,
    contentPadding = PaddingValues(0.dp),
    minItemsToShow = 10,
    edge = edge,
    layoutDirection = layoutDirection,
    label = null,
    hasMore = false,
    onEdgeChange = null,
)

private fun inStrip(config: ScrollbarConfig, isVertical: Boolean, crossLength: Float, cross: Float) =
    with(testDensity) { isInBarStrip(config, isVertical, crossLength, cross) }

class BarStripTest {
    @Test
    fun endEdgeOfVerticalListIsTheRightSideInLtr() {
        val config = config(FastScrollEdge.END)
        assertTrue(inStrip(config, isVertical = true, crossLength = 400f, cross = 390f))
        assertTrue(inStrip(config, isVertical = true, crossLength = 400f, cross = 352f)) // exactly 48 from the edge
        assertFalse(inStrip(config, isVertical = true, crossLength = 400f, cross = 351f))
        assertFalse(inStrip(config, isVertical = true, crossLength = 400f, cross = 10f))
    }

    @Test
    fun startEdgeOfVerticalListIsTheLeftSideInLtr() {
        val config = config(FastScrollEdge.START)
        assertTrue(inStrip(config, isVertical = true, crossLength = 400f, cross = 48f))
        assertFalse(inStrip(config, isVertical = true, crossLength = 400f, cross = 49f))
    }

    @Test
    fun rtlFlipsTheSidesOfVerticalLists() {
        val config = config(FastScrollEdge.END, LayoutDirection.Rtl)
        assertTrue(inStrip(config, isVertical = true, crossLength = 400f, cross = 10f))
        assertFalse(inStrip(config, isVertical = true, crossLength = 400f, cross = 390f))
    }

    @Test
    fun horizontalListsUseTopAndBottomRegardlessOfRtl() {
        val bottom = config(FastScrollEdge.END, LayoutDirection.Rtl)
        assertTrue(inStrip(bottom, isVertical = false, crossLength = 300f, cross = 290f))
        assertFalse(inStrip(bottom, isVertical = false, crossLength = 300f, cross = 10f))
        val top = config(FastScrollEdge.START, LayoutDirection.Rtl)
        assertTrue(inStrip(top, isVertical = false, crossLength = 300f, cross = 10f))
    }

    @Test
    fun aWideIconWidensTheStrip() {
        val iconStyle = ScrollbarDefaults.style(
            thumbAppearance = ThumbAppearance.Icon(ColorPainter(Color.Red), DpSize(80.dp, 80.dp)),
            touchTargetThickness = 48.dp,
            edgePadding = 4.dp,
        )
        val config = config(style = iconStyle)
        // 80 dp icon + 4 dp edge padding = 84 dp strip.
        assertTrue(inStrip(config, isVertical = true, crossLength = 400f, cross = 316f))
        assertFalse(inStrip(config, isVertical = true, crossLength = 400f, cross = 315f))
    }
}

class EdgeAtTest {
    @Test
    fun verticalLtrLeftIsStart() {
        val config = config(layoutDirection = LayoutDirection.Ltr)
        assertEquals(FastScrollEdge.START, config.edgeAt(isVertical = true, atOrigin = true))
        assertEquals(FastScrollEdge.END, config.edgeAt(isVertical = true, atOrigin = false))
    }

    @Test
    fun verticalRtlLeftIsEnd() {
        val config = config(layoutDirection = LayoutDirection.Rtl)
        assertEquals(FastScrollEdge.END, config.edgeAt(isVertical = true, atOrigin = true))
        assertEquals(FastScrollEdge.START, config.edgeAt(isVertical = true, atOrigin = false))
    }

    @Test
    fun horizontalTopIsAlwaysStart() {
        for (direction in LayoutDirection.entries) {
            assertEquals(FastScrollEdge.START, config(layoutDirection = direction).edgeAt(false, atOrigin = true))
        }
    }

    @Test
    fun edgeAtUndoesIsCrossAxisAtOrigin() {
        for (edge in FastScrollEdge.entries) {
            for (direction in LayoutDirection.entries) {
                for (isVertical in listOf(true, false)) {
                    val config = config(edge, direction)
                    val atOrigin = config.isCrossAxisAtOrigin(isVertical)
                    assertEquals(edge, config.edgeAt(isVertical, atOrigin), "$edge $direction vertical=$isVertical")
                }
            }
        }
    }
}

class CenteredProgressTest {
    private fun geometry(startPadding: Float = 0f, endPadding: Float = 0f, progress: Float = 0f) =
        ThumbGeometry().apply {
            set(testDensity, 1_000f, startPadding, endPadding, ThumbSizing.Fixed(40.dp), 0.1f, progress, -1f)
        }

    @Test
    fun centerOfTheContainerIsHalfway() {
        // Thumb 40, track 960: centering at 500 puts the thumb start at 480 = half the track.
        assertEquals(0.5f, centeredProgress(geometry(), 0f, 500f, isMirrored = false), 1e-6f)
    }

    @Test
    fun clampsAtBothEnds() {
        assertEquals(0f, centeredProgress(geometry(), 0f, 0f, isMirrored = false))
        assertEquals(1f, centeredProgress(geometry(), 0f, 1_000f, isMirrored = false))
    }

    @Test
    fun mirroredFlipsTheResult() {
        // Start at 240 of 960 = 0.25 on screen, 0.75 in list order.
        assertEquals(0.75f, centeredProgress(geometry(), 0f, 260f, isMirrored = true), 1e-6f)
    }

    @Test
    fun clickingTheThumbCenterKeepsItsPosition() {
        // Track click and hover preview both use this, so pointing at the thumb's own center
        // must map back to where the thumb already is, with or without padding.
        for (padding in listOf(0f to 0f, 100f to 50f)) {
            var step = 0
            while (step <= 20) {
                val progress = step / 20f
                val geometry = geometry(padding.first, padding.second, progress)
                val center = geometry.start + geometry.length / 2f
                assertEquals(progress, centeredProgress(geometry, padding.first, center, false), 1e-4f)
                step++
            }
        }
    }
}

class ThumbPlacementTest {
    @Test
    fun crossLengthDependsOnAppearance() = with(testDensity) {
        assertEquals(32f, crossLengthOfThumb(testStyle, isVertical = true))
        val bar = ScrollbarDefaults.style(thumbAppearance = ThumbAppearance.Bar, pressedThumbThickness = 12.dp)
        assertEquals(12f, crossLengthOfThumb(bar, isVertical = true)) // room to widen in place
        val icon = ScrollbarDefaults.style(
            thumbAppearance = ThumbAppearance.Icon(ColorPainter(Color.Red), DpSize(24.dp, 30.dp)),
        )
        assertEquals(24f, crossLengthOfThumb(icon, isVertical = true))
        assertEquals(30f, crossLengthOfThumb(icon, isVertical = false))
    }

    @Test
    fun restingPositionSitsAtTheEdgePadding() = with(testDensity) {
        assertEquals(364f, restingCrossStart(config(FastScrollEdge.END), true, 400f, 32f))
        assertEquals(4f, restingCrossStart(config(FastScrollEdge.START), true, 400f, 32f))
    }
}

class MoveHintPlacementTest {
    @Test
    fun hintSitsOnTheInnerSideOfTheThumb() {
        // Thumb at the right edge (crossStart 364, 32 wide, list 400): hint goes to its left.
        assertEquals(364f - 8f - 14f, moveHintCrossCenter(364f, 32f, 400f, hintRadius = 14f, gap = 8f))
        // Thumb at the left edge: hint goes to its right.
        assertEquals(4f + 32f + 8f + 14f, moveHintCrossCenter(4f, 32f, 400f, hintRadius = 14f, gap = 8f))
    }

    @Test
    fun hintStaysInsideANarrowList() {
        val center = moveHintCrossCenter(4f, 32f, 50f, hintRadius = 14f, gap = 8f)
        assertTrue(center in 14f..36f, "center $center must keep the whole circle inside")
    }
}

class StyleDefaultsTest {
    @Test
    fun fixedThumbDefaultsTo40Dp() = assertEquals(40.dp, ThumbSizing.Fixed().length)

    @Test
    fun defaultStyleFollowsThePlatform() {
        val expected = if (isDesktopPlatform) ThumbAppearance.Bar else ThumbAppearance.Default
        assertEquals(expected, ScrollbarDefaults.style().thumbAppearance)
        assertEquals(expected, DefaultScrollbarStyle.thumbAppearance)
    }

    @Test
    fun platformStylePicksTheStyleForThisPlatform() {
        val touch = ScrollbarDefaults.style(thumbColor = Color.Red)
        val desktop = ScrollbarDefaults.compactStyle(thumbColor = Color.Blue)
        val expected = if (isDesktopPlatform) desktop else touch
        assertTrue(ScrollbarDefaults.platformStyle(touch, desktop) === expected)
    }

    @Test
    fun compactStyleIsTheDesktopLookEverywhere() {
        val compact = ScrollbarDefaults.compactStyle()
        assertEquals(ThumbAppearance.Bar, compact.thumbAppearance)
        assertEquals(16.dp, compact.touchTargetThickness)
    }
}
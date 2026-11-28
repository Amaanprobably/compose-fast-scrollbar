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
import kotlin.test.assertSame
import kotlin.test.assertTrue

private val testDensity = Density(1f) // 1 dp == 1 px

// Explicit values, so these tests don't depend on the platform defaults.
private val testStyle = ScrollbarDefaults.style(
    thumbAppearance = ThumbAppearance.Capsule,
    thumbSizing = ThumbSizing.Fixed(40.dp),
    thumbThickness = 5.dp,
    pressedThumbThickness = 12.dp,
    touchTargetThickness = 48.dp,
    edgePadding = 4.dp,
    capsuleThickness = 32.dp,
)

private fun config(
    edge: ScrollbarEdge = ScrollbarEdge.End,
    layoutDirection: LayoutDirection = LayoutDirection.Ltr,
    style: ScrollbarStyle = testStyle,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    zoneTop: Float = 0f,
    zoneBottom: Float = 0f,
    barLeft: Float = 0f,
    barTop: Float = 0f,
    barRight: Float = 0f,
    barBottom: Float = 0f,
) = ScrollbarConfig(
    style = style,
    contentPadding = contentPadding,
    minItemsToShow = 10,
    edge = edge,
    layoutDirection = layoutDirection,
    label = null,
    hasMore = false,
    onEdgeChange = null,
    gestureZones = GestureZoneOverlap().apply {
        top = zoneTop
        bottom = zoneBottom
    },
    systemBars = SystemBarOverlap().apply {
        left = barLeft
        top = barTop
        right = barRight
        bottom = barBottom
    },
)

private fun inStrip(config: ScrollbarConfig, isVertical: Boolean, crossLength: Float, cross: Float) =
    with(testDensity) { isInBarStrip(config, isVertical, crossLength, cross) }

class BarStripTest {
    @Test
    fun endEdgeOfVerticalListIsTheRightSideInLtr() {
        val config = config(ScrollbarEdge.End)
        assertTrue(inStrip(config, isVertical = true, crossLength = 400f, cross = 390f))
        assertTrue(inStrip(config, isVertical = true, crossLength = 400f, cross = 352f)) // exactly 48 from the edge
        assertFalse(inStrip(config, isVertical = true, crossLength = 400f, cross = 351f))
        assertFalse(inStrip(config, isVertical = true, crossLength = 400f, cross = 10f))
    }

    @Test
    fun startEdgeOfVerticalListIsTheLeftSideInLtr() {
        val config = config(ScrollbarEdge.Start)
        assertTrue(inStrip(config, isVertical = true, crossLength = 400f, cross = 48f))
        assertFalse(inStrip(config, isVertical = true, crossLength = 400f, cross = 49f))
    }

    @Test
    fun rtlFlipsTheSidesOfVerticalLists() {
        val config = config(ScrollbarEdge.End, LayoutDirection.Rtl)
        assertTrue(inStrip(config, isVertical = true, crossLength = 400f, cross = 10f))
        assertFalse(inStrip(config, isVertical = true, crossLength = 400f, cross = 390f))
    }

    @Test
    fun horizontalListsUseTopAndBottomRegardlessOfRtl() {
        val bottom = config(ScrollbarEdge.End, LayoutDirection.Rtl)
        assertTrue(inStrip(bottom, isVertical = false, crossLength = 300f, cross = 290f))
        assertFalse(inStrip(bottom, isVertical = false, crossLength = 300f, cross = 10f))
        val top = config(ScrollbarEdge.Start, LayoutDirection.Rtl)
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
        assertEquals(ScrollbarEdge.Start, config.edgeAt(isVertical = true, atOrigin = true))
        assertEquals(ScrollbarEdge.End, config.edgeAt(isVertical = true, atOrigin = false))
    }

    @Test
    fun verticalRtlLeftIsEnd() {
        val config = config(layoutDirection = LayoutDirection.Rtl)
        assertEquals(ScrollbarEdge.End, config.edgeAt(isVertical = true, atOrigin = true))
        assertEquals(ScrollbarEdge.Start, config.edgeAt(isVertical = true, atOrigin = false))
    }

    @Test
    fun horizontalTopIsAlwaysStart() {
        for (direction in LayoutDirection.entries) {
            assertEquals(ScrollbarEdge.Start, config(layoutDirection = direction).edgeAt(false, atOrigin = true))
        }
    }

    @Test
    fun edgeAtUndoesIsCrossAxisAtOrigin() {
        for (edge in ScrollbarEdge.entries) {
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

class InsetOverlapTest {
    @Test
    fun listTouchingTheBottomOverlapsTheBottomZone() {
        // Window 800 tall, zone 32 at the bottom, list from 100 to 800.
        assertEquals(32f, overlapWithEndInset(listStart = 100f, listLength = 700f, windowLength = 800f, insetLength = 32f))
    }

    @Test
    fun listAboveTheZoneDoesNotOverlap() {
        assertEquals(0f, overlapWithEndInset(listStart = 100f, listLength = 600f, windowLength = 800f, insetLength = 32f))
    }

    @Test
    fun listUnderTheTopZoneOverlapsIt() {
        assertEquals(24f, overlapWithStartInset(listStart = 0f, listLength = 500f, insetLength = 24f))
        assertEquals(0f, overlapWithStartInset(listStart = 60f, listLength = 500f, insetLength = 24f))
    }

    @Test
    fun overlapNeverExceedsTheList() {
        assertEquals(10f, overlapWithEndInset(listStart = 790f, listLength = 10f, windowLength = 800f, insetLength = 32f))
        assertEquals(10f, overlapWithStartInset(listStart = 0f, listLength = 10f, insetLength = 24f))
    }

    @Test
    fun aListUnderASideNavigationBarOverlapsIt() {
        // Landscape, window 2,000 wide, a 120 px navigation bar on the right. A list across the
        // whole width runs under it; one that stops where the bar starts doesn't.
        assertEquals(120f, overlapWithEndInset(listStart = 0f, listLength = 2_000f, windowLength = 2_000f, insetLength = 120f))
        assertEquals(0f, overlapWithEndInset(listStart = 0f, listLength = 1_880f, windowLength = 2_000f, insetLength = 120f))
        // A cutout on the left, and a list that starts partway under it.
        assertEquals(40f, overlapWithStartInset(listStart = 60f, listLength = 1_000f, insetLength = 100f))
    }

    @Test
    fun aListPastTheWindowEdgeOverlapsOnlyTheInset() {
        // A pager page halfway in from the right, window 1,000 wide, no side inset: the page
        // isn't under anything, so its bar stays at the page's own edge.
        assertEquals(0f, overlapWithEndInset(listStart = 500f, listLength = 1_000f, windowLength = 1_000f, insetLength = 0f))
        // Same with a 120 px navigation bar on the right: the bar moves in by the inset, no more.
        assertEquals(120f, overlapWithEndInset(listStart = 500f, listLength = 1_000f, windowLength = 1_000f, insetLength = 120f))
        // A page halfway out to the left, and a list scrolled 300 px above the window's top.
        assertEquals(0f, overlapWithStartInset(listStart = -500f, listLength = 1_000f, insetLength = 0f))
        assertEquals(24f, overlapWithStartInset(listStart = -300f, listLength = 1_000f, insetLength = 24f))
        // A list running 400 px below the window's bottom.
        assertEquals(32f, overlapWithEndInset(listStart = 0f, listLength = 1_200f, windowLength = 800f, insetLength = 32f))
    }

    @Test
    fun anUnknownWindowSizeCoversNothing() {
        // Before the window has a size, the whole list would look covered and the bar would land
        // in the middle of the screen.
        assertEquals(0f, overlapWithEndInset(listStart = 0f, listLength = 2_000f, windowLength = 0f, insetLength = 120f))
    }
}

class GestureZonePaddingTest {
    private val bar = ScrollbarDefaults.style(thumbAppearance = ThumbAppearance.Bar, trackPadding = 8.dp)

    @Test
    fun trackStaysOutOfTheBottomZone() {
        val config = config(style = bar, zoneBottom = 48f)
        assertEquals(48f + 8f, config.mainAxisEndPaddingPx(testDensity, isVertical = true))
    }

    @Test
    fun contentPaddingAndZoneAreNotAddedTogether() {
        // An app that already passes the insets as content padding must not get them twice.
        val smallerPadding = config(style = bar, contentPadding = PaddingValues(bottom = 24.dp), zoneBottom = 48f)
        assertEquals(48f + 8f, smallerPadding.mainAxisEndPaddingPx(testDensity, isVertical = true))
        val largerPadding = config(style = bar, contentPadding = PaddingValues(bottom = 60.dp), zoneBottom = 48f)
        assertEquals(60f + 8f, largerPadding.mainAxisEndPaddingPx(testDensity, isVertical = true))
    }

    @Test
    fun topZoneMovesTheTrackStart() {
        assertEquals(24f + 8f, config(style = bar, zoneTop = 24f).mainAxisStartPaddingPx(testDensity, isVertical = true))
    }

    @Test
    fun horizontalBarAtTheBottomIsLiftedOutOfTheZone() {
        val bottomBar = config(edge = ScrollbarEdge.End, zoneBottom = 30f)
        assertEquals(30f, bottomBar.crossZoneInsetPx(isVertical = false))
        // Strip is 48 wide above the 30 px zone: 300 - 30 - 48 = 222.
        assertTrue(inStrip(bottomBar, isVertical = false, crossLength = 300f, cross = 222f))
        assertFalse(inStrip(bottomBar, isVertical = false, crossLength = 300f, cross = 221f))
        // Resting thumb: 300 - 32 (capsule) - 4 (edge padding) - 30 (zone) = 234.
        assertEquals(234f, with(testDensity) { restingCrossStart(bottomBar, false, 300f, 32f) })
    }

    @Test
    fun verticalBarsIgnoreTheZonesAcrossTheAxis() {
        assertEquals(0f, config(zoneTop = 24f, zoneBottom = 48f).crossZoneInsetPx(isVertical = true))
    }
}

class SystemBarPaddingTest {
    private val bar = ScrollbarDefaults.style(thumbAppearance = ThumbAppearance.Bar, trackPadding = 8.dp)

    @Test
    fun aVerticalBarSitsBesideASideNavigationBar() {
        // A list drawn behind a 120 px navigation bar on the right, like in landscape.
        val config = config(barRight = 120f)
        assertEquals(120f, config.crossZoneInsetPx(isVertical = true))
        // Resting thumb: 400 - 32 (capsule) - 4 (edge padding) - 120 (navigation bar) = 244.
        assertEquals(244f, with(testDensity) { restingCrossStart(config, true, 400f, 32f) })
        // The 48 px strip moves with it: 400 - 120 - 48 = 232.
        assertTrue(inStrip(config, isVertical = true, crossLength = 400f, cross = 232f))
        assertFalse(inStrip(config, isVertical = true, crossLength = 400f, cross = 231f))
    }

    @Test
    fun onlyTheSideTheBarIsOnCounts() {
        // A cutout on the left doesn't move a bar on the right, and the other way round.
        assertEquals(0f, config(edge = ScrollbarEdge.End, barLeft = 90f).crossZoneInsetPx(isVertical = true))
        assertEquals(90f, config(edge = ScrollbarEdge.Start, barLeft = 90f).crossZoneInsetPx(isVertical = true))
        // In RTL the end edge is the left side.
        val rtl = config(edge = ScrollbarEdge.End, layoutDirection = LayoutDirection.Rtl, barLeft = 90f)
        assertEquals(90f, rtl.crossZoneInsetPx(isVertical = true))
    }

    @Test
    fun aHorizontalBarTakesTheLargerOfTheGestureZoneAndTheSystemBar() {
        // A 3-button navigation bar has no gesture zone but still covers the bottom.
        assertEquals(96f, config(barBottom = 96f).crossZoneInsetPx(isVertical = false))
        assertEquals(96f, config(zoneBottom = 60f, barBottom = 96f).crossZoneInsetPx(isVertical = false))
        assertEquals(60f, config(zoneBottom = 60f, barBottom = 40f).crossZoneInsetPx(isVertical = false))
    }

    @Test
    fun theTrackStaysOutOfSystemBarsAtItsEnds() {
        // Vertical: the status bar at the top, a 3-button navigation bar at the bottom.
        val vertical = config(style = bar, barTop = 50f, barBottom = 96f)
        assertEquals(50f + 8f, vertical.mainAxisStartPaddingPx(testDensity, isVertical = true))
        assertEquals(96f + 8f, vertical.mainAxisEndPaddingPx(testDensity, isVertical = true))
        // Horizontal: a navigation bar on the right in landscape.
        val horizontal = config(style = bar, barRight = 120f)
        assertEquals(8f, horizontal.mainAxisStartPaddingPx(testDensity, isVertical = false))
        assertEquals(120f + 8f, horizontal.mainAxisEndPaddingPx(testDensity, isVertical = false))
    }

    @Test
    fun insetsPassedAsPaddingAreNotCountedTwice() {
        val config = config(style = bar, contentPadding = PaddingValues(bottom = 96.dp), barBottom = 96f)
        assertEquals(96f + 8f, config.mainAxisEndPaddingPx(testDensity, isVertical = true))
    }
}

class EdgeGuardSpanTest {
    @Test
    fun spanIsTheThumbPlusSlackInsideTheList() {
        assertEquals(76f, guardSpanStart(thumbStart = 100f, slack = 24f))
        assertEquals(0f, guardSpanStart(thumbStart = 10f, slack = 24f))
        assertEquals(164f, guardSpanEnd(thumbEnd = 140f, slack = 24f, containerLength = 1_000f))
        assertEquals(150f, guardSpanEnd(thumbEnd = 140f, slack = 24f, containerLength = 150f))
    }

    @Test
    fun guardIsOnlyUpdatedWhenTheThumbLeavesIt() {
        assertTrue(guardCoversThumb(76f, 164f, thumbStart = 90f, thumbEnd = 150f))
        assertFalse(guardCoversThumb(76f, 164f, thumbStart = 130f, thumbEnd = 170f))
        assertFalse(guardCoversThumb(Float.NaN, Float.NaN, thumbStart = 90f, thumbEnd = 150f), "nothing guarded yet")
    }
}

class ThumbEndPaddingTest {
    @Test
    fun theCapsuleStopsTheEdgeGapShortOfTheEnds() {
        // The thumb stays centered, so the capsule reaches 12 dp past it: 12 + 4 beats 8.
        val capsule = ScrollbarDefaults.style(
            thumbAppearance = ThumbAppearance.Capsule, trackPadding = 8.dp,
            edgePadding = 4.dp, capsuleExtraLength = 24.dp,
        )
        assertEquals(16.dp, config(style = capsule).thumbEndPadding)
    }

    @Test
    fun trackPaddingWinsWhenItIsAlreadyEnough() {
        val capsule = ScrollbarDefaults.style(
            thumbAppearance = ThumbAppearance.Capsule, trackPadding = 8.dp,
            edgePadding = 2.dp, capsuleExtraLength = 6.dp,
        )
        assertEquals(8.dp, config(style = capsule).thumbEndPadding) // 3 + 2 is less than 8
    }

    @Test
    fun theBarUsesTrackPaddingOnly() {
        val bar = ScrollbarDefaults.style(
            thumbAppearance = ThumbAppearance.Bar, trackPadding = 6.dp,
            edgePadding = 10.dp, capsuleExtraLength = 40.dp,
        )
        assertEquals(6.dp, config(style = bar).thumbEndPadding)
    }

    @Test
    fun anIconKeepsTheEdgeGapAtTheEnds() {
        val icon = ScrollbarDefaults.style(
            thumbAppearance = ThumbAppearance.Icon(ColorPainter(Color.Red), DpSize(24.dp, 30.dp)),
            trackPadding = 0.dp, edgePadding = 4.dp,
        )
        assertEquals(4.dp, config(style = icon).thumbEndPadding)
        val roomier = ScrollbarDefaults.style(
            thumbAppearance = ThumbAppearance.Icon(ColorPainter(Color.Red), DpSize(24.dp, 30.dp)),
            trackPadding = 8.dp, edgePadding = 4.dp,
        )
        assertEquals(8.dp, config(style = roomier).thumbEndPadding)
    }

    @Test
    fun aDefaultBarReachesTheEndsLikeTheSystemScrollbars() {
        // Close to the ends without touching them: iOS keeps 3pt and macOS 2px.
        assertEquals(2.dp, AndroidDefaults.trackPadding)
        assertEquals(3.dp, IosDefaults.trackPadding)
        assertEquals(2.dp, CompactDefaults.trackPadding)
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
        assertEquals(364f, restingCrossStart(config(ScrollbarEdge.End), true, 400f, 32f))
        assertEquals(4f, restingCrossStart(config(ScrollbarEdge.Start), true, 400f, 32f))
    }
}

class MoveHintPlacementTest {
    @Test
    fun hintGoesOnTheSideWithMoreRoom() {
        assertTrue(moveHintOnEndSide(thumbCrossCenter = 20f, crossLength = 400f), "left half: hint on the right")
        assertFalse(moveHintOnEndSide(thumbCrossCenter = 380f, crossLength = 400f), "right half: hint on the left")
    }

    @Test
    fun hintSitsNextToTheThumbOnEachSide() {
        // Thumb at the right edge (crossStart 364, 32 wide, list 400), hint on its left.
        assertEquals(364f - 8f - 14f, moveHintCrossCenter(364f, 32f, 400f, hintRadius = 14f, gap = 8f, endSide = 0f))
        // Thumb at the left edge, hint on its right.
        assertEquals(4f + 32f + 8f + 14f, moveHintCrossCenter(4f, 32f, 400f, hintRadius = 14f, gap = 8f, endSide = 1f))
    }

    @Test
    fun hintPassesTheThumbCenterHalfwayThroughTheSlide() {
        // Halfway between both sides is the thumb's own center (100 + 32 / 2 = 116)...
        assertEquals(116f, moveHintCrossCenter(100f, 32f, 400f, hintRadius = 14f, gap = 8f, endSide = 0.5f))
        // ...which is why it arcs around the thumb there, and not at either side.
        assertEquals(20f + 8f + 14f, moveHintArcOffset(0.5f, thumbHalfLength = 20f, gap = 8f, hintRadius = 14f), 1e-4f)
        assertEquals(0f, moveHintArcOffset(0f, 20f, 8f, 14f), 1e-4f)
        assertEquals(0f, moveHintArcOffset(1f, 20f, 8f, 14f), 1e-4f)
    }

    @Test
    fun hintStaysInsideANarrowList() {
        val center = moveHintCrossCenter(4f, 32f, 50f, hintRadius = 14f, gap = 8f, endSide = 1f)
        assertTrue(center in 14f..36f, "center $center must keep the whole circle inside")
    }
}

class AutomaticThumbSizingTest {
    @Test
    fun lazyListsOnTouchScreensGetAFixedThumb() {
        for (platform in listOf(Platform.Android, Platform.Ios)) {
            val sizing = ThumbSizing.Automatic.resolvedFor(hasExactContentSize = false, platform = platform)
            assertEquals(40.dp, (sizing as ThumbSizing.Fixed).length)
        }
    }

    @Test
    fun scrollStatesOnTouchScreensGetAProportionalThumb() {
        // Their content size is exact, so the length never jitters.
        for (platform in listOf(Platform.Android, Platform.Ios)) {
            val sizing = ThumbSizing.Automatic.resolvedFor(hasExactContentSize = true, platform = platform)
            assertTrue(sizing is ThumbSizing.Proportional && sizing.smoothed)
            assertEquals(48.dp, sizing.minLength)
        }
    }

    @Test
    fun desktopIsProportionalEverywhere() {
        for (exact in listOf(true, false)) {
            val sizing = ThumbSizing.Automatic.resolvedFor(hasExactContentSize = exact, platform = Platform.Desktop)
            assertEquals(24.dp, (sizing as ThumbSizing.Proportional).minLength)
        }
    }

    @Test
    fun aSizingYouPickIsKept() {
        val fixed = ThumbSizing.Fixed(56.dp)
        assertSame(fixed, fixed.resolvedFor(hasExactContentSize = true, platform = Platform.Android))
        val proportional = ThumbSizing.Proportional(minLength = 32.dp)
        assertSame(proportional, proportional.resolvedFor(hasExactContentSize = false, platform = Platform.Android))
    }

    @Test
    fun aCustomStyleOnTouchScreensStillGetsAutomatic() {
        // Leaving thumbSizing out of style(...) keeps the automatic choice, so a style that only
        // changes colors still gets the proportional thumb on a scrollable Column.
        val custom = ScrollbarDefaults.style(thumbColor = Color.Red)
        if (currentPlatform != Platform.Desktop) assertEquals(ThumbSizing.Automatic, custom.thumbSizing)
        assertTrue(config(style = custom).thumbSizing !is ThumbSizing.Automatic, "the config holds the resolved sizing")
    }
}

class StyleDefaultsTest {
    @Test
    fun fixedThumbDefaultsTo40Dp() = assertEquals(40.dp, ThumbSizing.Fixed().length)

    @Test
    fun defaultStyleFollowsThePlatform() {
        val expected = when (currentPlatform) {
            Platform.Android -> ThumbAppearance.Capsule
            Platform.Ios, Platform.Desktop -> ThumbAppearance.Bar
        }
        assertEquals(expected, ScrollbarDefaults.style().thumbAppearance)
        assertEquals(expected, DefaultScrollbarStyle.thumbAppearance)
    }

    @Test
    fun androidCapsuleIsSlimAndRoomy() {
        assertEquals(24.dp, AndroidDefaults.capsuleThickness)
        assertEquals(56.dp, AndroidDefaults.thumbLength + AndroidDefaults.capsuleExtraLength)
        // The capsule plus a 4 dp gap at each end takes the 64 dp the old flush capsule did, so the
        // thumb travels just as far.
        assertEquals(
            12.dp,
            AndroidDefaults.capsuleExtraLength / 2 + AndroidDefaults.edgePadding,
        )
        assertEquals(48.dp, AndroidDefaults.touchTargetThickness, "the grab area doesn't shrink with the capsule")
    }

    @Test
    fun iosBarIsThinAndWidensWhenGrabbed() {
        assertEquals(ThumbAppearance.Bar, IosDefaults.thumbAppearance)
        assertTrue(IosDefaults.thumbThickness < IosDefaults.pressedThumbThickness)
        assertEquals(48.dp, IosDefaults.touchTargetThickness, "a finger-sized grab area")
    }

    @Test
    fun platformStylePicksTheStyleForThisPlatform() {
        val android = ScrollbarDefaults.style(thumbColor = Color.Red)
        val ios = ScrollbarDefaults.style(thumbColor = Color.Green)
        val desktop = ScrollbarDefaults.compactStyle(thumbColor = Color.Blue)
        val expected = when (currentPlatform) {
            Platform.Android -> android
            Platform.Ios -> ios
            Platform.Desktop -> desktop
        }
        val picked = ScrollbarDefaults.platformStyle(android = android, ios = ios, desktop = desktop)
        assertTrue(picked === expected)
    }

    @Test
    fun platformStyleKeepsTheBuiltInLookForPlatformsLeftOut() {
        // Only one platform customized: everywhere else gets the platform's own default look.
        val picked = ScrollbarDefaults.platformStyle(
            desktop = ScrollbarDefaults.compactStyle(thumbColor = Color.Blue),
        )
        if (currentPlatform != Platform.Desktop) {
            assertEquals(ScrollbarDefaults.style().thumbAppearance, picked.thumbAppearance)
            assertEquals(ScrollbarDefaults.style().thumbColor, picked.thumbColor)
        }
    }

    @Test
    fun defaultVisibilityFollowsThePlatform() {
        assertEquals(platformDefaultVisibility, ScrollbarDefaults.style().visibility)
        assertEquals(platformDefaultVisibility, ScrollbarDefaults.compactStyle().visibility)
        assertEquals(platformDefaultVisibility, DefaultScrollbarStyle.visibility)
        if (currentPlatform != Platform.Desktop) {
            assertEquals(ScrollbarVisibility.AutoHide, platformDefaultVisibility, "touch platforms hide when idle")
        }
    }

    @Test
    fun thumbSizingFollowsThePlatform() {
        val sizing = ScrollbarDefaults.style().thumbSizing
        when (currentPlatform) {
            Platform.Desktop -> assertTrue(sizing is ThumbSizing.Proportional && sizing.smoothed && sizing.minLength == 24.dp)
            Platform.Android, Platform.Ios -> assertEquals(ThumbSizing.Automatic, sizing)
        }
        assertTrue(ScrollbarDefaults.compactStyle().thumbSizing is ThumbSizing.Proportional)
    }

    @Test
    fun compactStyleIsTheDesktopLookEverywhere() {
        val compact = ScrollbarDefaults.compactStyle()
        assertEquals(ThumbAppearance.Bar, compact.thumbAppearance)
        assertEquals(16.dp, compact.touchTargetThickness)
    }
}
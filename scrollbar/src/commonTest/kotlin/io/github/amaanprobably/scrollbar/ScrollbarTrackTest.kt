package io.github.amaanprobably.scrollbar

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame

/**
 * A 1000 px track at density 1 with 10 px padding at each end and a 40 px thumb: the thumb
 * travels from 10 to 990, so at [visualProgress] 0.5 it spans 480..520, centered on 500.
 */
private fun geometryAt(visualProgress: Float) = ThumbGeometry().apply {
    set(
        density = Density(1f),
        containerLength = 1000f,
        startPadding = 10f,
        endPadding = 10f,
        sizing = ThumbSizing.Fixed(40.dp),
        fraction = 0f,
        visualProgress = visualProgress,
        lengthOverride = -1f,
    )
}

class TrackSpanTest {
    @Test
    fun fullCoversTheThumbsWholeTravel() {
        val geometry = geometryAt(0.5f)
        for (mirrored in listOf(false, true)) {
            assertEquals(10f, trackDrawStart(TrackExtent.Full, geometry, mirrored))
            assertEquals(990f, trackDrawEnd(TrackExtent.Full, geometry, mirrored))
        }
    }

    @Test
    fun upToThumbRunsFromTheTopToTheThumbsMiddle() {
        val geometry = geometryAt(0.5f)
        assertEquals(10f, trackDrawStart(TrackExtent.UpToThumb, geometry, isMirrored = false))
        assertEquals(500f, trackDrawEnd(TrackExtent.UpToThumb, geometry, isMirrored = false))
    }

    @Test
    fun upToThumbOnAMirroredListRunsFromTheBottom() {
        // reverseLayout: the list starts at the bottom, so that's where the track grows from.
        val geometry = geometryAt(0.5f)
        assertEquals(500f, trackDrawStart(TrackExtent.UpToThumb, geometry, isMirrored = true))
        assertEquals(990f, trackDrawEnd(TrackExtent.UpToThumb, geometry, isMirrored = true))
    }

    @Test
    fun upToThumbAtTheStartIsOnlyAStubUnderTheThumb() {
        val geometry = geometryAt(0f) // thumb spans 10..50
        assertEquals(10f, trackDrawStart(TrackExtent.UpToThumb, geometry, isMirrored = false))
        assertEquals(30f, trackDrawEnd(TrackExtent.UpToThumb, geometry, isMirrored = false))
    }
}

class TrackCrossStartTest {
    @Test
    fun centeredOnTheThumb() {
        assertEquals(98f, trackCrossStart(thickness = 4f, thumbCrossCenter = 100f, crossLength = 120f))
    }

    @Test
    fun aWideTrackIsMovedInsideTheList() {
        // Centered it would span 80..140, past the 120 px edge, so it hugs the edge instead.
        assertEquals(60f, trackCrossStart(thickness = 60f, thumbCrossCenter = 110f, crossLength = 120f))
        assertEquals(0f, trackCrossStart(thickness = 60f, thumbCrossCenter = 10f, crossLength = 120f))
    }

    @Test
    fun aTrackWiderThanTheListStartsAtZero() {
        assertEquals(0f, trackCrossStart(thickness = 200f, thumbCrossCenter = 100f, crossLength = 120f))
    }
}

class RepeatTileLengthTest {
    @Test
    fun anExplicitLengthWins() {
        assertEquals(30f, repeatTileLength(tileLength = 30f, intrinsicMain = 20f, intrinsicCross = 10f, thickness = 8f))
    }

    @Test
    fun keepsThePaintersProportions() {
        // Twice as long as wide, drawn 8 px wide: 16 px per copy.
        assertEquals(16f, repeatTileLength(tileLength = Float.NaN, intrinsicMain = 20f, intrinsicCross = 10f, thickness = 8f))
    }

    @Test
    fun unknownOrInfiniteSizesGiveASquare() {
        assertEquals(8f, repeatTileLength(Float.NaN, Float.NaN, Float.NaN, thickness = 8f))
        assertEquals(8f, repeatTileLength(Float.NaN, Float.POSITIVE_INFINITY, 10f, thickness = 8f))
    }

    @Test
    fun neverShorterThanTheMinimum() {
        assertEquals(MIN_TRACK_TILE_LENGTH, repeatTileLength(0.1f, Float.NaN, Float.NaN, thickness = 8f))
        assertEquals(MIN_TRACK_TILE_LENGTH, repeatTileLength(Float.NaN, 1f, 1000f, thickness = 8f))
    }
}

class TrackStyleTest {
    @Test
    fun theTrackIsInvisibleByDefault() {
        assertSame(TrackAppearance.None, ScrollbarDefaults.style().trackAppearance)
        assertSame(TrackAppearance.None, ScrollbarDefaults.compactStyle().trackAppearance)
    }

    @Test
    fun bothStyleFunctionsPassTheTrackThrough() {
        val line = TrackAppearance.Line(extent = TrackExtent.UpToThumb)
        assertSame(line, ScrollbarDefaults.style(trackAppearance = line).trackAppearance)
        assertSame(line, ScrollbarDefaults.compactStyle(trackAppearance = line).trackAppearance)
        assertIs<TrackAppearance.Line>(ScrollbarDefaults.style(trackAppearance = line).trackAppearance)
    }

    @Test
    fun invalidSizesAreRejected() {
        assertFailsWith<IllegalArgumentException> { TrackAppearance.Line(thickness = 0.dp) }
        assertFailsWith<IllegalArgumentException> {
            TrackAppearance.Image(ColorPainter(Color.Red), thickness = 0.dp)
        }
        assertFailsWith<IllegalArgumentException> {
            TrackAppearance.Image(ColorPainter(Color.Red), thickness = 8.dp, tileLength = (-1).dp)
        }
    }
}
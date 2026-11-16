package io.github.amaanprobably.scrollbar

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

private const val NO_OVERRIDE = -1f
private val density = Density(1f) // 1 dp == 1 px keeps the numbers readable

private fun listMetrics(
    itemCount: Int,
    itemHeight: Float,
    viewportLength: Float,
    itemsPerLine: Int = 1,
): ScrollMetrics = ScrollMetrics().apply {
    this.itemCount = itemCount
    this.itemsPerLine = itemsPerLine
    lineCount = (itemCount + itemsPerLine - 1) / itemsPerLine
    this.viewportLength = viewportLength
    averageLineLength = itemHeight
    contentLength = lineCount * itemHeight
}

class VisualProgressTest {
    @Test
    fun unmirroredKeepsProgress() = assertEquals(0.3f, visualProgress(0.3f, isMirrored = false))

    @Test
    fun mirroredFlipsProgress() = assertEquals(0.7f, visualProgress(0.3f, isMirrored = true), 1e-6f)

    @Test
    fun mirroringTwiceIsIdentity() {
        val once = visualProgress(0.42f, isMirrored = true)
        assertEquals(0.42f, visualProgress(once, isMirrored = true), 1e-6f)
    }
}

class LogicalScrollOffsetTest {
    @Test
    fun listOffsetIsWholeItemsPlusTheVisiblePart() {
        assertEquals(2_000f * 50f + 20f, logicalScrollOffset(2_000, 20, itemsPerLine = 1, averageLineLength = 50f))
    }

    @Test
    fun gridOffsetCountsLinesNotItems() {
        // Item 30 in a 3-column grid is on line 10.
        assertEquals(10 * 80f, logicalScrollOffset(30, 0, itemsPerLine = 3, averageLineLength = 80f))
    }
}

class ScrollMetricsTest {
    @Test
    fun thumbFractionIsViewportOverContent() {
        val metrics = listMetrics(itemCount = 100, itemHeight = 100f, viewportLength = 1_000f)
        assertEquals(0.1f, metrics.thumbFraction, 1e-6f)
    }

    @Test
    fun thumbFractionIsOneWhenContentFits() {
        val metrics = listMetrics(itemCount = 3, itemHeight = 100f, viewportLength = 1_000f)
        assertEquals(1f, metrics.thumbFraction)
    }

    @Test
    fun maxScrollNeverDropsBelowOne() {
        val metrics = listMetrics(itemCount = 3, itemHeight = 100f, viewportLength = 1_000f)
        assertEquals(1f, metrics.maxScroll)
    }
}

class ScrollTargetKeyTest {
    private val list = listMetrics(itemCount = 100, itemHeight = 1_000f, viewportLength = 1_000f)

    @Test
    fun startMapsToFirstItem() {
        val key = scrollTargetKey(list, 0f)
        assertEquals(0, keyIndex(key))
        assertEquals(0, keyOffset(key))
    }

    @Test
    fun endMapsToLastItem() {
        val key = scrollTargetKey(list, 1f)
        assertEquals(99, keyIndex(key))
        assertEquals(0, keyOffset(key))
    }

    @Test
    fun middleMapsToItemAndOffset() {
        // maxScroll = 99_000, half of it is 49_500 => item 49, 500 px into it.
        val key = scrollTargetKey(list, 0.5f)
        assertEquals(49, keyIndex(key))
        assertEquals(500, keyOffset(key))
    }

    @Test
    fun gridMapsToFirstItemOfTheLine() {
        // 10 items, 3 per line => 4 lines of 100 px. maxScroll = 300.
        val grid = listMetrics(itemCount = 10, itemHeight = 100f, viewportLength = 100f, itemsPerLine = 3)
        val key = scrollTargetKey(grid, 0.5f) // 150 px => line 1 (items 3..5), 50 px in
        assertEquals(3, keyIndex(key))
        assertEquals(50, keyOffset(key))
        assertEquals(9, keyIndex(scrollTargetKey(grid, 1f)))
    }

    @Test
    fun singleItemListNeverThrows() {
        val single = listMetrics(itemCount = 1, itemHeight = 100f, viewportLength = 50f)
        for (progress in listOf(0f, 0.5f, 1f)) {
            assertEquals(0, keyIndex(scrollTargetKey(single, progress)))
        }
    }

    @Test
    fun everyProgressStaysInsideTheList() {
        val grid = listMetrics(itemCount = 10, itemHeight = 100f, viewportLength = 100f, itemsPerLine = 3)
        var step = 0
        while (step <= 1_000) {
            val key = scrollTargetKey(grid, step / 1_000f)
            assertTrue(keyIndex(key) in 0 until 10, "index out of range at step $step")
            assertTrue(keyOffset(key) >= 0, "negative offset at step $step")
            step++
        }
    }

    @Test
    fun indexNeverGoesBackwardsAsProgressGrows() {
        var previous = -1L
        var step = 0
        while (step <= 1_000) {
            val key = scrollTargetKey(list, step / 1_000f)
            val order = keyIndex(key).toLong() * 1_000_000 + keyOffset(key)
            assertTrue(order >= previous, "went backwards at step $step")
            previous = order
            step++
        }
    }
}

class ThumbGeometryTest {
    private fun geometry(
        container: Float,
        startPadding: Float = 0f,
        endPadding: Float = 0f,
        sizing: ThumbSizing = ThumbSizing.Fixed(48.dp),
        fraction: Float = 0.1f,
        progress: Float = 0f,
        lengthOverride: Float = NO_OVERRIDE,
    ) = ThumbGeometry().apply {
        set(density, container, startPadding, endPadding, sizing, fraction, progress, lengthOverride)
    }

    @Test
    fun fixedThumbPositionFollowsProgress() {
        val geometry = geometry(container = 1_000f, progress = 0.5f)
        assertEquals(48f, geometry.length)
        assertEquals(952f, geometry.trackLength)
        assertEquals(476f, geometry.start)
    }

    @Test
    fun thumbReachesBothEndsExactly() {
        assertEquals(0f, geometry(container = 1_000f, progress = 0f).start)
        val atEnd = geometry(container = 1_000f, progress = 1f)
        assertEquals(1_000f, atEnd.start + atEnd.length)
    }

    @Test
    fun paddingShrinksTheTrack() {
        val atEnd = geometry(container = 1_000f, startPadding = 100f, endPadding = 50f, progress = 1f)
        assertEquals(100f, geometry(container = 1_000f, startPadding = 100f, endPadding = 50f).start)
        assertEquals(950f, atEnd.start + atEnd.length)
    }

    @Test
    fun thumbLongerThanContainerIsClamped() {
        val geometry = geometry(container = 30f, progress = 1f)
        assertEquals(30f, geometry.length)
        assertEquals(0f, geometry.trackLength)
        assertEquals(0f, geometry.start)
    }

    @Test
    fun paddingBiggerThanContainerDoesNotCrash() {
        val geometry = geometry(container = 100f, startPadding = 60f, endPadding = 60f, progress = 0.5f)
        assertEquals(0f, geometry.length)
        assertEquals(0f, geometry.trackLength)
    }

    @Test
    fun zeroSizedContainerDoesNotCrash() {
        assertEquals(0f, geometry(container = 0f).length)
    }

    @Test
    fun lengthOverrideWinsAndIsClamped() {
        assertEquals(20f, geometry(container = 1_000f, lengthOverride = 20f).length)
        assertEquals(30f, geometry(container = 30f, lengthOverride = 200f).length)
    }

    @Test
    fun proportionalThumbFollowsFractionWithinLimits() {
        val sizing = ThumbSizing.Proportional(minLength = 48.dp, maxLengthFraction = 0.9f)
        assertEquals(48f, geometry(1_000f, sizing = sizing, fraction = 0.001f).length)
        assertEquals(500f, geometry(1_000f, sizing = sizing, fraction = 0.5f).length)
        assertEquals(900f, geometry(1_000f, sizing = sizing, fraction = 1f).length)
    }

    @Test
    fun proportionalMinLengthLargerThanTrackIsClamped() {
        val sizing = ThumbSizing.Proportional(minLength = 48.dp)
        assertEquals(40f, geometry(container = 40f, sizing = sizing, fraction = 0.01f).length)
    }

    @Test
    fun resolveThumbLengthNeverExceedsUsableLength() {
        val sizings = listOf(ThumbSizing.Fixed(500.dp), ThumbSizing.Proportional(minLength = 500.dp))
        for (sizing in sizings) {
            assertTrue(density.resolveThumbLength(sizing, 100f, 0.5f) <= 100f)
        }
    }

    @Test
    fun invalidSizingIsRejected() {
        assertFailsWith<IllegalArgumentException> { ThumbSizing.Fixed(0.dp) }
        assertFailsWith<IllegalArgumentException> { ThumbSizing.Proportional(maxLengthFraction = 1.5f) }
    }
}
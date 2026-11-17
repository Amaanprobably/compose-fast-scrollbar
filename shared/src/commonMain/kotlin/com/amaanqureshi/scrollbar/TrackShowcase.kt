package com.amaanqureshi.scrollbar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import io.github.amaanprobably.scrollbar.ScrollbarDefaults
import io.github.amaanprobably.scrollbar.ScrollbarVisibility
import io.github.amaanprobably.scrollbar.ThumbAppearance
import io.github.amaanprobably.scrollbar.TrackAppearance
import io.github.amaanprobably.scrollbar.TrackExtent
import io.github.amaanprobably.scrollbar.TrackFit
import io.github.amaanprobably.scrollbar.scrollbar

// 07 Hang on: a visible track. Placeholder drawings: swap in your own character, rope and pole.

private enum class TrackLook(val title: String) {
    Rope("Rope · up to thumb"),
    Ladder("Ladder · full")
}

/** One twist of rope, repeated along the track. 10 x 14, upright for a vertical list. */
private val RopeTile: ImageVector = ImageVector.Builder(
    name = "RopeTile", defaultWidth = 10.dp, defaultHeight = 14.dp, viewportWidth = 10f, viewportHeight = 14f,
).apply {
    path(fill = SolidColor(Color(0xFFD9A86C))) {
        moveTo(2f, 0f); lineTo(8f, 0f); lineTo(8f, 14f); lineTo(2f, 14f); close()
    }
    path(stroke = SolidColor(Color(0xFF8A5A2B)), strokeLineWidth = 1.6f, strokeLineCap = StrokeCap.Round) {
        moveTo(2.5f, 2f); lineTo(7.5f, 8f)
    }
}.build()

/** One rung of a ladder, repeated along the track. 28 x 20. */
private val LadderTile: ImageVector = ImageVector.Builder(
    name = "LadderTile", defaultWidth = 28.dp, defaultHeight = 20.dp, viewportWidth = 28f, viewportHeight = 20f,
).apply {
    path(stroke = SolidColor(Color(0xFF9AA3B5)), strokeLineWidth = 4f) {
        moveTo(3f, 0f); lineTo(3f, 20f)
        moveTo(25f, 0f); lineTo(25f, 20f)
    }
    path(stroke = SolidColor(Color(0xFF9AA3B5)), strokeLineWidth = 3f, strokeLineCap = StrokeCap.Round) {
        moveTo(3f, 10f); lineTo(25f, 10f)
    }
}.build()

/** Placeholder character: a coral ball with a white ring. Replace with your own drawing. */
private val Climber: ImageVector = ImageVector.Builder(
    name = "Climber", defaultWidth = 40.dp, defaultHeight = 40.dp, viewportWidth = 40f, viewportHeight = 40f,
).apply {
    path(fill = SolidColor(Brand.Coral)) {
        moveTo(20f, 2f)
        arcTo(18f, 18f, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = 20f, y1 = 38f)
        arcTo(18f, 18f, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = 20f, y1 = 2f)
        close()
    }
    path(stroke = SolidColor(Color.White), strokeLineWidth = 3f) {
        moveTo(20f, 9f)
        arcTo(11f, 11f, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = 20f, y1 = 31f)
        arcTo(11f, 11f, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = 20f, y1 = 9f)
    }
}.build()

@Composable
internal fun TrackScreen(onBack: () -> Unit) {
    val notes = remember { buildNotes() }
    val listState = rememberLazyListState()
    var look by rememberSaveable { mutableStateOf(TrackLook.Rope) }
    var reverse by rememberSaveable { mutableStateOf(false) }

    val climber = rememberVectorPainter(Climber)
    val rope = rememberVectorPainter(RopeTile)
    val ladder = rememberVectorPainter(LadderTile)
    val style = remember(look, climber, rope, ladder) {
        ScrollbarDefaults.style(
            visibility = ScrollbarVisibility.Always,
            thumbAppearance = ThumbAppearance.Icon(idle = climber, size = DpSize(40.dp, 40.dp)),
            trackAppearance = when (look) {
                TrackLook.Rope -> TrackAppearance.Image(
                    painter = rope, thickness = 10.dp, extent = TrackExtent.UpToThumb, fit = TrackFit.Repeat,
                )
                TrackLook.Ladder -> TrackAppearance.Image(
                    painter = ladder, thickness = 28.dp, extent = TrackExtent.Full, fit = TrackFit.Repeat,
                )
            },
        )
    }
    val padding = PaddingValues(start = 16.dp, end = 64.dp, top = 4.dp, bottom = navigationBarBottom() + 16.dp)

    DemoScreen(Showcase.Track, onBack) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TrackLook.entries.forEach { option -> TrackChip(look == option, option.title) { look = option } }
            TrackChip(reverse, "Reverse") { reverse = !reverse }
        }
        LazyColumn(
            state = listState,
            reverseLayout = reverse,
            contentPadding = padding,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxSize()
                .scrollbar(listState, contentPadding = padding, style = style),
        ) {
            items(notes.size, key = { it }) { index -> NoteCard(notes[index]) }
        }
    }
}

@Composable
private fun TrackChip(selected: Boolean, text: String, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(text) },
        shape = RoundedCornerShape(50),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = Showcase.Track.accent,
            selectedLabelColor = Showcase.Track.onAccent,
        ),
    )
}
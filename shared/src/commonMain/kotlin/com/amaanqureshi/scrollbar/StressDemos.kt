package com.amaanqureshi.scrollbar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.amaanprobably.scrollbar.ScrollbarEdge
import io.github.amaanprobably.scrollbar.ScrollbarLabel
import io.github.amaanprobably.scrollbar.ScrollbarLabelStyle
import io.github.amaanprobably.scrollbar.ScrollbarStyle
import io.github.amaanprobably.scrollbar.scrollbar
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

// Playground stress tabs: the scenarios real apps hit that the other demos don't.

/**
 * Items inserted at the top of a normal list, like new messages in an inbox. With keys, the list
 * stays on the item you were reading; the thumb should nudge down smoothly, not jump.
 * New items are tinted, so you can see them arrive.
 */
@Composable
internal fun InsertAtTopDemo(
    style: ScrollbarStyle,
    labelStyle: ScrollbarLabelStyle,
    edge: ScrollbarEdge,
    onEdgeChange: (ScrollbarEdge) -> Unit,
    modifier: Modifier,
) {
    val listState = rememberLazyListState()
    val ids = remember { mutableStateListOf<Int>().apply { addAll(0 until 300) } }
    var nextNewId by remember { mutableIntStateOf(1) }
    var autoInsert by remember { mutableStateOf(false) }
    val label = remember(labelStyle) {
        ScrollbarLabel(sectionHaptics = false, style = labelStyle) { index -> ids.getOrNull(index)?.let(::insertLabel) }
    }

    fun insertAtTop(count: Int) {
        // Negative ids for new items, so keys never collide with the original ones.
        ids.addAll(0, List(count) { -(nextNewId + it) })
        nextNewId += count
    }

    LaunchedEffect(autoInsert) {
        while (autoInsert) {
            delay(1_000.milliseconds)
            insertAtTop(5)
        }
    }

    Column(modifier) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledTonalButton(onClick = { insertAtTop(20) }) { Text("Add 20 at top") }
            FilledTonalButton(onClick = { autoInsert = !autoInsert }) {
                Text(if (autoInsert) "Stop auto" else "Auto: 5 / sec")
            }
            Text("${ids.size} items", style = MaterialTheme.typography.labelSmall)
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .scrollbar(listState, edge = edge, style = style, label = label, onEdgeChange = onEdgeChange),
        ) {
            items(ids.size, key = { ids[it] }) { index ->
                val id = ids[index]
                StressRow(
                    text = insertLabel(id),
                    tint = if (id < 0) Brand.Lime.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surfaceVariant,
                )
            }
        }
    }
}

private fun insertLabel(id: Int): String = if (id < 0) "New ${-id}" else "Item $id"

/**
 * Two lists on one screen, both with the bar on the same edge. On Android each one protects its own
 * thumb from the back gesture, and both share one View: check that back is blocked on both thumbs,
 * works elsewhere on the edge, and comes back on each one as it fades out.
 */
@Composable
internal fun TwoListsDemo(
    style: ScrollbarStyle,
    edge: ScrollbarEdge,
    onEdgeChange: (ScrollbarEdge) -> Unit,
    modifier: Modifier,
) {
    Column(modifier) {
        listOf("Top", "Bottom").forEachIndexed { half, name ->
            val listState = rememberLazyListState()
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .scrollbar(listState, edge = edge, style = style, onEdgeChange = onEdgeChange),
            ) {
                items(1_000, key = { it }) { index ->
                    StressRow(
                        text = "$name list · $index",
                        tint = if (half == 0) Brand.Violet.copy(alpha = 0.25f) else Brand.Mint.copy(alpha = 0.25f),
                    )
                }
            }
            if (half == 0) Spacer(Modifier.height(12.dp))
        }
    }
}

/**
 * A grid whose tiles do deliberately slow work the first time they compose, like apps that decode
 * or rasterize images on the main thread. Fast-scroll it: the thumb should still track your finger,
 * and the list should catch up without landing in the wrong place.
 */
@Composable
internal fun HeavyGridDemo(
    style: ScrollbarStyle,
    edge: ScrollbarEdge,
    onEdgeChange: (ScrollbarEdge) -> Unit,
    modifier: Modifier,
) {
    val gridState = rememberLazyGridState()
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        state = gridState,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = modifier.scrollbar(gridState, edge = edge, style = style, onEdgeChange = onEdgeChange),
    ) {
        items(3_000, key = { it }) { index ->
            // Runs again whenever the tile comes back on screen, like a real decode without a cache.
            val checksum = remember(index) { slowWork(index) }
            Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
                PhotoArt(index, Modifier.fillMaxWidth().aspectRatio(1f))
                Text(
                    text = "$index · $checksum",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    modifier = Modifier.align(Alignment.BottomStart).padding(6.dp),
                )
            }
        }
    }
}

/** A few milliseconds of busy work on a phone. The result is shown, so it can't be optimized away. */
private fun slowWork(seed: Int): Int {
    var value = seed
    repeat(400_000) { value = value * 1_103_515_245 + 12_345 }
    return (value ushr 16) % 1_000
}

@Composable
private fun StressRow(text: String, tint: Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(tint)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    )
}
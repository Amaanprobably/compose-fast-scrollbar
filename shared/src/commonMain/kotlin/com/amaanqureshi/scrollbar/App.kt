package com.amaanqureshi.scrollbar

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.amaanprobably.scrollbar.ScrollbarDefaults
import io.github.amaanprobably.scrollbar.ScrollbarLabel
import io.github.amaanprobably.scrollbar.ScrollbarVisibility
import io.github.amaanprobably.scrollbar.scrollbar
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

private enum class Demo(val title: String) { Column("Column"), Grid("Grid"), Row("Row"), Feed("Feed") }

private val itemCountOptions = listOf(5, 50, 10_000)

/** Deterministic pseudo-random size, so the demo looks the same on every run. */
private fun variableSize(index: Int, base: Int, spread: Int): Int = base + (index * 37 % spread)

/** Spreads the items over the letters A-Z, like a contact list sorted by name. */
private fun sectionLetter(itemIndex: Int, itemCount: Int): String {
    val letterIndex = (itemIndex.toLong() * 26 / itemCount.coerceAtLeast(1)).toInt().coerceIn(0, 25)
    return ('A' + letterIndex).toString()
}

@Composable
@Preview
fun App() {
    MaterialTheme {
        var demo by remember { mutableStateOf(Demo.Column) }
        var itemCount by remember { mutableStateOf(10_000) }
        var rightToLeft by remember { mutableStateOf(false) }
        var reverseLayout by remember { mutableStateOf(false) }
        var withPadding by remember { mutableStateOf(false) }
        var alwaysVisible by remember { mutableStateOf(false) }
        var showLabel by remember { mutableStateOf(true) }
        var shortContainer by remember { mutableStateOf(false) }

        val layoutDirection = if (rightToLeft) LayoutDirection.Rtl else LayoutDirection.Ltr

        CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
            Column(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surface)
                    .safeContentPadding()
                    .fillMaxSize(),
            ) {
                Controls(
                    demo = demo, onDemo = { demo = it },
                    itemCount = itemCount, onItemCount = { itemCount = it },
                    rightToLeft = rightToLeft, onRightToLeft = { rightToLeft = it },
                    reverseLayout = reverseLayout, onReverseLayout = { reverseLayout = it },
                    withPadding = withPadding, onWithPadding = { withPadding = it },
                    alwaysVisible = alwaysVisible, onAlwaysVisible = { alwaysVisible = it },
                    showLabel = showLabel, onShowLabel = { showLabel = it },
                    shortContainer = shortContainer, onShortContainer = { shortContainer = it },
                )

                val style = ScrollbarDefaults.style(
                    visibility = if (alwaysVisible) ScrollbarVisibility.Always else ScrollbarVisibility.AutoHide,
                )
                val label = if (showLabel) {
                    ScrollbarLabel { itemIndex -> sectionLetter(itemIndex, itemCount) }
                } else {
                    null
                }
                val demoModifier = Modifier
                    .fillMaxWidth()
                    .then(if (shortContainer) Modifier.height(160.dp) else Modifier.weight(1f))

                when (demo) {
                    Demo.Column -> ColumnDemo(itemCount, reverseLayout, withPadding, style, label, demoModifier)
                    Demo.Grid -> GridDemo(itemCount, reverseLayout, withPadding, style, label, demoModifier)
                    Demo.Row -> RowDemo(itemCount, reverseLayout, withPadding, style, label, demoModifier)
                    Demo.Feed -> FeedDemo(style, demoModifier)
                }
            }
        }
    }
}

@Composable
private fun Controls(
    demo: Demo, onDemo: (Demo) -> Unit,
    itemCount: Int, onItemCount: (Int) -> Unit,
    rightToLeft: Boolean, onRightToLeft: (Boolean) -> Unit,
    reverseLayout: Boolean, onReverseLayout: (Boolean) -> Unit,
    withPadding: Boolean, onWithPadding: (Boolean) -> Unit,
    alwaysVisible: Boolean, onAlwaysVisible: (Boolean) -> Unit,
    showLabel: Boolean, onShowLabel: (Boolean) -> Unit,
    shortContainer: Boolean, onShortContainer: (Boolean) -> Unit,
) {
    Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp)) {
        ChipRow {
            Demo.entries.forEach { option ->
                FilterChip(selected = demo == option, onClick = { onDemo(option) }, label = { Text(option.title) })
            }
        }
        ChipRow {
            itemCountOptions.forEach { option ->
                FilterChip(selected = itemCount == option, onClick = { onItemCount(option) }, label = { Text("$option items") })
            }
        }
        ChipRow {
            FilterChip(selected = rightToLeft, onClick = { onRightToLeft(!rightToLeft) }, label = { Text("RTL") })
            FilterChip(selected = reverseLayout, onClick = { onReverseLayout(!reverseLayout) }, label = { Text("Reverse") })
            FilterChip(selected = withPadding, onClick = { onWithPadding(!withPadding) }, label = { Text("Padding") })
            FilterChip(selected = alwaysVisible, onClick = { onAlwaysVisible(!alwaysVisible) }, label = { Text("Always visible") })
            FilterChip(selected = showLabel, onClick = { onShowLabel(!showLabel) }, label = { Text("Label") })
            FilterChip(selected = shortContainer, onClick = { onShortContainer(!shortContainer) }, label = { Text("Short") })
        }
    }
}

@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

@Composable
private fun Cell(text: String, modifier: Modifier) {
    Box(
        modifier = modifier
            .padding(4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

@Composable
private fun ColumnDemo(
    itemCount: Int,
    reverseLayout: Boolean,
    withPadding: Boolean,
    style: io.github.amaanprobably.scrollbar.ScrollbarStyle,
    label: ScrollbarLabel?,
    modifier: Modifier,
) {
    val listState = rememberLazyListState()
    val padding = if (withPadding) PaddingValues(vertical = 48.dp) else PaddingValues(0.dp)
    LazyColumn(
        state = listState,
        reverseLayout = reverseLayout,
        contentPadding = padding,
        modifier = modifier.scrollbar(listState, contentPadding = padding, style = style, label = label),
    ) {
        items(itemCount, key = { it }) { index ->
            Cell(
                text = "${sectionLetter(index, itemCount)} · Item $index",
                modifier = Modifier.fillMaxWidth().height(variableSize(index, base = 40, spread = 90).dp),
            )
        }
    }
}

@Composable
private fun GridDemo(
    itemCount: Int,
    reverseLayout: Boolean,
    withPadding: Boolean,
    style: io.github.amaanprobably.scrollbar.ScrollbarStyle,
    label: ScrollbarLabel?,
    modifier: Modifier,
) {
    val gridState = rememberLazyGridState()
    val padding = if (withPadding) PaddingValues(vertical = 48.dp) else PaddingValues(0.dp)
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        state = gridState,
        reverseLayout = reverseLayout,
        contentPadding = padding,
        modifier = modifier.scrollbar(gridState, contentPadding = padding, style = style, label = label),
    ) {
        // Full-span header: the awkward case for the items-per-line estimate.
        item(key = "header", span = { GridItemSpan(maxLineSpan) }) {
            Cell(
                text = "Header (full span)",
                modifier = Modifier.fillMaxWidth().height(64.dp),
            )
        }
        items(itemCount, key = { it }) { index ->
            Cell(
                text = "${sectionLetter(index, itemCount)}\n$index",
                modifier = Modifier.fillMaxWidth().height(variableSize(index / 3, base = 64, spread = 60).dp),
            )
        }
    }
}

@Composable
private fun RowDemo(
    itemCount: Int,
    reverseLayout: Boolean,
    withPadding: Boolean,
    style: io.github.amaanprobably.scrollbar.ScrollbarStyle,
    label: ScrollbarLabel?,
    modifier: Modifier,
) {
    val listState = rememberLazyListState()
    val padding = if (withPadding) PaddingValues(horizontal = 48.dp) else PaddingValues(0.dp)
    LazyRow(
        state = listState,
        reverseLayout = reverseLayout,
        contentPadding = padding,
        modifier = modifier.scrollbar(listState, contentPadding = padding, style = style, label = label),
    ) {
        items(itemCount, key = { it }) { index ->
            Cell(
                text = "${sectionLetter(index, itemCount)}\n$index",
                modifier = Modifier.width(variableSize(index, base = 70, spread = 80).dp).fillMaxSize(),
            )
        }
    }
}

private const val FEED_PAGE_SIZE = 30
private const val FEED_TOTAL = 300

/** Simulates an infinite feed: 30 more items arrive 600 ms after you get near the end. */
@Composable
private fun FeedDemo(
    style: io.github.amaanprobably.scrollbar.ScrollbarStyle,
    modifier: Modifier,
) {
    val listState = rememberLazyListState()
    var loadedCount by remember { mutableStateOf(FEED_PAGE_SIZE) }
    val hasMore = loadedCount < FEED_TOTAL
    val label = remember { ScrollbarLabel(sectionHaptics = false) { index -> "Page ${index / FEED_PAGE_SIZE + 1}" } }

    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collect { lastVisible ->
                if (lastVisible != null && lastVisible >= loadedCount - 5 && loadedCount < FEED_TOTAL) {
                    delay(600.milliseconds)
                    loadedCount += FEED_PAGE_SIZE
                }
            }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.scrollbar(listState, hasMore = hasMore, style = style, label = label),
    ) {
        items(loadedCount, key = { it }) { index ->
            Cell(
                text = "Post $index",
                modifier = Modifier.fillMaxWidth().height(variableSize(index, base = 60, spread = 80).dp),
            )
        }
    }
}
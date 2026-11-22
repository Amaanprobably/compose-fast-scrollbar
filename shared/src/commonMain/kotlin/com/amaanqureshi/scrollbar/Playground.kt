package com.amaanqureshi.scrollbar

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.amaanprobably.scrollbar.ScrollbarDefaults
import io.github.amaanprobably.scrollbar.ScrollbarEdge
import io.github.amaanprobably.scrollbar.ScrollbarLabel
import io.github.amaanprobably.scrollbar.ScrollbarLabelStyle
import io.github.amaanprobably.scrollbar.ScrollbarStyle
import io.github.amaanprobably.scrollbar.ScrollbarVisibility
import io.github.amaanprobably.scrollbar.scrollbar
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

// 07 Playground: the original test bench, every option on one screen.

private enum class Demo(val title: String) {
    Column("Column"),
    Grid("Grid"),
    Row("Row"),
    Feed("Feed"),
    InsertAtTop("Insert at top"),
    TwoLists("Two lists"),
    Heavy("Heavy"),
}

private val itemCountOptions = listOf(5, 50, 10_000)

/** Deterministic pseudo-random size, so the demo looks the same on every run. */
private fun variableSize(index: Int, base: Int, spread: Int): Int = base + (index * 37 % spread)

/** Spreads the items over the letters A-Z, like a contact list sorted by name. */
private fun sectionLetter(itemIndex: Int, itemCount: Int): String {
    val letterIndex = (itemIndex.toLong() * 26 / itemCount.coerceAtLeast(1)).toInt().coerceIn(0, 25)
    return ('A' + letterIndex).toString()
}

@Composable
internal fun PlaygroundScreen(onBack: () -> Unit) {
    var demo by rememberSaveable { mutableStateOf(Demo.Column) }
    var itemCount by rememberSaveable { mutableIntStateOf(10_000) }
    var rightToLeft by rememberSaveable { mutableStateOf(false) }
    var reverseLayout by rememberSaveable { mutableStateOf(false) }
    var withPadding by rememberSaveable { mutableStateOf(false) }
    var showLabel by rememberSaveable { mutableStateOf(true) }
    var shortContainer by rememberSaveable { mutableStateOf(false) }
    var alwaysVisible by rememberSaveable {
        mutableStateOf(ScrollbarDefaults.style().visibility == ScrollbarVisibility.Always)
    }
    // On (the default), only the top is padded, like a typical app, so the list reaches the side
    // edges (the back gesture zones) and runs under the gesture bar. Off, safeContentPadding()
    // pads every side by the gesture zones too, which keeps the bar away from the back gesture.
    var edgeToEdge by rememberSaveable { mutableStateOf(true) }
    // Long-press the thumb and drag it across to move the bar. A real app would persist this.
    var edge by rememberSaveable { mutableStateOf(ScrollbarEdge.End) }
    val onEdgeChange: (ScrollbarEdge) -> Unit = { edge = it }

    val style = brandScrollbarStyle(
        visibility = if (alwaysVisible) ScrollbarVisibility.Always else ScrollbarVisibility.AutoHide,
    )
    val labelStyle = brandLabelStyle()
    val label = remember(showLabel, itemCount, labelStyle) {
        if (showLabel) ScrollbarLabel(style = labelStyle) { itemIndex -> sectionLetter(itemIndex, itemCount) } else null
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .then(if (edgeToEdge) Modifier else Modifier.safeContentPadding()),
    ) {
        // safeContentPadding() consumes the insets it pads, so DemoScreen's own top padding
        // doesn't add to it when edge-to-edge is off.
        DemoScreen(Showcase.Playground, onBack) {
            Controls(
                demo = demo, onDemo = { demo = it },
                itemCount = itemCount, onItemCount = { itemCount = it },
                rightToLeft = rightToLeft, onRightToLeft = { rightToLeft = it },
                reverseLayout = reverseLayout, onReverseLayout = { reverseLayout = it },
                withPadding = withPadding, onWithPadding = { withPadding = it },
                alwaysVisible = alwaysVisible, onAlwaysVisible = { alwaysVisible = it },
                showLabel = showLabel, onShowLabel = { showLabel = it },
                shortContainer = shortContainer, onShortContainer = { shortContainer = it },
                edgeToEdge = edgeToEdge, onEdgeToEdge = { edgeToEdge = it },
            )
            Spacer(Modifier.height(8.dp))

            val layoutDirection = if (rightToLeft) LayoutDirection.Rtl else LayoutDirection.Ltr
            CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
                val demoModifier = Modifier
                    .fillMaxWidth()
                    .then(if (shortContainer) Modifier.height(160.dp) else Modifier.weight(1f))

                when (demo) {
                    Demo.Column -> ColumnDemo(itemCount, reverseLayout, withPadding, style, label, edge, onEdgeChange, demoModifier)
                    Demo.Grid -> GridDemo(itemCount, reverseLayout, withPadding, style, label, edge, onEdgeChange, demoModifier)
                    Demo.Row -> RowDemo(itemCount, reverseLayout, withPadding, style, label, edge, onEdgeChange, demoModifier)
                    Demo.Feed -> FeedDemo(reverseLayout, style, labelStyle, edge, onEdgeChange, demoModifier)
                    Demo.InsertAtTop -> InsertAtTopDemo(style, labelStyle, edge, onEdgeChange, demoModifier)
                    Demo.TwoLists -> TwoListsDemo(style, edge, onEdgeChange, demoModifier)
                    Demo.Heavy -> HeavyGridDemo(style, edge, onEdgeChange, demoModifier)
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
    edgeToEdge: Boolean, onEdgeToEdge: (Boolean) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        ChipRow {
            Demo.entries.forEach { option ->
                Chip(selected = demo == option, text = option.title, onClick = { onDemo(option) })
            }
        }
        ChipRow {
            itemCountOptions.forEach { option ->
                Chip(selected = itemCount == option, text = "$option items", onClick = { onItemCount(option) })
            }
        }
        ChipRow {
            Chip(selected = rightToLeft, text = "RTL", onClick = { onRightToLeft(!rightToLeft) })
            Chip(selected = reverseLayout, text = "Reverse", onClick = { onReverseLayout(!reverseLayout) })
            Chip(selected = withPadding, text = "Padding", onClick = { onWithPadding(!withPadding) })
            Chip(selected = alwaysVisible, text = "Always visible", onClick = { onAlwaysVisible(!alwaysVisible) })
            Chip(selected = showLabel, text = "Label", onClick = { onShowLabel(!showLabel) })
            Chip(selected = shortContainer, text = "Short", onClick = { onShortContainer(!shortContainer) })
            Chip(selected = edgeToEdge, text = "Edge-to-edge", onClick = { onEdgeToEdge(!edgeToEdge) })
        }
    }
}

@Composable
private fun Chip(selected: Boolean, text: String, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(text) },
        shape = RoundedCornerShape(50),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = Showcase.Playground.accent,
            selectedLabelColor = Showcase.Playground.onAccent,
        ),
    )
}

@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

@Composable
private fun Cell(text: String, modifier: Modifier) {
    Box(
        modifier = modifier
            .padding(4.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun ColumnDemo(
    itemCount: Int,
    reverseLayout: Boolean,
    withPadding: Boolean,
    style: ScrollbarStyle,
    label: ScrollbarLabel?,
    edge: ScrollbarEdge,
    onEdgeChange: (ScrollbarEdge) -> Unit,
    modifier: Modifier,
) {
    val listState = rememberLazyListState()
    val padding = if (withPadding) PaddingValues(vertical = 48.dp) else PaddingValues(0.dp)
    LazyColumn(
        state = listState,
        reverseLayout = reverseLayout,
        contentPadding = padding,
        modifier = modifier.scrollbar(
            listState, edge = edge, contentPadding = padding, style = style, label = label,
            onEdgeChange = onEdgeChange,
        ),
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
    style: ScrollbarStyle,
    label: ScrollbarLabel?,
    edge: ScrollbarEdge,
    onEdgeChange: (ScrollbarEdge) -> Unit,
    modifier: Modifier,
) {
    val gridState = rememberLazyGridState()
    val padding = if (withPadding) PaddingValues(vertical = 48.dp) else PaddingValues(0.dp)
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        state = gridState,
        reverseLayout = reverseLayout,
        contentPadding = padding,
        modifier = modifier.scrollbar(
            gridState, edge = edge, contentPadding = padding, style = style, label = label,
            onEdgeChange = onEdgeChange,
        ),
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
    style: ScrollbarStyle,
    label: ScrollbarLabel?,
    edge: ScrollbarEdge,
    onEdgeChange: (ScrollbarEdge) -> Unit,
    modifier: Modifier,
) {
    val listState = rememberLazyListState()
    val padding = if (withPadding) PaddingValues(horizontal = 48.dp) else PaddingValues(0.dp)
    LazyRow(
        state = listState,
        reverseLayout = reverseLayout,
        contentPadding = padding,
        modifier = modifier.scrollbar(
            listState, edge = edge, contentPadding = padding, style = style, label = label,
            onEdgeChange = onEdgeChange,
        ),
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

/**
 * Simulates an infinite feed: 30 more items arrive 600 ms after you get near the end.
 * With Reverse on it behaves like a chat: newest at the bottom, older pages load at the top.
 */
@Composable
private fun FeedDemo(
    reverseLayout: Boolean,
    style: ScrollbarStyle,
    labelStyle: ScrollbarLabelStyle,
    edge: ScrollbarEdge,
    onEdgeChange: (ScrollbarEdge) -> Unit,
    modifier: Modifier,
) {
    val listState = rememberLazyListState()
    var loadedCount by remember { mutableStateOf(FEED_PAGE_SIZE) }
    val hasMore = loadedCount < FEED_TOTAL
    val label = remember(labelStyle) {
        ScrollbarLabel(sectionHaptics = false, style = labelStyle) { index -> "Page ${index / FEED_PAGE_SIZE + 1}" }
    }

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
        reverseLayout = reverseLayout,
        modifier = modifier.scrollbar(
            listState, edge = edge, hasMore = hasMore, style = style, label = label,
            onEdgeChange = onEdgeChange,
        ),
    ) {
        items(loadedCount, key = { it }) { index ->
            Cell(
                text = "Post $index",
                modifier = Modifier.fillMaxWidth().height(variableSize(index, base = 60, spread = 80).dp),
            )
        }
    }
}
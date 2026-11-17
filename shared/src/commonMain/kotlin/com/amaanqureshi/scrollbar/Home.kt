package com.amaanqureshi.scrollbar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

/** The gallery: a big header, then one bold card per showcase. */
@Composable
internal fun HomeScreen(listState: LazyListState, onOpen: (Showcase) -> Unit) {
    val padding = PaddingValues(start = 24.dp, end = 24.dp, top = 8.dp, bottom = navigationBarBottom() + 24.dp)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top)),
        contentAlignment = Alignment.TopCenter,
    ) {
        LazyColumn(
            state = listState,
            contentPadding = padding,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                // Wide desktop windows: keep the cards a readable width.
                .widthIn(max = 640.dp)
                .fillMaxSize()
        ) {
            item(key = "hero") { Hero() }
            items(Showcase.entries, key = { it.name }) { showcase ->
                ShowcaseCard(showcase, onClick = { onOpen(showcase) })
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Hero() {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.padding(top = 24.dp, bottom = 12.dp)) {
        Text(
            text = buildAnnotatedString {
                append("Fast\nscroller")
                withStyle(SpanStyle(color = colors.primary)) { append(".") }
            },
            style = MaterialTheme.typography.displayLarge,
            color = colors.onBackground,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "One Modifier.scrollbar for LazyColumn, LazyRow and LazyVerticalGrid. " +
                    "Grab it, drag it, land exactly where you meant to.",
            style = MaterialTheme.typography.bodyLarge,
            color = colors.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Android", "iOS", "Desktop").forEach { Tag(it, colors.onBackground) }
        }
        Spacer(Modifier.height(28.dp))
        Text("PICK A DEMO", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ShowcaseCard(showcase: Showcase, onClick: () -> Unit) {
    val content = showcase.onAccent
    // The same hue, lighter at the top left and deeper at the bottom right.
    val gradient = remember(showcase.accent) {
        Brush.linearGradient(
            listOf(lerp(showcase.accent, Color.White, 0.18f), lerp(showcase.accent, Color.Black, 0.22f)),
        )
    }
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "cardScale",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(28.dp))
            .background(gradient)
            .clickable(interactionSource = interactionSource, indication = LocalIndication.current, onClick = onClick)
            .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(showcase.number, style = MaterialTheme.typography.labelSmall, color = content.copy(alpha = 0.7f))
            Spacer(Modifier.height(4.dp))
            Text(showcase.title, style = MaterialTheme.typography.displaySmall, color = content)
            Spacer(Modifier.height(8.dp))
            Text(showcase.blurb, style = MaterialTheme.typography.bodyMedium, color = content.copy(alpha = 0.85f))
            Spacer(Modifier.height(14.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                showcase.tags.forEach { Tag(it, content) }
            }
        }
        // Each card's thumb sits a little lower, so the column of cards reads like a scroll.
        ScrollbarMotif(
            color = content,
            thumbPosition = showcase.ordinal / (Showcase.entries.size - 1f),
            modifier = Modifier.padding(start = 16.dp).size(width = 10.dp, height = 88.dp),
        )
    }
}
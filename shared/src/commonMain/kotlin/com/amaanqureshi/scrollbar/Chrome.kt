package com.amaanqureshi.scrollbar

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Every demo screen: the home cards are built from this list, and each one opens a screen. */
internal enum class Showcase(
    val number: String,
    val title: String,
    val blurb: String,
    val hint: String,
    val tags: List<String>,
    val accent: Color,
    val onAccent: Color,
) {
    Contacts(
        number = "01",
        title = "Contacts",
        blurb = "2,000 people, A to Z. The bubble shows the letter and ticks at each new one.",
        hint = "Drag the thumb. Feel the tick at each new letter.",
        tags = listOf("label", "haptics"),
        accent = Brand.Coral,
        onAccent = Color.White,
    ),
    Photos(
        number = "02",
        title = "Photos",
        blurb = "Four years of photos in a grid with month headers and a proportional bar.",
        hint = "Scrub through the months. The bubble shows where you'll land.",
        tags = listOf("grid", "bar", "proportional"),
        accent = Brand.Violet,
        onAccent = Color.White,
    ),
    Chat(
        number = "03",
        title = "Chat",
        blurb = "Newest at the bottom. Older messages load as you go up.",
        hint = "Drag up. The bar stops short of the end while older messages load.",
        tags = listOf("reverse", "hasMore"),
        accent = Brand.Mint,
        onAccent = Brand.Ink,
    ),
    Playlist(
        number = "04",
        title = "Your logo",
        blurb = "Swap the thumb for any icon, like your app's own mark.",
        hint = "The thumb is an icon. It changes while you hold it.",
        tags = listOf("icon thumb"),
        accent = Brand.Amber,
        onAccent = Brand.Ink,
    ),
    Notes(
        number = "05",
        title = "Move it",
        blurb = "Hold the thumb for half a second, then drag it to the other side.",
        hint = "Hold the thumb still, then drag it across the screen.",
        tags = listOf("onEdgeChange", "always visible"),
        accent = Brand.Sky,
        onAccent = Color.White,
    ),
    Shelf(
        number = "06",
        title = "Shelf",
        blurb = "A horizontal row of albums with the bar along the bottom.",
        hint = "Grab the bar at the bottom and slide through the years.",
        tags = listOf("LazyRow", "label"),
        accent = Brand.Pink,
        onAccent = Color.White,
    ),
    Playground(
        number = "07",
        title = "Playground",
        blurb = "Every option on one screen: RTL, reverse, padding, sizes and more.",
        hint = "Mix the options and try to break it.",
        tags = listOf("all options"),
        accent = Brand.Lime,
        onAccent = Brand.Ink,
    ),
}

/** The bottom navigation bar inset, so edge-to-edge lists can pad their last item above it. */
@Composable
internal fun navigationBarBottom(): Dp = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

/**
 * The frame for a demo screen: the top bar, a hint, then [content].
 *
 * Like a typical edge-to-edge app, only the top is padded, so lists reach the side edges (where
 * the back gesture lives) and run under the navigation bar. The scrollbar handles both.
 */
@Composable
internal fun DemoScreen(
    showcase: Showcase,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BackButton(onBack)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = showcase.title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${showcase.number} · ${showcase.tags.joinToString(" · ")}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            actions()
        }
        HintPill(text = showcase.hint, accent = showcase.accent)
        content()
    }
}

@Composable
private fun BackButton(onClick: () -> Unit) {
    val color = MaterialTheme.colorScheme.onSurface
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        // A drawn arrow, so the demo needs no icon library. It points left in both directions:
        // the demo's own RTL toggle only flips the playground's list, not the chrome.
        Canvas(Modifier.size(18.dp)) {
            val stroke = 2.4.dp.toPx()
            val middle = size.height / 2
            drawLine(color, Offset(size.width, middle), Offset(stroke, middle), stroke, StrokeCap.Round)
            drawLine(color, Offset(stroke, middle), Offset(size.width * 0.45f, size.height * 0.12f), stroke, StrokeCap.Round)
            drawLine(color, Offset(stroke, middle), Offset(size.width * 0.45f, size.height * 0.88f), stroke, StrokeCap.Round)
        }
    }
}

@Composable
private fun HintPill(text: String, accent: Color) {
    Row(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(accent.copy(alpha = 0.14f))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(accent))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground)
    }
}

/** A small rounded tag, used on the home cards. */
@Composable
internal fun Tag(text: String, color: Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.16f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

@Composable
internal fun Avatar(initials: String, hue: Float, size: Dp = 44.dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(Color.hsl(hue, 0.65f, 0.55f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(initials, style = MaterialTheme.typography.titleMedium, color = Color.White)
    }
}

/**
 * Generated "photo": a sky gradient, a sun and two hills, all from [seed]. Cheap enough to draw
 * thousands of while fast scrolling.
 */
@Composable
internal fun PhotoArt(seed: Int, modifier: Modifier = Modifier) {
    val hue = hueFor(seed)
    val skyTop = Color.hsl(hue, 0.55f, 0.72f)
    val skyBottom = Color.hsl((hue + 40f) % 360f, 0.65f, 0.82f)
    val sun = Color.hsl((hue + 180f) % 360f, 0.9f, 0.7f)
    val hillBack = Color.hsl((hue + 20f) % 360f, 0.35f, 0.42f)
    val hillFront = Color.hsl((hue + 30f) % 360f, 0.40f, 0.28f)
    val sunX = 0.2f + (seed * 37 % 60) / 100f
    Canvas(modifier) {
        drawRect(Brush.verticalGradient(listOf(skyTop, skyBottom)))
        drawCircle(sun, radius = size.minDimension * 0.13f, center = Offset(size.width * sunX, size.height * 0.36f))
        drawPath(
            Path().apply {
                moveTo(0f, size.height * 0.72f)
                quadraticTo(size.width * 0.35f, size.height * 0.48f, size.width * 0.7f, size.height * 0.7f)
                quadraticTo(size.width * 0.85f, size.height * 0.62f, size.width, size.height * 0.66f)
                lineTo(size.width, size.height)
                lineTo(0f, size.height)
                close()
            },
            hillBack,
        )
        drawPath(
            Path().apply {
                moveTo(0f, size.height * 0.86f)
                quadraticTo(size.width * 0.55f, size.height * 0.7f, size.width, size.height * 0.9f)
                lineTo(size.width, size.height)
                lineTo(0f, size.height)
                close()
            },
            hillFront,
        )
    }
}

/** Generated album cover: a bold gradient with one of three shapes. */
@Composable
internal fun AlbumArt(hue: Float, shape: Int, modifier: Modifier = Modifier) {
    val base = Color.hsl(hue, 0.7f, 0.5f)
    val deep = Color.hsl((hue + 300f) % 360f, 0.6f, 0.25f)
    val shapeColor = Color.hsl((hue + 60f) % 360f, 0.9f, 0.75f)
    Canvas(modifier) {
        drawRect(Brush.linearGradient(listOf(base, deep), start = Offset.Zero, end = Offset(size.width, size.height)))
        val center = Offset(size.width / 2, size.height / 2)
        val unit = size.minDimension
        when (shape) {
            0 -> drawCircle(shapeColor, radius = unit * 0.28f, center = center)
            1 -> repeat(4) { ring ->
                drawCircle(
                    shapeColor.copy(alpha = 1f - ring * 0.2f),
                    radius = unit * (0.12f + ring * 0.08f),
                    center = center,
                    style = Stroke(width = unit * 0.025f),
                )
            }
            else -> drawRoundRect(
                shapeColor,
                topLeft = Offset(unit * 0.22f, unit * 0.22f),
                size = Size(unit * 0.56f, unit * 0.56f),
                cornerRadius = CornerRadius(unit * 0.08f),
            )
        }
    }
}

/** The brand motif on the home cards: a little track with a capsule thumb. */
@Composable
internal fun ScrollbarMotif(color: Color, thumbPosition: Float, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val trackWidth = size.width * 0.28f
        drawRoundRect(
            color.copy(alpha = 0.25f),
            topLeft = Offset((size.width - trackWidth) / 2, 0f),
            size = Size(trackWidth, size.height),
            cornerRadius = CornerRadius(trackWidth / 2),
        )
        val thumbLength = size.height * 0.34f
        val thumbTop = (size.height - thumbLength) * thumbPosition.coerceIn(0f, 1f)
        drawRoundRect(
            color,
            topLeft = Offset(0f, thumbTop),
            size = Size(size.width, thumbLength),
            cornerRadius = CornerRadius(size.width / 2),
        )
    }
}
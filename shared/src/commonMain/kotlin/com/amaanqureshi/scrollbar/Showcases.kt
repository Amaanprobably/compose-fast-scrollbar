package com.amaanqureshi.scrollbar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import io.github.amaanprobably.scrollbar.FastScrollEdge
import io.github.amaanprobably.scrollbar.ScrollbarLabel
import io.github.amaanprobably.scrollbar.ScrollbarVisibility
import io.github.amaanprobably.scrollbar.ThumbAppearance
import io.github.amaanprobably.scrollbar.ThumbSizing
import io.github.amaanprobably.scrollbar.scrollbar
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

// =============================================================================================
// 01 Contacts: label + section haptics
// =============================================================================================

@Composable
internal fun ContactsScreen(onBack: () -> Unit) {
    val rows = remember { buildContactRows() }
    val listState = rememberLazyListState()
    val labelStyle = brandLabelStyle()
    val label = remember(rows, labelStyle) { ScrollbarLabel(style = labelStyle) { index -> rows[index].letter } }
    val padding = PaddingValues(bottom = navigationBarBottom() + 8.dp)

    DemoScreen(Showcase.Contacts, onBack) {
        LazyColumn(
            state = listState,
            contentPadding = padding,
            modifier = Modifier
                .fillMaxSize()
                .scrollbar(listState, contentPadding = padding, style = brandScrollbarStyle(), label = label),
        ) {
            items(rows.size, key = { rows[it].key }, contentType = { rows[it] is ContactRow.Header }) { index ->
                when (val row = rows[index]) {
                    is ContactRow.Header -> LetterHeader(row.letter)
                    is ContactRow.Person -> ContactItem(row.contact)
                }
            }
        }
    }
}

@Composable
private fun LetterHeader(letter: String) {
    Text(
        text = letter,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 20.dp, top = 18.dp, bottom = 4.dp),
    )
}

@Composable
private fun ContactItem(contact: Contact) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(contact.initials, contact.hue)
        Spacer(Modifier.width(14.dp))
        Column {
            Text(contact.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
            Text(contact.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// =============================================================================================
// 02 Photos: grid, month headers, Bar look, proportional thumb
// =============================================================================================

@Composable
internal fun PhotosScreen(onBack: () -> Unit) {
    val library = remember { buildPhotoLibrary() }
    val gridState = rememberLazyGridState()
    val labelStyle = brandLabelStyle()
    val label = remember(library, labelStyle) {
        ScrollbarLabel(style = labelStyle) { index -> library[index].monthLabel }
    }
    val style = brandScrollbarStyle(thumbAppearance = ThumbAppearance.Bar, thumbSizing = ThumbSizing.Proportional())
    val padding = PaddingValues(bottom = navigationBarBottom() + 8.dp)

    DemoScreen(Showcase.Photos, onBack) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            state = gridState,
            contentPadding = padding,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier
                .fillMaxSize()
                .scrollbar(gridState, contentPadding = padding, style = style, label = label),
        ) {
            items(
                count = library.size,
                key = { index -> index },
                span = { index ->
                    when (library[index]) {
                        is PhotoEntry.MonthHeader -> GridItemSpan(maxLineSpan)
                        is PhotoEntry.Photo -> GridItemSpan(1)
                    }
                },
                contentType = { index -> library[index] is PhotoEntry.MonthHeader },
            ) { index ->
                when (val entry = library[index]) {
                    is PhotoEntry.MonthHeader -> MonthHeader(entry)
                    is PhotoEntry.Photo -> PhotoArt(entry.seed, Modifier.fillMaxWidth().aspectRatio(1f))
                }
            }
        }
    }
}

@Composable
private fun MonthHeader(header: PhotoEntry.MonthHeader) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(header.title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.weight(1f))
        Text("${header.count} photos", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// =============================================================================================
// 03 Chat: reverse layout + hasMore (older pages load at the top)
// =============================================================================================

private const val CHAT_PAGE_SIZE = 60

@Composable
internal fun ChatScreen(onBack: () -> Unit) {
    val history = remember { buildChatHistory() }
    val listState = rememberLazyListState()
    var loadedCount by remember { mutableIntStateOf(CHAT_PAGE_SIZE) }
    val hasMore = loadedCount < history.size
    val labelStyle = brandLabelStyle()
    // The loading row has no day, so it shows no bubble (getOrNull past the loaded messages).
    val label = remember(history, labelStyle) {
        ScrollbarLabel(style = labelStyle) { index -> history.getOrNull(index)?.dayLabel }
    }

    // Like a real paged API: when you get near the oldest loaded message, the next page arrives
    // after a short delay. Meanwhile, the bar stays short of the end (hasMore).
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collect { lastVisible ->
                if (lastVisible != null && lastVisible >= loadedCount - 10 && loadedCount < history.size) {
                    delay(700.milliseconds)
                    loadedCount = (loadedCount + CHAT_PAGE_SIZE).coerceAtMost(history.size)
                }
            }
    }

    DemoScreen(Showcase.Chat, onBack) {
        LazyColumn(
            state = listState,
            reverseLayout = true,
            contentPadding = PaddingValues(vertical = 8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .scrollbar(
                    listState,
                    contentPadding = PaddingValues(vertical = 8.dp),
                    hasMore = hasMore,
                    style = brandScrollbarStyle(),
                    label = label,
                ),
        ) {
            items(loadedCount, key = { it }, contentType = { history[it] is ChatItem.DaySeparator }) { index ->
                when (val item = history[index]) {
                    is ChatItem.Message -> ChatBubble(item)
                    is ChatItem.DaySeparator -> DaySeparator(item.dayLabel)
                }
            }
            if (hasMore) {
                item(key = "loading") {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = Showcase.Chat.accent,
                            strokeWidth = 3.dp,
                        )
                    }
                }
            }
        }
        Composer()
    }
}

@Composable
private fun ChatBubble(message: ChatItem.Message) {
    val colors = MaterialTheme.colorScheme
    val background = if (message.fromMe) Showcase.Chat.accent else colors.surfaceVariant
    val textColor = if (message.fromMe) Showcase.Chat.onAccent else colors.onSurface
    val shape = if (message.fromMe) {
        RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 6.dp)
    } else {
        RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 6.dp, bottomEnd = 20.dp)
    }
    Box(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 3.dp),
        contentAlignment = if (message.fromMe) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(shape)
                .background(background)
                .padding(horizontal = 14.dp, vertical = 9.dp),
        ) {
            Text(message.text, style = MaterialTheme.typography.bodyLarge, color = textColor)
            Text(
                text = message.time,
                style = MaterialTheme.typography.labelSmall,
                color = textColor.copy(alpha = 0.6f),
                modifier = Modifier.align(Alignment.End),
            )
        }
    }
}

@Composable
private fun DaySeparator(label: String) {
    Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 12.dp, vertical = 5.dp),
        )
    }
}

/** A fake message box, so the screen looks like a real chat. It sits above the navigation bar. */
@Composable
private fun Composer() {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.background)
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = navigationBarBottom() + 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Message",
            style = MaterialTheme.typography.bodyLarge,
            color = colors.onSurfaceVariant,
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(50))
                .background(colors.surfaceVariant)
                .padding(horizontal = 18.dp, vertical = 12.dp),
        )
        Spacer(Modifier.width(10.dp))
        Box(Modifier.size(46.dp).clip(CircleShape).background(Showcase.Chat.accent))
    }
}

// =============================================================================================
// 04 Your logo: ThumbAppearance.Icon
// =============================================================================================

/** A made-up brand mark: a rounded square with up and down chevrons. Built in code, no resources. */
private fun logoVector(top: Color, bottom: Color): ImageVector =
    ImageVector.Builder(
        name = "DemoLogo",
        defaultWidth = 40.dp,
        defaultHeight = 40.dp,
        viewportWidth = 40f,
        viewportHeight = 40f,
    ).apply {
        path(fill = Brush.linearGradient(listOf(top, bottom), start = Offset(0f, 0f), end = Offset(40f, 40f))) {
            moveTo(14f, 0f)
            horizontalLineTo(26f)
            quadTo(40f, 0f, 40f, 14f)
            verticalLineTo(26f)
            quadTo(40f, 40f, 26f, 40f)
            horizontalLineTo(14f)
            quadTo(0f, 40f, 0f, 26f)
            verticalLineTo(14f)
            quadTo(0f, 0f, 14f, 0f)
            close()
        }
        // A thin light rim, so the mark stays crisp on dark and colored backgrounds.
        path(stroke = SolidColor(Color.White), strokeAlpha = 0.35f, strokeLineWidth = 1.5f) {
            moveTo(14f, 1f)
            horizontalLineTo(26f)
            quadTo(39f, 1f, 39f, 14f)
            verticalLineTo(26f)
            quadTo(39f, 39f, 26f, 39f)
            horizontalLineTo(14f)
            quadTo(1f, 39f, 1f, 26f)
            verticalLineTo(14f)
            quadTo(1f, 1f, 14f, 1f)
            close()
        }
        path(
            stroke = SolidColor(Color.White),
            strokeLineWidth = 4f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(13f, 16f)
            lineTo(20f, 9f)
            lineTo(27f, 16f)
            moveTo(13f, 24f)
            lineTo(20f, 31f)
            lineTo(27f, 24f)
        }
    }.build()

private val LogoIdle = logoVector(Brand.Amber, Brand.Coral)
private val LogoDragging = logoVector(Brand.Coral, Brand.Violet)

@Composable
internal fun PlaylistScreen(onBack: () -> Unit) {
    val tracks = remember { buildPlaylist() }
    val listState = rememberLazyListState()
    val idle = rememberVectorPainter(LogoIdle)
    val dragging = rememberVectorPainter(LogoDragging)
    val appearance = remember(idle, dragging) {
        ThumbAppearance.Icon(idle = idle, size = DpSize(44.dp, 44.dp), dragging = dragging)
    }
    val style = brandScrollbarStyle(thumbAppearance = appearance)
    val labelStyle = brandLabelStyle()
    // A per-item label, so haptics are off (they would tick on every track).
    val label = remember(labelStyle) {
        ScrollbarLabel(sectionHaptics = false, style = labelStyle) { index ->
            if (index == 0) null else "#$index"
        }
    }
    val padding = PaddingValues(bottom = navigationBarBottom() + 8.dp)

    DemoScreen(Showcase.Playlist, onBack) {
        LazyColumn(
            state = listState,
            contentPadding = padding,
            modifier = Modifier
                .fillMaxSize()
                .scrollbar(listState, contentPadding = padding, style = style, label = label),
        ) {
            item(key = "header") { PlaylistHeader(tracks.size) }
            items(tracks.size, key = { it }) { index -> TrackRow(tracks[index]) }
        }
    }
}

@Composable
private fun PlaylistHeader(trackCount: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AlbumArt(hue = 36f, shape = 1, modifier = Modifier.size(96.dp).clip(RoundedCornerShape(20.dp)))
        Spacer(Modifier.width(16.dp))
        Column {
            Text("Late Night Mix", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground)
            Text(
                "$trackCount tracks · about 40 hours",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TrackRow(track: Track) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = track.number.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(36.dp),
        )
        AlbumArt(track.hue, shape = track.number % 3, modifier = Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                track.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(track.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            track.duration,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            // Room for the icon thumb, which sits over this edge.
            modifier = Modifier.padding(end = 40.dp),
        )
    }
}

// =============================================================================================
// 05 Move it: onEdgeChange + always visible
// =============================================================================================

@Composable
internal fun NotesScreen(onBack: () -> Unit) {
    val notes = remember { buildNotes() }
    val listState = rememberLazyListState()
    // A real app would persist this, so the bar stays where the user put it.
    var edge by rememberSaveable { mutableStateOf(FastScrollEdge.END) }
    val padding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = navigationBarBottom() + 16.dp)

    DemoScreen(
        showcase = Showcase.Notes,
        onBack = onBack,
        actions = {
            Tag(if (edge == FastScrollEdge.END) "bar: end" else "bar: start", MaterialTheme.colorScheme.onBackground)
        },
    ) {
        LazyColumn(
            state = listState,
            contentPadding = padding,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxSize()
                .scrollbar(
                    listState,
                    edge = edge,
                    contentPadding = padding,
                    style = brandScrollbarStyle(visibility = ScrollbarVisibility.Always),
                    onEdgeChange = { edge = it },
                ),
        ) {
            items(notes.size, key = { it }) { index -> NoteCard(notes[index]) }
        }
    }
}

@Composable
private fun NoteCard(note: Note) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 18.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                note.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(note.date, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(4.dp))
        Text(note.body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// =============================================================================================
// 06 Shelf: LazyRow, bar along the bottom
// =============================================================================================

@Composable
internal fun ShelfScreen(onBack: () -> Unit) {
    val albums = remember { buildAlbums() }
    val listState = rememberLazyListState()
    val labelStyle = brandLabelStyle()
    val label = remember(albums, labelStyle) { ScrollbarLabel(style = labelStyle) { index -> albums[index].year.toString() } }
    val padding = PaddingValues(horizontal = 16.dp)

    DemoScreen(Showcase.Shelf, onBack) {
        Text(
            text = "New releases, newest first",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
        )
        LazyRow(
            state = listState,
            contentPadding = padding,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(bottom = navigationBarBottom())
                .scrollbar(listState, contentPadding = padding, style = brandScrollbarStyle(), label = label),
        ) {
            items(albums.size, key = { it }) { index -> AlbumCard(albums[index]) }
        }
    }
}

@Composable
private fun AlbumCard(album: Album) {
    Column(Modifier.width(220.dp)) {
        AlbumArt(album.hue, album.shape, Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(24.dp)))
        Spacer(Modifier.height(12.dp))
        Text(
            album.title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            "${album.artist} · ${album.year}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
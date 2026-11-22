package com.amaanqureshi.scrollbar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.amaanprobably.scrollbar.ScrollbarLabel
import io.github.amaanprobably.scrollbar.scrollbar
import kotlin.random.Random

// 09 Book: fifty pages in a plain Column with verticalScroll, not a lazy list. Positions are exact.

private const val PAGES_PER_CHAPTER = 5

private class Chapter(val title: String, val lines: List<String>)

private class BookPage(val number: Int, val chapter: Chapter, val opensChapter: Boolean, val paragraphs: List<String>)

private val Chapters = listOf(
    Chapter("The night shift", listOf(
        "The lanterns came on one street at a time, like someone walking ahead of us with a match.",
        "By eight the tarmac had disappeared under folding tables and the smell of charcoal.",
        "Mei said she only wanted one skewer, and we both knew that was a lie.",
        "The office towers behind us went dark floor by floor, as if they were handing the night over.",
        "A bicycle bell rang somewhere, and the crowd parted without anyone looking up.",
    )),
    Chapter("Getting there", listOf(
        "We caught the last train before the night timetable, the one that smells of rain and newspapers.",
        "At the river stop the carriage emptied and filled again with people carrying folding stools.",
        "An old man across from us was asleep with a bag of oranges balanced perfectly on his knees.",
        "Mei followed the stools off the train, because the stools always know where the good tables are.",
        "The station clock was four minutes fast, and nobody had fixed it in years.",
    )),
    Chapter("The first street", listOf(
        "The first street was for people in a hurry, all buns and skewers and paper cones of chestnuts.",
        "A vendor waved us past his own stall and told us to come back once the real queues went down.",
        "Steam rose from every second table, and the lanterns turned it gold.",
        "I burned my tongue on a dumpling and pretended I hadn't.",
        "Mei kept a running list of everything she wanted to try, and it was already too long.",
    )),
    Chapter("Reading the queues", listOf(
        "A queue of tourists meant a stall was famous, and a queue of people in work uniforms meant it was good.",
        "A queue of grandmothers meant you joined it immediately and asked no questions.",
        "We stood behind a nurse still wearing her lanyard, which felt like a promise.",
        "The man in front of us ordered for himself and, apparently, for three absent friends.",
        "No queue at all meant either the stall had just opened or everyone knew something we didn't.",
    )),
    Chapter("Noodles, eventually", listOf(
        "The noodle stall had no sign and no menu, only one pot, one ladle and one woman.",
        "She had made the same broth every night for twenty years, and she didn't look up once.",
        "We waited, and then we waited some more, and nobody in the line looked bored.",
        "When the bowls arrived, the whole table went quiet at the same time.",
        "Mei asked for extra chilli, and the woman looked at her for a long, measuring second.",
    )),
    Chapter("Things that aren't food", listOf(
        "Between the food streets were the stalls that sold everything else.",
        "There were phone cases, hand-painted fans and records nobody had played since the eighties.",
        "A sleeping cat sat on a stack of maps, and a card beside it said it was not for sale.",
        "Mei haggled for a fan for five minutes and then paid the first price anyway.",
        "The record seller insisted on playing us a song before he would sell us anything.",
    )),
    Chapter("The tea house", listOf(
        "Halfway through the night, everyone ended up at the tea house on the corner.",
        "It had six tables, forty customers and a kettle that never once stopped.",
        "We shared a table with two students and a retired bus driver who told us about every route he had driven.",
        "A stranger slid a plate of biscuits towards us without a word.",
        "The windows fogged up, and the lanterns outside became soft, round smudges of light.",
    )),
    Chapter("When it rains", listOf(
        "The rain came without warning, the way it always does here.",
        "Within a minute every stall had a tarp up, and the market shrank into one covered corridor.",
        "Nobody left, and if anything the crowd grew louder.",
        "Water ran along the gutters, carrying paper cups and lantern light towards the river.",
        "Mei said the noodles tasted better in the rain, and I couldn't argue.",
    )),
    Chapter("The regulars", listOf(
        "After a while you start to recognise people, and then they start to recognise you.",
        "There was the couple who always shared one bowl and argued over the last bite.",
        "There was the student who read at the tea house until the lights went off.",
        "At midnight exactly, a man walked his tortoise down the middle of the main street.",
        "The vendor who had waved us away earlier now saved us two stools without being asked.",
    )),
    Chapter("Going home", listOf(
        "Around two the lanterns began to go out, one by one, the way they had come on.",
        "The vendors packed up faster than seemed possible, and the sweepers were already waiting.",
        "By three the tarmac was back, and you would never know a thousand people had eaten dinner on it.",
        "The first morning train was full of quiet, sticky, very happy people.",
        "Mei fell asleep on my shoulder and woke up asking what we were eating next week.",
    )),
)

/** Fifty pages, built the same way every time: each page mixes its chapter's lines into paragraphs. */
private val BookPages: List<BookPage> = buildList {
    Chapters.forEachIndexed { chapterIndex, chapter ->
        repeat(PAGES_PER_CHAPTER) { pageInChapter ->
            val number = chapterIndex * PAGES_PER_CHAPTER + pageInChapter + 1
            val random = Random(number)
            val paragraphs = List(4) {
                chapter.lines.shuffled(random).take(random.nextInt(3, 5)).joinToString(" ")
            }
            add(BookPage(number, chapter, opensChapter = pageInChapter == 0, paragraphs = paragraphs))
        }
    }
}

@Composable
internal fun BookScreen(onBack: () -> Unit) {
    val scrollState = rememberScrollState()
    val navigationBar = navigationBarBottom()
    // Where each page starts inside the content, filled in by layout. Not state: layout writes it,
    // and the page counter below re-reads it every time the scroll position changes.
    val pageTops = remember { IntArray(BookPages.size) { Int.MAX_VALUE } }
    // The page at a scroll offset, using the same reading line as the counter. Binary search,
    // because the label asks on almost every pixel of a drag.
    fun pageAt(offset: Int): Int {
        val readingLine = offset + scrollState.viewportSize / 3
        var low = 0
        var high = pageTops.size
        while (low < high) {
            val middle = (low + high) / 2
            if (pageTops[middle] <= readingLine) low = middle + 1 else high = middle
        }
        return low.coerceIn(1, BookPages.size)
    }
    val labelStyle = brandLabelStyle()
    val label = remember(labelStyle) {
        ScrollbarLabel(style = labelStyle) { offset -> "Page ${ pageAt(offset) }" }
    }
    val currentPage by remember(scrollState) {
        derivedStateOf {
            // The page under a reading line a third of the way down the screen.
            val readingLine = scrollState.value + scrollState.viewportSize / 3
            pageTops.count { it <= readingLine }.coerceIn(1, BookPages.size)
        }
    }

    DemoScreen(
        showcase = Showcase.Book,
        onBack = onBack,
        actions = { Tag("Page $currentPage of ${BookPages.size}", MaterialTheme.colorScheme.onBackground) },
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                // Before verticalScroll, so the bar stays on screen instead of scrolling away.
                .scrollbar(
                    scrollState,
                    contentPadding = PaddingValues(bottom = navigationBar),
                    style = brandScrollbarStyle(),
                    label = label,
                    scrollToStartLabel = "Back to the first page",
                    scrollToEndLabel = "Jump to the last page",
                )
                .verticalScroll(scrollState)
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = navigationBar + 32.dp),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.widthIn(max = 680.dp).fillMaxWidth(),
            ) {
                BookTitlePage()
                BookPages.forEachIndexed { index, page ->
                    BookPageCard(
                        page,
                        Modifier.onPlaced { coordinates -> pageTops[index] = coordinates.positionInParent().y.toInt() },
                    )
                }
            }
        }
    }
}

@Composable
private fun BookTitlePage() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
    ) {
        Text(
            "A Night at the Market",
            style = MaterialTheme.typography.headlineLarge,
            fontFamily = FontFamily.Serif,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "${Chapters.size} chapters · ${BookPages.size} pages",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun BookPageCard(page: BookPage, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 560.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(colors.surfaceVariant)
            .padding(horizontal = 28.dp, vertical = 24.dp),
    ) {
        // Running header, like a printed book.
        Text(
            page.chapter.title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurfaceVariant.copy(alpha = 0.7f),
        )
        if (page.opensChapter) {
            val chapterNumber = (page.number - 1) / PAGES_PER_CHAPTER + 1
            Spacer(Modifier.height(28.dp))
            Text(
                "Chapter $chapterNumber",
                style = MaterialTheme.typography.labelLarge,
                color = colors.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                page.chapter.title,
                style = MaterialTheme.typography.headlineSmall,
                fontFamily = FontFamily.Serif,
                color = colors.onSurface,
            )
        }
        for (paragraph in page.paragraphs) {
            Spacer(Modifier.height(16.dp))
            Text(
                paragraph,
                style = MaterialTheme.typography.bodyLarge,
                fontFamily = FontFamily.Serif,
                color = colors.onSurface,
            )
        }
        Spacer(Modifier.weight(1f, fill = false).height(24.dp))
        Text(
            "— ${page.number} —",
            style = MaterialTheme.typography.labelMedium,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Preview
@Composable
private fun BookScreenPreview() {
    DemoTheme { BookScreen(onBack = {}) }
}

@Preview
@Composable
private fun BookPagePreview() {
    DemoTheme {
        Column(Modifier.padding(16.dp)) { BookPageCard(BookPages[0]) }
    }
}
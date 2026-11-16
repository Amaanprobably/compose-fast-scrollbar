package com.amaanqureshi.scrollbar

import kotlin.random.Random

// Everything here is generated offline from fixed seeds, so the demo looks the same on every run
// and every platform, which matters for recording the video.

private val FirstNames = listOf(
    "Aarav", "Aditi", "Aisha", "Alex", "Amaan", "Amelia", "Ananya", "Arjun", "Ava", "Ben",
    "Bilal", "Chloe", "Daniel", "Diya", "Elena", "Emma", "Ethan", "Fatima", "Felix", "Gabriel",
    "Grace", "Hana", "Hugo", "Ibrahim", "Ines", "Isha", "Jack", "Jonas", "Kabir", "Kai",
    "Laila", "Leo", "Lucas", "Maya", "Meera", "Mia", "Mohammed", "Nadia", "Nikhil", "Noah",
    "Nora", "Olivia", "Omar", "Priya", "Quinn", "Rahul", "Riya", "Rohan", "Sara", "Sofia",
    "Tara", "Theo", "Uma", "Vikram", "Vivaan", "Wen", "Xavier", "Yara", "Yusuf", "Zara", "Zoe",
)

private val LastNames = listOf(
    "Ahmed", "Bauer", "Chen", "Costa", "Das", "Dubois", "Evans", "Fischer", "Garcia", "Gupta",
    "Haddad", "Ito", "Iyer", "Jansen", "Khan", "Kim", "Kowalski", "Lopez", "Mehta", "Moreau",
    "Nair", "Novak", "Okafor", "Patel", "Qureshi", "Rossi", "Sato", "Schmidt", "Shah", "Silva",
    "Singh", "Tanaka", "Torres", "Usman", "Verma", "Wagner", "Wong", "Yilmaz", "Zhang",
)

private val Cities = listOf(
    "Delhi", "Mumbai", "Bengaluru", "Berlin", "Lisbon", "Tokyo", "Toronto", "Nairobi", "Dubai",
    "London", "Seoul", "Paris", "Austin", "Jaipur", "Pune", "Oslo",
)

private val Words1 = listOf(
    "Midnight", "Neon", "Golden", "Paper", "Velvet", "Electric", "Quiet", "Monsoon", "Silver",
    "Lucid", "Static", "Northern", "Hollow", "Copper", "Distant", "Crystal", "Sunday", "Wild",
)

private val Words2 = listOf(
    "Static", "Rivers", "Hours", "Lights", "Season", "Signals", "Gardens", "Echoes", "Motel",
    "Dreams", "Avenue", "Satellites", "Tides", "Engines", "Orchard", "Weather", "Radio", "Skyline",
)

private val Artists = listOf(
    "Lunar Bloom", "The Paper Kites", "Kavya & the Tides", "North Avenue", "Static Hearts",
    "Mira Sol", "Arcade Club", "Velvet June", "Ozone Kids", "Saffron Sky", "Low Orbit",
    "Juno Park", "The Night Shift", "Coastline", "Tiny Planets", "Golden Hour Club",
)

private val ChatLines = listOf(
    "On my way!", "Did you push the fix?", "haha yes", "Lunch at 1?", "Can you review my PR when you get a sec",
    "The scrollbar feels so smooth now", "Sending the build in 5", "Works on my phone!",
    "Call later?", "Just landed", "Which one, the blue or the coral?", "lol", "Okay, merging",
    "The demo looks great", "Got it, thanks!", "Running the tests now", "Wait what",
    "Let's ship v0.1 this week", "Did you see the video?", "Can't make it today, sorry",
    "Tomorrow works", "Coffee?", "That's a really long message to see how the bubble wraps when the text goes over a couple of lines",
    "nice", "Yes!!", "Sure", "Haven't tried it on iOS yet", "Back gesture is fixed btw",
)

private val NoteTitles = listOf(
    "Groceries", "Ideas for the video", "Book list", "Meeting notes", "Trip to Jaipur",
    "Workout plan", "Gift ideas", "Release checklist", "Recipes to try", "Weekend plan",
    "Bug list", "Quotes", "Podcast picks", "Packing list", "Questions for the interview",
)

private val NoteLines = listOf(
    "Milk, eggs, coffee beans and the good bread from the corner shop.",
    "Open on the A to Z list, then switch to the grid and the chat.",
    "Finish the one about habits, then start the sci-fi one.",
    "Decided to ship the smaller version first and collect feedback.",
    "Book the train early, the morning ones fill up fast.",
    "Three days a week, keep it short and do not skip warm-up.",
    "Something handmade this year, maybe a printed photo book.",
    "Name, license, explicit API, README install snippet, CI badge.",
    "The lentil soup and the one-pan pasta from last week.",
    "Market in the morning, long walk, early night.",
)

/** Deterministic hue in 0..360 for an index, spread so neighbors look different. */
internal fun hueFor(seed: Int): Float = ((seed * 137.508f) % 360f + 360f) % 360f

// ---------------------------------------------------------------------------------------------
// Dates (no date library needed: a tiny day-by-day calendar walking backwards)
// ---------------------------------------------------------------------------------------------

private val MonthShort = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
private val MonthLong = listOf(
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December",
)
private val WeekdayShort = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

/** [month] is 1..12, [weekday] is 0 (Monday)..6 (Sunday). */
internal data class SimpleDate(val year: Int, val month: Int, val day: Int, val weekday: Int) {
    fun previousDay(): SimpleDate {
        val weekdayBefore = (weekday + 6) % 7
        return when {
            day > 1 -> copy(day = day - 1, weekday = weekdayBefore)
            month > 1 -> SimpleDate(year, month - 1, daysInMonth(year, month - 1), weekdayBefore)
            else -> SimpleDate(year - 1, 12, 31, weekdayBefore)
        }
    }

    fun previousMonth(): SimpleDate = when (month) {
        1 -> copy(year = year - 1, month = 12, day = 1)
        else -> copy(month = month - 1, day = 1)
    }

    val shortLabel: String get() = "${WeekdayShort[weekday]}, $day ${MonthShort[month - 1]}"
    val monthYearShort: String get() = "${MonthShort[month - 1]} $year"
    val monthYearLong: String get() = "${MonthLong[month - 1]} $year"
}

private fun daysInMonth(year: Int, month: Int): Int = when (month) {
    2 -> if (year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)) 29 else 28
    4, 6, 9, 11 -> 30
    else -> 31
}

/** The demo's fixed "today", so the data never changes. */
private val DemoToday = SimpleDate(year = 2026, month = 11, day = 17, weekday = 1)

// ---------------------------------------------------------------------------------------------
// Contacts: an A-Z list with letter headers
// ---------------------------------------------------------------------------------------------

internal class Contact(val name: String, val detail: String, val hue: Float) {
    val initials: String = name.split(' ').take(2).joinToString("") { it.take(1) }
}

internal sealed interface ContactRow {
    val letter: String
    val key: Any

    class Header(override val letter: String) : ContactRow {
        override val key: Any get() = "header-$letter"
    }

    class Person(val contact: Contact, val index: Int) : ContactRow {
        override val letter: String get() = contact.name.take(1)
        override val key: Any get() = index
    }
}

internal fun buildContactRows(count: Int = 2_000): List<ContactRow> {
    val random = Random(7)
    val contacts = List(count) {
        val name = "${FirstNames.random(random)} ${LastNames.random(random)}"
        val detail = "Mobile · ${Cities.random(random)}"
        Contact(name, detail, hue = random.nextFloat() * 360f)
    }.sortedBy { it.name }

    val rows = ArrayList<ContactRow>(count + 26)
    var currentLetter = ""
    contacts.forEachIndexed { index, contact ->
        val letter = contact.name.take(1)
        if (letter != currentLetter) {
            rows += ContactRow.Header(letter)
            currentLetter = letter
        }
        rows += ContactRow.Person(contact, index)
    }
    return rows
}

// ---------------------------------------------------------------------------------------------
// Photos: a grid with month headers, newest first
// ---------------------------------------------------------------------------------------------

internal sealed interface PhotoEntry {
    val monthLabel: String

    class MonthHeader(override val monthLabel: String, val title: String, val count: Int) : PhotoEntry
    class Photo(override val monthLabel: String, val seed: Int) : PhotoEntry
}

internal fun buildPhotoLibrary(months: Int = 48): List<PhotoEntry> {
    val random = Random(11)
    val entries = ArrayList<PhotoEntry>()
    var month = DemoToday.copy(day = 1)
    var seed = 0
    repeat(months) {
        val count = random.nextInt(24, 180)
        entries += PhotoEntry.MonthHeader(month.monthYearShort, month.monthYearLong, count)
        repeat(count) { entries += PhotoEntry.Photo(month.monthYearShort, seed++) }
        month = month.previousMonth()
    }
    return entries
}

// ---------------------------------------------------------------------------------------------
// Chat: newest first (index 0), for a reversed list. Each day ends with its date separator.
// ---------------------------------------------------------------------------------------------

internal sealed interface ChatItem {
    val dayLabel: String

    class Message(override val dayLabel: String, val text: String, val fromMe: Boolean, val time: String) : ChatItem
    class DaySeparator(override val dayLabel: String) : ChatItem
}

internal fun buildChatHistory(days: Int = 160): List<ChatItem> {
    val random = Random(3)
    val items = ArrayList<ChatItem>()
    var date = DemoToday
    repeat(days) { daysAgo ->
        val label = when (daysAgo) {
            0 -> "Today"
            1 -> "Yesterday"
            else -> date.shortLabel
        }
        val messageCount = random.nextInt(2, 14)
        // Times run backwards from the evening, because index 0 is the newest message.
        var minutes = random.nextInt(19 * 60, 23 * 60)
        repeat(messageCount) {
            val time = "${(minutes / 60).toString().padStart(2, '0')}:${(minutes % 60).toString().padStart(2, '0')}"
            items += ChatItem.Message(label, ChatLines.random(random), fromMe = random.nextBoolean(), time = time)
            minutes = (minutes - random.nextInt(1, 50)).coerceAtLeast(8 * 60)
        }
        items += ChatItem.DaySeparator(label)
        date = date.previousDay()
    }
    return items
}

// ---------------------------------------------------------------------------------------------
// Music: a playlist and an album shelf
// ---------------------------------------------------------------------------------------------

internal class Track(val number: Int, val title: String, val artist: String, val duration: String, val hue: Float)

internal fun buildPlaylist(count: Int = 600): List<Track> {
    val random = Random(5)
    return List(count) { index ->
        val seconds = random.nextInt(130, 330)
        Track(
            number = index + 1,
            title = "${Words1.random(random)} ${Words2.random(random)}",
            artist = Artists.random(random),
            duration = "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}",
            hue = hueFor(index),
        )
    }
}

internal class Album(val title: String, val artist: String, val year: Int, val hue: Float, val shape: Int)

/** Newest first, a few albums per year. */
internal fun buildAlbums(count: Int = 320): List<Album> {
    val random = Random(9)
    return List(count) { index ->
        Album(
            title = "${Words1.random(random)} ${Words2.random(random)}",
            artist = Artists.random(random),
            year = 2026 - index / 8,
            hue = hueFor(index * 3 + 1),
            shape = random.nextInt(3),
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Notes, for the movable bar
// ---------------------------------------------------------------------------------------------

internal class Note(val title: String, val body: String, val date: String)

internal fun buildNotes(count: Int = 300): List<Note> {
    val random = Random(13)
    var date = DemoToday
    return List(count) {
        val note = Note(NoteTitles.random(random), NoteLines.random(random), date.shortLabel)
        repeat(random.nextInt(0, 3)) { date = date.previousDay() }
        note
    }
}
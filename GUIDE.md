# Compose Fast Scrollbar guide

Everything you might want to do with the scrollbar, one task per section. Each section explains when you'd want it and shows the code. Most have a short video of the result.

New here? Start with [Add a scrollbar to a list or grid](#add-a-scrollbar-to-a-list-or-grid). The rest can be read in any order.

## Contents

1. [Add a scrollbar to a list or grid](#add-a-scrollbar-to-a-list-or-grid)
2. [Show where the user will land](#show-where-the-user-will-land)
3. [Infinite feeds, chats and paging](#infinite-feeds-chats-and-paging)
4. [Stay clear of system bars, padding and gestures](#stay-clear-of-system-bars-padding-and-gestures)
5. [Change the look](#change-the-look)
6. [Use your own icon as the thumb](#use-your-own-icon-as-the-thumb)
7. [Add a visible track](#add-a-visible-track)
8. [Different looks per platform](#different-looks-per-platform)
9. [Let users move the bar](#let-users-move-the-bar)
10. [Horizontal lists](#horizontal-lists)
11. [Mouse and desktop](#mouse-and-desktop)
12. [Plain Columns and Rows](#plain-columns-and-rows)
13. [Accessibility](#accessibility)
14. [API reference](#api-reference)
15. [Troubleshooting](#troubleshooting)

---

## Add a scrollbar to a list or grid

https://github.com/user-attachments/assets/7fb26ed4-3fd9-4ae3-b652-81d0e929feb3

Add the dependency to `commonMain` (or to your app module in a plain Android project):

```kotlin
dependencies {
    implementation("io.github.amaanprobably:compose-fast-scrollbar:0.2.1")
}
```

Then apply `Modifier.scrollbar` to the lazy layout itself, and pass it the same state the layout uses.

**Lists** (`LazyColumn`, `LazyRow`) take a `LazyListState`:

```kotlin
val listState = rememberLazyListState()

LazyColumn(
    state = listState,
    modifier = Modifier.fillMaxSize().scrollbar(listState),
) {
    items(contacts, key = { it.id }) { contact -> ContactRow(contact) }
}
```

**Grids** (`LazyVerticalGrid`, `LazyHorizontalGrid`) take a `LazyGridState`:

```kotlin
val gridState = rememberLazyGridState()

LazyVerticalGrid(
    columns = GridCells.Adaptive(minSize = 110.dp),
    state = gridState,
    modifier = Modifier.fillMaxSize().scrollbar(gridState),
) {
    items(photos, key = { it.id }) { photo -> PhotoTile(photo) }
}
```

A few things that are easy to get wrong:

- **Put it on the list, not on a parent.** The scrollbar draws over the list's own bounds and only takes touches that drag or hold the thumb. A quick tap on the thumb still reaches the item under it, and everything else goes straight to the list.
- **Use the same state object** you pass to `state =`. A second `rememberLazyListState()` would track a list that doesn't exist.
- **Short lists hide the bar.** It stays hidden when the list can't scroll, or has fewer than `minItemsToShow` items (10 by default). Fast scrolling only pays off on long lists, so raise it for lists that are usually short:

  ```kotlin
  Modifier.scrollbar(listState, minItemsToShow = 50)
  ```

### All parameters

The list and grid overloads take the same parameters. The `ScrollState` overload is covered in [Plain Columns and Rows](#plain-columns-and-rows).

| Parameter                 | Type                              | Default                     | Use it when                                                                                                           |
|---------------------------|-----------------------------------|-----------------------------|-----------------------------------------------------------------------------------------------------------------------|
| `listState` / `gridState` | `LazyListState` / `LazyGridState` | required                    | Always: the same state the list uses.                                                                                 |
| `edge`                    | `ScrollbarEdge`                   | `ScrollbarEdge.End`         | You want the bar on the other side. See [Horizontal lists](#horizontal-lists) for rows.                               |
| `contentPadding`          | `PaddingValues`                   | `PaddingValues(0.dp)`       | The list has padding or draws behind system bars. See [system bars](#stay-clear-of-system-bars-padding-and-gestures). |
| `minItemsToShow`          | `Int`                             | `10`                        | You want the bar only on long lists.                                                                                  |
| `hasMore`                 | `Boolean`                         | `false`                     | The list loads more as you scroll. See [infinite feeds](#infinite-feeds-chats-and-paging).                            |
| `style`                   | `ScrollbarStyle`                  | `ScrollbarDefaults.style()` | You want a different look, size, color, visibility or timing. See [Change the look](#change-the-look).                |
| `label`                   | `ScrollbarLabel?`                 | `null`                      | You want a bubble while dragging. See [Show where the user will land](#show-where-the-user-will-land).                |
| `scrollToStartLabel`      | `String?`                         | `null`                      | You want a screen-reader action to jump to the start. See [Accessibility](#accessibility).                            |
| `scrollToEndLabel`        | `String?`                         | `null`                      | You want a screen-reader action to jump to the end.                                                                   |
| `onEdgeChange`            | `((ScrollbarEdge) -> Unit)?`      | `null`                      | Users should be able to move the bar. See [Let users move the bar](#let-users-move-the-bar).                          |

---

## Show where the user will land

https://github.com/user-attachments/assets/5ad4ff2b-640f-43bc-8442-8dcbb010cf9a

A label bubble follows the thumb while it's dragged and shows where the list will land. Pass a `ScrollbarLabel`. Its lambda gets the index of the item the drag is jumping to, and returns the text to show, or `null` to hide the bubble.

### Which kind of label

| Your list                            | Label                           | `sectionHaptics`                |
|--------------------------------------|---------------------------------|---------------------------------|
| Contacts, songs, anything sorted A–Z | The first letter                | `true` (one tick per letter)    |
| Photos, messages, events             | The month, or the day           | `true` (one tick per month)     |
| Numbered items, search results       | "Item 1234", or a position      | `false`, or it ticks constantly |
| Mixed content with headers           | The section the item belongs to | `true`                          |

### Sections (letters, months)

Return the same text for every item in a section. The device ticks once each time the text changes, so you get one tick per section:

```kotlin
// Work out the labels once, not inside the lambda.
val initials = remember(contacts) {
    contacts.map { it.name.first().uppercaseChar().toString() }
}

LazyColumn(
    state = listState,
    modifier = Modifier.scrollbar(
        listState,
        label = ScrollbarLabel { index -> initials.getOrNull(index) },
    ),
) {
    items(contacts, key = { it.id }) { ContactRow(it) }
}
```

Dates work the same way. Format them ahead of time:

```kotlin
val months = remember(photos) {
    photos.map { photo -> formatMonth(photo.takenOn) } // your own formatting, e.g. "Aug 2024"
}

Modifier.scrollbar(gridState, label = ScrollbarLabel { index -> months.getOrNull(index) })
```

### Per-item labels

When every item has its own text, turn the haptics off:

```kotlin
Modifier.scrollbar(
    listState,
    label = ScrollbarLabel(sectionHaptics = false) { index -> "#${index + 1}" },
)
```

### Lists with headers

If your list mixes headers and rows, the index counts every item, headers included. Keep a parallel list of section names in the same order as your items:

```kotlin
val sectionOfItem: List<String> = remember(groups) {
    groups.flatMap { group -> List(group.rows.size + 1) { group.title } } // +1 for the header
}

Modifier.scrollbar(listState, label = ScrollbarLabel { index -> sectionOfItem.getOrNull(index) })
```

### Keep the lambda cheap

The lambda runs on the main thread whenever the drag moves to a different item, which can be many times per second. Look values up, don't compute them: no formatting, sorting or database reads inside it.

### Style the bubble

```kotlin
ScrollbarLabel(
    style = ScrollbarLabelStyle(
        textStyle = MaterialTheme.typography.titleMedium,
        textColor = MaterialTheme.colorScheme.onPrimary,
        bubbleColor = MaterialTheme.colorScheme.primary,
        cornerRadius = 20.dp,
        gap = 12.dp, // distance between the thumb and the bubble
    ),
) { index -> initials.getOrNull(index) }
```

All options are in [`ScrollbarLabelStyle`](#scrollbarlabelstyle).

On desktop, hovering the track with a mouse previews the label for that spot before you click. See [Mouse and desktop](#mouse-and-desktop).

---

## Infinite feeds, chats and paging

https://github.com/user-attachments/assets/48e8bc28-5abe-4149-b28b-e8f2c2445482

### Feeds that load more as you scroll

A feed that loads pages doesn't know where it ends. Tell the scrollbar with `hasMore`:

```kotlin
val listState = rememberLazyListState()
val posts by viewModel.posts.collectAsState()
val endReached by viewModel.endReached.collectAsState()

LazyColumn(
    state = listState,
    modifier = Modifier.scrollbar(listState, hasMore = !endReached),
) {
    items(posts, key = { it.id }) { PostCard(it) }
}
```

While `hasMore` is `true`:

- The thumb stops short of the very end, so it never claims you've reached the bottom of an unfinished list.
- Dragging to the bottom jumps to the last loaded item, which is usually what triggers your next page.
- If a page loads while the user is still dragging, the drag carries on into the new items.

When `hasMore` becomes `false`, the thumb glides to the true end.

If the user holds the thumb at the end, the drag keeps going into each page as it loads, until they let go or `hasMore` becomes `false`. Each page still loads through your own pagination, one at a time, exactly as if the user kept scrolling to the bottom.

### Chats (reversed lists)

Chats usually use `reverseLayout = true`, so the newest message sits at the bottom. The scrollbar follows the layout direction on its own. Use `hasMore` for older messages that are still loading:

```kotlin
LazyColumn(
    state = listState,
    reverseLayout = true,
    modifier = Modifier.scrollbar(listState, hasMore = olderMessagesAvailable),
) {
    items(messages, key = { it.id }) { MessageBubble(it) }
}
```

### Items added while the user drags

New items at the top (a new message, a refreshed feed) or pages appended at the bottom change the item count. The thumb glides to its new position instead of snapping, and stays under the user's finger mid-drag. Give your items stable `key`s so the list keeps its place when items are inserted.

With keys, the list keeps its place even at the very top: items inserted above the first visible item stay out of view, and the thumb moves down to show how much is now above. That's standard `LazyColumn` behavior, not the scrollbar. To show new items instead when the user is already at the top (common in feeds), scroll there yourself:

```kotlin
LaunchedEffect(posts.size) {
    if (listState.firstVisibleItemIndex == 0) listState.scrollToItem(0)
}
```

### Paging with placeholders

If you use Paging with placeholders turned on, the list already knows its full size, so you don't need `hasMore`. With placeholders off, pass `hasMore = true` until the last page has loaded.

---

## Stay clear of system bars, padding and gestures

https://github.com/user-attachments/assets/6020fa97-ef11-4d9f-b312-928b86e9361f

### Lists with padding

If your list has `contentPadding`, pass the same padding to the scrollbar so the track stays inside it:

```kotlin
val padding = PaddingValues(vertical = 16.dp)

LazyColumn(
    state = listState,
    contentPadding = padding,
    modifier = Modifier.scrollbar(listState, contentPadding = padding),
) { /* ... */ }
```

Only the padding along the scroll axis is used: top and bottom for a vertical list. To move the bar away from the side edge, use `edgePadding` in the style (below).

### Edge-to-edge screens

For a list that draws behind the status and navigation bars, pass the system bar insets the same way:

```kotlin
val insets = WindowInsets.systemBars.asPaddingValues()

LazyColumn(
    state = listState,
    contentPadding = insets,
    modifier = Modifier.scrollbar(listState, contentPadding = insets),
) { /* ... */ }
```

### Gesture navigation

You don't need to do anything for Android's gesture navigation:

- The visible thumb is excluded from the back gesture, so grabbing it never sends the user back.
- If the list reaches the top or bottom of the screen, the track stays out of the system gesture zones there, and a horizontal bar is lifted above the bottom one.
- The larger of your padding and the gesture zone is used, not the sum, so passing insets as `contentPadding` never pushes the track in twice.

iOS has no way for apps to reserve part of a screen edge. A bar on the left edge can compete with the swipe-back gesture there, so keep the default right-hand side on iOS unless users move it themselves.

### Moving the bar inward or along the track

| You want                                     | Style parameter |
|----------------------------------------------|-----------------|
| More space between the bar and the side edge | `edgePadding`   |
| More space at both ends of the track         | `trackPadding`  |

```kotlin
Modifier.scrollbar(
    listState,
    style = ScrollbarDefaults.style(edgePadding = 8.dp, trackPadding = 24.dp),
)
```

---

## Change the look

https://github.com/user-attachments/assets/36539602-bb7a-4c85-a449-80a6bdb2fa29

Everything about the look lives in a `ScrollbarStyle`, created with `ScrollbarDefaults.style(...)`. Pass only what you want to change; everything else keeps the platform default.

### Create the style once

`style()` is a plain function, so build the style once and reuse it instead of creating it on every recomposition:

```kotlin
// Top level, for a fixed look
val appScrollbarStyle = ScrollbarDefaults.style(
    thumbColor = Color(0x99888888),
    pressedThumbColor = Color(0xFF3D7BFF),
)
```

When it reads theme colors, remember it with the colors as keys:

```kotlin
val colors = MaterialTheme.colorScheme
val scrollbarStyle = remember(colors) {
    ScrollbarDefaults.style(
        thumbColor = colors.onSurface.copy(alpha = 0.5f),
        pressedThumbColor = colors.primary,
        capsuleColor = colors.onSurface.copy(alpha = 0.12f),
    )
}

Modifier.scrollbar(listState, style = scrollbarStyle)
```

### Pick a thumb shape

| Look                        | What it is                                                       | Use it when                                                                                    |
|-----------------------------|------------------------------------------------------------------|------------------------------------------------------------------------------------------------|
| `ThumbAppearance.Capsule`   | A translucent capsule with a thin thumb inside. Android default. | You want the classic touch fast-scroller look.                                                 |
| `ThumbAppearance.Bar`       | One rounded bar that widens when held. iOS and desktop default.  | You want something minimal, or a native iOS feel on Android.                                   |
| `ThumbAppearance.Icon(...)` | Your own icon.                                                   | You want a branded or playful thumb. See [Use your own icon](#use-your-own-icon-as-the-thumb). |

```kotlin
ScrollbarDefaults.style(thumbAppearance = ThumbAppearance.Bar)
```

`ScrollbarDefaults.compactStyle()` is the full desktop look: a thin proportional bar with a mouse-sized grab area. It's hard to hit with a finger, so on touch screens prefer `style(thumbAppearance = ThumbAppearance.Bar)`, which keeps the finger-sized grab area.

### Sizes

```kotlin
ScrollbarDefaults.style(
    thumbThickness = 6.dp,          // at rest
    pressedThumbThickness = 10.dp,  // while held (Bar widens to it)
    touchTargetThickness = 48.dp,   // the invisible grab strip along the edge
    capsuleThickness = 28.dp,       // Capsule look only
)
```

Keep `touchTargetThickness` at 48 dp or more on touch screens. Touches in that strip that miss the thumb still reach the list, so a wide strip doesn't steal scrolls, and a quick tap on the thumb reaches the item under it.

### Visibility and timing

| You want                                                             | Set                                         |
|----------------------------------------------------------------------|---------------------------------------------|
| The bar to fade out after scrolling (Android, iOS and macOS default) | `visibility = ScrollbarVisibility.AutoHide` |
| The bar to stay longer before fading                                 | `hideDelay = 3.seconds`                     |
| The bar always on screen (Windows and Linux default)                 | `visibility = ScrollbarVisibility.Always`   |
| Faster or slower fades                                               | `fadeInDuration`, `fadeOutDuration`         |

```kotlin
ScrollbarDefaults.style(
    visibility = ScrollbarVisibility.Always,
)
```

Use `Always` for long reading screens, settings and desktop apps, where people like seeing where they are. Keep `AutoHide` for feeds and galleries, where the bar would cover content.

### Thumb length

| Option                          | Behavior                                                                                   | Use it when                                                                       |
|---------------------------------|--------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------|
| `ThumbSizing.Fixed(40.dp)`      | Constant length. The default on touch screens, and it never jitters.                       | Touch screens, especially with uneven items.                                      |
| `ThumbSizing.Proportional(...)` | Length follows viewport / content. The default on desktop, smoothed, with a 24 dp minimum. | Desktop, or same-size items where you want the length to hint at the list's size. |

```kotlin
ScrollbarDefaults.style(
    thumbSizing = ThumbSizing.Proportional(
        minLength = 48.dp,
        maxLengthFraction = 0.9f,
        smoothed = true, // animates size changes instead of snapping
    ),
)
```

Lazy lists don't know their true content size, so a proportional thumb changes length as items of different sizes scroll in. `smoothed` hides most of that, but `Fixed` avoids it entirely.

---

## Use your own icon as the thumb

https://github.com/user-attachments/assets/aa80c810-5b37-4a0e-9ad8-e652538bcb6c

Replace the capsule or bar with any `Painter`: a logo, a mascot, a character.

```kotlin
Modifier.scrollbar(
    listState,
    style = ScrollbarDefaults.style(
        thumbAppearance = ThumbAppearance.Icon(
            idle = painterResource(Res.drawable.logo),
            size = DpSize(40.dp, 40.dp),
        ),
    ),
)
```

### A different icon while held

```kotlin
ThumbAppearance.Icon(
    idle = painterResource(Res.drawable.logo),
    pressed = painterResource(Res.drawable.logo_active),
    size = DpSize(44.dp, 44.dp),
    pressedScale = 1.15f, // grows a little while held; 1f turns it off
)
```

The pressed icon fades in when the thumb is grabbed and out on release.

### One-color icons

Multicolor logos keep their own colors by default. For a one-color vector, recolor it with `tint`:

```kotlin
ThumbAppearance.Icon(
    idle = rememberVectorPainter(Icons.Rounded.DragIndicator),
    size = DpSize(32.dp, 32.dp),
    tint = MaterialTheme.colorScheme.primary,
)
```

### Things to know

- `size` is required, because an icon's own size is ignored. Along the scroll axis it's the thumb's length, so `thumbSizing`, the thicknesses, the colors and the capsule settings don't apply to icon thumbs.
- The whole icon is grabbable, and the touch target is never smaller than `touchTargetThickness`.
- The icon is never rotated or mirrored. For right-to-left layouts, set `autoMirror` on a vector if it should flip.
- Use remembered painters (`painterResource` and `rememberVectorPainter` already are). A painter created on every recomposition gets replaced mid-scroll.
- Animated painters might work, but they aren't tested yet.

### From an SVG

Import the SVG as a vector drawable (Android Studio → New → Vector Asset), put it in `composeResources/drawable/`, and load it with `painterResource(Res.drawable.your_icon)`. PNGs work the same way.

---

## Add a visible track

https://github.com/user-attachments/assets/61289f76-b153-43b6-90e0-814a07049650

The track is the path the thumb moves along. It's invisible by default. Give it a look with `trackAppearance`.

### A plain line

```kotlin
ScrollbarDefaults.style(
    trackAppearance = TrackAppearance.Line(
        color = Color(0x33808080),
        thickness = 2.dp,
        extent = TrackExtent.Full,
    ),
)
```

### Your own drawing

`TrackAppearance.Image` takes any painter. Two choices decide how it fills the track:

| Choice   | Option      | What it does                                                                  | Good for                             |
|----------|-------------|-------------------------------------------------------------------------------|--------------------------------------|
| `fit`    | `Stretch`   | One copy, stretched to the track's length.                                    | Ropes, webs, poles, lines            |
| `fit`    | `Repeat`    | Copies end to end, keeping their proportions.                                 | Ladders, chains, brick towers        |
| `extent` | `Full`      | The whole track.                                                              | Rails and ladders the thumb rides on |
| `extent` | `UpToThumb` | From the start of the list to the middle of the thumb, growing as you scroll. | A rope the thumb hangs from          |

A rope the thumb hangs from:

```kotlin
ScrollbarDefaults.style(
    thumbAppearance = ThumbAppearance.Icon(
        idle = painterResource(Res.drawable.thief),
        size = DpSize(40.dp, 40.dp),
    ),
    trackAppearance = TrackAppearance.Image(
        painter = painterResource(Res.drawable.rope),
        thickness = 10.dp,
        extent = TrackExtent.UpToThumb,
        fit = TrackFit.Stretch,
    ),
)
```

A ladder the thumb climbs:

```kotlin
ScrollbarDefaults.style(
    thumbAppearance = ThumbAppearance.Icon(
        idle = painterResource(Res.drawable.firefighter),
        size = DpSize(44.dp, 44.dp),
    ),
    trackAppearance = TrackAppearance.Image(
        painter = painterResource(Res.drawable.ladder_rung),
        thickness = 28.dp,
        extent = TrackExtent.Full,
        fit = TrackFit.Repeat,
        tileLength = 24.dp, // length of one rung; leave it out to keep the drawing's proportions
    ),
)
```

### Things to know

- It's drawn under the thumb, centered on it, and fades in and out with the bar. It's only decoration, so touches behave exactly as without it.
- It's never rotated. Draw it upright for vertical lists and sideways for horizontal ones.
- On `reverseLayout` lists, `UpToThumb` grows from the bottom, where the list starts.
- A track wider than the space between the thumb and the list's edge is moved inward, so it never draws outside the list.
- `tint` recolors it, the same way as for icons.

---

## Different looks per platform

https://github.com/user-attachments/assets/e29dc856-bf78-4d82-8a1c-1a4b3736a9f9

By default, the scrollbar already follows each platform:

| Value                   | Android    | iOS        | Desktop (and `compactStyle`)                  |
|-------------------------|------------|------------|-----------------------------------------------|
| Look                    | `Capsule`  | `Bar`      | `Bar`                                         |
| `thumbThickness`        | 5 dp       | 3 dp       | 8 dp                                          |
| `pressedThumbThickness` | 8 dp       | 8 dp       | 12 dp                                         |
| Thumb length            | 40 dp      | 40 dp      | Proportional, min 24 dp                       |
| Visibility              | `AutoHide` | `AutoHide` | `Always` (Windows, Linux), `AutoHide` (macOS) |
| `touchTargetThickness`  | 48 dp      | 48 dp      | 16 dp                                         |
| `trackPadding`          | 8 dp       | 6 dp       | 4 dp                                          |
| `edgePadding`           | 4 dp       | 3 dp       | 2 dp                                          |
| `capsuleThickness`      | 24 dp      | 24 dp      | 16 dp                                         |
| `capsuleExtraLength`    | 24 dp      | 24 dp      | 8 dp                                          |

To give each platform its own style from shared code, without `expect`/`actual` or platform checks, use `platformStyle`. Each argument is a full style, so anything can differ. Pass only the platforms you want to change:

```kotlin
val scrollbarStyle = ScrollbarDefaults.platformStyle(
    android = ScrollbarDefaults.style(
        thumbAppearance = ThumbAppearance.Bar,
        visibility = ScrollbarVisibility.Always,
    ),
    ios = ScrollbarDefaults.style(
        thumbAppearance = ThumbAppearance.Icon(idle = logoPainter, size = DpSize(32.dp, 32.dp)),
    ),
    desktop = ScrollbarDefaults.compactStyle(
        thumbSizing = ThumbSizing.Fixed(),
        hideDelay = 3.seconds,
    ),
)
```

---

## Let users move the bar

https://github.com/user-attachments/assets/41df05fe-3580-4771-b69b-0c97551c55ba

Left-handed and one-handed users often prefer the bar on the other side. Pass `onEdgeChange` to let them move it:

```kotlin
var edge by rememberSaveable { mutableStateOf(ScrollbarEdge.End) }

Modifier.scrollbar(
    listState,
    edge = edge,
    onEdgeChange = { edge = it },
)
```

How it works for the user: hold the thumb still for half a second (or the system long-press time, if that's longer), and a small two-way arrow appears. Drag across, let go, and the bar snaps to the nearest edge.

Treat the edge like any other setting. To keep it after the app restarts, store it in your preferences:

```kotlin
val edge by settings.scrollbarEdge.collectAsState(initial = ScrollbarEdge.End)

Modifier.scrollbar(
    listState,
    edge = edge,
    onEdgeChange = { newEdge -> scope.launch { settings.setScrollbarEdge(newEdge) } },
)
```

It's touch only, and off unless you pass `onEdgeChange`. People won't guess the long press on their own, so mention it somewhere, such as a settings toggle or a one-time tip.

---

## Horizontal lists

https://github.com/user-attachments/assets/d41fd018-3997-49a3-8bb8-bd766598c96f

`LazyRow` and `LazyHorizontalGrid` work with the same modifier. On horizontal lists, `edge` means top or bottom:

| `edge`                | Vertical list                             | Horizontal list |
|-----------------------|-------------------------------------------|-----------------|
| `ScrollbarEdge.Start` | Leading side (left in LTR, right in RTL)  | Top             |
| `ScrollbarEdge.End`   | Trailing side (right in LTR, left in RTL) | Bottom          |

```kotlin
val rowState = rememberLazyListState()

LazyRow(
    state = rowState,
    contentPadding = PaddingValues(horizontal = 16.dp),
    modifier = Modifier.scrollbar(
        rowState,
        contentPadding = PaddingValues(horizontal = 16.dp),
        label = ScrollbarLabel { index -> albums.getOrNull(index)?.year?.toString() },
    ),
) {
    items(albums, key = { it.id }) { AlbumCard(it) }
}
```

For a horizontal list, `contentPadding` uses the start and end padding. Right-to-left layouts are handled automatically.

---

## Mouse and desktop

https://github.com/user-attachments/assets/74f9a4c8-0392-4a7f-b869-2a88df90c067

With a mouse (on desktop, or a mouse connected to Android or an iPad):

- Hovering near the bar reveals it and highlights the thumb. The `Bar` look widens halfway to `pressedThumbThickness`.
- Hovering the track previews the [label](#show-where-the-user-will-land) for that spot.
- Clicking the track jumps there.
- Dragging works as with a finger.
- Mouse wheel and trackpad scrolling always reach the list.

Touch behavior isn't affected by any of this. On desktop the default style is already the compact, mouse-sized bar: proportional, and always visible on Windows and Linux like the system's own scrollbars. To keep it visible on macOS too:

```kotlin
ScrollbarDefaults.style(visibility = ScrollbarVisibility.Always)
```

---

## Plain Columns and Rows

https://github.com/user-attachments/assets/33f1168d-84b8-4934-adf2-299713d58d1d

For content that isn't a lazy list (articles, settings screens, terms, long forms), pass the `ScrollState` of a `Column` with `verticalScroll` or a `Row` with `horizontalScroll`:

```kotlin
val scrollState = rememberScrollState()

Column(
    Modifier
        .fillMaxSize()
        .scrollbar(scrollState)
        .verticalScroll(scrollState)
) { /* long content */ }
```

**Put `scrollbar` before `verticalScroll`**, on the same element. Placed after it, the bar sits inside the scrolled content and scrolls away with it.

A scroll state knows the full size of its content, so positions are exact, unlike the estimate lazy lists need.

### Horizontal and reversed

The scroll state doesn't know its direction, so pass the same values the scroll modifier uses:

```kotlin
Row(
    Modifier
        .scrollbar(scrollState, orientation = Orientation.Horizontal)
        .horizontalScroll(scrollState)
) { /* ... */ }

Column(
    Modifier
        .scrollbar(scrollState, reverseScrolling = true)
        .verticalScroll(scrollState, reverseScrolling = true)
) { /* ... */ }
```

### Labels

The label lambda gets the **scroll offset in pixels** the drag is jumping to, not an item index. Map it to your own sections, for example the chapter or page at that offset:

```kotlin
// Where each page starts, filled in by layout (onPlaced { positionInParent() }).
val pageTops = remember { IntArray(pageCount) }

val label = remember {
    ScrollbarLabel { offset ->
        // Binary search: the lambda runs on almost every pixel of a drag.
        var low = 0
        var high = pageTops.size
        while (low < high) {
            val middle = (low + high) / 2
            if (pageTops[middle] <= offset) low = middle + 1 else high = middle
        }
        "Page ${low.coerceAtLeast(1)}"
    }
}

Modifier.scrollbar(scrollState, label = label)
```

With `sectionHaptics` (on by default), it ticks once per page, because ticks only fire when the text changes.

### Parameters

The same as the list overload, minus `hasMore` and `minItemsToShow` (there are no items or pages to load; the bar hides whenever the content fits), plus:

| Parameter          | Type          | Default                | Use it when                                         |
|--------------------|---------------|------------------------|-----------------------------------------------------|
| `orientation`      | `Orientation` | `Orientation.Vertical` | The content scrolls horizontally.                   |
| `reverseScrolling` | `Boolean`     | `false`                | The scroll modifier uses `reverseScrolling = true`. |

---

## Accessibility

Screen-reader users can't drag a thumb. Give them actions to jump to either end instead:

```kotlin
Modifier.scrollbar(
    listState,
    scrollToStartLabel = stringResource(Res.string.scroll_to_top),
    scrollToEndLabel = stringResource(Res.string.scroll_to_bottom),
)
```

Each label adds a custom accessibility action on the list, with that label as its name. No action is added when a label is `null`. Use localized strings, since screen readers read them out.

Fuller TalkBack and VoiceOver support and keyboard support on desktop are on the roadmap.

---

## API reference

Every public type, parameter and default.

### `ScrollbarDefaults`

| Function                               | Returns                                                                                                  |
|----------------------------------------|----------------------------------------------------------------------------------------------------------|
| `style(...)`                           | A style with the platform's look: capsule on Android, thin bar on iOS, compact bar on desktop.           |
| `compactStyle(...)`                    | The desktop look on every platform: thin bar, mouse-sized grab area. Same parameters as `style`.         |
| `platformStyle(android, ios, desktop)` | One of the three styles, picked for the platform it runs on. Each defaults to that platform's `style()`. |

### Style parameters

All parameters of `style(...)` and `compactStyle(...)`. "Platform" means the value from the [platform table](#different-looks-per-platform).

| Parameter               | Type                  | Default                    | Description                                                                                       |
|-------------------------|-----------------------|----------------------------|---------------------------------------------------------------------------------------------------|
| `thumbAppearance`       | `ThumbAppearance`     | platform                   | `Capsule`, `Bar` or `Icon(...)`.                                                                  |
| `thumbSizing`           | `ThumbSizing`         | platform                   | `Fixed(length)` or `Proportional(...)`.                                                           |
| `thumbThickness`        | `Dp`                  | platform                   | Thumb width (height for horizontal bars) at rest.                                                 |
| `pressedThumbThickness` | `Dp`                  | platform                   | Thumb width while held. The bar look widens to it, and halfway to it on mouse hover.              |
| `touchTargetThickness`  | `Dp`                  | platform                   | Width of the grab strip along the edge. Touches in it that miss the thumb still reach the list.   |
| `trackPadding`          | `Dp`                  | platform                   | Space kept free at both ends of the track, along the scroll axis.                                 |
| `edgePadding`           | `Dp`                  | platform                   | Space between the bar and the list's edge.                                                        |
| `capsuleThickness`      | `Dp`                  | platform                   | Width of the capsule around the thumb (`Capsule` look only).                                      |
| `capsuleExtraLength`    | `Dp`                  | platform                   | How much longer the capsule is than the thumb, split between both ends (`Capsule` look only).     |
| `visibility`            | `ScrollbarVisibility` | platform                   | `AutoHide` (fades out after scrolling stops) or `Always`.                                         |
| `hideDelay`             | `Duration`            | `1.5.seconds`              | How long the bar stays after scrolling stops (`AutoHide` only).                                   |
| `fadeInDuration`        | `Duration`            | `150.milliseconds`         | Fade-in time.                                                                                     |
| `fadeOutDuration`       | `Duration`            | `300.milliseconds`         | Fade-out time.                                                                                    |
| `thumbColor`            | `Color`               | `Color(0x80808080)`        | Thumb color at rest.                                                                              |
| `pressedThumbColor`     | `Color`               | `Color(0xFF2196F3)`        | Thumb color while held.                                                                           |
| `capsuleColor`          | `Color`               | `Color(0x2E808080)`        | Capsule color (`Capsule` look only).                                                              |
| `trackAppearance`       | `TrackAppearance`     | `TrackAppearance.None`     | An optional visible track.                                                                        |

### `ThumbAppearance.Icon`

| Parameter      | Type      | Default             | Description                                                      |
|----------------|-----------|---------------------|------------------------------------------------------------------|
| `idle`         | `Painter` | required            | Shown at rest and after release.                                 |
| `size`         | `DpSize`  | required            | The icon's size. Along the scroll axis, it's the thumb's length. |
| `pressed`      | `Painter` | `idle`              | Faded in while the thumb is held.                                |
| `tint`         | `Color`   | `Color.Unspecified` | Recolors the icon. Unspecified keeps its own colors.             |
| `pressedScale` | `Float`   | `1.1f`              | Grows the icon while held. `1f` turns it off.                    |

### `ThumbSizing`

| Option                                                                       | Description                                                                                                                                                                                           |
|------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `Fixed(length = 40.dp)`                                                      | Constant length. The default on touch screens, and it never jitters.                                                                                                                                  |
| `Proportional(minLength = 48.dp, maxLengthFraction = 0.9f, smoothed = true)` | Length follows viewport / content, between `minLength` and `maxLengthFraction` of the track. `smoothed` animates changes in the estimated content size. The desktop default uses `minLength = 24.dp`. |

### `TrackAppearance`

`Line` parameters:

| Parameter   | Type          | Default             | Description                        |
|-------------|---------------|---------------------|------------------------------------|
| `color`     | `Color`       | `Color(0x33808080)` | Line color.                        |
| `thickness` | `Dp`          | `2.dp`              | Line width across the scroll axis. |
| `extent`    | `TrackExtent` | `Full`              | `Full` or `UpToThumb`.             |

`Image` parameters:

| Parameter    | Type          | Default             | Description                                                                              |
|--------------|---------------|---------------------|------------------------------------------------------------------------------------------|
| `painter`    | `Painter`     | required            | The drawing. Draw it upright for vertical lists, sideways for horizontal ones.           |
| `thickness`  | `Dp`          | required            | Its size across the scroll axis.                                                         |
| `extent`     | `TrackExtent` | `Full`              | `Full`: the whole track. `UpToThumb`: from the list's start to the middle of the thumb.  |
| `fit`        | `TrackFit`    | `Stretch`           | `Stretch`: one copy, stretched. `Repeat`: copies end to end, keeping their proportions.  |
| `tileLength` | `Dp`          | `Dp.Unspecified`    | With `Repeat`, the length of one copy. Unspecified keeps the painter's own proportions.  |
| `tint`       | `Color`       | `Color.Unspecified` | Recolors it. Unspecified keeps its own colors.                                           |

### `ScrollbarLabel`

```kotlin
ScrollbarLabel(sectionHaptics = true, style = ScrollbarLabelStyle()) { itemIndex -> "A" }
```

| Parameter        | Type                          | Default                    | Description                                                                            |
|------------------|-------------------------------|----------------------------|----------------------------------------------------------------------------------------|
| `sectionHaptics` | `Boolean`                     | `true`                     | Ticks whenever the text changes. Turn it off for per-item labels.                      |
| `style`          | `ScrollbarLabelStyle`         | `ScrollbarLabelStyle()`    | The bubble's look.                                                                     |
| `provider`       | `(itemIndex: Int) -> String?` | required (trailing lambda) | Text for the item the drag is jumping to, or `null` to hide the bubble. Keep it cheap. |

### `ScrollbarLabelStyle`

| Parameter           | Type        | Default                                            | Description                                |
|---------------------|-------------|----------------------------------------------------|--------------------------------------------|
| `textStyle`         | `TextStyle` | `TextStyle(fontSize = 18.sp, fontWeight = Medium)` | The label text.                            |
| `textColor`         | `Color`     | `Color.White`                                      | Text color.                                |
| `bubbleColor`       | `Color`     | `Color(0xE6212121)`                                | Bubble background.                         |
| `cornerRadius`      | `Dp`        | `14.dp`                                            | Bubble corner radius.                      |
| `horizontalPadding` | `Dp`        | `14.dp`                                            | Space left and right of the text.          |
| `verticalPadding`   | `Dp`        | `8.dp`                                             | Space above and below the text.            |
| `gap`               | `Dp`        | `8.dp`                                             | Distance between the thumb and the bubble. |

---

## Troubleshooting

**The bar doesn't show.**
It only shows when the list can scroll and has at least `minItemsToShow` items (10 by default). With `AutoHide`, it also stays hidden until the list is scrolled. Check that the modifier is on the lazy layout itself, and that you passed the same state object the layout uses.

**The bar scrolls away with my Column.**
`scrollbar` is chained after `verticalScroll`, so it's inside the scrolled content. Move it before: `.scrollbar(scrollState).verticalScroll(scrollState)`.

**The list lands a little off the thumb.**
Lazy lists only know the size of items they've measured, so dragging maps the thumb to an item using the average item size. With uniform items it's exact. With very different item sizes the list can land slightly off. Grids estimate items per line from the visible items, so grids with full-span headers or uneven spans can be off too.

**The position shifts on short lists.**
Fast scrolling pays off on lists at least 4 screens tall (roughly 50+ items), like Android's own fast scroller. On short lists with very uneven items, the estimate moves as items are measured. Raise `minItemsToShow` to hide the bar on lists like that.

**A proportional thumb keeps changing length.**
That's the estimated content size changing as items of different sizes scroll in. Keep `smoothed = true`, or use `ThumbSizing.Fixed` (the default on touch screens).

**A collapsing toolbar doesn't react to fast scrolling.**
Fast scrolling jumps the list straight to its target instead of scrolling through nested scroll, so `TopAppBar` scroll behaviors don't see it. Swiping the list still drives them as usual.

**Swiping back on iOS fights the bar.**
iOS can't reserve part of a screen edge for an app. Keep the bar on the right in left-to-right layouts. If users can move it with `onEdgeChange`, they may notice this on the left edge.

**The label ticks constantly.**
Your labels change on every item. Pass `ScrollbarLabel(sectionHaptics = false)`, or return one label per section.

**Dragging feels slow on a big list.**
Check your label lambda. It runs every time the drag reaches a new item, so it should only look values up. Work labels out ahead of time with `remember`, as in [Show where the user will land](#show-where-the-user-will-land).

Still stuck? [Open an issue](https://github.com/Amaanprobably/compose-fast-scrollbar/issues) with your platform, Compose version, list type and a short recording.

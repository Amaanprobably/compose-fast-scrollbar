# Compose Fast Scroller
[![CI](https://github.com/Amaanprobably/compose-fast-scroller/actions/workflows/ci.yml/badge.svg)](https://github.com/Amaanprobably/compose-fast-scroller/actions/workflows/ci.yml)

A draggable fast scrollbar for Compose Multiplatform lazy layouts. One modifier, no wrapper composable, works on Android, iOS and Desktop.

> **Status:** early development, not published yet. The full test suite passes on Android, Desktop and the iOS simulator; it hasn't been tried on a real iPhone yet.

## Features

- Drag the thumb to jump through long lists. Touches that miss the thumb pass straight through to the list.
- Works with `LazyColumn`, `LazyRow`, `LazyVerticalGrid` and `LazyHorizontalGrid`.
- Fixed-length thumb by default, with an optional proportional (and smoothed) thumb.
- `contentPadding` support, so the track stays clear of system bars and insets.
- RTL and `reverseLayout` support.
- Auto-hides after scrolling stops, with a smooth settle animation after you release the thumb. Or keep it always visible.
- Optional label bubble next to the thumb while dragging (a letter, date or section name), with a haptic tick when you cross into a new section.
- Fully styleable: thickness, colors, timings and edge placement.
- Use your own brand icon or logo as the thumb, with separate idle and dragging icons.
- Optional visible track with any look: a plain line, or your own drawing stretched or repeated along it. Show it full length, or only up to the thumb so the thumb hangs from it like a rope.
- Plays well with Android gesture navigation: the visible thumb is excluded from the back gesture, and the track stays out of the top and bottom system gesture zones, so grabbing it never sends you back or home.
- Mouse support (desktop, and mice on Android and iPad): hovering reveals the bar and highlights the thumb, hovering the track previews the label for that spot, and clicking the track jumps there. Wheel and trackpad scrolling always reach the list, and touch behavior is unchanged.
- No recomposition while scrolling or dragging. All state is read in the draw phase.

## Installation

Not published yet. Once it is:

```kotlin
// commonMain
dependencies {
    implementation("io.github.Amaanprobably:compose-fast-scroller:0.1.0")
}
```

> **0.x versions:** the API may still change between minor versions until 1.0. Breaking changes are listed in the [changelog](CHANGELOG.md).

**Plain Android apps (no Multiplatform):** the same line works in your app module. The library is built on Compose 1.12, so Gradle raises your app's Compose dependencies to at least 1.12.1 if they're older.

**Supported targets:** Android (minSdk 24), iOS (`iosArm64`, `iosSimulatorArm64`) and Desktop (JVM).

## Usage

Apply the modifier to the lazy layout itself, and pass the same state you give the list.

```kotlin
val listState = rememberLazyListState()

LazyColumn(
    state = listState,
    modifier = Modifier.scrollbar(listState),
) {
    items(1_000) { index -> Text("Item $index") }
}
```

Or if you want to specify your own brand icon/logo as the thumb itself:

```kotlin
Modifier.scrollbar(
    listState,
    style = ScrollbarDefaults.style(
        thumbAppearance = ThumbAppearance.Icon(
            idle = painterResource(Res.drawable.logo),
            dragging = painterResource(Res.drawable.logo_active),
            size = DpSize(48.dp, 48.dp),
        ),
    ),
)
```

Notes on icon thumbs:

- `size` is required. It becomes the thumb length along the scroll axis, and the capsule, thickness, color and `ThumbSizing` options are ignored.
- `dragging` is optional. Leave it out to use one icon for both states.
- `tint` is off by default, so multicolor logos keep their colors.
- The icon is never mirrored or rotated. For right-to-left layouts, set `autoMirror` on a vector if you want it to flip.
- Use `remember`ed painters (`painterResource` already does this). Animated painters might work but aren't tested yet.

### Label bubble and section haptics

```kotlin
Modifier.scrollbar(
    listState,
    label = ScrollbarLabel { itemIndex -> contacts[itemIndex].name.first().toString() },
)
```

- The lambda gets the index of the item the list is jumping to and returns the text, or `null` to hide the bubble. It runs on the main thread whenever the drag moves to a different item, so keep it cheap.
- The device ticks whenever the returned text changes. Return the same text for every item in a section (A, B, C...) and you get one tick per section.
- For per-item labels like "Item 1234", pass `sectionHaptics = false`, or it will tick constantly.
- Style the bubble with `ScrollbarLabelStyle`.

### Infinite feeds

Lists that load more items as you scroll don't have a known end. Tell the scrollbar with `hasMore`:

```kotlin
Modifier.scrollbar(listState, hasMore = hasMore)
```

While `hasMore` is `true`, the thumb stops short of the very end, and dragging to the bottom jumps to the last loaded item. If a page loads while you are dragging, the drag continues into the new content. When `hasMore` becomes `false`, the thumb glides to the end. Item count changes (pagination, prepended messages) glide the thumb instead of snapping it. If your list uses Paging placeholders, the total is already known, and you don't need `hasMore`.

### Movable bar

Let users move the bar to the other side, which helps left-handed and one-handed use. Hold the thumb still for half a second, drag it across, and it snaps to the nearest edge:

```kotlin
var edge by rememberSaveable { mutableStateOf(FastScrollEdge.END) }

Modifier.scrollbar(listState, edge = edge, onEdgeChange = { edge = it })
```

Store the edge wherever you keep settings. It's touch only, and off unless you pass `onEdgeChange`.

While the thumb is picked up, a small two-way arrow appears next to it to show it can be moved across. People won't guess the long press on their own, so mention it somewhere in your app, such as a settings toggle or a one-time tip.

### Always-visible bar

```kotlin
Modifier.scrollbar(
    listState,
    style = ScrollbarDefaults.style(visibility = ScrollbarVisibility.Always),
)
```

### Grids

```kotlin
val gridState = rememberLazyGridState()

LazyVerticalGrid(
    columns = GridCells.Fixed(3),
    state = gridState,
    modifier = Modifier.scrollbar(gridState),
) { /* ... */ }
```

### Content padding and system insets

Pass the same padding the list uses, so the track stays inside it:

```kotlin
val insets = WindowInsets.systemBars.asPaddingValues()

LazyColumn(
    state = listState,
    contentPadding = insets,
    modifier = Modifier.scrollbar(listState, contentPadding = insets),
) { /* ... */ }
```

You don't need to add the system gesture insets yourself. If the list reaches the top or bottom of the screen, the track automatically stays out of the gesture zones there (and a horizontal bar is lifted above the bottom one). The larger of your padding and the gesture zone is used, not the sum, so passing insets as `contentPadding` never pushes the track in twice.

### Styling

```kotlin
Modifier.scrollbar(
    listState,
    edge = FastScrollEdge.START,
    style = ScrollbarDefaults.style(
        thumbColor = Color.Gray,
        pressedThumbColor = Color(0xFF6200EE),
        thumbSizing = ThumbSizing.Proportional(minLength = 40.dp),
        hideDelay = 2.seconds,
    ),
)
```

The look follows the platform: a capsule with a thumb inside on Android, a thin bar that widens when grabbed on iOS (like the system scroll indicator), and a mouse-sized bar on desktop. Pick a look yourself with `thumbAppearance`:

| Appearance                  | Look                                                                                |
|-----------------------------|-------------------------------------------------------------------------------------|
| `ThumbAppearance.Default`   | Translucent capsule with a thin thumb inside. The Android default.                  |
| `ThumbAppearance.Bar`       | One rounded bar, no capsule. The iOS and desktop default. Widens on hover and drag. |
| `ThumbAppearance.Icon(...)` | Your own icon or logo.                                                              |

`ScrollbarDefaults.compactStyle()` gives the full desktop look (thin bar, mouse-sized grab area) everywhere. For a plain bar with a finger-sized grab area on mobile, use `style(thumbAppearance = ThumbAppearance.Bar)`.

Want a different style per platform? Set them in one place, with no platform checks of your own. Each one is a full style, so anything can differ: the look, sizing, visibility, colors and timings. Pass only the platforms you want to change, and the others keep their built-in look:

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
        thumbSizing = ThumbSizing.Proportional(),
        hideDelay = 3.seconds,
    ),
)
```

Using Material3? Pass your theme colors:

```kotlin
ScrollbarDefaults.style(
    thumbColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
    pressedThumbColor = MaterialTheme.colorScheme.primary,
)
```

### Visible track

The track is invisible by default. Give it a look with `trackAppearance`, for example a rope your icon thumb hangs from:

```kotlin
ScrollbarDefaults.style(
    thumbAppearance = ThumbAppearance.Icon(idle = climberPainter, size = DpSize(40.dp, 40.dp)),
    trackAppearance = TrackAppearance.Image(
        painter = painterResource(Res.drawable.rope),
        thickness = 8.dp,
        extent = TrackExtent.UpToThumb,
        fit = TrackFit.Repeat,
    ),
)
```

| Option        | Choices                                                                                                                                                                    |
|---------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Look          | `TrackAppearance.None` (default), `Line(color, thickness)`, or `Image(painter, thickness)`                                                                                 |
| `extent`      | `Full`: the whole track. `UpToThumb`: from the start of the list to the middle of the thumb, so it grows as you scroll. On `reverseLayout` lists it grows from the bottom. |
| `fit` (Image) | `Stretch`: one copy, stretched. Good for lines, webs and ropes. `Repeat`: copies end to end, keeping their proportions. Good for ladders, chains and towers.               |

Any `Painter` works, so no custom drawing code is needed. 

Have an SVG? Import it as a vector drawable (Android Studio → New → Vector Asset), put it in `composeResources/drawable/`, and load it with `painterResource`. PNGs and `ImageVector`s (through `rememberVectorPainter`) work too.

The track is drawn under the thumb and centered on it, so it follows when the bar is moved to the other edge. It's never rotated: draw it upright for vertical lists and sideways for horizontal ones. It's only decoration, so touches behave exactly as without it.

### Thumb sizing

| Option                          | Behavior                                                                                             |
|---------------------------------|------------------------------------------------------------------------------------------------------|
| `ThumbSizing.Fixed(length)`     | Constant length. This is the default, and it never jitters.                                          |
| `ThumbSizing.Proportional(...)` | Length follows viewport / content. `smoothed = true` animates changes in the estimated content size. |

### Parameters

| Parameter            | Default                     | Description                                                                                                     |
|----------------------|-----------------------------|-----------------------------------------------------------------------------------------------------------------|
| `edge`               | `END`                       | Which side of the cross axis the bar sits on. For horizontal lists, `START` is the top and `END` is the bottom. |
| `contentPadding`     | `0.dp`                      | Padding along the scroll axis that the track stays inside.                                                      |
| `minItemsToShow`     | `10`                        | The bar stays hidden for lists with this many items or fewer.                                                   |
| `style`              | `ScrollbarDefaults.style()` | Sizes, colors, timings, and the thumb and track appearance.                                                     |                                                           |
| `hasMore`            | `false`                     | `true` for feeds that load more as you scroll. See above.                                                       |
| `label`              | `null`                      | Bubble shown next to the thumb while dragging. See above.                                                       |
| `onEdgeChange`       | `null`                      | Long-press and drag the thumb across to move the bar to the other edge. See above.                              |
| `scrollToStartLabel` | `null`                      | Accessibility label for a "scroll to start" action. No action is added when `null`.                             |
| `scrollToEndLabel`   | `null`                      | Accessibility label for a "scroll to end" action. No action is added when `null`.                               |

## Known limitations

- **Best on long lists:** fast scrolling pays off when content is at least 4 screens tall (roughly 50+ items), like Android's own fast scroller. On short lists with very uneven items, the estimated position can visibly shift. Raise `minItemsToShow` to hide the bar on lists like that.
- **Position accuracy:** lazy layouts only know the size of items they've measured, so dragging maps the thumb to an item using the average item size. With uniform items it's exact, and with variable sizes the list can land slightly off the thumb.
- **Grids:** the number of items per line is estimated from the visible items. Grids with full-span headers or uneven spans may land slightly off while dragging.
- **iOS screen edges:** iOS has no way for apps to reserve part of a screen edge, unlike Android. A bar on the left edge (in right-to-left languages, or after users move it there) can compete with the swipe-back gesture. The default right-side placement in left-to-right layouts doesn't.
- **Collapsing toolbars:** fast-scrolling jumps the list straight to its target instead of scrolling through nested scroll, so nested-scroll effects like a collapsing toolbar (`TopAppBar` scroll behaviors) don't react to it. Swiping the list still drives them as usual.

## Roadmap

- Plain `ScrollState` support (`Column` with `verticalScroll`), with exact positions
- `LazyVerticalStaggeredGrid` support
- TalkBack and VoiceOver actions, and keyboard support on desktop
- Fine scrub: move the finger sideways to drag the thumb slower, for precise landing (opt-in)
- Section snapping (opt-in)

## Project structure

| Module                                  | Purpose                                            |
|-----------------------------------------|----------------------------------------------------|
| `/scrollbar`                            | The library. Source lives in `commonMain`.         |
| `/androidApp`, `/desktopApp`, `/iosApp` | Demo apps for trying the library on each platform. |

> `iosX64` (the Intel-Mac simulator) isn't a target, because Compose Multiplatform doesn't publish artifacts for it.

### Running the demo apps

- Android: `./gradlew :androidApp:assembleDebug`
- Desktop: `./gradlew :desktopApp:run`
- iOS: open the [/iosApp](./iosApp) directory in Xcode and run it from there. This requires macOS.

### Running tests

- Android host (unit tests): `./gradlew :scrollbar:testAndroidHostTest`
- Android device or emulator (includes UI and back-gesture tests): `./gradlew :scrollbar:connectedAndroidDeviceTest`
- Desktop: `./gradlew :scrollbar:desktopTest`
- iOS: `./gradlew :scrollbar:iosSimulatorArm64Test` (requires macOS)

## License

Copyright 2026 Amaan Qureshi

Licensed under the [Apache License, Version 2.0](LICENSE). You can use this library in any app, commercial or open source. 

If you redistribute it or a modified version, keep the copyright notice and the [NOTICE](NOTICE) file.
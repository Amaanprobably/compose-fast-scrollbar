# Scrollbar

A draggable fast scrollbar for Compose Multiplatform lazy layouts. One modifier, no wrapper composable, works on Android, iOS and Desktop.

> **Status:** early development. Not published yet, and the iOS and Desktop targets haven't been tested on real devices.

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
- No recomposition while scrolling or dragging. All state is read in the draw phase.

## Installation

Not published yet. Once it is, it will look like this (`<version>` and the artifact name are placeholders):

```kotlin
// commonMain
dependencies {
    implementation("io.github.amaanprobably:scrollbar:<version>")
}
```

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
- `tint` is off by default, so multi-color logos keep their colors.
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

While `hasMore` is `true`, the thumb stops short of the very end, and dragging to the bottom jumps to the last loaded item. If a page loads while you are dragging, the drag continues into the new content. When `hasMore` becomes `false`, the thumb glides to the end. Item count changes (pagination, prepended messages) glide the thumb instead of snapping it. If your list uses Paging placeholders, the total is already known and you don't need `hasMore`.

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

Using Material3? Pass your theme colors:

```kotlin
ScrollbarDefaults.style(
    thumbColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
    pressedThumbColor = MaterialTheme.colorScheme.primary,
)
```

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
| `style`              | `ScrollbarDefaults.style()` | Sizes, colors, timings and the thumb appearance.                                                                |
| `hasMore`            | `false`                     | `true` for feeds that load more as you scroll. See above.                                                       |
| `label`              | `null`                      | Bubble shown next to the thumb while dragging. See above.                                                       |
| `scrollToStartLabel` | `null`                      | Accessibility label for a "scroll to start" action. No action is added when `null`.                             |
| `scrollToEndLabel`   | `null`                      | Accessibility label for a "scroll to end" action. No action is added when `null`.                               |

## Known limitations

- **Position accuracy:** lazy layouts only know the size of items they've measured, so dragging maps the thumb to an item using the average item size. With uniform items it's exact, and with variable sizes the list can land slightly off the thumb.
- **Grids:** the number of items per line is estimated from the visible items. Grids with full-span headers or uneven spans may land slightly off while dragging.

## Roadmap

- Desktop mouse support: hover reveal, hover highlight, track click
- TalkBack and VoiceOver actions

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

- Android: `./gradlew :scrollbar:testAndroidHostTest`
- Desktop: `./gradlew :scrollbar:desktopTest`
- iOS: `./gradlew :scrollbar:iosSimulatorArm64Test` (requires macOS)

## License

To be decided before the first release.
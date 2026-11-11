# Scrollbar

A draggable fast scrollbar for Compose Multiplatform lazy layouts. One modifier, no wrapper composable, works on Android, iOS and Desktop.

> **Status:** early development. Not published yet, and the iOS and Desktop targets haven't been tested on real devices.

## Features

- Drag the thumb to jump through long lists. Touches that miss the thumb pass straight through to the list.
- Works with `LazyColumn`, `LazyRow`, `LazyVerticalGrid` and `LazyHorizontalGrid`.
- Fixed-length thumb by default, with an optional proportional (and smoothed) thumb.
- `contentPadding` support, so the track stays clear of system bars and insets.
- RTL and `reverseLayout` support.
- Auto-hides after scrolling stops, with a smooth settle animation after you release the thumb.
- Fully styleable: thickness, colors, timings and edge placement.
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
        hideDelayMillis = 2_000,
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

| Option | Behavior |
|---|---|
| `ThumbSizing.Fixed(length)` | Constant length. This is the default, and it never jitters. |
| `ThumbSizing.Proportional(...)` | Length follows viewport / content. `smoothed = true` animates changes in the estimated content size. |

### Parameters

| Parameter | Default | Description |
|---|---|---|
| `edge` | `END` | Which side of the cross axis the bar sits on. For horizontal lists, `START` is the top and `END` is the bottom. |
| `contentPadding` | `0.dp` | Padding along the scroll axis that the track stays inside. |
| `minItemsToShow` | `10` | The bar stays hidden for lists with this many items or fewer. |
| `style` | `ScrollbarDefaults.style()` | Sizes, colors and timings. |

## Known limitations

- **Position accuracy:** lazy layouts only know the size of items they've measured, so dragging maps the thumb to an item using the average item size. With uniform items it's exact, and with variable sizes the list can land slightly off the thumb.
- **Grids:** the number of items per line is estimated from the visible items. Grids with full-span headers or uneven spans may land slightly off while dragging.

## Roadmap

- Position label bubble shown while dragging (a letter, date or section name)
- Always-visible mode
- Haptic ticks when crossing sections
- Custom thumb icon
- TalkBack and VoiceOver actions

## Project structure

| Module | Purpose |
|---|---|
| `/scrollbar` | The library. Source lives in `commonMain`. |
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
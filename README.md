<h1 align="center">
  <img
    width="128"
    alt="Compose Fast Scrollbar logo"
    src="https://github.com/user-attachments/assets/09c15543-8bc6-4f58-8183-26d2d7e2022d"
    align="middle"
  />
  Compose Fast Scrollbar
</h1>

<p align="center">
  <a href="https://github.com/Amaanprobably/compose-fast-scrollbar/actions/workflows/ci.yml"><img src="https://github.com/Amaanprobably/compose-fast-scrollbar/actions/workflows/ci.yml/badge.svg" alt="CI"></a>
  <a href="https://central.sonatype.com/artifact/io.github.amaanprobably/compose-fast-scrollbar"><img src="https://img.shields.io/maven-central/v/io.github.amaanprobably/compose-fast-scrollbar" alt="Maven Central"></a>
</p>

A draggable fast scrollbar for Jetpack Compose and Compose Multiplatform: LazyColumn, LazyRow, grids, and scrollable Columns and Rows. One modifier, no wrapper composable, works on Android, iOS and Desktop.

> **Status:** early (0.x). The full test suite passes on Android devices, Desktop and the iOS simulator.

## Demo

https://github.com/user-attachments/assets/8f0c4779-44e3-4107-b11b-78473a625502

## Features

- **Drag to jump** anywhere in long lists. Touches that miss the thumb still reach the list.
- **One modifier** for `LazyColumn`, `LazyRow`, lazy grids and scrollable `Column`/`Row`. No wrapper composable.
- **Steady thumb:** fixed length by default, and it glides instead of jumping when items change.
- **Label bubble** while dragging (a letter, date or section), with a haptic tick per section.
- **Infinite feeds and chats:** `hasMore`, reversed lists, and pages that load mid-drag.
- **Make it yours:** colors, sizes, your own icon as the thumb, and an optional visible track.
- **Native on each platform:** capsule on Android, thin bar on iOS, and on desktop a proportional bar with hover and click-to-jump, always visible on Windows and Linux like the system's own.
- **Edge-safe:** stays clear of Android gesture zones, and supports `contentPadding` and RTL.
- **Zero recompositions** while scrolling or dragging.

## Installation

```kotlin
// commonMain, or your app module in a plain Android project
dependencies {
    implementation("io.github.amaanprobably:compose-fast-scrollbar:0.2.0")
}
```

**Requirements:** Compose 1.10 or newer (Compose Multiplatform 1.10.3+), compileSdk 36+, minSdk 24. Targets Android, iOS (`iosArm64`, `iosSimulatorArm64`) and Desktop (JVM).

> **0.x versions:** the API may still change between minor versions until 1.0. Breaking changes are listed in the [changelog](CHANGELOG.md).

## Usage

Apply the modifier to the lazy layout itself, and pass the same state you give the list:

```kotlin
val listState = rememberLazyListState()

LazyColumn(
    state = listState,
    modifier = Modifier.scrollbar(listState),
) {
    items(1_000) { index -> Text("Item $index") }
}
```

Grids work the same way with a `LazyGridState`.

### Plain Columns and Rows

```kotlin
val scrollState = rememberScrollState()

Column(
    Modifier
        .fillMaxSize()
        .scrollbar(scrollState)       // before verticalScroll
        .verticalScroll(scrollState)
) { /* long content */ }
```

### Show where you'll land

```kotlin
Modifier.scrollbar(
    listState,
    label = ScrollbarLabel { index -> contacts[index].name.take(1) },
)
```

### Infinite feeds

```kotlin
Modifier.scrollbar(listState, hasMore = !endReached)
```

### Your own thumb

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

### The most used parameters

| Parameter        | What it does                                                               |
|------------------|----------------------------------------------------------------------------|
| `edge`           | Which side the bar sits on (`ScrollbarEdge.Start` or `End`).               |
| `contentPadding` | The list's padding, so the track stays clear of bars and insets.           |
| `hasMore`        | `true` while more items can load. The thumb stops short of the end.        |
| `style`          | The look, sizes, colors, visibility and timings.                           |
| `label`          | A bubble next to the thumb while dragging.                                 |

Every parameter, with when to use it, is in the [guide](GUIDE.md).

## Guide

[GUIDE.md](GUIDE.md) walks through everything you might want to do, with code and short videos:

- [Add a scrollbar to any lazy list or grid](GUIDE.md#add-a-scrollbar-to-a-list-or-grid)
- [Plain Columns and Rows](GUIDE.md#plain-columns-and-rows)
- [Show where the user will land](GUIDE.md#show-where-the-user-will-land)
- [Infinite feeds, chats and paging](GUIDE.md#infinite-feeds-chats-and-paging)
- [Stay clear of system bars, padding and gestures](GUIDE.md#stay-clear-of-system-bars-padding-and-gestures)
- [Change the look](GUIDE.md#change-the-look)
- [Use your own icon as the thumb](GUIDE.md#use-your-own-icon-as-the-thumb)
- [Add a visible track](GUIDE.md#add-a-visible-track)
- [Different looks per platform](GUIDE.md#different-looks-per-platform)
- [Let users move the bar](GUIDE.md#let-users-move-the-bar)
- [Horizontal lists](GUIDE.md#horizontal-lists)
- [Mouse and desktop](GUIDE.md#mouse-and-desktop)
- [Accessibility](GUIDE.md#accessibility)
- [API reference](GUIDE.md#api-reference)
- [Troubleshooting](GUIDE.md#troubleshooting)

## Compared to Material 3's scrollbar

Material 3 has a visual-only scrollbar, with an interactive one on the way. This library is a fast scroller for long content: a label bubble with haptics, infinite feeds that load mid-drag, gesture navigation handling, a movable bar and custom thumbs and tracks. It doesn't need Material 3, and it works on Android, iOS and desktop.

## Known limitations

- **Best on long lists** (roughly 50+ items). On short lists with very uneven items, the estimated position can visibly shift.
- **Variable item sizes:** lazy lists only know the size of items they've measured, so the list can land slightly off the thumb.
- **Collapsing toolbars** don't react to fast scrolling, because it jumps the list instead of scrolling through nested scroll.

Details and workarounds are in [Troubleshooting](GUIDE.md#troubleshooting).

## Roadmap

- `LazyVerticalStaggeredGrid` support (0.3)
- TalkBack and VoiceOver actions, and keyboard support on desktop (0.4)
- Fine scrub: move the finger sideways to drag the thumb slower, for precise landing (opt-in)
- Section snapping (opt-in)

## Contributing

Bug reports, feature ideas and support requests are welcome as [issues](https://github.com/Amaanprobably/compose-fast-scrollbar/issues/new/choose). Pull requests are closed until 1.0. See [CONTRIBUTING.md](CONTRIBUTING.md) for details and for building the project from source.

## License

Copyright 2026 Amaan Qureshi

Licensed under the [Apache License, Version 2.0](LICENSE). You can use this library in any app, commercial or open source. If you redistribute it or a modified version, keep the copyright notice and the [NOTICE](NOTICE) file.

The demo app's artwork (launcher icon and characters) is licensed separately under [CC BY-NC-ND 4.0](ASSETS-LICENSE.md).

The music in the demo videos is "Lo-Fi Background Lounge" by lsMusic, used under the [Pixabay Content License](https://pixabay.com/service/license-summary/).

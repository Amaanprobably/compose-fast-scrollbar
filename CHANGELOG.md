# Changelog

All notable changes to this project are documented here. The project follows
[semantic versioning](https://semver.org). While it's on 0.x, minor versions may include breaking changes.

## 0.2.0 (2026-11-22)

### Added
- `Modifier.scrollbar(scrollState)` for `Column` and `Row` with `verticalScroll` or `horizontalScroll`. Positions are exact, and `reverseScrolling` and labels are supported (the label lambda gets the scroll offset in pixels).

### Changed
- Desktop defaults follow the system's own scrollbars: always visible on Windows and Linux (macOS still hides it when idle), and a proportional thumb with a 24dp minimum. `compactStyle()` uses the proportional thumb on every platform. Pass `visibility` or `thumbSizing` to keep the old look.

### Fixed
- When a page loads while the thumb is held still at the end, the label now follows the list into the new page instead of showing the old item.
- The scroll to start and scroll to end accessibility actions stop a running fling first, so the fling can't pull the list away from where they jumped.
- A smoothed proportional thumb no longer shows up at a stale length (often full height, then shrinking) when the bar appears, when the list state changes, or when smoothing is turned on.

## 0.1.1 (2026-11-19)

- Works with Compose 1.10+ and compileSdk 36. Apps no longer need Compose 1.12, compileSdk 37 or AGP 9.2.
- When a page loads or items are added mid-drag, the thumb now stays under the finger, and the end of the list stays reachable in the same drag. Before, the thumb jumped away and the end couldn't be reached without grabbing it again.
- Holding the thumb at the end of a `hasMore` list keeps loading into each new page until released.

## 0.1.0 (2026-11-19)

First release.

- `Modifier.scrollbar` for `LazyColumn`, `LazyRow`, `LazyVerticalGrid` and `LazyHorizontalGrid` on Android, iOS and Desktop.
- Platform looks: a capsule on Android, a thin bar that widens when grabbed on iOS, and a mouse-sized bar on desktop. `platformStyle` sets a different style per platform.
- Fixed-length thumb by default, with an optional proportional (and smoothed) thumb.
- Label bubble while dragging, with a haptic tick at each new section.
- Infinite feeds: `hasMore` keeps the thumb short of the end while more items can load.
- Movable bar: hold the thumb, then drag it to the other edge (`onEdgeChange`).
- Custom looks: any icon as the thumb, and an optional visible track (a line or any painter, full length or up to the thumb).
- Android gesture navigation: the visible thumb is excluded from the back gesture, and the track stays out of the system gesture zones.
- Mouse support: hover reveal and highlight, label preview on hover, and click-to-jump on the track.
- RTL, `reverseLayout` and `contentPadding` support. No recomposition while scrolling or dragging.
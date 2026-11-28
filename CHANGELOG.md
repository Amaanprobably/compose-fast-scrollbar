# Changelog

All notable changes to this project are documented here. The project follows
[semantic versioning](https://semver.org). While it's on 0.x, minor versions may include breaking changes.

## 0.2.2 (2026-11-28)

### Changed
- New `ThumbSizing.Automatic`, the default on touch screens: a fixed 40dp thumb on lazy lists and grids, like before, and a proportional one on scrollable Columns and Rows (`Modifier.scrollbar(scrollState)`), like Android's own ScrollView. A scroll state knows its exact content size, so that thumb never jitters. A style of your own gets it too unless it sets `thumbSizing`. `style().thumbSizing` is now `Automatic` on Android and iOS instead of `Fixed(40.dp)`; if you `when` over `ThumbSizing`, add a branch for it. Desktop is unchanged.

### Fixed
- The bar stays clear of the system bars and display cutouts on every side when the list draws behind them, like it already did for the gesture zones. In landscape it no longer sits under a navigation bar at the side of the screen, and a horizontal bar is lifted above a 3-button navigation bar. Lists that are already padded clear of the bars don't change.

## 0.2.1 (2026-11-26)

### Changed
- An auto-hiding bar shows once when the list first has something to scroll, then fades after the usual delay, so people can see the list goes on. Android's own views and iOS's scroll indicators do the same.
- After a drag the bar fades a little sooner: 1.2 s instead of 1.5 s with the default `hideDelay` (four fifths of it), like RecyclerView's fast scroller. Scrolling still keeps it 1.5 s.
- The capsule no longer touches the list's edge at the ends: it stops `edgePadding` short of them (4dp on Android), the same gap as at the side, with the thumb still centered in it. The default capsule is 8dp shorter to make room (`capsuleExtraLength` 16dp instead of 24dp on Android and iOS), so the thumb travels just as far, and its grab area now covers the whole capsule. Pass `capsuleExtraLength = 24.dp` for the old length.
- The bar reaches closer to the ends of the track, like the system's own scrollbars: `trackPadding` is 2dp on Android (was 8dp), 3dp on iOS (was 6dp) and 2dp on desktop (was 4dp), so the thumb travels further. An icon thumb keeps its `edgePadding` at the ends. Pass `trackPadding` to keep the old spacing.

### Fixed
- Taps on the thumb reach the item underneath (a checkbox, a button, the row itself). The thumb only grabs when a finger drags it or holds still on it for a moment (300 ms with Android's default touch-and-hold delay, longer if it's set slower). A drag goes on from where it was grabbed, so the list doesn't jump. While the list is scrolling, a touch on the thumb still grabs it right away, and a mouse still grabs on press.
- A touch the system cancels while the bar is picked up (the notification shade, a parent that takes the touch) puts the bar back instead of moving it to the side it was carried to.

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
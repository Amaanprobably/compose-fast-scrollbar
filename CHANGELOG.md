# Changelog

All notable changes to this project are documented here. The project follows
[semantic versioning](https://semver.org). While it's on 0.x, minor versions may include breaking changes.

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
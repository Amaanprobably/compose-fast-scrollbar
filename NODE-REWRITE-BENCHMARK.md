# Would a Modifier.Node rewrite be faster?

`Modifier.scrollbar` is a composable modifier: it uses `remember`, effects and `pointerInput`
inside a `@Composable` function. A rewrite as a `Modifier.Node` would skip that work when the
screen around the list recomposes. This benchmark measures the most such a rewrite could save.

## Short answer

For normal use, nothing you'd notice. Scrolling and dragging never recompose anything, so the
rewrite only matters when the screen around the list recomposes, and then only by about 8 µs
per list. The difference only becomes measurable in an extreme case: many lists on one screen,
all recomposing on every frame.

## Results

Median of 3 rounds, 2,000 frames each, after 500 warm-up frames. Every frame recomposes the whole
screen while the list scrolls, with a content padding that changes each frame, so nothing can be
skipped.

| Lists on screen | No scrollbar | `Modifier.scrollbar` today | Empty `Modifier.Node` | Most a rewrite saves |
|---|---|---|---|---|
| 1 | 658.5 µs, 35.6 KB | 824.0 µs, 42.5 KB | 815.7 µs, 36.8 KB | **8.3 µs per frame** (0.05% of a 60 Hz frame), 5.7 KB |
| 20 | 8,482 µs, 548 KB | 12,166 µs, 1,264 KB | 8,624 µs, 565 KB | **3.5 ms per frame** (21% of a 60 Hz frame), 699 KB |

Times are per frame. Sizes are memory allocated per frame.

## How to read it

- **The empty node is the floor.** It takes the same parameters and redraws on every update but
  does no work, so no real rewrite can be cheaper. "Today" minus "empty node" is the most a
  rewrite could ever save, not what it would save.
- **One list: no difference.** 8 µs is within the noise of a frame that takes over 800 µs.
- **Twenty lists, every frame: a real cost.** Each scrollbar allocates about 35 KB per
  recomposition, and at more than a megabyte per frame the garbage collector does the rest. It
  takes both conditions together: a screen full of lists that each have a bar (a feed of
  carousels, say) and something recomposing that whole screen on every frame.
- **The worst case is deliberate.** Real screens recompose now and then, not 60 times a second,
  and nothing recomposes while a list scrolls or the thumb is dragged.

## What's planned

A full rewrite isn't worth it for the cases people actually hit, so it stays a "maybe" before
1.0. The cheaper first step is to find what those 35 KB per recomposition are and remember them,
which keeps the API and the code as they are.

## Setup

- Compose Fast Scrollbar 0.2.1, Compose Multiplatform 1.10.3, desktop JVM (JDK 21).
- `runComposeUiTest` on the test's frame clock. The lists sit side by side in a 1,200 × 800 dp
  row, each a `LazyColumn` of 10,000 text rows, scrolling 40 px per frame (down, then back up).
- Three modes: no scrollbar, the real `Modifier.scrollbar` (always visible, with a label and both
  accessibility actions), and an empty `Modifier.Node` stub with the same parameters.
- Time from `System.nanoTime()`. Memory from the JVM's per-thread allocation counters, summed over
  all threads.
- A phone will be slower in absolute terms, but the comparison between the three modes holds.

The benchmark is [NodeRewriteBenchmark.kt](scrollbar/src/desktopTest/kotlin/io/github/amaanprobably/scrollbar/NodeRewriteBenchmark.kt).
It's skipped in normal test runs. To run it, then read `scrollbar/build/node-benchmark.txt`:

```
./gradlew :scrollbar:desktopTest -Pbenchmark --tests "*NodeRewriteBenchmark*"
```

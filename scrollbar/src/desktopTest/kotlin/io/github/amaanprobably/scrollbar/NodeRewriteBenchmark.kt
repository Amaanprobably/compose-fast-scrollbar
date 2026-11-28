package io.github.amaanprobably.scrollbar

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import java.io.File
import java.lang.management.ManagementFactory
import kotlin.test.Test

private enum class NodeRewriteBenchmarkMode { None, Current, NodeStub }

/**
 * Not a real test: measures what a Modifier.Node rewrite could save, worst case. The numbers are
 * in NODE-REWRITE-BENCHMARK.md. Skipped by default; run it, then read
 * scrollbar/build/node-benchmark.txt:
 *
 *     ./gradlew :scrollbar:desktopTest -Pbenchmark --tests "*NodeRewriteBenchmark*"
 *
 * The draw and input code would be the same after a rewrite, so the only difference is what
 * happens when the screen around the list recomposes. Worst case: it recomposes on every frame,
 * while the list scrolls, with a padding that changes every frame.
 *
 * - None: no scrollbar, the floor.
 * - Current: the real Modifier.scrollbar.
 * - NodeStub: an empty Modifier.Node with the same parameters that redraws on every update. It's
 *   the cheapest a rewrite could ever be, so Current minus NodeStub is the most a rewrite saves.
 */
@OptIn(ExperimentalTestApi::class)
class NodeRewriteBenchmark {

    private class Result(val microsPerFrame: Double, val bytesPerFrame: Double)

    @Test
    fun compareRecompositionCost() {
        val listCounts = listOf(1, 20) // one list, and a screen full of lists, each with a bar
        val rounds = 3
        val results = mutableMapOf<Pair<NodeRewriteBenchmarkMode, Int>, MutableList<Result>>()
        repeat(rounds) {
            for (count in listCounts) {
                for (mode in NodeRewriteBenchmarkMode.entries) {
                    results.getOrPut(mode to count) { mutableListOf() }.add(measure(mode, count))
                }
            }
        }

        val report = buildString {
            appendLine("Median of $rounds rounds, $MEASURED_FRAMES frames each, recomposing every frame.")
            for (count in listCounts) {
                appendLine()
                appendLine("$count list(s) on screen:")
                val medians = NodeRewriteBenchmarkMode.entries.associateWith { mode ->
                    val runs = results.getValue(mode to count)
                    Result(
                        microsPerFrame = runs.map { it.microsPerFrame }.sorted()[runs.size / 2],
                        bytesPerFrame = runs.map { it.bytesPerFrame }.sorted()[runs.size / 2],
                    )
                }
                for ((mode, result) in medians) {
                    appendLine(
                        "  %-9s %8.1f us/frame %10.0f bytes/frame".format(
                            mode.name, result.microsPerFrame, result.bytesPerFrame,
                        ),
                    )
                }
                val current = medians.getValue(NodeRewriteBenchmarkMode.Current)
                val stub = medians.getValue(NodeRewriteBenchmarkMode.NodeStub)
                val savedMicros = current.microsPerFrame - stub.microsPerFrame
                appendLine(
                    "  Most a rewrite saves: %.1f us/frame (%.2f%% of a 60 Hz frame, %.2f%% at 120 Hz), %.0f bytes/frame"
                        .format(
                            savedMicros,
                            savedMicros / 16_667.0 * 100,
                            savedMicros / 8_333.0 * 100,
                            current.bytesPerFrame - stub.bytesPerFrame,
                        ),
                )
            }
        }
        println(report)
        File("build/node-benchmark.txt").apply { parentFile?.mkdirs() }.writeText(report)
    }

    private fun measure(mode: NodeRewriteBenchmarkMode, listCount: Int): Result {
        var result = Result(0.0, 0.0)
        runComposeUiTest {
            mainClock.autoAdvance = false
            val frame = mutableIntStateOf(0)
            val states = arrayOfNulls<LazyListState>(listCount)
            setContent {
                // Read here, so this whole scope recomposes on every frame.
                val currentFrame = frame.intValue
                val style = remember { ScrollbarDefaults.style(visibility = ScrollbarVisibility.Always) }
                val label = remember { ScrollbarLabel { "Item $it" } }
                Row(Modifier.size(1200.dp, 800.dp)) {
                    repeat(listCount) { listIndex ->
                        val state = rememberLazyListState()
                        states[listIndex] = state
                        BenchmarkList(mode, currentFrame, state, style, label, Modifier.weight(1f))
                    }
                }
            }
            waitForIdle()

            fun step(index: Int) {
                runOnUiThread {
                    frame.intValue++
                    // Scrolls down, then back up, so it never just sits at an end.
                    val delta = if ((index / 500) % 2 == 0) 40f else -40f
                    for (state in states) state?.dispatchRawDelta(delta)
                }
                mainClock.advanceTimeByFrame()
            }

            repeat(WARMUP_FRAMES) { step(it) }
            val bytesBefore = allocatedBytes()
            val start = System.nanoTime()
            repeat(MEASURED_FRAMES) { step(WARMUP_FRAMES + it) }
            val elapsed = System.nanoTime() - start
            val bytes = allocatedBytes() - bytesBefore
            result = Result(
                microsPerFrame = elapsed / 1_000.0 / MEASURED_FRAMES,
                bytesPerFrame = bytes.toDouble() / MEASURED_FRAMES,
            )
        }
        return result
    }

    private fun allocatedBytes(): Long {
        val bean = ManagementFactory.getThreadMXBean() as? com.sun.management.ThreadMXBean ?: return 0L
        return bean.getThreadAllocatedBytes(bean.allThreadIds).filter { it > 0 }.sum()
    }

    private companion object {
        const val WARMUP_FRAMES = 500
        const val MEASURED_FRAMES = 2_000
    }
}

@Composable
private fun BenchmarkList(
    mode: NodeRewriteBenchmarkMode,
    frame: Int,
    state: LazyListState,
    style: ScrollbarStyle,
    label: ScrollbarLabel,
    modifier: Modifier,
) {
    // A new padding every frame, like insets passed inline, so the parameters really change.
    val padding = PaddingValues(bottom = (frame % 2).dp)
    val scrollbarModifier = when (mode) {
        NodeRewriteBenchmarkMode.None -> Modifier
        NodeRewriteBenchmarkMode.Current -> Modifier.scrollbar(
            state,
            contentPadding = padding,
            style = style,
            label = label,
            scrollToStartLabel = "Top",
            scrollToEndLabel = "Bottom",
        )
        NodeRewriteBenchmarkMode.NodeStub -> Modifier.then(StubElement(state, padding, style, label))
    }
    LazyColumn(state = state, modifier = modifier.fillMaxHeight().then(scrollbarModifier)) {
        items(10_000) { index ->
            BasicText("Item $index", Modifier.fillMaxWidth().height(48.dp))
        }
    }
}

/** The cheapest possible Node version: stores nothing, draws nothing, redraws on every update. */
private data class StubElement(
    val state: LazyListState,
    val padding: PaddingValues,
    val style: ScrollbarStyle,
    val label: ScrollbarLabel,
) : ModifierNodeElement<StubNode>() {
    override fun create(): StubNode = StubNode()
    override fun update(node: StubNode) = node.invalidateDraw()
}

private class StubNode : Modifier.Node(), DrawModifierNode {
    override fun ContentDrawScope.draw() = drawContent()
}

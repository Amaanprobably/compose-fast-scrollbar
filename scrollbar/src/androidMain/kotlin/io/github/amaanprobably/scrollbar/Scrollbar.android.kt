package io.github.amaanprobably.scrollbar

import android.graphics.Rect
import android.os.Build
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import kotlin.math.roundToInt

internal actual val currentPlatform: Platform = Platform.Android

/** Android's scrollbars and fast scroller fade out when scrolling stops. */
internal actual val platformDefaultVisibility: ScrollbarVisibility = ScrollbarVisibility.AutoHide

@Composable
internal actual fun rememberEdgeGestureGuard(): EdgeGestureGuard {
    val view = LocalView.current
    return remember(view) { AndroidEdgeGestureGuard(view) }
}

/**
 * Uses Android's system gesture exclusion (Android 10+), so a touch that starts on the thumb isn't
 * taken by the back gesture. Exclusions are per view, so this merges with any others set on the
 * same view (for example by Compose's own `Modifier.systemGestureExclusion`) instead of replacing
 * them. Android honors up to 200dp per screen edge; the protected area stays well under that.
 */
private class AndroidEdgeGestureGuard(private val view: View) : EdgeGestureGuard {
    private var current: Rect? = null

    override fun protect(left: Float, top: Float, right: Float, bottom: Float) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val rect = Rect(left.roundToInt(), top.roundToInt(), right.roundToInt(), bottom.roundToInt())
        if (rect == current) return
        val rects = view.systemGestureExclusionRects.toMutableList()
        current?.let { previous -> rects.remove(previous) }
        rects.add(rect)
        view.systemGestureExclusionRects = rects
        current = rect
    }

    override fun clear() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val previous = current ?: return
        val rects = view.systemGestureExclusionRects.toMutableList()
        rects.remove(previous)
        view.systemGestureExclusionRects = rects
        current = null
    }
}
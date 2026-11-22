package io.github.amaanprobably.scrollbar

import androidx.compose.runtime.Composable

internal actual val currentPlatform: Platform = Platform.Ios

/** iOS scroll indicators fade out when scrolling stops. */
internal actual val platformDefaultVisibility: ScrollbarVisibility = ScrollbarVisibility.AutoHide

/** iOS has no API for an app to reserve part of a screen edge from system gestures. */
@Composable
internal actual fun rememberEdgeGestureGuard(): EdgeGestureGuard = NoEdgeGestureGuard
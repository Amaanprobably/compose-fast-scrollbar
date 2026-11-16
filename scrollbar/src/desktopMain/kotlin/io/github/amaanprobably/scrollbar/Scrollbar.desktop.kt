package io.github.amaanprobably.scrollbar

import androidx.compose.runtime.Composable

internal actual val currentPlatform: Platform = Platform.Desktop

/** Desktop windows have no system edge gestures to guard against. */
@Composable
internal actual fun rememberEdgeGestureGuard(): EdgeGestureGuard = NoEdgeGestureGuard
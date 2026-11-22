package io.github.amaanprobably.scrollbar

import androidx.compose.runtime.Composable

internal actual val currentPlatform: Platform = Platform.Desktop

internal actual val platformDefaultVisibility: ScrollbarVisibility =
    desktopDefaultVisibility(System.getProperty("os.name").orEmpty())

/** macOS hides its scrollbars when idle (for trackpads); Windows and Linux keep them visible. */
internal fun desktopDefaultVisibility(osName: String): ScrollbarVisibility =
    if (osName.startsWith("Mac", ignoreCase = true)) ScrollbarVisibility.AutoHide
    else ScrollbarVisibility.Always

/** Desktop windows have no system edge gestures to guard against. */
@Composable
internal actual fun rememberEdgeGestureGuard(): EdgeGestureGuard = NoEdgeGestureGuard
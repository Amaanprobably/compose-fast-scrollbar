package com.amaanqureshi.scrollbar

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.tooling.preview.Preview

/**
 * The demo app: a gallery of showcases (see [Showcase]) and a playground with every option.
 *
 * Navigation is a single state value, so the demo needs no navigation library. [backHandler] lets
 * the Android app hook the system back button to it (common code has no stable back API yet):
 *
 * ```
 * setContent { App(backHandler = { enabled, onBack -> BackHandler(enabled, onBack) }) }
 * ```
 */
@Composable
@Preview
fun App(
    backHandler: @Composable (enabled: Boolean, onBack: () -> Unit) -> Unit = { _, _ -> },
) {
    DemoTheme {
        var current by rememberSaveable { mutableStateOf<Showcase?>(null) }
        // Hoisted, so the gallery keeps its scroll position when you come back to it.
        val homeListState = rememberLazyListState()
        val goHome = { current = null }

        backHandler(current != null, goHome)

        AnimatedContent(
            targetState = current,
            transitionSpec = {
                val opening = targetState != null
                val direction = if (opening) 1 else -1
                (slideInHorizontally(tween(320)) { width -> direction * width / 4 } + fadeIn(tween(220)))
                    .togetherWith(slideOutHorizontally(tween(320)) { width -> -direction * width / 4 } + fadeOut(tween(180)))
            },
            label = "screen",
        ) { screen ->
            when (screen) {
                null -> HomeScreen(homeListState, onOpen = { current = it })
                Showcase.Contacts -> ContactsScreen(goHome)
                Showcase.Photos -> PhotosScreen(goHome)
                Showcase.Chat -> ChatScreen(goHome)
                Showcase.Playlist -> PlaylistScreen(goHome)
                Showcase.Notes -> NotesScreen(goHome)
                Showcase.Shelf -> ShelfScreen(goHome)
                Showcase.Track -> TrackScreen(goHome)
                Showcase.Playground -> PlaygroundScreen(goHome)
            }
        }
    }
}
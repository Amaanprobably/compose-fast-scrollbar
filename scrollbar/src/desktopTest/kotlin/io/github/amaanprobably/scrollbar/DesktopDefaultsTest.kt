package io.github.amaanprobably.scrollbar

import kotlin.test.Test
import kotlin.test.assertEquals

class DesktopDefaultsTest {

    @Test
    fun macOsHidesTheBarWhenIdle() {
        assertEquals(ScrollbarVisibility.AutoHide, desktopDefaultVisibility("Mac OS X"))
    }

    @Test
    fun windowsAndLinuxKeepTheBarVisible() {
        assertEquals(ScrollbarVisibility.Always, desktopDefaultVisibility("Windows 11"))
        assertEquals(ScrollbarVisibility.Always, desktopDefaultVisibility("Linux"))
    }
}

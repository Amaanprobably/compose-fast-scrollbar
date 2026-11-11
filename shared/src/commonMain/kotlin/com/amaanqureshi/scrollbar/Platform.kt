package com.amaanqureshi.scrollbar

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform
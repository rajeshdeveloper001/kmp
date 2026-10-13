package com.vplayed

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform
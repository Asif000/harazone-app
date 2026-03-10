package com.harazone

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform
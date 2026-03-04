package com.areadiscovery

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform
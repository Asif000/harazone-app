package com.areadiscovery.util

import kotlin.time.Clock
import kotlin.time.ExperimentalTime

interface AppClock {
    fun nowMs(): Long
}

class SystemClock : AppClock {
    @OptIn(ExperimentalTime::class)
    override fun nowMs(): Long = Clock.System.now().toEpochMilliseconds()
}

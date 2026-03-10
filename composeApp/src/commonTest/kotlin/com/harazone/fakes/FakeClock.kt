package com.harazone.fakes

import com.harazone.util.AppClock

class FakeClock(var nowMs: Long = 1_000_000_000L) : AppClock {
    override fun nowMs(): Long = nowMs
}

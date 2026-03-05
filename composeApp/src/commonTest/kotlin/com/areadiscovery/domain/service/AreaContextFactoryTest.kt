package com.areadiscovery.domain.service

import com.areadiscovery.fakes.FakeClock
import kotlin.test.Test
import kotlin.test.assertEquals

class AreaContextFactoryTest {

    private val fakeClock = FakeClock()
    private val factory = AreaContextFactory(fakeClock)

    // Time-of-day tests
    // Using UTC hours: epoch 0 = midnight Thursday Jan 1 1970

    @Test
    fun `3am UTC resolves to night`() {
        fakeClock.nowMs = 3 * 3600 * 1000L // 3:00 AM
        assertEquals("night", factory.create().timeOfDay)
    }

    @Test
    fun `5am UTC resolves to morning`() {
        fakeClock.nowMs = 5 * 3600 * 1000L // 5:00 AM
        assertEquals("morning", factory.create().timeOfDay)
    }

    @Test
    fun `11am UTC resolves to morning`() {
        fakeClock.nowMs = 11 * 3600 * 1000L // 11:00 AM
        assertEquals("morning", factory.create().timeOfDay)
    }

    @Test
    fun `12pm UTC resolves to afternoon`() {
        fakeClock.nowMs = 12 * 3600 * 1000L // 12:00 PM
        assertEquals("afternoon", factory.create().timeOfDay)
    }

    @Test
    fun `16pm UTC resolves to afternoon`() {
        fakeClock.nowMs = 16 * 3600 * 1000L // 4:00 PM
        assertEquals("afternoon", factory.create().timeOfDay)
    }

    @Test
    fun `17pm UTC resolves to evening`() {
        fakeClock.nowMs = 17 * 3600 * 1000L // 5:00 PM
        assertEquals("evening", factory.create().timeOfDay)
    }

    @Test
    fun `20pm UTC resolves to evening`() {
        fakeClock.nowMs = 20 * 3600 * 1000L // 8:00 PM
        assertEquals("evening", factory.create().timeOfDay)
    }

    @Test
    fun `21pm UTC resolves to night`() {
        fakeClock.nowMs = 21 * 3600 * 1000L // 9:00 PM
        assertEquals("night", factory.create().timeOfDay)
    }

    @Test
    fun `midnight resolves to night`() {
        fakeClock.nowMs = 0L // midnight
        assertEquals("night", factory.create().timeOfDay)
    }

    // Day-of-week tests
    // Epoch 0 = Thursday, Jan 1, 1970

    @Test
    fun `epoch 0 is Thursday`() {
        fakeClock.nowMs = 0L
        assertEquals("Thursday", factory.create().dayOfWeek)
    }

    @Test
    fun `day 1 is Friday`() {
        fakeClock.nowMs = 86_400_000L // 1 day
        assertEquals("Friday", factory.create().dayOfWeek)
    }

    @Test
    fun `day 2 is Saturday`() {
        fakeClock.nowMs = 2 * 86_400_000L
        assertEquals("Saturday", factory.create().dayOfWeek)
    }

    @Test
    fun `day 3 is Sunday`() {
        fakeClock.nowMs = 3 * 86_400_000L
        assertEquals("Sunday", factory.create().dayOfWeek)
    }

    @Test
    fun `day 4 is Monday`() {
        fakeClock.nowMs = 4 * 86_400_000L
        assertEquals("Monday", factory.create().dayOfWeek)
    }

    @Test
    fun `day 5 is Tuesday`() {
        fakeClock.nowMs = 5 * 86_400_000L
        assertEquals("Tuesday", factory.create().dayOfWeek)
    }

    @Test
    fun `day 6 is Wednesday`() {
        fakeClock.nowMs = 6 * 86_400_000L
        assertEquals("Wednesday", factory.create().dayOfWeek)
    }

    @Test
    fun `day 7 wraps back to Thursday`() {
        fakeClock.nowMs = 7 * 86_400_000L
        assertEquals("Thursday", factory.create().dayOfWeek)
    }

    // Default values

    @Test
    fun `visitCount defaults to 0`() {
        assertEquals(0, factory.create().visitCount)
    }

    @Test
    fun `preferredLanguage defaults to en`() {
        assertEquals("en", factory.create().preferredLanguage)
    }
}

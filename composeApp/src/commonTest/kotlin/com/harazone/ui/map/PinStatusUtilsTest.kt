package com.harazone.ui.map

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PinStatusUtilsTest {

    @Test
    fun liveStatusToColor_open_returnsGreen() {
        assertEquals(PinStatusColor.GREEN, liveStatusToColor("open"))
        assertEquals(PinStatusColor.GREEN, liveStatusToColor("Open now"))
    }

    @Test
    fun liveStatusToColor_closingSoon_returnsOrange() {
        assertEquals(PinStatusColor.ORANGE, liveStatusToColor("closing soon"))
        assertEquals(PinStatusColor.ORANGE, liveStatusToColor("Closes soon"))
    }

    @Test
    fun liveStatusToColor_closed_returnsRed() {
        assertEquals(PinStatusColor.RED, liveStatusToColor("closed"))
        assertEquals(PinStatusColor.RED, liveStatusToColor("Closed until tomorrow"))
    }

    @Test
    fun liveStatusToColor_null_returnsGrey() {
        assertEquals(PinStatusColor.GREY, liveStatusToColor(null))
    }

    @Test
    fun liveStatusToColor_unknown_returnsGrey() {
        assertEquals(PinStatusColor.GREY, liveStatusToColor("unknown"))
    }

    @Test
    fun isClosed_closed_returnsTrue() {
        assertTrue(isClosed("closed"))
    }

    @Test
    fun isClosed_open_returnsFalse() {
        assertFalse(isClosed("open"))
    }

    @Test
    fun deriveBadge_closingSoon_returnsClosingSoon() {
        assertEquals(PinBadge.CLOSING_SOON, deriveBadge("closing soon"))
    }

    @Test
    fun deriveBadge_trending_returnsTrending() {
        assertEquals(PinBadge.TRENDING, deriveBadge("trending now"))
    }

    @Test
    fun deriveBadge_event_returnsEvent() {
        assertEquals(PinBadge.EVENT, deriveBadge("event tonight"))
    }

    @Test
    fun deriveBadge_open_returnsNull() {
        assertNull(deriveBadge("open"))
    }

    @Test
    fun deriveBadge_null_returnsNull() {
        assertNull(deriveBadge(null))
    }

    // --- deriveStatusFromHours ---

    @Test
    fun deriveStatusFromHours_null_returnsNull() {
        assertNull(deriveStatusFromHours(null))
    }

    @Test
    fun deriveStatusFromHours_24hours_returnsOpen() {
        assertEquals("open", deriveStatusFromHours("Open 24 hours"))
        assertEquals("open", deriveStatusFromHours("24 hours"))
        assertEquals("open", deriveStatusFromHours("24h"))
    }

    @Test
    fun deriveStatusFromHours_currentlyOpen_returnsOpen() {
        // 14:00 (2pm) is within 9am-10pm
        assertEquals("open", deriveStatusFromHours("9am-10pm", nowMinutesOverride = 14 * 60))
    }

    @Test
    fun deriveStatusFromHours_currentlyClosed_returnsClosed() {
        // 1:00 AM is outside 9am-10pm
        assertEquals("closed", deriveStatusFromHours("9am-10pm", nowMinutesOverride = 1 * 60))
    }

    @Test
    fun deriveStatusFromHours_closingSoon_returnsClosingSoon() {
        // 9:15pm = 21*60+15 = 1275 min, closing at 10pm = 22*60 = 1320 min, diff = 45 min
        assertEquals("closing soon", deriveStatusFromHours("9am-10pm", nowMinutesOverride = 1275))
    }

    @Test
    fun deriveStatusFromHours_withMinutes_parsesCorrectly() {
        // 11:30am - 9:30pm, now at 3pm (900 min)
        assertEquals("open", deriveStatusFromHours("11:30am-9:30pm", nowMinutesOverride = 15 * 60))
    }

    @Test
    fun deriveStatusFromHours_withSpaces_parsesCorrectly() {
        assertEquals("open", deriveStatusFromHours("9 am - 10 pm", nowMinutesOverride = 14 * 60))
    }

    @Test
    fun deriveStatusFromHours_overnight_openBeforeMidnight() {
        // 6pm-2am, now at 11pm (1380 min)
        assertEquals("open", deriveStatusFromHours("6pm-2am", nowMinutesOverride = 23 * 60))
    }

    @Test
    fun deriveStatusFromHours_overnight_openAfterMidnight() {
        // 6pm-2am, now at 11:30pm (30 min past open, well before close)
        assertEquals("open", deriveStatusFromHours("6pm-2am", nowMinutesOverride = 23 * 60 + 30))
    }

    @Test
    fun deriveStatusFromHours_overnight_closingSoonBeforeClose() {
        // 6pm-2am, now at 1am (60 min before 2am close)
        assertEquals("closing soon", deriveStatusFromHours("6pm-2am", nowMinutesOverride = 60))
    }

    @Test
    fun deriveStatusFromHours_overnight_closedDuringDay() {
        // 6pm-2am, now at 10am (600 min)
        assertEquals("closed", deriveStatusFromHours("6pm-2am", nowMinutesOverride = 600))
    }

    @Test
    fun deriveStatusFromHours_unparseable_returnsNull() {
        assertNull(deriveStatusFromHours("varies"))
        assertNull(deriveStatusFromHours("call for hours"))
    }

    @Test
    fun deriveStatusFromHours_12amEdge_parsesCorrectly() {
        // 10pm-12am = 2 hour window. At 10:15pm → open (105 min to close)
        assertEquals("open", deriveStatusFromHours("10pm-12am", nowMinutesOverride = 22 * 60 + 15))
        // At 11pm → closing soon (60 min to midnight)
        assertEquals("closing soon", deriveStatusFromHours("10pm-12am", nowMinutesOverride = 23 * 60))
        // At 12:01am → closed
        assertEquals("closed", deriveStatusFromHours("10pm-12am", nowMinutesOverride = 1))
    }

    @Test
    fun deriveStatusFromHours_12pmEdge_parsesCorrectly() {
        // 12pm = noon = 720 minutes
        assertEquals("open", deriveStatusFromHours("12pm-6pm", nowMinutesOverride = 13 * 60))
        assertEquals("closed", deriveStatusFromHours("12pm-6pm", nowMinutesOverride = 11 * 60))
    }

    // --- resolveStatus ---

    @Test
    fun resolveStatus_prefersHoursDerived() {
        // Gemini says "open" but hours say it's closed at 1am
        assertEquals("closed", resolveStatus("open", "9am-10pm", nowMinutesOverride = 60))
    }

    @Test
    fun resolveStatus_fallsBackToGemini_whenHoursUnparseable() {
        assertEquals("open", resolveStatus("open", "varies"))
    }

    @Test
    fun resolveStatus_fallsBackToGemini_whenHoursNull() {
        assertEquals("closed", resolveStatus("closed", null))
    }

    @Test
    fun resolveStatus_bothNull_returnsNull() {
        assertNull(resolveStatus(null, null))
    }

    // --- parseOpeningHour ---

    @Test
    fun parseOpeningHour_standard_returnsHour() {
        assertEquals(9, parseOpeningHour("9am-10pm"))
    }

    @Test
    fun parseOpeningHour_withMinutes_returnsHour() {
        assertEquals(11, parseOpeningHour("11:30am-9:30pm"))
    }

    @Test
    fun parseOpeningHour_pm_returnsCorrectHour() {
        assertEquals(18, parseOpeningHour("6pm-2am"))
    }

    @Test
    fun parseOpeningHour_12am_returnsZero() {
        assertEquals(0, parseOpeningHour("12am-6pm"))
    }

    @Test
    fun parseOpeningHour_12pm_returnsTwelve() {
        assertEquals(12, parseOpeningHour("12pm-6pm"))
    }

    @Test
    fun parseOpeningHour_24hours_returnsZero() {
        assertEquals(0, parseOpeningHour("24 hours"))
        assertEquals(0, parseOpeningHour("Open 24 hours"))
        assertEquals(0, parseOpeningHour("24h"))
    }

    @Test
    fun parseOpeningHour_null_returnsNull() {
        assertNull(parseOpeningHour(null))
    }

    @Test
    fun parseOpeningHour_unparseable_returnsNull() {
        assertNull(parseOpeningHour("varies"))
        assertNull(parseOpeningHour("call for hours"))
    }
}

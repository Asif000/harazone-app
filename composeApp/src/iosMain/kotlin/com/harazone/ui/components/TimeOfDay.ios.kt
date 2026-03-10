package com.harazone.ui.components

import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarUnitHour
import platform.Foundation.NSCalendarUnitMinute
import platform.Foundation.NSDate

actual fun currentHour(): Int {
    val cal = NSCalendar.currentCalendar
    return cal.components(NSCalendarUnitHour, NSDate()).hour.toInt()
}

actual fun currentMinute(): Int {
    val cal = NSCalendar.currentCalendar
    return cal.components(NSCalendarUnitMinute, NSDate()).minute.toInt()
}

actual fun currentTimeMillis(): Long =
    (NSDate().timeIntervalSinceReferenceDate * 1000 + 978307200000).toLong()

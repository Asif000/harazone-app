package com.areadiscovery.ui.components

actual fun currentHour(): Int =
    java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)

actual fun currentMinute(): Int =
    java.util.Calendar.getInstance().get(java.util.Calendar.MINUTE)

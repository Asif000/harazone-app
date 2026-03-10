package com.harazone.ui.components

import androidx.compose.ui.graphics.Color

expect fun currentHour(): Int
expect fun currentMinute(): Int
expect fun currentTimeMillis(): Long

enum class TimeOfDaySlot { DAWN, DAY, DUSK, NIGHT }

fun timeOfDaySlot(hour: Int = currentHour()): TimeOfDaySlot = when (hour) {
    in 5..7   -> TimeOfDaySlot.DAWN
    in 8..16  -> TimeOfDaySlot.DAY
    in 17..19 -> TimeOfDaySlot.DUSK
    else      -> TimeOfDaySlot.NIGHT
}

fun TimeOfDaySlot.backgroundColors(): Pair<Color, Color> = when (this) {
    TimeOfDaySlot.DAWN  -> Color(0xFF1A0A2E) to Color(0xFF3D1C00)
    TimeOfDaySlot.DAY   -> Color(0xFF0B0F1E) to Color(0xFF060709)
    TimeOfDaySlot.DUSK  -> Color(0xFF1A0A00) to Color(0xFF2D0A1A)
    TimeOfDaySlot.NIGHT -> Color(0xFF0B0F1E) to Color(0xFF060709)
}

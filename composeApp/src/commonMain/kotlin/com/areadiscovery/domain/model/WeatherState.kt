package com.areadiscovery.domain.model

data class WeatherState(
    val temperatureF: Int,
    val weatherCode: Int,
    val conditionLabel: String,
    val emoji: String,
) {
    companion object {
        fun fromCode(code: Int, tempF: Int): WeatherState {
            val (label, emoji) = when (code) {
                0 -> "Clear" to "\u2600\uFE0F"
                in 1..3 -> "Partly Cloudy" to "\u26C5"
                in 45..48 -> "Foggy" to "\uD83C\uDF2B\uFE0F"
                in 51..67 -> "Rainy" to "\uD83C\uDF27\uFE0F"
                in 71..77 -> "Snowy" to "\u2744\uFE0F"
                in 80..82 -> "Showers" to "\uD83C\uDF26\uFE0F"
                in 95..99 -> "Thunderstorm" to "\u26C8\uFE0F"
                else -> "Cloudy" to "\uD83C\uDF25\uFE0F"
            }
            return WeatherState(
                temperatureF = tempF,
                weatherCode = code,
                conditionLabel = label,
                emoji = emoji,
            )
        }
    }
}

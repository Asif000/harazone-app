package com.harazone.domain.provider

import com.harazone.domain.model.WeatherState

interface WeatherProvider {
    suspend fun getWeather(latitude: Double, longitude: Double): Result<WeatherState>
}

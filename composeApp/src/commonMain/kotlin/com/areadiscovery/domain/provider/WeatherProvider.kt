package com.areadiscovery.domain.provider

import com.areadiscovery.domain.model.WeatherState

interface WeatherProvider {
    suspend fun getWeather(latitude: Double, longitude: Double): Result<WeatherState>
}

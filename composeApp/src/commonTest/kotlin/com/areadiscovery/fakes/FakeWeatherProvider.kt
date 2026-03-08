package com.areadiscovery.fakes

import com.areadiscovery.domain.model.WeatherState
import com.areadiscovery.domain.provider.WeatherProvider

class FakeWeatherProvider(
    private val result: Result<WeatherState> = Result.success(
        WeatherState(72, 0, "Clear", "\u2600\uFE0F")
    ),
) : WeatherProvider {
    var callCount = 0
        private set
    var lastLatitude = 0.0
        private set
    var lastLongitude = 0.0
        private set

    override suspend fun getWeather(latitude: Double, longitude: Double): Result<WeatherState> {
        callCount++
        lastLatitude = latitude
        lastLongitude = longitude
        return result
    }
}

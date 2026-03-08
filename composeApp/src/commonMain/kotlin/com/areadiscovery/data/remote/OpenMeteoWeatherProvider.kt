package com.areadiscovery.data.remote

import com.areadiscovery.domain.model.WeatherState
import com.areadiscovery.domain.provider.WeatherProvider
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
internal data class OpenMeteoResponse(
    val current: OpenMeteoCurrent,
    @SerialName("utc_offset_seconds") val utcOffsetSeconds: Int = 0,
)

@Serializable
internal data class OpenMeteoCurrent(
    @SerialName("temperature_2m") val temperatureF: Float,
    val weathercode: Int,
)

class OpenMeteoWeatherProvider(
    private val httpClient: HttpClient,
) : WeatherProvider {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun getWeather(latitude: Double, longitude: Double): Result<WeatherState> {
        return try {
            val url = "https://api.open-meteo.com/v1/forecast" +
                "?latitude=$latitude&longitude=$longitude" +
                "&current=temperature_2m,weathercode" +
                "&temperature_unit=fahrenheit" +
                "&timezone=auto"
            val responseText = httpClient.get(url).bodyAsText()
            val response = json.decodeFromString<OpenMeteoResponse>(responseText)
            val tempF = response.current.temperatureF.toInt()
            val code = response.current.weathercode
            Result.success(WeatherState.fromCode(code, tempF, response.utcOffsetSeconds))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

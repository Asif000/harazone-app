package com.harazone.fakes

import com.harazone.data.remote.MapTilerGeocodingProvider
import com.harazone.data.remote.ReverseGeocodeInfo
import com.harazone.domain.model.GeocodingSuggestion
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode

class FakeMapTilerGeocodingProvider(
    var result: Result<List<GeocodingSuggestion>> = Result.success(emptyList()),
) : MapTilerGeocodingProvider(HttpClient(MockEngine { respond("", HttpStatusCode.OK) }), FakeLocaleProvider()) {

    var callCount: Int = 0
        private set
    var lastQuery: String? = null
        private set

    var reverseGeocodeResult: Result<ReverseGeocodeInfo> = Result.success(
        ReverseGeocodeInfo(countryCode = "US", countryName = "United States", regionName = null)
    )

    override suspend fun search(query: String, limit: Int): Result<List<GeocodingSuggestion>> {
        callCount++
        lastQuery = query
        return result
    }

    override suspend fun reverseGeocodeInfo(latitude: Double, longitude: Double): Result<ReverseGeocodeInfo> {
        return reverseGeocodeResult
    }
}

package com.harazone.di

import com.harazone.data.local.AreaDiscoveryDatabase
import com.harazone.data.local.DatabaseDriverFactory
import com.harazone.data.remote.BuildKonfigApiKeyProvider
import com.harazone.data.remote.GeminiAreaIntelligenceProvider
import com.harazone.data.remote.GeminiPromptBuilder
import com.harazone.data.remote.GeminiResponseParser
import com.harazone.data.remote.HttpClientFactory
import com.harazone.data.remote.MapTilerGeocodingProvider
import com.harazone.data.remote.WikipediaImageRepository
import com.harazone.data.repository.AreaRepositoryImpl
import com.harazone.data.repository.RecentPlacesRepositoryImpl
import com.harazone.data.repository.ProfileIdentityCacheRepository
import com.harazone.data.repository.SavedPoiRepositoryImpl
import com.harazone.data.repository.UserPreferencesRepository
import com.harazone.domain.provider.ApiKeyProvider
import com.harazone.domain.repository.RecentPlacesRepository
import com.harazone.domain.repository.SavedPoiRepository
import com.harazone.domain.provider.AreaIntelligenceProvider
import com.harazone.domain.repository.AreaRepository
import com.harazone.domain.service.PrivacyPipeline
import com.harazone.domain.usecase.GetAreaPortraitUseCase
import com.harazone.data.remote.OpenMeteoWeatherProvider
import com.harazone.domain.provider.WeatherProvider
import com.harazone.util.AppClock
import com.harazone.util.ConnectivityMonitor
import com.harazone.domain.companion.CompanionNudgeEngine
import com.harazone.domain.provider.AdvisoryProvider
import com.harazone.data.remote.FcdoAdvisoryProvider
import com.harazone.data.remote.GooglePlacesProvider
import com.harazone.domain.provider.PlacesProvider
import com.harazone.util.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.qualifier.named
import org.koin.dsl.module

val dataModule = module {
    single<ApiKeyProvider> { BuildKonfigApiKeyProvider() }
    single { GeminiPromptBuilder() }
    single { GeminiResponseParser() }
    // App-scoped singleton — lives for process lifetime. Koin 4.x KMP does not
    // support onClose callbacks; close() will be called via KoinApplication.close()
    // when the app terminates or Koin is stopped.
    single { HttpClientFactory.create() }
    single { WikipediaImageRepository(get()) }
    single<AreaIntelligenceProvider> { GeminiAreaIntelligenceProvider(get(), get(), get(), get()) }
    single { PrivacyPipeline(get()) }

    // Database & caching (Story 2.3)
    single { AreaDiscoveryDatabase(get<DatabaseDriverFactory>().createDriver()) }
    single<AppClock> { SystemClock() }
    single(named("appScope")) { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
    single<PlacesProvider> { GooglePlacesProvider(get(), get(), get(), get()) }
    single<AreaRepository> {
        AreaRepositoryImpl(
            aiProvider = get(),
            database = get(),
            scope = get(named("appScope")),
            clock = get(),
            connectivityObserver = { get<ConnectivityMonitor>().observe() },
            wikipediaImageRepository = get(),
            placesProvider = get(),
        )
    }
    single { GetAreaPortraitUseCase(get()) }
    single<WeatherProvider> { OpenMeteoWeatherProvider(get()) }
    single { MapTilerGeocodingProvider(get(), get()) }
    single<RecentPlacesRepository> { RecentPlacesRepositoryImpl(get(), get()) }
    single<SavedPoiRepository> { SavedPoiRepositoryImpl(get(), get()) }
    single { UserPreferencesRepository(get()) }
    single { ProfileIdentityCacheRepository(get(), get()) }
    single { CompanionNudgeEngine(get(), get(), get()) }
    single<AdvisoryProvider> { FcdoAdvisoryProvider(get(), get(), get()) }
}

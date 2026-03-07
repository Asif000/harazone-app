package com.areadiscovery.di

import com.areadiscovery.data.local.AreaDiscoveryDatabase
import com.areadiscovery.data.local.DatabaseDriverFactory
import com.areadiscovery.data.remote.BuildKonfigApiKeyProvider
import com.areadiscovery.data.remote.GeminiAreaIntelligenceProvider
import com.areadiscovery.data.remote.GeminiPromptBuilder
import com.areadiscovery.data.remote.GeminiResponseParser
import com.areadiscovery.data.remote.HttpClientFactory
import com.areadiscovery.data.remote.WikipediaImageRepository
import com.areadiscovery.data.repository.AreaRepositoryImpl
import com.areadiscovery.domain.provider.ApiKeyProvider
import com.areadiscovery.domain.provider.AreaIntelligenceProvider
import com.areadiscovery.domain.repository.AreaRepository
import com.areadiscovery.domain.service.PrivacyPipeline
import com.areadiscovery.domain.usecase.GetAreaPortraitUseCase
import com.areadiscovery.data.remote.OpenMeteoWeatherProvider
import com.areadiscovery.domain.provider.WeatherProvider
import com.areadiscovery.util.AppClock
import com.areadiscovery.util.ConnectivityMonitor
import com.areadiscovery.util.SystemClock
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
    single<AreaRepository> {
        AreaRepositoryImpl(
            aiProvider = get(),
            database = get(),
            scope = get(named("appScope")),
            clock = get(),
            connectivityObserver = { get<ConnectivityMonitor>().observe() },
            wikipediaImageRepository = get(),
        )
    }
    single { GetAreaPortraitUseCase(get()) }
    single<WeatherProvider> { OpenMeteoWeatherProvider(get()) }
}

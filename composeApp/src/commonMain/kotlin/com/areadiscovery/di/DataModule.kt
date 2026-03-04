package com.areadiscovery.di

import com.areadiscovery.data.remote.BuildKonfigApiKeyProvider
import com.areadiscovery.data.remote.GeminiAreaIntelligenceProvider
import com.areadiscovery.data.remote.GeminiPromptBuilder
import com.areadiscovery.data.remote.GeminiResponseParser
import com.areadiscovery.data.remote.HttpClientFactory
import com.areadiscovery.domain.provider.ApiKeyProvider
import com.areadiscovery.domain.provider.AreaIntelligenceProvider
import com.areadiscovery.domain.service.PrivacyPipeline
import org.koin.dsl.module

val dataModule = module {
    single<ApiKeyProvider> { BuildKonfigApiKeyProvider() }
    single { GeminiPromptBuilder() }
    single { GeminiResponseParser() }
    single { HttpClientFactory.create() }
    single<AreaIntelligenceProvider> { GeminiAreaIntelligenceProvider(get(), get(), get(), get()) }
    single { PrivacyPipeline(get()) }
}

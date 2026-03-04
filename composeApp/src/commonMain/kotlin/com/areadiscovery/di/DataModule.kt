package com.areadiscovery.di

import com.areadiscovery.data.remote.MockAreaIntelligenceProvider
import com.areadiscovery.domain.provider.AreaIntelligenceProvider
import com.areadiscovery.domain.service.PrivacyPipeline
import org.koin.dsl.module

val dataModule = module {
    single<AreaIntelligenceProvider> { MockAreaIntelligenceProvider() }
    single { PrivacyPipeline(get()) }
}

package com.areadiscovery.di

import com.areadiscovery.util.AnalyticsTracker
import com.areadiscovery.util.IosAnalyticsTracker
import org.koin.dsl.module

actual fun platformModule() = module {
    single<AnalyticsTracker> { IosAnalyticsTracker() }
}

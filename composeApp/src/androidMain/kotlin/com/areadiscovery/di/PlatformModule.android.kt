package com.areadiscovery.di

import com.areadiscovery.util.AnalyticsTracker
import com.areadiscovery.util.AndroidAnalyticsTracker
import org.koin.dsl.module

actual fun platformModule() = module {
    single<AnalyticsTracker> { AndroidAnalyticsTracker() }
}

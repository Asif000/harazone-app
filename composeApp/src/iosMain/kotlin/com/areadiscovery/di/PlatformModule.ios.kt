package com.areadiscovery.di

import com.areadiscovery.data.local.DatabaseDriverFactory
import com.areadiscovery.location.IosLocationProvider
import com.areadiscovery.location.LocationProvider
import com.areadiscovery.util.AnalyticsTracker
import com.areadiscovery.util.ConnectivityMonitor
import com.areadiscovery.util.IosAnalyticsTracker
import org.koin.dsl.module

actual fun platformModule() = module {
    single<AnalyticsTracker> { IosAnalyticsTracker() }
    single<LocationProvider> { IosLocationProvider() }
    single { DatabaseDriverFactory() }
    single { ConnectivityMonitor() }
}

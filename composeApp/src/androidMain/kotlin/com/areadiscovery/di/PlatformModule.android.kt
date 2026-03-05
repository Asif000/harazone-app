package com.areadiscovery.di

import com.areadiscovery.data.local.DatabaseDriverFactory
import com.areadiscovery.location.AndroidLocationProvider
import com.areadiscovery.location.LocationProvider
import com.areadiscovery.util.AnalyticsTracker
import com.areadiscovery.util.AndroidAnalyticsTracker
import com.areadiscovery.util.ConnectivityMonitor
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

actual fun platformModule() = module {
    single<AnalyticsTracker> { AndroidAnalyticsTracker() }
    single<LocationProvider> { AndroidLocationProvider(androidContext()) }
    single { DatabaseDriverFactory(androidContext()) }
    single { ConnectivityMonitor(androidContext()) }
}

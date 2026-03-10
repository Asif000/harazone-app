package com.harazone.di

import com.harazone.data.local.DatabaseDriverFactory
import com.harazone.location.AndroidLocationProvider
import com.harazone.location.LocationProvider
import com.harazone.util.AnalyticsTracker
import com.harazone.util.AndroidAnalyticsTracker
import com.harazone.util.ConnectivityMonitor
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

actual fun platformModule() = module {
    single<AnalyticsTracker> { AndroidAnalyticsTracker() }
    single<LocationProvider> { AndroidLocationProvider(androidContext()) }
    single { DatabaseDriverFactory(androidContext()) }
    single { ConnectivityMonitor(androidContext()) }
}

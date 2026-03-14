package com.harazone.di

import com.harazone.data.local.DatabaseDriverFactory
import com.harazone.feedback.FeedbackReporter
import com.harazone.feedback.IosFeedbackReporter
import com.harazone.feedback.IosShakeDetector
import com.harazone.feedback.ShakeDetector
import com.harazone.location.IosLocationProvider
import com.harazone.location.LocationProvider
import com.harazone.util.AnalyticsTracker
import com.harazone.util.ConnectivityMonitor
import com.harazone.util.IosAnalyticsTracker
import org.koin.dsl.module

actual fun platformModule() = module {
    single<AnalyticsTracker> { IosAnalyticsTracker() }
    single<LocationProvider> { IosLocationProvider() }
    single { DatabaseDriverFactory() }
    single { ConnectivityMonitor() }
    single<FeedbackReporter> { IosFeedbackReporter() }
    single<ShakeDetector> { IosShakeDetector() }
}

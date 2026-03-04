package com.areadiscovery.util

class IosAnalyticsTracker : AnalyticsTracker {
    override fun trackEvent(name: String, params: Map<String, String>) {
        // TODO: Wire Firebase Analytics iOS after CocoaPods Kotlin interop is configured
        AppLogger.d { "trackEvent: $name $params" }
    }
}

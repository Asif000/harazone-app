package com.areadiscovery.util

class NoOpAnalyticsTracker : AnalyticsTracker {
    override fun trackEvent(name: String, params: Map<String, String>) = Unit
}

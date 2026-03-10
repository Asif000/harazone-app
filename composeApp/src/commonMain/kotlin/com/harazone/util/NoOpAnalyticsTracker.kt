package com.harazone.util

class NoOpAnalyticsTracker : AnalyticsTracker {
    override fun trackEvent(name: String, params: Map<String, String>) = Unit
}

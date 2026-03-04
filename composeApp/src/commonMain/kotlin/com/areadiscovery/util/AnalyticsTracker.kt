package com.areadiscovery.util

interface AnalyticsTracker {
    fun trackEvent(name: String, params: Map<String, String> = emptyMap())
}

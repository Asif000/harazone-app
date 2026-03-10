package com.harazone.util

interface AnalyticsTracker {
    fun trackEvent(name: String, params: Map<String, String> = emptyMap())
}

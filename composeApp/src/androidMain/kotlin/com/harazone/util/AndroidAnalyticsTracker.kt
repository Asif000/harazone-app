package com.harazone.util

import com.google.firebase.Firebase
import com.google.firebase.analytics.analytics
import com.google.firebase.analytics.logEvent

class AndroidAnalyticsTracker : AnalyticsTracker {
    private val firebaseAnalytics = Firebase.analytics

    override fun trackEvent(name: String, params: Map<String, String>) {
        firebaseAnalytics.logEvent(name) {
            params.forEach { (key, value) -> param(key, value) }
        }
    }
}

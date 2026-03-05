package com.areadiscovery.fakes

import com.areadiscovery.util.AnalyticsTracker
import kotlin.test.assertTrue

class FakeAnalyticsTracker : AnalyticsTracker {
    val recordedEvents = mutableListOf<Pair<String, Map<String, String>>>()

    fun reset() {
        recordedEvents.clear()
    }

    override fun trackEvent(name: String, params: Map<String, String>) {
        recordedEvents += name to params
    }

    fun assertEventTracked(name: String, params: Map<String, String> = emptyMap()) {
        val found = recordedEvents.any { it.first == name && it.second == params }
        assertTrue(found, "Expected event '$name' with params $params to be tracked. Recorded: $recordedEvents")
    }
}

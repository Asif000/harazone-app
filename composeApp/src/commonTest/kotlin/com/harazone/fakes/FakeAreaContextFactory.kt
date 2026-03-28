package com.harazone.fakes

import com.harazone.domain.model.AreaContext
import com.harazone.domain.model.DiscoveryMode
import com.harazone.domain.service.AreaContextFactory

class FakeAreaContextFactory(
    private val context: AreaContext = AreaContext(
        timeOfDay = "morning",
        dayOfWeek = "Wednesday",
        visitCount = 0,
        preferredLanguage = "en",
    ),
) : AreaContextFactory(FakeClock(), FakeLocaleProvider()) {

    var callCount = 0
        private set

    override fun create(discoveryMode: DiscoveryMode): AreaContext {
        callCount++
        return context.copy(discoveryMode = discoveryMode)
    }
}

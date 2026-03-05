package com.areadiscovery.fakes

import com.areadiscovery.domain.model.AreaContext
import com.areadiscovery.domain.service.AreaContextFactory

class FakeAreaContextFactory(
    private val context: AreaContext = AreaContext(
        timeOfDay = "morning",
        dayOfWeek = "Wednesday",
        visitCount = 0,
        preferredLanguage = "en",
    ),
) : AreaContextFactory(FakeClock()) {

    var callCount = 0
        private set

    override fun create(): AreaContext {
        callCount++
        return context
    }
}

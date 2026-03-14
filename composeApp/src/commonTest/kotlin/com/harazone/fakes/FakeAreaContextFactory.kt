package com.harazone.fakes

import com.harazone.domain.model.AreaContext
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

    override fun create(): AreaContext {
        callCount++
        return context
    }
}

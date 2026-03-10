package com.harazone.fakes

import com.harazone.domain.service.PrivacyPipeline
import com.harazone.location.LocationProvider

open class FakePrivacyPipeline(
    private var result: Result<String> = Result.success("Test Area"),
) : PrivacyPipeline(FakeLocationProvider()) {

    var callCount = 0
        private set

    fun setResult(result: Result<String>) {
        this.result = result
    }

    override suspend fun resolveAreaName(): Result<String> {
        callCount++
        return result
    }
}

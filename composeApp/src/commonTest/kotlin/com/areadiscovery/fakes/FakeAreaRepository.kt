package com.areadiscovery.fakes

import com.areadiscovery.domain.model.AreaContext
import com.areadiscovery.domain.model.BucketUpdate
import com.areadiscovery.domain.repository.AreaRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow

class FakeAreaRepository(
    private val updates: List<BucketUpdate> = listOf(BucketUpdate.PortraitComplete(emptyList()))
) : AreaRepository {
    var callCount = 0
        private set

    override fun getAreaPortrait(areaName: String, context: AreaContext): Flow<BucketUpdate> {
        callCount++
        return updates.asFlow()
    }
}

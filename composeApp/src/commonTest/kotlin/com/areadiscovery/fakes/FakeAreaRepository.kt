package com.areadiscovery.fakes

import com.areadiscovery.domain.model.AreaContext
import com.areadiscovery.domain.model.BucketUpdate
import com.areadiscovery.domain.repository.AreaRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

class FakeAreaRepository : AreaRepository {
    override fun getAreaPortrait(areaName: String, context: AreaContext): Flow<BucketUpdate> =
        emptyFlow()
}

package com.harazone.fakes

import com.harazone.domain.model.AreaContext
import com.harazone.domain.model.BucketUpdate
import com.harazone.domain.repository.AreaRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow

class FakeAreaRepository(
    private val updates: List<BucketUpdate> = listOf(BucketUpdate.PortraitComplete(emptyList())),
    private val updatesFactory: (() -> List<BucketUpdate>)? = null,
) : AreaRepository {
    var callCount = 0
        private set

    override fun getAreaPortrait(areaName: String, context: AreaContext): Flow<BucketUpdate> {
        callCount++
        val result = updatesFactory?.invoke() ?: updates
        return result.asFlow()
    }
}

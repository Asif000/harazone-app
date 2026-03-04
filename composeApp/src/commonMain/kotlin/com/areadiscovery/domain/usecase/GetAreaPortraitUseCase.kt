package com.areadiscovery.domain.usecase

import com.areadiscovery.domain.model.AreaContext
import com.areadiscovery.domain.model.BucketUpdate
import com.areadiscovery.domain.repository.AreaRepository
import kotlinx.coroutines.flow.Flow

class GetAreaPortraitUseCase(private val repository: AreaRepository) {
    operator fun invoke(areaName: String, context: AreaContext): Flow<BucketUpdate> =
        repository.getAreaPortrait(areaName, context)
}

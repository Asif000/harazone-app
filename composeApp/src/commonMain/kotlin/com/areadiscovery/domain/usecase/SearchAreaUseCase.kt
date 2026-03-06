package com.areadiscovery.domain.usecase

import com.areadiscovery.domain.model.AreaContext
import com.areadiscovery.domain.model.BucketUpdate
import com.areadiscovery.domain.repository.AreaRepository
import kotlinx.coroutines.flow.Flow

open class SearchAreaUseCase(private val repository: AreaRepository) {
    open operator fun invoke(areaName: String, context: AreaContext): Flow<BucketUpdate> =
        repository.getAreaPortrait(areaName, context)
}

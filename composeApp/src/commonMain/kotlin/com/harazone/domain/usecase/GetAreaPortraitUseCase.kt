package com.harazone.domain.usecase

import com.harazone.domain.model.AreaContext
import com.harazone.domain.model.BucketUpdate
import com.harazone.domain.repository.AreaRepository
import kotlinx.coroutines.flow.Flow

open class GetAreaPortraitUseCase(private val repository: AreaRepository) {
    open operator fun invoke(areaName: String, context: AreaContext): Flow<BucketUpdate> =
        repository.getAreaPortrait(areaName, context)
}

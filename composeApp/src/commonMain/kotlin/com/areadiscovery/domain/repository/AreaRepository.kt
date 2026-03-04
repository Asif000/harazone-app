package com.areadiscovery.domain.repository

import com.areadiscovery.domain.model.AreaContext
import com.areadiscovery.domain.model.BucketUpdate
import kotlinx.coroutines.flow.Flow

interface AreaRepository {
    fun getAreaPortrait(areaName: String, context: AreaContext): Flow<BucketUpdate>
}

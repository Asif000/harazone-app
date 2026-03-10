package com.harazone.domain.repository

import com.harazone.domain.model.AreaContext
import com.harazone.domain.model.BucketUpdate
import kotlinx.coroutines.flow.Flow

interface AreaRepository {
    fun getAreaPortrait(areaName: String, context: AreaContext): Flow<BucketUpdate>
}

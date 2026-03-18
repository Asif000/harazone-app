package com.harazone.domain.provider

import com.harazone.domain.model.AreaAdvisory

interface AdvisoryProvider {
    suspend fun getAdvisory(countryCode: String, regionName: String? = null): Result<AreaAdvisory>
}

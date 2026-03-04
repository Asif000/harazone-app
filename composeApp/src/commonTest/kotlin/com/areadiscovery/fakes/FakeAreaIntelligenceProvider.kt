package com.areadiscovery.fakes

import com.areadiscovery.domain.model.AreaContext
import com.areadiscovery.domain.model.BucketContent
import com.areadiscovery.domain.model.BucketType
import com.areadiscovery.domain.model.BucketUpdate
import com.areadiscovery.domain.model.ChatMessage
import com.areadiscovery.domain.model.ChatToken
import com.areadiscovery.domain.model.Confidence
import com.areadiscovery.domain.provider.AreaIntelligenceProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow

class FakeAreaIntelligenceProvider : AreaIntelligenceProvider {
    var emissions: List<BucketUpdate> = emptyList()
    var shouldThrow: Boolean = false
    var errorMessage: String = "Test error"
    var callCount = 0
    var responseFlow: Flow<BucketUpdate>? = null

    override fun streamAreaPortrait(areaName: String, context: AreaContext): Flow<BucketUpdate> {
        callCount++
        responseFlow?.let { return it }
        return flow {
            if (shouldThrow) throw RuntimeException(errorMessage)
            if (emissions.isNotEmpty()) {
                emissions.forEach { emit(it) }
            } else {
                defaultBucketEmissions().forEach { emit(it) }
            }
        }
    }

    override fun streamChatResponse(
        query: String,
        areaName: String,
        conversationHistory: List<ChatMessage>,
    ): Flow<ChatToken> = emptyFlow()
}

fun defaultBucketEmissions(): List<BucketUpdate> = buildList {
    BucketType.entries.forEach { type ->
        add(
            BucketUpdate.BucketComplete(
                BucketContent(
                    type = type,
                    highlight = "Test highlight $type",
                    content = "Test content $type",
                    confidence = Confidence.HIGH,
                    sources = emptyList()
                )
            )
        )
    }
    add(BucketUpdate.PortraitComplete(pois = emptyList()))
}

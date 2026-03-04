package com.areadiscovery.fakes

import com.areadiscovery.domain.model.AreaContext
import com.areadiscovery.domain.model.BucketUpdate
import com.areadiscovery.domain.model.ChatMessage
import com.areadiscovery.domain.model.ChatToken
import com.areadiscovery.domain.provider.AreaIntelligenceProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class FakeAreaIntelligenceProvider : AreaIntelligenceProvider {
    var emissions: List<BucketUpdate> = emptyList()
    var shouldThrow: Boolean = false
    var errorMessage: String = "Test error"

    override fun streamAreaPortrait(areaName: String, context: AreaContext): Flow<BucketUpdate> = flow {
        if (shouldThrow) throw RuntimeException(errorMessage)
        emissions.forEach { emit(it) }
    }

    override fun streamChatResponse(
        query: String,
        areaName: String,
        conversationHistory: List<ChatMessage>,
    ): Flow<ChatToken> = flow {
        // Not used in this story
    }
}

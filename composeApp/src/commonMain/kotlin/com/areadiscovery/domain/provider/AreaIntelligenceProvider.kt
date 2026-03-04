package com.areadiscovery.domain.provider

import com.areadiscovery.domain.model.AreaContext
import com.areadiscovery.domain.model.BucketUpdate
import com.areadiscovery.domain.model.ChatMessage
import com.areadiscovery.domain.model.ChatToken
import kotlinx.coroutines.flow.Flow

interface AreaIntelligenceProvider {
    fun streamAreaPortrait(areaName: String, context: AreaContext): Flow<BucketUpdate>
    fun streamChatResponse(query: String, areaName: String, conversationHistory: List<ChatMessage>): Flow<ChatToken>
}

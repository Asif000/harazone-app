package com.harazone.domain.provider

import com.harazone.domain.model.AreaContext
import com.harazone.domain.model.BucketUpdate
import com.harazone.domain.model.ChatMessage
import com.harazone.domain.model.ChatToken
import kotlinx.coroutines.flow.Flow

interface AreaIntelligenceProvider {
    fun streamAreaPortrait(areaName: String, context: AreaContext): Flow<BucketUpdate>
    fun streamChatResponse(query: String, areaName: String, conversationHistory: List<ChatMessage>): Flow<ChatToken>
}

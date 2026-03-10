package com.harazone.fakes

import com.harazone.domain.model.AreaContext
import com.harazone.domain.model.BucketContent
import com.harazone.domain.model.BucketType
import com.harazone.domain.model.BucketUpdate
import com.harazone.domain.model.ChatMessage
import com.harazone.domain.model.ChatToken
import com.harazone.domain.model.Confidence
import com.harazone.domain.provider.AreaIntelligenceProvider
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

    var chatTokens: List<ChatToken> = emptyList()
    var shouldThrowChat: Boolean = false
    var chatCallCount = 0
    var lastChatHistory: List<ChatMessage> = emptyList()

    override fun streamChatResponse(
        query: String,
        areaName: String,
        conversationHistory: List<ChatMessage>,
    ): Flow<ChatToken> {
        chatCallCount++
        lastChatHistory = conversationHistory
        return flow {
            if (shouldThrowChat) throw RuntimeException("Chat test error")
            chatTokens.forEach { emit(it) }
        }
    }
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

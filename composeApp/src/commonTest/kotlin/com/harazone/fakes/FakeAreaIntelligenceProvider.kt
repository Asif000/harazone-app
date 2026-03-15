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

    var poiContextResult: Triple<String, String, String>? = Triple(
        "Mock context blurb.",
        "Great time to visit.",
        "",
    )
    var shouldReturnNullPoiContext: Boolean = false
    var poiContextCallCount = 0

    override suspend fun generatePoiContext(
        poiName: String,
        poiType: String,
        areaName: String,
        timeHint: String,
        languageTag: String,
    ): Triple<String, String, String>? {
        poiContextCallCount++
        return if (shouldReturnNullPoiContext) null else poiContextResult
    }

    var chatTokens: List<ChatToken> = emptyList()
    var shouldThrowChat: Boolean = false
    var chatCallCount = 0
    var lastChatHistory: List<ChatMessage> = emptyList()
    var lastChatQuery: String = ""

    override fun streamChatResponse(
        query: String,
        areaName: String,
        conversationHistory: List<ChatMessage>,
    ): Flow<ChatToken> {
        chatCallCount++
        lastChatHistory = conversationHistory
        lastChatQuery = query
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

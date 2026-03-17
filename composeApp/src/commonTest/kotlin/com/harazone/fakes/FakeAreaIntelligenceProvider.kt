package com.harazone.fakes

import com.harazone.domain.model.AreaContext
import com.harazone.domain.model.BucketContent
import com.harazone.domain.model.BucketType
import com.harazone.domain.model.BucketUpdate
import com.harazone.domain.model.ChatMessage
import com.harazone.domain.model.ChatToken
import com.harazone.domain.model.Confidence
import com.harazone.domain.model.EngagementLevel
import com.harazone.domain.model.GeoArea
import com.harazone.domain.model.ProfileIdentity
import com.harazone.domain.model.SavedPoi
import com.harazone.domain.model.TasteProfile
import com.harazone.domain.model.VibeInsight
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
    var profileIdentityResult: ProfileIdentity? = ProfileIdentity(
        explorerName = "Test Explorer",
        tagline = "Testing the unknown",
        avatarEmoji = "🧪",
        totalVisits = 5,
        totalAreas = 2,
        totalVibes = 3,
        geoFootprint = listOf(GeoArea("TestArea", "XX")),
        vibeInsights = listOf(VibeInsight("culture", "Test insight")),
    )
    var shouldReturnNullProfileIdentity: Boolean = false
    var profileIdentityCallCount = 0

    override suspend fun generateProfileIdentity(
        savedPois: List<SavedPoi>,
        tasteProfile: TasteProfile,
        engagementLevel: EngagementLevel,
        languageTag: String,
    ): ProfileIdentity? {
        profileIdentityCallCount++
        return if (shouldReturnNullProfileIdentity) null else profileIdentityResult
    }

    var companionNudgeResult: String? = "Test companion nudge"
    var companionNudgeCallCount = 0

    override suspend fun generateCompanionNudge(
        promptType: String,
        context: String,
        languageTag: String,
    ): String? {
        companionNudgeCallCount++
        return companionNudgeResult
    }

    var profileChatTokens: List<ChatToken> = listOf(
        ChatToken(text = "Fake profile chat response", isComplete = false),
        ChatToken(text = "", isComplete = true),
    )
    var shouldThrowProfileChat: Boolean = false
    var profileChatCallCount = 0

    override fun streamProfileChat(
        query: String,
        savedPois: List<SavedPoi>,
        tasteProfile: TasteProfile,
        conversationHistory: List<ChatMessage>,
        languageTag: String,
    ): Flow<ChatToken> {
        profileChatCallCount++
        return flow {
            if (shouldThrowProfileChat) throw RuntimeException("Profile chat test error")
            profileChatTokens.forEach { emit(it) }
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

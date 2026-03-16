package com.harazone.domain.provider

import com.harazone.domain.model.AreaContext
import com.harazone.domain.model.BucketUpdate
import com.harazone.domain.model.ChatMessage
import com.harazone.domain.model.ChatToken
import com.harazone.domain.model.EngagementLevel
import com.harazone.domain.model.ProfileIdentity
import com.harazone.domain.model.SavedPoi
import com.harazone.domain.model.TasteProfile
import kotlinx.coroutines.flow.Flow

interface AreaIntelligenceProvider {
    fun streamAreaPortrait(areaName: String, context: AreaContext): Flow<BucketUpdate>
    fun streamChatResponse(query: String, areaName: String, conversationHistory: List<ChatMessage>): Flow<ChatToken>
    suspend fun generatePoiContext(poiName: String, poiType: String, areaName: String, timeHint: String, languageTag: String = "en"): Triple<String, String, String>?
    suspend fun generateProfileIdentity(
        savedPois: List<SavedPoi>,
        tasteProfile: TasteProfile,
        engagementLevel: EngagementLevel,
        languageTag: String = "en",
    ): ProfileIdentity?
    fun streamProfileChat(
        query: String,
        savedPois: List<SavedPoi>,
        tasteProfile: TasteProfile,
        conversationHistory: List<ChatMessage>,
        languageTag: String = "en",
    ): Flow<ChatToken>
}

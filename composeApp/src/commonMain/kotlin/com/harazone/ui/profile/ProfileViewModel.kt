package com.harazone.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.harazone.domain.model.ChatMessage
import com.harazone.domain.model.ChatToken
import com.harazone.domain.model.DomainError
import com.harazone.domain.model.DomainErrorException
import com.harazone.domain.model.EngagementLevel
import com.harazone.domain.model.MessageRole
import com.harazone.domain.model.POI
import com.harazone.domain.model.ProfileIdentity
import com.harazone.domain.model.SavedPoi
import com.harazone.domain.model.TasteProfileBuilder
import com.harazone.domain.provider.AreaIntelligenceProvider
import com.harazone.domain.provider.LocaleProvider
import com.harazone.domain.repository.SavedPoiRepository
import com.harazone.data.repository.ProfileIdentityCacheRepository
import com.harazone.util.AppClock
import com.harazone.util.AppLogger
import com.harazone.util.generateUuid
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GeoEntry(
    val areaName: String,
    val countryCode: String,
    val countryFlag: String,
    val poiCount: Int,
    val isHome: Boolean,
)

data class VibeGroup(
    val vibeName: String,
    val poiCount: Int,
    val isTop: Boolean,
    val places: List<SavedPoi>,
    val aiInsight: String,
)

data class ProfileChatBubble(
    val id: String,
    val text: String,
    val isUser: Boolean,
    val isError: Boolean = false,
)

data class ProfileUiState(
    val isLoading: Boolean = true,
    val identity: ProfileIdentity? = null,
    val geoFootprint: List<GeoEntry> = emptyList(),
    val vibeGroups: List<VibeGroup> = emptyList(),
    val expandedVibe: String? = null,
    val chatBubbles: List<ProfileChatBubble> = emptyList(),
    val isStreaming: Boolean = false,
    val suggestionPills: List<String> = emptyList(),
    val isOffline: Boolean = false,
    val identityRefreshAvailable: Boolean = false,
    val error: String? = null,
)

class ProfileViewModel(
    private val savedPoiRepository: SavedPoiRepository,
    private val aiProvider: AreaIntelligenceProvider,
    private val cacheRepository: ProfileIdentityCacheRepository,
    private val clock: AppClock,
    private val localeProvider: LocaleProvider,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    private var savedPois: List<SavedPoi> = emptyList()
    private var streamJob: Job? = null
    private var lastUserMessage: String? = null
    private var sessionIdentity: ProfileIdentity? = null
    private var pendingRefreshIdentity: ProfileIdentity? = null

    companion object {
        private const val MAX_HISTORY_TURNS = 20
    }

    init {
        viewModelScope.launch {
            savedPoiRepository.observeAll().collect { pois ->
                savedPois = pois
                if (pois.isEmpty()) {
                    _uiState.update {
                        it.copy(isLoading = false, identity = null, error = null)
                    }
                    return@collect
                }
                loadProfile(pois)
            }
        }
    }

    private suspend fun loadProfile(pois: List<SavedPoi>) {
        val geoFootprint = buildGeoFootprint(pois, null)
        val vibeGroups = buildVibeGroups(pois, null)

        // Try cache first
        val cached = cacheRepository.getCached()
        val currentHash = cacheRepository.computeInputHash(pois)

        if (cached != null) {
            val (cachedIdentity, cachedHash) = cached
            if (sessionIdentity == null) {
                sessionIdentity = cachedIdentity
            }
            val geo = buildGeoFootprint(pois, cachedIdentity)
            val vibes = buildVibeGroups(pois, cachedIdentity)
            _uiState.update {
                it.copy(
                    isLoading = false,
                    identity = sessionIdentity,
                    geoFootprint = geo,
                    vibeGroups = vibes,
                    chatBubbles = it.chatBubbles,
                    suggestionPills = it.suggestionPills,
                )
            }

            if (cachedHash != currentHash) {
                // Background refresh
                fetchFreshIdentity(pois, showShimmer = false)
            }
        } else {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    geoFootprint = geoFootprint,
                    vibeGroups = vibeGroups,
                )
            }
            fetchFreshIdentity(pois, showShimmer = true)
        }
    }

    private suspend fun fetchFreshIdentity(pois: List<SavedPoi>, showShimmer: Boolean) {
        val tasteProfile = TasteProfileBuilder.build(pois, clock.nowMs())
        val engagement = EngagementLevel.from(pois, clock.nowMs())
        val freshIdentity = aiProvider.generateProfileIdentity(
            savedPois = pois,
            tasteProfile = tasteProfile,
            engagementLevel = engagement,
            languageTag = localeProvider.languageTag,
        )

        if (freshIdentity != null) {
            val currentHash = cacheRepository.computeInputHash(pois)
            cacheRepository.cache(freshIdentity, currentHash)

            if (sessionIdentity != null && !showShimmer) {
                // Already showing cached — offer refresh pill
                pendingRefreshIdentity = freshIdentity
                _uiState.update { it.copy(identityRefreshAvailable = true) }
            } else {
                sessionIdentity = freshIdentity
                val geo = buildGeoFootprint(pois, freshIdentity)
                val vibes = buildVibeGroups(pois, freshIdentity)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        identity = freshIdentity,
                        geoFootprint = geo,
                        vibeGroups = vibes,
                        chatBubbles = it.chatBubbles,
                        suggestionPills = it.suggestionPills,
                    )
                }
            }
        } else if (showShimmer) {
            // Fallback — show client-side stats
            val geo = buildGeoFootprint(pois, null)
            val vibes = buildVibeGroups(pois, null)
            _uiState.update {
                it.copy(
                    isLoading = false,
                    identity = null,
                    geoFootprint = geo,
                    vibeGroups = vibes,
                    error = "identity_failed",
                    chatBubbles = it.chatBubbles,
                    suggestionPills = it.suggestionPills,
                )
            }
        }
    }

    fun setLocalizedStrings(greeting: String, pills: List<String>) {
        if (_uiState.value.chatBubbles.isEmpty()) {
            _uiState.update {
                it.copy(
                    chatBubbles = listOf(ProfileChatBubble(id = generateUuid(), text = greeting, isUser = false)),
                    suggestionPills = pills,
                )
            }
        }
    }

    fun toggleVibe(vibeName: String) {
        _uiState.update {
            it.copy(expandedVibe = if (it.expandedVibe == vibeName) null else vibeName)
        }
    }

    fun sendMessage(text: String) {
        if (_uiState.value.isStreaming) return
        if (text.isBlank()) return

        lastUserMessage = text
        val userBubble = ProfileChatBubble(
            id = generateUuid(),
            text = text,
            isUser = true,
        )

        _uiState.update {
            it.copy(
                chatBubbles = it.chatBubbles + userBubble,
                isStreaming = true,
                suggestionPills = emptyList(),
                isOffline = false,
            )
        }

        val aiBubbleId = generateUuid()
        _uiState.update {
            it.copy(chatBubbles = it.chatBubbles + ProfileChatBubble(id = aiBubbleId, text = "", isUser = false))
        }

        val history = buildConversationHistory()
        val tasteProfile = TasteProfileBuilder.build(savedPois, clock.nowMs())

        streamJob = viewModelScope.launch {
            val accumulated = StringBuilder()
            aiProvider.streamProfileChat(
                query = text,
                savedPois = savedPois,
                tasteProfile = tasteProfile,
                conversationHistory = history.takeLast(MAX_HISTORY_TURNS),
                languageTag = localeProvider.languageTag,
            ).catch { e ->
                _uiState.update { it.copy(isStreaming = false) }
                val isNetwork = e is DomainErrorException && e.domainError is DomainError.NetworkError
                if (isNetwork) {
                    _uiState.update {
                        it.copy(
                            isOffline = true,
                            chatBubbles = replaceLastAiBubble(it.chatBubbles, aiBubbleId, "Connection lost", isError = true),
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            chatBubbles = replaceLastAiBubble(it.chatBubbles, aiBubbleId, "Something went wrong", isError = true),
                        )
                    }
                }
            }.collect { token ->
                if (token.isComplete) {
                    _uiState.update { it.copy(isStreaming = false) }
                } else {
                    accumulated.append(token.text)
                    _uiState.update {
                        it.copy(
                            chatBubbles = replaceLastAiBubble(it.chatBubbles, aiBubbleId, accumulated.toString()),
                        )
                    }
                }
            }
        }
    }

    fun retryLastMessage() {
        val last = lastUserMessage ?: return
        // Remove last error bubble
        _uiState.update { state ->
            val bubbles = state.chatBubbles.toMutableList()
            if (bubbles.lastOrNull()?.isError == true) {
                bubbles.removeAt(bubbles.lastIndex)
            }
            // Also remove the user message that will be re-sent
            if (bubbles.lastOrNull()?.isUser == true) {
                bubbles.removeAt(bubbles.lastIndex)
            }
            state.copy(chatBubbles = bubbles)
        }
        sendMessage(last)
    }

    fun retryConnection() {
        _uiState.update { it.copy(isOffline = false) }
        val last = lastUserMessage ?: return
        // Remove error bubble only
        _uiState.update { state ->
            val bubbles = state.chatBubbles.toMutableList()
            if (bubbles.lastOrNull()?.isError == true) {
                bubbles.removeAt(bubbles.lastIndex)
            }
            if (bubbles.lastOrNull()?.isUser == true) {
                bubbles.removeAt(bubbles.lastIndex)
            }
            state.copy(chatBubbles = bubbles)
        }
        sendMessage(last)
    }

    fun applyRefreshedIdentity() {
        val refreshed = pendingRefreshIdentity ?: return
        sessionIdentity = refreshed
        pendingRefreshIdentity = null
        val geo = buildGeoFootprint(savedPois, refreshed)
        val vibes = buildVibeGroups(savedPois, refreshed)
        _uiState.update {
            it.copy(
                identity = refreshed,
                geoFootprint = geo,
                vibeGroups = vibes,
                identityRefreshAvailable = false,
            )
        }
    }

    fun tapSuggestionPill(pill: String) {
        sendMessage(pill)
    }

    fun retryIdentity() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            fetchFreshIdentity(savedPois, showShimmer = true)
        }
    }

    fun getPoiForDetail(savedPoi: SavedPoi): POI {
        return POI(
            name = savedPoi.name,
            type = savedPoi.type,
            description = savedPoi.description ?: savedPoi.whySpecial,
            confidence = com.harazone.domain.model.Confidence.HIGH,
            latitude = savedPoi.lat,
            longitude = savedPoi.lng,
            vibe = savedPoi.vibe,
            insight = savedPoi.whySpecial,
            imageUrl = savedPoi.imageUrl,
            rating = savedPoi.rating,
            userNote = savedPoi.userNote,
        )
    }

    private fun buildGeoFootprint(pois: List<SavedPoi>, identity: ProfileIdentity?): List<GeoEntry> {
        val grouped = pois.groupBy { it.areaName }
        val entries = grouped.map { (areaName, areaPois) ->
            val countryCode = identity?.geoFootprint
                ?.firstOrNull { it.areaName.trim().equals(areaName.trim(), ignoreCase = true) }
                ?.countryCode ?: ""
            val flag = if (countryCode.length == 2) {
                countryCode.uppercase().map { c ->
                    val cp = 0x1F1E6 - 'A'.code + c.code
                    codePointToString(cp)
                }.joinToString("")
            } else {
                "🌍"
            }
            GeoEntry(
                areaName = areaName,
                countryCode = countryCode,
                countryFlag = flag,
                poiCount = areaPois.size,
                isHome = false,
            )
        }.sortedByDescending { it.poiCount }

        return entries.mapIndexed { index, entry ->
            entry.copy(isHome = index == 0)
        }
    }

    private fun buildVibeGroups(pois: List<SavedPoi>, identity: ProfileIdentity?): List<VibeGroup> {
        val grouped = pois.filter { it.vibe.isNotBlank() }.groupBy { it.vibe }
        val sorted = grouped.entries.sortedByDescending { it.value.size }
        val topVibe = sorted.firstOrNull()?.key
        return sorted.map { (vibeName, vibePois) ->
            val aiInsight = identity?.vibeInsights
                ?.firstOrNull { it.vibeName.equals(vibeName, ignoreCase = true) }
                ?.insight ?: ""
            VibeGroup(
                vibeName = vibeName,
                poiCount = vibePois.size,
                isTop = vibeName == topVibe,
                places = vibePois,
                aiInsight = aiInsight,
            )
        }
    }

    private fun buildConversationHistory(): List<ChatMessage> {
        return _uiState.value.chatBubbles
            .filter { !it.isError && it.text.isNotBlank() }
            .map { bubble ->
                ChatMessage(
                    id = bubble.id,
                    role = if (bubble.isUser) MessageRole.USER else MessageRole.AI,
                    content = bubble.text,
                    timestamp = clock.nowMs(),
                    sources = emptyList(),
                )
            }
    }

    private fun replaceLastAiBubble(
        bubbles: List<ProfileChatBubble>,
        targetId: String,
        text: String,
        isError: Boolean = false,
    ): List<ProfileChatBubble> {
        return bubbles.map { bubble ->
            if (bubble.id == targetId) bubble.copy(text = text, isError = isError)
            else bubble
        }
    }
}

private fun codePointToString(cp: Int): String {
    if (cp <= 0xFFFF) return Char(cp).toString()
    val hi = ((cp - 0x10000) shr 10) + 0xD800
    val lo = ((cp - 0x10000) and 0x3FF) + 0xDC00
    return "${Char(hi)}${Char(lo)}"
}

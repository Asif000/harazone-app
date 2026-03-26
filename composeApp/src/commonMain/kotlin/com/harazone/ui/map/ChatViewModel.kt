package com.harazone.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.harazone.data.remote.GeminiPromptBuilder
import com.harazone.domain.model.ChatIntent
import com.harazone.domain.model.ChatMessage
import com.harazone.domain.model.ContextualPill
import com.harazone.domain.model.EngagementLevel
import com.harazone.domain.model.MessageRole
import com.harazone.domain.model.DynamicVibe
import com.harazone.domain.model.POI
import com.harazone.domain.model.SavedPoi
import com.harazone.domain.model.TasteProfileBuilder
import com.harazone.domain.model.VisitState
import com.harazone.domain.provider.AreaIntelligenceProvider
import com.harazone.domain.provider.LocaleProvider
import com.harazone.domain.repository.SavedPoiRepository
import com.harazone.util.AppClock
import com.harazone.util.AppLogger
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

internal class ChatViewModel(
    private val aiProvider: AreaIntelligenceProvider,
    private val promptBuilder: GeminiPromptBuilder,
    private val clock: AppClock,
    private val savedPoiRepository: SavedPoiRepository,
    private val localeProvider: LocaleProvider,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var conversationHistory: MutableList<ChatMessage> = mutableListOf()
    private var chatJob: Job? = null
    private var poiContextJob: Job? = null
    private var tipRefreshJob: Job? = null
    private var nextId = 0L
    private fun nextId() = (nextId++).toString()

    private val json = Json { ignoreUnknownKeys = true }
    private var lastParseOffset = 0
    private var parsedCards = mutableListOf<ChatPoiCard>()
    private var pendingSaveIds = setOf<String>()
    private var pendingUnsaveIds = setOf<String>()
    private var latestSavedPois: List<SavedPoi> = emptyList()
    private var sessionPois: List<POI> = emptyList()
    private var selectedIntent: ChatIntent? = null
    private var currentEngagementLevel: EngagementLevel = EngagementLevel.FRESH
    private var pendingFramingHint: String? = null
    private var isIntentSelected: Boolean = false
    private var currentEntryPoint: ChatEntryPoint = ChatEntryPoint.Default
    private val recommendedPoiNames: MutableList<String> = mutableListOf()
    private var injectedContextIndex: Int = -1
    private var activeVibeName: String? = null
    private var chatOpenedAt: Long = 0L
    private val EXPIRY_MS = 30 * 60 * 1000L // 30 minutes

    companion object {
        private const val MAX_HISTORY_TURNS = 20 // 20 messages = ~10 back-and-forth turns
        internal const val LABEL_NEW_TOPIC = "New topic"
    }

    init {
        viewModelScope.launch {
            savedPoiRepository.observeSavedIds().collect { ids ->
                // Merge DB state with pending optimistic updates to prevent flicker
                val merged = (ids + pendingSaveIds) - pendingUnsaveIds
                _uiState.value = _uiState.value.copy(savedPoiIds = merged)
            }
        }
        viewModelScope.launch {
            savedPoiRepository.observeAll().collect { latestSavedPois = it }
        }
    }

    fun openChat(
        areaName: String,
        pois: List<POI>,
        activeDynamicVibe: DynamicVibe?,
        entryPoint: ChatEntryPoint = ChatEntryPoint.Default,
        forceReset: Boolean = false,
    ) {
        val current = _uiState.value
        // F7/M2: Same area — preserve conversation (whether open or closed)
        // TODO(BACKLOG-MEDIUM): chatOpenedAt measures from first open, not last interaction — re-open at T+31min triggers expiry even if user was active 2min ago. Consider resetting on each re-open.
        if (!forceReset && current.areaName == areaName && (current.isOpen || current.bubbles.isNotEmpty())) {
            val isExpired = current.bubbles.isNotEmpty() && (clock.nowMs() - chatOpenedAt) > EXPIRY_MS
            if (isExpired) {
                _uiState.value = current.copy(isOpen = true, showReturnDialog = true)
                return
            }
            // Pills are persistent — always preserve them on reopen (collapsed from dead if/else)
            _uiState.value = current.copy(isOpen = true)
            return
        }
        // M2: Different area while open — fall through to reset and reinitialize

        chatJob?.cancel()
        conversationHistory = mutableListOf()
        nextId = 0L
        isIntentSelected = false
        selectedIntent = null
        chatOpenedAt = clock.nowMs()
        currentEntryPoint = entryPoint
        val vibeName = activeDynamicVibe?.label
        activeVibeName = vibeName
        recommendedPoiNames.clear()
        injectedContextIndex = -1
        sessionPois = pois

        // Map ChatEntryPoint to plain String — keeps data.remote free of ui.map imports
        pendingFramingHint = when (entryPoint) {
            is ChatEntryPoint.SavesSheet ->
                "The user is currently reviewing their saved places — lead with suggestions based on those first."
            is ChatEntryPoint.PoiCard ->
                "The user is currently looking at ${entryPoint.poi.name} — lead with context about that place."
            is ChatEntryPoint.SavedCard ->
                "The user is currently looking at ${entryPoint.poiName} — lead with context about that place."
            is ChatEntryPoint.VisitAction -> buildVisitFramingHint(entryPoint.poi, entryPoint.visitState)
            is ChatEntryPoint.CompanionNudge ->
                "Continue this thought as the user's travel companion: \"${entryPoint.nudgeText}\". Pick up naturally — expand on it, offer something connected, invite the user deeper."
            is ChatEntryPoint.Default -> null
        }

        _uiState.value = ChatUiState(
            isOpen = true,
            areaName = areaName,
            vibeName = vibeName,
            bubbles = emptyList(),
                        poiCards = emptyList(),
            showSkeletons = false,
            savedPoiIds = _uiState.value.savedPoiIds,
            persistentPills = pillsFor(entryPoint, areaName),
            inputText = preFillFor(entryPoint),
            contextBanner = bannerFor(entryPoint),
            depthLevel = 0,
        )
    }

    fun openChatForPoi(poi: POI, areaName: String, pois: List<POI>, activeDynamicVibe: DynamicVibe?) {
        openChat(areaName, pois, activeDynamicVibe, ChatEntryPoint.PoiCard(poi), forceReset = true)
        tapIntentPill(ContextualPill(
            label = poi.name,
            message = "Tell me about ${poi.name}.",
            intent = ChatIntent.DISCOVER,
            emoji = "\uD83D\uDCCD",
        ))
        poiContextJob?.cancel()
        poiContextJob = viewModelScope.launch {
            fetchPoiContext(poi, areaName)
        }
    }

    fun refreshLocalTip(poi: POI, areaName: String) {
        tipRefreshJob?.cancel()
        val previousTip = _uiState.value.localTip
        _uiState.value = _uiState.value.copy(localTip = null, isTipRefreshing = true)
        tipRefreshJob = viewModelScope.launch {
            val timeHint = currentTimeHint()
            val result = aiProvider.generatePoiContext(poi.name, poi.type, areaName, timeHint, localeProvider.languageTag)
            val newTip = result?.third?.ifBlank { null }
            _uiState.value = _uiState.value.copy(
                localTip = newTip ?: previousTip,
                isTipRefreshing = false,
            )
        }
    }

    fun openChatForPoiVisit(poi: POI, visitState: VisitState, areaName: String, pois: List<POI>, activeDynamicVibe: DynamicVibe?) {
        openChat(areaName, pois, activeDynamicVibe, ChatEntryPoint.VisitAction(poi, visitState), forceReset = true)
        tapIntentPill(ContextualPill(
            label = poi.name,
            message = "I want to visit ${poi.name}.",
            intent = ChatIntent.DISCOVER,
            emoji = "\uD83D\uDCCD",
        ))
        if (_uiState.value.contextBlurb == null && !_uiState.value.isContextLoading) {
            poiContextJob?.cancel()
            poiContextJob = viewModelScope.launch {
                fetchPoiContext(poi, areaName)
            }
        }
    }

    private var pendingVisitMessage: String? = null

    private fun drainPendingVisitMessage() {
        val msg = pendingVisitMessage ?: return
        pendingVisitMessage = null
        sendMessage(msg)
    }

    fun sendVisitMessage(poi: POI, visitState: VisitState) {
        val query = when (visitState) {
            VisitState.GO_NOW -> "I want to visit ${poi.name} right now. What should I know before heading there?"
            VisitState.PLAN_SOON -> "I want to visit ${poi.name} but it's currently closed. When's the best time to go?"
            VisitState.WANT_TO_GO -> "I want to visit ${poi.name}. What makes it special and when should I go?"
        }
        if (_uiState.value.isStreaming) {
            pendingVisitMessage = query
        } else {
            sendMessage(query)
        }
    }

    private fun buildVisitFramingHint(poi: POI, visitState: VisitState): String {
        val status = resolveStatus(poi.liveStatus, poi.hours)
        return when (visitState) {
            VisitState.GO_NOW -> "The user just tapped Visit on ${poi.name} — it is currently $status. Lead with a Go Now response: best approach right now, crowd level, one thing to do/order/see first. Conversational, not a checklist. End with a question."
            VisitState.PLAN_SOON -> "The user just tapped Visit on ${poi.name} — it is currently closed. Lead with a Plan Soon response: best time to visit (day/time), what to anticipate, why worth the wait. Conversational. End with a question."
            VisitState.WANT_TO_GO -> "The user just tapped Visit on ${poi.name}. Lead with an engaging overview — highlight what makes it special and the best time to visit."
        }
    }

    private fun currentTimeHint(): String {
        val hour = com.harazone.ui.components.currentHour()
        return when {
            hour in 5..10 -> "morning"
            hour in 11..16 -> "afternoon"
            hour in 17..20 -> "evening"
            else -> "night"
        }
    }

    private suspend fun fetchPoiContext(poi: POI, areaName: String) {
        _uiState.value = _uiState.value.copy(
            contextBlurb = null, whyNow = null, localTip = null, isContextLoading = true,
        )
        val timeHint = currentTimeHint()
        val result = aiProvider.generatePoiContext(poi.name, poi.type, areaName, timeHint, localeProvider.languageTag)
        val (blurb, whyNow, tip) = result ?: Triple(
            "${poi.name} is a ${poi.type} in $areaName.",
            when (timeHint) {
                "morning" -> "A great way to start your morning."
                "afternoon" -> "Perfect for an afternoon visit."
                "evening" -> "A lovely spot for the evening."
                else -> "Worth a visit at any time."
            },
            "",
        )
        _uiState.value = _uiState.value.copy(
            contextBlurb = blurb.ifBlank { "${poi.name} is a ${poi.type} in $areaName." },
            whyNow = whyNow.ifBlank { null },
            localTip = tip.ifBlank { null },
            isContextLoading = false,
        )
    }

    fun tapIntentPill(pill: ContextualPill) {
        if (isIntentSelected) return
        isIntentSelected = true
        selectedIntent = pill.intent
        viewModelScope.launch {
            // Eagerly fetch saves if background collector hasn't emitted yet (M2 race fix)
            val saves = latestSavedPois.ifEmpty {
                savedPoiRepository.observeAll().first().also { latestSavedPois = it }
            }
            currentEngagementLevel = EngagementLevel.from(saves, clock.nowMs())
            val tasteProfile = TasteProfileBuilder.build(saves, clock.nowMs())
            val areaName = _uiState.value.areaName
            val poiCount = sessionPois.size
            val systemContext = promptBuilder.buildChatSystemContext(
                areaName, sessionPois, pill.intent, currentEngagementLevel,
                saves, tasteProfile, poiCount, pendingFramingHint, activeVibeName,
                languageTag = localeProvider.languageTag,
            )
            conversationHistory.add(
                ChatMessage(
                    id = nextId(),
                    role = MessageRole.USER,
                    content = systemContext,
                    timestamp = clock.nowMs(),
                    sources = emptyList(),
                )
            )
            _uiState.value = _uiState.value.copy(
                inputText = pill.message,
                                persistentPills = emptyList(),
                depthLevel = 0,
            )
            sendMessage()
        }
    }

    // Test-only accessor — allows ChatViewModelTest to verify system context injection
    internal val systemContextForTest: String
        get() = conversationHistory.firstOrNull()?.content.orEmpty()

    fun closeChat() {
        chatJob?.cancel()
        val s = _uiState.value
        _uiState.value = s.copy(
            isOpen = false,
            isStreaming = false,
            showSkeletons = false,
            bubbles = s.bubbles.map { if (it.isStreaming) it.copy(isStreaming = false) else it },
        )
    }

    fun continueConversation() {
        chatOpenedAt = clock.nowMs()
        _uiState.value = _uiState.value.copy(showReturnDialog = false)
    }

    fun startFreshConversation() {
        _uiState.value = _uiState.value.copy(showReturnDialog = false)
        chatOpenedAt = clock.nowMs()
        resetToIntentPills()
    }

    /** Reopen chat without resetting conversation state (e.g. after dismissing a POI detail card). */
    fun reopenChat() {
        _uiState.value = _uiState.value.copy(isOpen = true)
    }

    fun updateInput(text: String) {
        _uiState.value = _uiState.value.copy(inputText = text)
    }

    fun sendMessage(query: String = _uiState.value.inputText) {
        if (query.isBlank() || _uiState.value.isStreaming) return
        _uiState.value = _uiState.value.copy(depthLevel = _uiState.value.depthLevel + 1)
        chatJob?.cancel()
        val userBubbleId = nextId()
        val aiBubbleId = nextId()
        val userBubble = ChatBubble(id = userBubbleId, role = MessageRole.USER, content = query)
        _uiState.value = _uiState.value.copy(
            bubbles = _uiState.value.bubbles + userBubble + ChatBubble(
                id = aiBubbleId, role = MessageRole.AI, content = "", isStreaming = true,
            ),
            isStreaming = true,
                        inputText = "",
            lastUserQuery = query,
            showSkeletons = true,
        )
        lastParseOffset = 0
        parsedCards = mutableListOf()
        // M4: Trim before snapshot so the snapshot reflects the capped history
        // F1: Take snapshot BEFORE adding user msg — provider appends query itself
        trimHistory()

        // Follow-up context injection: prevent duplicate POI recommendations
        if (conversationHistory.size > 1 && recommendedPoiNames.isNotEmpty()) {
            val dedupMsg = ChatMessage(
                id = nextId(), role = MessageRole.USER,
                content = "Context: Do not repeat these previously recommended places: ${recommendedPoiNames.joinToString(", ")}. Return only NEW places.",
                timestamp = clock.nowMs(), sources = emptyList(),
            )
            conversationHistory.add(dedupMsg)
            injectedContextIndex = conversationHistory.size - 1
        }

        // TODO(BACKLOG-LOW): mapPoiReminder duplicates POI names already in system context MAP POIS section — ~200-400 redundant tokens per turn. Consider removing once REFERENCE RULE proves reliable.
        val mapPoiReminder = if (sessionPois.isNotEmpty()) {
            " Map POIs: ${sessionPois.take(15).joinToString("; ") { it.name }}."
        } else ""
        val reinforcedQuery = query + "\n[REQUIRED: JSON only. prose = 2-3 sentences, LAST SENTENCE ENDS WITH '?'.$mapPoiReminder Every named place in prose → entry in pois array.]"
        val historySnapshot = conversationHistory.toList()
        conversationHistory.add(
            ChatMessage(
                id = userBubbleId, role = MessageRole.USER, content = query,
                timestamp = clock.nowMs(), sources = emptyList(),
            )
        )

        chatJob = viewModelScope.launch {
            var accumulated = ""
            aiProvider.streamChatResponse(reinforcedQuery, _uiState.value.areaName, historySnapshot)
                .catch { e ->
                    AppLogger.e(e) { "ChatViewModel: stream failed" }
                    removeInjectedContext()
                    val s = _uiState.value
                    _uiState.value = s.copy(
                        bubbles = s.bubbles.map {
                            if (it.id == aiBubbleId) ChatBubble(
                                id = aiBubbleId, role = MessageRole.AI,
                                content = "Something went wrong. Tap to retry.",
                                isError = true,
                            ) else it
                        },
                        isStreaming = false,
                                                showSkeletons = false,
                    )
                }
                .collect { token ->
                    if (token.isComplete) {
                        val response = parseChatResponse(accumulated)
                        recommendedPoiNames += response.pois.map { it.name }
                        removeInjectedContext()
                        val displayText = response.prose.ifBlank { stripPoiJson(accumulated) }
                        val s = _uiState.value
                        val newCards = response.pois.ifEmpty { parsedCards.toList() }
                        val existingIds = s.poiCards.map { it.id }.toSet()
                        val deduped = newCards.filter { it.id !in existingIds }
                        // Newest cards first so user sees them without scrolling
                        val updatedBubbleCards = if (deduped.isNotEmpty()) {
                            s.bubblePoiCards + (aiBubbleId to deduped)
                        } else s.bubblePoiCards
                        _uiState.value = s.copy(
                            bubbles = s.bubbles.map {
                                if (it.id == aiBubbleId) ChatBubble(
                                    id = aiBubbleId, role = MessageRole.AI, content = displayText
                                ) else it
                            },
                            isStreaming = false,
                                                        persistentPills = computePersistentPills(selectedIntent, query, _uiState.value.depthLevel),
                            showSkeletons = false,
                            poiCards = deduped + s.poiCards,
                            bubblePoiCards = updatedBubbleCards,
                        )
                        conversationHistory.add(
                            ChatMessage(
                                id = aiBubbleId, role = MessageRole.AI,
                                content = accumulated, timestamp = clock.nowMs(), sources = emptyList(),
                            )
                        )
                        drainPendingVisitMessage()
                    } else {
                        accumulated += token.text
                        val displayText = extractStreamingProse(accumulated)
                        val s = _uiState.value
                        _uiState.value = s.copy(
                            bubbles = s.bubbles.map {
                                if (it.id == aiBubbleId) ChatBubble(
                                    id = aiBubbleId, role = MessageRole.AI,
                                    content = displayText, isStreaming = true,
                                ) else it
                            },
                            showSkeletons = true,
                        )
                    }
                }
        }
    }

    private fun parseChatResponse(text: String): ChatResponse {
        val cleaned = text.trim().let {
            if (it.startsWith("```")) it.lines().drop(1).dropLast(1).joinToString("\n") else it
        }
        return try {
            json.decodeFromString<ChatResponse>(cleaned)
        } catch (_: Exception) {
            // Fallback 1: regex-extract prose from {"prose":"...","pois":[...]} wrapper
            val proseMatch = Regex(""""prose"\s*:\s*"((?:[^"\\]|\\.)*)"""").find(cleaned)
            if (proseMatch != null) {
                val prose = proseMatch.groupValues[1]
                    .replace("\\n", "\n").replace("\\\"", "\"").replace("\\\\", "\\")
                parsePoiCardsIncremental(text)
                return ChatResponse(prose = prose, pois = parsedCards.toList())
            }
            // Fallback 2: extract POI cards the old way, use stripped text as prose
            parsePoiCardsIncremental(text)
            ChatResponse(prose = stripMarkdownChars(stripPoiJson(text)), pois = parsedCards.toList())
        }
    }

    private fun extractStreamingProse(text: String): String {
        // If the response is a JSON wrapper {"prose":"...",...}, extract the prose value mid-stream
        val proseMatch = Regex(""""prose"\s*:\s*"((?:[^"\\]|\\.)*)""").find(text)
        if (proseMatch != null) {
            return proseMatch.groupValues[1]
                .replace("\\n", "\n").replace("\\\"", "\"").replace("\\\\", "\\")
        }
        return stripPoiJson(text)
    }

    private fun stripMarkdownChars(text: String): String =
        text.replace("**", "").replace(Regex("(?<![\\w*])\\*(?![\\w*])"), "").replace("`", "").replace(Regex("^- ", RegexOption.MULTILINE), "")

    private fun removeInjectedContext() {
        if (injectedContextIndex >= 0 && injectedContextIndex < conversationHistory.size) {
            conversationHistory.removeAt(injectedContextIndex)
        }
        injectedContextIndex = -1
    }

    fun savePoi(card: ChatPoiCard, areaName: String) {
        pendingSaveIds = pendingSaveIds + card.id
        pendingUnsaveIds = pendingUnsaveIds - card.id
        val s = _uiState.value
        _uiState.value = s.copy(savedPoiIds = s.savedPoiIds + card.id)
        viewModelScope.launch {
            try {
                savedPoiRepository.save(
                    SavedPoi(
                        id = card.id,
                        name = card.name,
                        type = card.type,
                        areaName = areaName,
                        lat = card.lat,
                        lng = card.lng,
                        whySpecial = card.whySpecial,
                        savedAt = 0L, // Repository sets the actual timestamp
                        imageUrl = card.imageUrl,
                        vibe = _uiState.value.vibeName ?: "",
                    )
                )
            } catch (e: Exception) {
                AppLogger.e(e) { "ChatViewModel: save POI failed" }
                val current = _uiState.value
                _uiState.value = current.copy(savedPoiIds = current.savedPoiIds - card.id)
            } finally {
                pendingSaveIds = pendingSaveIds - card.id
            }
        }
    }

    fun unsavePoi(id: String) {
        pendingUnsaveIds = pendingUnsaveIds + id
        pendingSaveIds = pendingSaveIds - id
        val s = _uiState.value
        _uiState.value = s.copy(savedPoiIds = s.savedPoiIds - id)
        viewModelScope.launch {
            try {
                savedPoiRepository.unsave(id)
            } catch (e: Exception) {
                AppLogger.e(e) { "ChatViewModel: unsave POI failed" }
                val current = _uiState.value
                _uiState.value = current.copy(savedPoiIds = current.savedPoiIds + id)
            } finally {
                pendingUnsaveIds = pendingUnsaveIds - id
            }
        }
    }

    fun retryLastMessage() {
        val lastQuery = _uiState.value.lastUserQuery
        if (lastQuery.isBlank()) return
        val s = _uiState.value
        // Only drop if last bubble is an error
        val lastBubble = s.bubbles.lastOrNull()
        if (lastBubble?.isError != true) return
        _uiState.value = s.copy(bubbles = s.bubbles.dropLast(2), isStreaming = false)
        if (conversationHistory.lastOrNull()?.role == MessageRole.USER) {
            conversationHistory.removeLastOrNull()
        }
        sendMessage(lastQuery)
    }

    fun tapChip(chip: String) {
        sendMessage(chip)
    }

    // F3: Keep system context (index 0) + last MAX_HISTORY_TURNS messages
    private fun trimHistory() {
        if (conversationHistory.size > MAX_HISTORY_TURNS + 1) {
            val system = conversationHistory.first()
            val trimmed = conversationHistory.takeLast(MAX_HISTORY_TURNS)
            conversationHistory = (mutableListOf(system) + trimmed).toMutableList()
        }
    }

    internal fun stripPoiJson(text: String): String {
        // Reuse parsePoiCards to find JSON block ranges, then remove them
        val parsed = parsePoiCards(text, 0)
        if (parsed.isEmpty()) return text
        val sb = StringBuilder(text)
        for ((_, startOff, endOff) in parsed.asReversed()) {
            sb.deleteRange(startOff, endOff)
        }
        return sb.toString().replace(Regex("\n{3,}"), "\n\n").trim()
    }

    private fun parsePoiCardsIncremental(text: String) {
        val newCards = parsePoiCards(text, lastParseOffset)
        if (newCards.isNotEmpty()) {
            parsedCards.addAll(newCards.map { it.first })
            lastParseOffset = newCards.last().third // endOffset
        }
    }

    // Returns (card, jsonStartOffset, jsonEndOffset) triples
    internal fun parsePoiCards(text: String, startOffset: Int = 0): List<Triple<ChatPoiCard, Int, Int>> {
        val results = mutableListOf<Triple<ChatPoiCard, Int, Int>>()
        var i = startOffset
        while (i < text.length) {
            if (text[i] == '{') {
                var depth = 0
                val start = i
                var insideString = false
                var escaped = false
                while (i < text.length) {
                    val c = text[i]
                    if (escaped) {
                        escaped = false
                    } else when {
                        c == '\\' && insideString -> escaped = true
                        c == '"' -> insideString = !insideString
                        !insideString && c == '{' -> depth++
                        !insideString && c == '}' -> {
                            depth--
                            if (depth == 0) {
                                val block = text.substring(start, i + 1)
                                try {
                                    val card = json.decodeFromString<ChatPoiCard>(block)
                                    results.add(Triple(card, start, i + 1))
                                } catch (_: Exception) {
                                    // Silently ignore malformed JSON
                                }
                                break
                            }
                        }
                    }
                    i++
                }
            }
            i++
        }
        return results
    }

    fun tapPersistentPill(pill: ContextualPill) {
        if (_uiState.value.isStreaming) return
        if (selectedIntent == null) selectedIntent = pill.intent
        sendMessage(pill.message)
    }

    private fun computePersistentPills(intent: ChatIntent?, lastQuery: String, depthLevel: Int): List<ContextualPill> {
        val areaName = _uiState.value.areaName
        if (depthLevel >= 3) {
            return listOf(
                ContextualPill(LABEL_NEW_TOPIC, "Let's change topic", ChatIntent.DISCOVER, "🔄"),
            ) + computeContextualPills(lastQuery, areaName).take(2)
        }
        return computeContextualPills(lastQuery, areaName).take(3)
    }

    private fun computeContextualPills(query: String, areaName: String): List<ContextualPill> {
        val q = query.lowercase()
        return when {
            q.containsAnyWord("safe", "crime", "danger", "night") -> listOf(
                ContextualPill("Safe at night?", "Is it safe at night around here?", ChatIntent.DISCOVER, "🌙"),
                ContextualPill("Areas to avoid", "What areas should I avoid?", ChatIntent.DISCOVER, "⚠️"),
            )
            q.containsAnyWord("food", "eat", "restaurant", "drink") -> listOf(
                ContextualPill("Best time to go", "What's the best time to visit?", ChatIntent.HUNGRY, "⏰"),
                ContextualPill("Veggie options?", "Are there vegetarian options nearby?", ChatIntent.HUNGRY, "🥗"),
            )
            q.containsAnyWord("history", "historic", "built", "founded") -> listOf(
                ContextualPill("Tell me more", "Tell me more about the history here", ChatIntent.DISCOVER, "📖"),
                ContextualPill("Famous events?", "Any famous events happened here?", ChatIntent.DISCOVER, "🏛️"),
            )
            else -> listOf(
                ContextualPill("What's nearby?", "What else is nearby worth seeing?", ChatIntent.DISCOVER, "📍"),
                ContextualPill("Surprise me", "Surprise me with something unexpected in $areaName", ChatIntent.SURPRISE, "🎲"),
            )
        }
    }

    private fun String.containsAnyWord(vararg terms: String) = terms.any { term ->
        this.contains("\\b${Regex.escape(term)}\\b".toRegex())
    }

    fun resetToIntentPills() {
        chatJob?.cancel()
        conversationHistory = mutableListOf()
        nextId = 0L
        isIntentSelected = false
        selectedIntent = null
        recommendedPoiNames.clear()
        removeInjectedContext()
        val areaName = _uiState.value.areaName
        _uiState.value = _uiState.value.copy(
            bubbles = emptyList(),
                        poiCards = emptyList(),
            bubblePoiCards = emptyMap(),
            showSkeletons = false,
            isStreaming = false,
            persistentPills = pillsFor(currentEntryPoint, areaName),
            inputText = preFillFor(currentEntryPoint),
            contextBanner = bannerFor(currentEntryPoint),
            depthLevel = 0,
        )
    }

    fun dismissContextBanner() {
        _uiState.value = _uiState.value.copy(contextBanner = null)
    }

    private fun defaultPills(areaName: String): List<ContextualPill> = listOf(
        ContextualPill("What's on tonight in $areaName?", "What's on tonight in $areaName?", ChatIntent.TONIGHT, "🌙"),
        ContextualPill("Best food right now", "Where should I eat right now in $areaName?", ChatIntent.HUNGRY, "🍜"),
        ContextualPill("Show me hidden gems", "Show me hidden gems in $areaName", ChatIntent.DISCOVER, "🔍"),
        ContextualPill("Get me outside", "Get me outside in $areaName", ChatIntent.OUTSIDE, "🌳"),
        ContextualPill("Surprise me in $areaName", "Surprise me in $areaName", ChatIntent.SURPRISE, "🎲"),
    )

    private fun poiCardPills(poiName: String): List<ContextualPill> = listOf(
        ContextualPill("Tell me more about $poiName", "Tell me more about $poiName", ChatIntent.DISCOVER, "✨"),
        ContextualPill("What's nearby?", "What's nearby $poiName?", ChatIntent.DISCOVER, "📍"),
        ContextualPill("Is this worth visiting?", "Is $poiName worth visiting?", ChatIntent.DISCOVER, "⭐"),
    )

    private fun savesOverviewPills(): List<ContextualPill> = listOf(
        ContextualPill("Plan a day trip from my saves", "Plan a day trip using my saved places", ChatIntent.DISCOVER, "🗺️"),
        ContextualPill("Find patterns in my saves", "What patterns do you see in my saved places?", ChatIntent.DISCOVER, "🔍"),
        ContextualPill("What am I missing?", "What places am I missing that I should save?", ChatIntent.DISCOVER, "🤔"),
    )

    private fun pillsFor(entryPoint: ChatEntryPoint, areaName: String): List<ContextualPill> = when (entryPoint) {
        is ChatEntryPoint.Default -> defaultPills(areaName)
        is ChatEntryPoint.SavesSheet -> savesOverviewPills()
        is ChatEntryPoint.PoiCard -> poiCardPills(entryPoint.poi.name)
        is ChatEntryPoint.SavedCard -> poiCardPills(entryPoint.poiName)
        is ChatEntryPoint.VisitAction -> poiCardPills(entryPoint.poi.name)
        is ChatEntryPoint.CompanionNudge -> defaultPills(areaName)
    }

    private fun preFillFor(entryPoint: ChatEntryPoint): String = when (entryPoint) {
        is ChatEntryPoint.PoiCard -> "Tell me more about ${entryPoint.poi.name}"
        is ChatEntryPoint.SavedCard -> "Tell me more about ${entryPoint.poiName}"
        is ChatEntryPoint.VisitAction -> "I want to visit ${entryPoint.poi.name}"
        is ChatEntryPoint.CompanionNudge -> ""
        else -> ""
    }

    private fun bannerFor(entryPoint: ChatEntryPoint): String? = when (entryPoint) {
        is ChatEntryPoint.PoiCard -> "Asking about: ${entryPoint.poi.name}"
        is ChatEntryPoint.SavedCard -> "Asking about: ${entryPoint.poiName}"
        is ChatEntryPoint.VisitAction -> "Planning visit: ${entryPoint.poi.name}"
        is ChatEntryPoint.SavesSheet -> "Using your saved places"
        is ChatEntryPoint.CompanionNudge -> null
        is ChatEntryPoint.Default -> null
    }
}

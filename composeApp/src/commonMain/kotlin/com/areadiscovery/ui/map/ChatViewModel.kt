package com.areadiscovery.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.areadiscovery.data.remote.GeminiPromptBuilder
import com.areadiscovery.domain.model.ChatMessage
import com.areadiscovery.domain.model.MessageRole
import com.areadiscovery.domain.model.POI
import com.areadiscovery.domain.model.SavedPoi
import com.areadiscovery.domain.model.Vibe
import com.areadiscovery.domain.provider.AreaIntelligenceProvider
import com.areadiscovery.domain.repository.SavedPoiRepository
import com.areadiscovery.util.AppClock
import com.areadiscovery.util.AppLogger
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

internal class ChatViewModel(
    private val aiProvider: AreaIntelligenceProvider,
    private val promptBuilder: GeminiPromptBuilder,
    private val clock: AppClock,
    private val savedPoiRepository: SavedPoiRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var conversationHistory: MutableList<ChatMessage> = mutableListOf()
    private var chatJob: Job? = null
    private var nextId = 0L
    private fun nextId() = (nextId++).toString()

    private val json = Json { ignoreUnknownKeys = true }
    private var lastParseOffset = 0
    private var parsedCards = mutableListOf<ChatPoiCard>()
    private var pendingSaveIds = setOf<String>()
    private var pendingUnsaveIds = setOf<String>()

    companion object {
        private const val MAX_HISTORY_TURNS = 20 // 20 messages = ~10 back-and-forth turns
    }

    init {
        viewModelScope.launch {
            savedPoiRepository.observeSavedIds().collect { ids ->
                // Merge DB state with pending optimistic updates to prevent flicker
                val merged = (ids + pendingSaveIds) - pendingUnsaveIds
                _uiState.value = _uiState.value.copy(savedPoiIds = merged)
            }
        }
    }

    fun openChat(areaName: String, pois: List<POI>, activeVibe: Vibe?) {
        val current = _uiState.value
        // F7/M2: Same area — preserve conversation (whether open or closed)
        if (current.areaName == areaName && (current.isOpen || current.bubbles.isNotEmpty())) {
            _uiState.value = current.copy(isOpen = true)
            return
        }
        // M2: Different area while open — fall through to reset and reinitialize

        chatJob?.cancel()
        conversationHistory = mutableListOf()
        nextId = 0L
        val vibeName = activeVibe?.name
        val poiNames = pois.take(5).map { it.name }
        val systemContext = promptBuilder.buildChatSystemContext(areaName, poiNames, vibeName)
        // F2: Store system context separately — don't add as "model" role in history
        // (Gemini requires first turn to be "user", not "model")
        conversationHistory.add(
            ChatMessage(
                id = nextId(),
                role = MessageRole.USER,
                content = systemContext,
                timestamp = clock.nowMs(),
                sources = emptyList(),
            )
        )
        _uiState.value = ChatUiState(
            isOpen = true,
            areaName = areaName,
            vibeName = vibeName,
            bubbles = emptyList(),
            followUpChips = computeStarterChips(activeVibe),
            poiCards = emptyList(),
            showSkeletons = false,
            savedPoiIds = _uiState.value.savedPoiIds,
        )
    }

    fun closeChat() {
        chatJob?.cancel()
        _uiState.value = _uiState.value.copy(isOpen = false)
    }

    fun updateInput(text: String) {
        _uiState.value = _uiState.value.copy(inputText = text)
    }

    fun sendMessage(query: String = _uiState.value.inputText) {
        if (query.isBlank() || _uiState.value.isStreaming) return
        chatJob?.cancel()
        val userBubbleId = nextId()
        val aiBubbleId = nextId()
        val userBubble = ChatBubble(id = userBubbleId, role = MessageRole.USER, content = query)
        _uiState.value = _uiState.value.copy(
            bubbles = _uiState.value.bubbles + userBubble + ChatBubble(
                id = aiBubbleId, role = MessageRole.AI, content = "", isStreaming = true,
            ),
            isStreaming = true,
            followUpChips = emptyList(),
            inputText = "",
            lastUserQuery = query,
            showSkeletons = true,
            poiCards = emptyList(),
        )
        lastParseOffset = 0
        parsedCards = mutableListOf()
        // M4: Trim before snapshot so the snapshot reflects the capped history
        // F1: Take snapshot BEFORE adding user msg — provider appends query itself
        trimHistory()
        val historySnapshot = conversationHistory.toList()
        conversationHistory.add(
            ChatMessage(
                id = userBubbleId, role = MessageRole.USER, content = query,
                timestamp = clock.nowMs(), sources = emptyList(),
            )
        )

        chatJob = viewModelScope.launch {
            var accumulated = ""
            aiProvider.streamChatResponse(query, _uiState.value.areaName, historySnapshot)
                .catch { e ->
                    AppLogger.e(e) { "ChatViewModel: stream failed" }
                    // F4: Find bubble by ID instead of assuming it's the last one
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
                        followUpChips = emptyList(),
                        showSkeletons = false,
                    )
                }
                .collect { token ->
                    if (token.isComplete) {
                        parsePoiCardsIncremental(accumulated)
                        val displayText = stripPoiJson(accumulated)
                        val s = _uiState.value
                        _uiState.value = s.copy(
                            bubbles = s.bubbles.map {
                                if (it.id == aiBubbleId) ChatBubble(
                                    id = aiBubbleId, role = MessageRole.AI, content = displayText
                                ) else it
                            },
                            isStreaming = false,
                            followUpChips = computeFollowUpChips(query),
                            showSkeletons = false,
                            poiCards = parsedCards.toList(),
                        )
                        conversationHistory.add(
                            ChatMessage(
                                id = aiBubbleId, role = MessageRole.AI,
                                content = accumulated, timestamp = clock.nowMs(), sources = emptyList(),
                            )
                        )
                    } else {
                        accumulated += token.text
                        parsePoiCardsIncremental(accumulated)
                        val displayText = stripPoiJson(accumulated)
                        val s = _uiState.value
                        _uiState.value = s.copy(
                            bubbles = s.bubbles.map {
                                if (it.id == aiBubbleId) ChatBubble(
                                    id = aiBubbleId, role = MessageRole.AI,
                                    content = displayText, isStreaming = true,
                                ) else it
                            },
                            poiCards = parsedCards.toList(),
                            showSkeletons = parsedCards.size < 3,
                        )
                    }
                }
        }
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
        // Use the same brace-counting parser to find JSON block ranges, then remove them
        val ranges = mutableListOf<IntRange>()
        var i = 0
        while (i < text.length) {
            if (text[i] == '{') {
                var depth = 0
                val start = i
                var insideString = false
                var escaped = false
                var j = i
                while (j < text.length) {
                    val c = text[j]
                    if (escaped) { escaped = false; j++; continue }
                    when {
                        c == '\\' && insideString -> escaped = true
                        c == '"' -> insideString = !insideString
                        !insideString && c == '{' -> depth++
                        !insideString && c == '}' -> {
                            depth--
                            if (depth == 0) {
                                val block = text.substring(start, j + 1)
                                try {
                                    json.decodeFromString<ChatPoiCard>(block)
                                    ranges.add(start..j)
                                } catch (_: Exception) { /* not a POI card */ }
                                i = j
                                break
                            }
                        }
                    }
                    j++
                }
            }
            i++
        }
        if (ranges.isEmpty()) return text
        val sb = StringBuilder(text)
        for (range in ranges.asReversed()) sb.deleteRange(range.first, range.last + 1)
        return sb.toString().replace(Regex("\n{3,}"), "\n\n").trim()
    }

    private fun parsePoiCardsIncremental(text: String) {
        val newCards = parsePoiCards(text, lastParseOffset)
        if (newCards.isNotEmpty()) {
            parsedCards.addAll(newCards.map { it.first })
            lastParseOffset = newCards.last().second
        }
    }

    internal fun parsePoiCards(text: String, startOffset: Int = 0): List<Pair<ChatPoiCard, Int>> {
        val results = mutableListOf<Pair<ChatPoiCard, Int>>()
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
                                    results.add(card to (i + 1))
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

    private fun computeStarterChips(vibe: Vibe?): List<String> = when (vibe) {
        Vibe.SAFETY -> listOf("Is it safe right now?", "Areas to avoid?", "Safe at night?", "Emergency services nearby?")
        Vibe.WHATS_ON -> listOf("What's on tonight?", "Best events this week?", "Free things to do?", "Where's the crowd tonight?")
        Vibe.CHARACTER -> listOf("What's the vibe here?", "Best local spots?", "Hidden gems?", "Who lives here?")
        Vibe.HISTORY -> listOf("What's the history here?", "Oldest buildings nearby?", "Famous events here?", "Hidden historical gems?")
        Vibe.COST -> listOf("Is this area expensive?", "Budget tips?", "Free attractions?", "Cheapest eats nearby?")
        Vibe.NEARBY -> listOf("What's close by?", "Best way to get around?", "Transport options?", "What's within walking distance?")
        null -> listOf("What's special about here?", "Hidden gems?", "Best time to visit?", "What's nearby?")
    }

    // F11: Use word boundary matching to avoid false positives (e.g., "on" in "information")
    private fun computeFollowUpChips(query: String): List<String> {
        val q = query.lowercase()
        return when {
            q.containsAnyWord("safe", "crime", "danger", "night") -> listOf("Is it safe at night?", "What areas to avoid?")
            q.containsAnyWord("food", "eat", "restaurant", "drink", "brunch") -> listOf("Best time to visit?", "Vegetarian options?")
            q.containsAnyWord("history", "historic", "old", "founded", "built") -> listOf("When was it built?", "Any famous events here?")
            q.containsAnyWord("cost", "price", "expensive", "cheap", "budget") -> listOf("Budget tips?", "Free things to do?")
            q.containsAnyWord("event", "tonight", "happening", "going on") -> listOf("How busy will it be?", "Best way to get there?")
            else -> listOf("Tell me more", "What's nearby?")
        }
    }

    private fun String.containsAnyWord(vararg terms: String) = terms.any { term ->
        this.contains("\\b${Regex.escape(term)}\\b".toRegex())
    }
}

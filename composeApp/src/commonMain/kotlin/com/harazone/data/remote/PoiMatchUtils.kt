package com.harazone.data.remote

internal object PoiMatchUtils {
    private val NON_ALNUM_REGEX = Regex("[^a-z0-9 ]")

    fun isConfidentMatch(poiName: String, displayName: String): Boolean {
        val normalize = { s: String -> s.lowercase().replace(NON_ALNUM_REGEX, "").trim() }
        val poiTokens = normalize(poiName).split(" ").filter { it.length >= 3 }.toSet()
        val dispTokens = normalize(displayName).split(" ").filter { it.length >= 3 }.toSet()
        if (poiTokens.isEmpty() || dispTokens.isEmpty()) return false
        val (shorter, longer) = if (poiTokens.size <= dispTokens.size) poiTokens to dispTokens else dispTokens to poiTokens
        return shorter.all { it in longer }
    }
}

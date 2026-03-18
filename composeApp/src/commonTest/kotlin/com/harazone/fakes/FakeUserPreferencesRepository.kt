package com.harazone.fakes

import com.harazone.data.repository.UserPreferencesRepository

class FakeUserPreferencesRepository : UserPreferencesRepository(db = null) {
    private var coldStartSeen = false
    private var pinnedVibes = emptyList<String>()
    private var lastDeltaShownAt = 0L
    private var lastProximityPingKey = ""
    private var vibeMilestonesSeenSet = emptySet<String>()
    private var whisperShownForArea = ""
    private val advisoryCache = mutableMapOf<String, String>()
    private val advisoryCountryNames = mutableMapOf<String, String>()

    override fun getColdStartSeen(): Boolean = coldStartSeen
    override fun setColdStartSeen() { coldStartSeen = true }
    override fun getPinnedVibes(): List<String> = pinnedVibes
    override fun setPinnedVibes(labels: List<String>) { pinnedVibes = labels }

    override fun getLastDeltaShownAt(): Long = lastDeltaShownAt
    override fun setLastDeltaShownAt(ts: Long) { lastDeltaShownAt = ts }
    override fun getLastProximityPingKey(): String = lastProximityPingKey
    override fun setLastProximityPingKey(key: String) { lastProximityPingKey = key }
    override fun getVibeMilestonesSeenSet(): Set<String> = vibeMilestonesSeenSet
    override fun setVibeMilestonesSeenSet(milestones: Set<String>) { vibeMilestonesSeenSet = milestones }
    override fun getWhisperShownForArea(): String = whisperShownForArea
    override fun setWhisperShownForArea(area: String) { whisperShownForArea = area }

    override fun getAdvisoryCache(countryCode: String): String? = advisoryCache[countryCode]
    override fun setAdvisoryCache(countryCode: String, advisoryJson: String) { advisoryCache[countryCode] = advisoryJson }
    override fun getAdvisoryCachedCountryName(countryCode: String): String? = advisoryCountryNames[countryCode]
    override fun setAdvisoryCachedCountryName(countryCode: String, name: String) { advisoryCountryNames[countryCode] = name }
}

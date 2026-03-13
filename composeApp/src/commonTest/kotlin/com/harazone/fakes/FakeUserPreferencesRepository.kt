package com.harazone.fakes

import com.harazone.data.repository.UserPreferencesRepository

class FakeUserPreferencesRepository : UserPreferencesRepository(db = null) {
    private var coldStartSeen = false
    private var pinnedVibes = emptyList<String>()

    override fun getColdStartSeen(): Boolean = coldStartSeen
    override fun setColdStartSeen() { coldStartSeen = true }
    override fun getPinnedVibes(): List<String> = pinnedVibes
    override fun setPinnedVibes(labels: List<String>) { pinnedVibes = labels }
}

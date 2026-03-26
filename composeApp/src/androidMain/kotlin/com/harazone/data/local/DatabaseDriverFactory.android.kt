package com.harazone.data.local

import android.content.Context
import app.cash.sqldelight.db.AfterVersion
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver

actual class DatabaseDriverFactory(private val context: Context) {
    actual fun createDriver(): SqlDriver {
        val driver = AndroidSqliteDriver(
            schema = AreaDiscoveryDatabase.Schema,
            context = context,
            name = "area_discovery.db",
            callback = AndroidSqliteDriver.Callback(
                schema = AreaDiscoveryDatabase.Schema,
                AfterVersion(1) { it.ensureSavedPoisTable() },
                AfterVersion(2) { it.ensureSavedPoisTable() },
                AfterVersion(3) { it.ensureSavedPoisTable() },
                AfterVersion(4) { it.ensureSavedPoisTable() },
                AfterVersion(5) { it.ensureSavedPoisTable() },
            ),
        )
        // Safety net: ensure tables exist even if migrations didn't fire
        // (e.g., DB already at current version, or test device with stale state)
        driver.ensureSavedPoisTable()
        driver.ensurePlacesEnrichmentCacheTable()
        return driver
    }
}

private fun SqlDriver.ensurePlacesEnrichmentCacheTable() {
    execute(null, """
        CREATE TABLE IF NOT EXISTS places_enrichment_cache (
            saved_id    TEXT NOT NULL PRIMARY KEY,
            hours       TEXT,
            live_status TEXT,
            rating      REAL,
            review_count INTEGER,
            price_range TEXT,
            image_url   TEXT,
            image_urls  TEXT,
            website_uri TEXT,
            google_maps_uri TEXT,
            international_phone_number TEXT,
            formatted_address TEXT,
            expires_at  INTEGER NOT NULL,
            cached_at   INTEGER NOT NULL
        )
    """.trimIndent(), 0)
    execute(null, "CREATE INDEX IF NOT EXISTS idx_places_cache_expires_at ON places_enrichment_cache(expires_at)", 0)
}

private fun SqlDriver.ensureSavedPoisTable() {
    execute(null, """
        CREATE TABLE IF NOT EXISTS saved_pois (
            poi_id      TEXT NOT NULL PRIMARY KEY,
            name        TEXT NOT NULL,
            type        TEXT NOT NULL,
            area_name   TEXT NOT NULL,
            lat         REAL NOT NULL,
            lng         REAL NOT NULL,
            why_special TEXT NOT NULL,
            saved_at    INTEGER NOT NULL
        )
    """.trimIndent(), 0)
}

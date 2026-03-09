package com.areadiscovery.data.local

import app.cash.sqldelight.db.AfterVersion
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver

actual class DatabaseDriverFactory {
    actual fun createDriver(): SqlDriver {
        val driver = NativeSqliteDriver(
            schema = AreaDiscoveryDatabase.Schema,
            name = "area_discovery.db",
            callbacks = arrayOf(
                AfterVersion(1) { it.ensureSavedPoisTable() },
                AfterVersion(2) { it.ensureSavedPoisTable() },
                AfterVersion(3) { it.ensureSavedPoisTable() },
                AfterVersion(4) { it.ensureSavedPoisTable() },
            ),
        )
        driver.ensureSavedPoisTable()
        return driver
    }
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

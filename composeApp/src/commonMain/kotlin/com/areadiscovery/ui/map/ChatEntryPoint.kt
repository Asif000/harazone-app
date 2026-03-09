package com.areadiscovery.ui.map

import com.areadiscovery.domain.model.POI

sealed class ChatEntryPoint {
    data object Default : ChatEntryPoint()
    data object SavesSheet : ChatEntryPoint()
    data class PoiCard(val poi: POI) : ChatEntryPoint()
}

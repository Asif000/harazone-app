package com.harazone.ui.map

import com.harazone.domain.model.POI

sealed class ChatEntryPoint {
    data object Default : ChatEntryPoint()
    data object SavesSheet : ChatEntryPoint()
    data class PoiCard(val poi: POI) : ChatEntryPoint()
}

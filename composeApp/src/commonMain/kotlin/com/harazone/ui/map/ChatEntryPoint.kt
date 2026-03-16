package com.harazone.ui.map

import com.harazone.domain.model.POI
import com.harazone.domain.model.VisitState

sealed class ChatEntryPoint {
    data object Default : ChatEntryPoint()
    data object SavesSheet : ChatEntryPoint()
    data class PoiCard(val poi: POI) : ChatEntryPoint()
    data class SavedCard(val poiName: String) : ChatEntryPoint()
    data class VisitAction(val poi: POI, val visitState: VisitState) : ChatEntryPoint()
}

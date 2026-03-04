package com.areadiscovery.ui.summary

import com.areadiscovery.domain.model.BucketType
import com.areadiscovery.domain.model.Confidence
import com.areadiscovery.domain.model.Source

data class BucketDisplayState(
    val bucketType: BucketType,
    val highlightText: String = "",
    val bodyText: String = "",
    val confidence: Confidence? = null,
    val sources: List<Source> = emptyList(),
    val isStreaming: Boolean = false,
    val isComplete: Boolean = false,
)

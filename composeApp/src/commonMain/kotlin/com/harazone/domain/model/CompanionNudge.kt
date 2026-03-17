package com.harazone.domain.model

enum class NudgeType {
    PROXIMITY,          // #17 — near a saved place
    RELAUNCH_DELTA,     // #8  — interesting fact on session start
    VIBE_REVEAL,        // #39 — pattern milestone
    AMBIENT_WHISPER,    // #13 — idle commentary on current view
    ANTICIPATION_SEED,  // #37 — WANT_TO_GO save acknowledgment
    INSTANT_NEIGHBOR,   // #34 — nearby same-vibe suggestion on save
}

data class CompanionNudge(
    val type: NudgeType,
    val text: String,
    val chatContext: String = text,
)

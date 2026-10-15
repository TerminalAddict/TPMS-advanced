package com.masselis.tpmsadvanced.feature.background.interfaces

internal enum class AlertSound { NONE, TONES, SPEECH }

internal data class AlertOptions(
    val sound: AlertSound = AlertSound.NONE,
    val vibrate: Boolean = false,
    val repeatSeconds: Int = 120,
    val criticalSeconds: Int = 20,
) {
    val enabled: Boolean get() = sound != AlertSound.NONE || vibrate
}

internal data class AlertCue(val key: String, val phrase: String, val critical: Boolean, val receivedAt: Double)

/** Packet refreshes change freshness, not cadence. Silence applies to all levels, including critical. */
internal class AlertCadence {
    private companion object { const val MILLIS_PER_SECOND = 1_000L }
    private val lastPlayed = mutableMapOf<String, Long>()

    fun due(cues: List<AlertCue>, options: AlertOptions, now: Long, silencedUntil: Long): List<AlertCue> {
        lastPlayed.keys.retainAll(cues.map { it.key }.toSet())
        if (!options.enabled || now < silencedUntil) return emptyList()
        return cues.filter { cue ->
            val interval = (if (cue.critical) options.criticalSeconds else options.repeatSeconds) * MILLIS_PER_SECOND
            val previous = lastPlayed[cue.key]
            (previous == null || now - previous >= interval).also { if (it) lastPlayed[cue.key] = now }
        }.sortedByDescending { it.critical }
    }
}

package com.masselis.tpmsadvanced.data.vehicle.model

public data class ReadingSession(val start: Double, val end: Double, val count: Int)

/** Reading sessions are inferred from gaps, not claimed to be GPS-tracked rides. */
public fun readingSessions(readings: List<Tyre.Located>): List<ReadingSession> {
    val result = mutableListOf<ReadingSession>()
    readings.sortedBy { it.timestamp }.forEach { reading ->
        val previous = result.lastOrNull()
        if (previous == null || reading.timestamp - previous.end > SESSION_GAP_SECONDS)
            result += ReadingSession(reading.timestamp, reading.timestamp, 1)
        else result[result.lastIndex] = previous.copy(end = reading.timestamp, count = previous.count + 1)
    }
    return result
}

public fun csvCell(value: String): String {
    val safe = if (value.trimStart().firstOrNull() in listOf('=', '+', '-', '@', '\t', '\r')) "'$value" else value
    return "\"${safe.replace("\"", "\"\"")}\""
}

private const val SESSION_GAP_SECONDS = 900.0

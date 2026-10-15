package com.masselis.tpmsadvanced.data.vehicle.interfaces.impl

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/** Retains start history across subscriptions and Bluetooth toggles. */
internal class ScanStartLimiter(private val timeSource: TimeSource = TimeSource.Monotonic) {
    private val mutex = Mutex()
    private val starts = ArrayDeque<TimeMark>()

    suspend fun awaitTurn() = mutex.withLock {
        while (true) {
            while (starts.firstOrNull()?.elapsedNow()?.let { it >= WINDOW } == true) {
                starts.removeFirst()
            }
            val spacing = starts.lastOrNull()?.let { SPACING - it.elapsedNow() }
                ?: kotlin.time.Duration.ZERO
            val window = if (starts.size >= MAX_STARTS) WINDOW - starts.first().elapsedNow()
                else kotlin.time.Duration.ZERO
            val wait = maxOf(spacing, window)
            if (wait.isPositive()) delay(wait) else break
        }
        starts.addLast(timeSource.markNow())
    }

    private companion object {
        // Keep headroom below Android's commonly observed five-starts-per-30-seconds limit.
        const val MAX_STARTS = 4
        val WINDOW = 30.seconds
        val SPACING = 5.seconds
    }
}

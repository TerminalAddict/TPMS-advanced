@file:Suppress("MagicNumber") // Bounded telemetry window and BLE RSSI sentinel.

package com.masselis.tpmsadvanced.data.vehicle.model

public data class ReceptionQuality(val packets: Long, val latestRssi: Int?, val meanRssi: Double?,
    val minimumRssi: Int?, val maximumRssi: Int?, val lastSeenMs: Long, val windowPackets: Int,
    val estimatedMissing: Long?, val longestGapSeconds: Long, val available: Boolean)

/** Radio-callback observations, not connection events. Paused scan epochs are never counted as loss. */
public class ReceptionTracker(private val sensorLimit: Int = 256, private val windowLimit: Int = 512) {
    private data class Observation(val elapsed: Long, val epoch: Long, val rssi: Int?)
    private data class Sensor(var count: Long = 0, val recent: ArrayDeque<Observation> = ArrayDeque())
    private val sensors = linkedMapOf<Int, Sensor>()
    private var epoch = 0L
    private var scanning = false
    init { require(sensorLimit > 0 && windowLimit > 1) }

    @Synchronized public fun available(value: Boolean) {
        if (scanning != value) { epoch++; scanning = value }
    }
    @Synchronized public fun reset() { sensors.clear(); epoch++ }
    @Synchronized public fun record(sensorId: Int, rssi: Int, elapsed: Long) {
        if (!scanning) return
        val sensor = sensors.remove(sensorId) ?: Sensor()
        if (sensor.recent.lastOrNull()?.elapsed?.let { elapsed < it } == true) sensor.recent.clear()
        sensor.count++
        sensor.recent.addLast(Observation(elapsed, epoch, rssi.takeIf { it in -127..20 }))
        if (sensor.recent.size > windowLimit) sensor.recent.removeFirst()
        sensors[sensorId] = sensor
        if (sensors.size > sensorLimit) sensors.remove(sensors.keys.first())
    }
    @Synchronized public fun snapshot(sensorId: Int, elapsed: Long, expectedSeconds: Int?): ReceptionQuality? {
        val sensor = sensors[sensorId] ?: return null
        val samples = sensor.recent.toList()
        val last = samples.last()
        val gaps = samples.zipWithNext().filter { (a, b) -> a.epoch == b.epoch }
            .map { (a, b) -> (b.elapsed - a.elapsed).coerceAtLeast(0) }.toMutableList()
        if (scanning && last.epoch == epoch) gaps += (elapsed - last.elapsed).coerceAtLeast(0)
        val rssi = samples.mapNotNull { it.rssi }
        val missing = expectedSeconds?.takeIf { it > 0 }?.let { seconds ->
            gaps.sumOf { ((it / (seconds * 1000L)) - 1).coerceAtLeast(0) }
        }
        return ReceptionQuality(sensor.count, last.rssi, rssi.takeIf { it.isNotEmpty() }?.average(),
            rssi.minOrNull(), rssi.maxOrNull(), last.elapsed, samples.size, missing,
            (gaps.maxOrNull() ?: 0L) / 1000, scanning)
    }
}

@file:Suppress("MagicNumber") // Physical plausibility bounds and observation windows, not alert thresholds.

package com.masselis.tpmsadvanced.data.vehicle.model

import kotlinx.serialization.Serializable

@Serializable
public data class SensorMetadata(
    val id: Int, val label: String = "", val manufacturer: String = "", val notes: String = "",
) {
    public fun validate() {
        require(label.length <= 80 && manufacturer.length <= 120 && notes.length <= 1000)
    }
}

public data class SensorHealthDay(
    val day: Long, val packets: Long, val rssi: Double?, val voltage: Double?, val percent: Double?,
    val observedSeconds: Double, val missed: Long, val longestGap: Double, val questionable: Long,
)

@Suppress("CyclomaticComplexMethod") // Independent evidence checks deliberately allow more than one finding.
public fun sensorHealthFindings(days: List<SensorHealthDay>): List<String> = buildList {
    val ordered = days.sortedBy { it.day }
    if (ordered.isEmpty()) { add("No reception history yet."); return@buildList }
    val span = ordered.last().day - ordered.first().day
    if (ordered.size < 3 || span < 7) add("Collect readings on at least 3 days spanning a week to assess trends.")
    else {
        fun trend(value: (SensorHealthDay) -> Double?, threshold: Double, unit: String) {
            val measured = ordered.mapNotNull { day -> value(day)?.let { day.day to it } }
            if (measured.size >= 3 && measured.last().first - measured.first().first >= 7) {
                val change = measured.last().second - measured.first().second
                if (change <= -threshold) add("Battery decreased by %.2f %s across the observed days. "
                    .format(-change, unit) + "Check the battery; temperature and usage also affect it.")
                if (change >= threshold) add("Battery increased across the observed days; replacement or " +
                    "temperature changes can explain this.")
            }
        }
        trend({ it.voltage }, 0.15, "V")
        trend({ it.percent }, 10.0, "percentage points")
        val earlier = ordered.take(ordered.size / 2).mapNotNull { it.rssi }
        val recent = ordered.takeLast(ordered.size / 2).mapNotNull { it.rssi }
        if (earlier.isNotEmpty() && recent.isNotEmpty() && recent.average() < earlier.average() - 10)
            add("Reception has weakened by over 10 dBm. Check mounting, distance and interference.")
    }
    if (ordered.takeLast(3).mapNotNull { it.rssi }.takeIf { it.isNotEmpty() }?.average()?.let { it < -85 } == true)
        add("Recent signal strength is weak. Reception problems do not prove a failing sensor.")
    if (ordered.sumOf { it.missed } > 0)
        add("Expected broadcasts were missed while the scanner was available. Sleeping or parked sensors " +
            "and interference may also cause gaps.")
    if (ordered.sumOf { it.questionable } > 0) add("Questionable readings recorded; inspect the diagnostic details.")
    if (isEmpty()) add("No deterioration indicated by the available observations. This is not a failure prediction.")
}

/** Returns evidence, never a decision to drop a pressure emergency. Zero pressure is legitimate. */
public fun suspiciousReading(pressure: Float, temperature: Float, battery: SensorBattery?): List<String> = buildList {
    if (!pressure.isFinite() || pressure < 0 || pressure > 2000) add("Pressure field is implausible")
    if (!temperature.isFinite() || temperature < -60 || temperature > 150) add("Temperature field is implausible")
    when (battery) {
        is SensorBattery.Voltage -> if (!battery.volts.isFinite() || battery.volts !in 0f..5f)
            add("Battery voltage is implausible")
        is SensorBattery.Percentage -> if (battery.percent !in 0..100) add("Battery percentage is invalid")
        null -> Unit
    }
}

public data class ReceptionInterval(val seconds: Double = 0.0, val missed: Long = 0)

/** Radio downtime, process restarts and out-of-order batches never become sensor failures. */
public class HealthReceptionTracker {
    private data class Seen(val nanos: Long, val epoch: Long, val expected: Int?)
    private val seen = linkedMapOf<Int, Seen>()
    private var epoch = 0L
    private var scanning = false
    @Synchronized public fun available(value: Boolean) { if (value != scanning) { epoch++; scanning = value } }
    @Suppress("ReturnCount") // Availability, out-of-order and new-session guards must precede gap arithmetic.
    @Synchronized public fun record(id: Int, nanos: Long, expected: Int?): ReceptionInterval {
        if (!scanning) return ReceptionInterval()
        val previous = seen[id]
        if (previous != null && previous.epoch == epoch && nanos <= previous.nanos) return ReceptionInterval()
        seen.remove(id)
        seen[id] = Seen(nanos, epoch, expected)
        if (seen.size > 1000) seen.remove(seen.keys.first())
        if (previous == null || previous.epoch != epoch) return ReceptionInterval()
        val seconds = (nanos - previous.nanos) / 1_000_000_000.0
        val missed = if (expected != null && expected == previous.expected)
            (seconds.toLong() / expected - 1).coerceAtLeast(0) else 0
        return ReceptionInterval(seconds, missed)
    }
}

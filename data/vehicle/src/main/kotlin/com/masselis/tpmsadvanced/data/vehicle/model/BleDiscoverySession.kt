package com.masselis.tpmsadvanced.data.vehicle.model

import kotlin.math.abs

/** Bounded, screen-owned capture. Call from one dispatcher (the discovery UI uses Main). */
public class BleDiscoverySession(
    private val deviceLimit: Int = 200,
    private val packetLimit: Int = 1_000,
) {
    public data class Device(
        val latest: BleAdvertisement,
        val decoded: BleAdvertisement?,
        val firstSeen: Double,
        val packets: Long,
        val newSinceMark: Boolean,
        val pressureChanged: Boolean,
        val payloadChanged: Boolean,
    )

    private val devices = linkedMapOf<String, Device>()
    private val packets = ArrayDeque<BleAdvertisement>()
    private var baseline: Map<String, BleAdvertisement> = emptyMap()
    public var marked: Boolean = false
        private set
    public var received: Long = 0
        private set

    init { require(deviceLimit > 0 && packetLimit > 0) }

    public fun observe(packet: BleAdvertisement) {
        received++
        packets.addLast(packet)
        if (packets.size > packetLimit) packets.removeFirst()
        val previous = devices.remove(packet.address)
        val reference = baseline[packet.address]
        val oldPressure = (reference?.reading as? Tyre)?.pressure?.kpa
        val pressure = (packet.reading as? Tyre)?.pressure?.kpa
        devices[packet.address] = Device(packet,
            if (packet.reading is Tyre) packet else previous?.decoded,
            previous?.firstSeen ?: packet.timestamp, (previous?.packets ?: 0) + 1,
            marked && reference == null,
            previous?.pressureChanged == true ||
                (marked && oldPressure != null && pressure != null &&
                    abs(pressure - oldPressure) >= INSTALLATION_CHANGE_KPA),
            previous?.payloadChanged == true || (marked && reference != null && packet.bytes != reference.bytes))
        if (devices.size > deviceLimit) devices.remove(devices.keys.first())
    }

    /** Take a before-installation snapshot; a candidate is evidence to inspect, never an automatic binding. */
    public fun markInstallation() {
        marked = true
        baseline = devices.mapValues { it.value.decoded ?: it.value.latest }
        devices.replaceAll { _, value ->
            value.copy(newSinceMark = false, pressureChanged = false, payloadChanged = false)
        }
    }

    public fun devices(): List<Device> = devices.values.reversed()
    public fun capture(address: String? = null): List<BleAdvertisement> = packets.filter {
        address == null || it.address == address
    }

    private companion object { const val INSTALLATION_CHANGE_KPA = 5f }
}

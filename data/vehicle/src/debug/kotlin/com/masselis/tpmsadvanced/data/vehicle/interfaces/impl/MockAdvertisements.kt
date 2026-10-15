package com.masselis.tpmsadvanced.data.vehicle.interfaces.impl

import android.Manifest.permission.DUMP
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Parcel
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import co.touchlab.kermit.Logger
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.merge

/** PR #44's debug replay idea, adapted to TyrePing's package and packet formats. */
@SuppressLint("MissingPermission")
internal fun Flow<ScanResult>.withMockAdvertisements(context: Context, filters: List<ScanFilter>?): Flow<ScanResult> =
    merge(this, callbackFlow {
        val logger = Logger.withTag("MockAdvertisements")
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                runCatching {
                    val adapter = requireNotNull(context.getSystemService<BluetoothManager>()?.adapter)
                    check(adapter.isEnabled) { "Enable Bluetooth before replaying packets" }
                    val bytes = intent.packet()
                    require(bytes.isNotEmpty() && bytes.size <= MAX_PACKET_BYTES) { "Invalid packet size" }
                    val result = bytes.asScanResult(
                        adapter.getRemoteDevice(intent.getStringExtra("address") ?: "AA:BB:CC:DD:EE:01"),
                        intent.number("rssi", -60f).toInt()
                    )
                    if (filters != null) {
                        require(filters.any { it.matches(result) }) { "Packet does not match a TPMS scan filter" }
                        require(decodeAdvertisement(result)?.asTyre() != null) { "Unsupported TPMS advertisement" }
                    }
                    check(trySend(result).isSuccess) { "Scan receiver is busy or closed" }
                }.onSuccess {
                    logger.i("Accepted simulated advertisement")
                    resultCode = 1
                    resultData = "Accepted simulated advertisement"
                }.onFailure {
                    logger.w("Rejected simulated advertisement: ${it.message}")
                    resultCode = 0
                    resultData = it.message
                }
            }
        }
        // DUMP is held by adb shell, but is unavailable to ordinary third-party apps.
        ContextCompat.registerReceiver(
            context, receiver, IntentFilter("${context.packageName}.MOCK_ADVERTISEMENT"),
            DUMP, null, ContextCompat.RECEIVER_EXPORTED
        )
        awaitClose { context.unregisterReceiver(receiver) }
    })

@Suppress("MagicNumber")
private fun Intent.packet(): ByteArray {
    getStringExtra("bytes")?.let { return it.hexToByteArray() }
    val kpa = number("kpa", Float.NaN)
    val celsius = number("celsius", 20f)
    val id = getIntExtra("id", 0x123400)
    return when (getStringExtra("brand") ?: "bekubee") {
        "bekubee" -> MockSensorPacket.bekubee(id, kpa, celsius, number("volts", 3f))
        "sysgration" -> MockSensorPacket.sysgration(
            id, kpa, celsius, number("battery", 100f).toInt(), getIntExtra("wheel", 0), getBooleanExtra("alarm", false)
        )
        else -> error("Use brand bekubee or sysgration, or supply raw bytes")
    }
}

@Suppress("DEPRECATION")
private fun Intent.number(name: String, default: Float): Float = extras?.get(name)?.let {
    require(it is Number) { "$name requires --ei or --ef" }
    it.toFloat()
} ?: default

/** Mirror ScanResult's parcel format to use Android's ScanRecord parser without hidden API calls. */
@Suppress("MagicNumber")
private fun ByteArray.asScanResult(device: BluetoothDevice, rssi: Int): ScanResult {
    val parcel = Parcel.obtain()
    return try {
        parcel.writeInt(1)
        device.writeToParcel(parcel, 0)
        parcel.writeInt(1)
        parcel.writeByteArray(this)
        parcel.writeInt(rssi)
        parcel.writeLong(SystemClock.elapsedRealtimeNanos())
        parcel.writeInt(0x11) // Complete legacy connectable advertisement
        parcel.writeInt(BluetoothDevice.PHY_LE_1M)
        parcel.writeInt(ScanResult.PHY_UNUSED)
        parcel.writeInt(ScanResult.SID_NOT_PRESENT)
        parcel.writeInt(ScanResult.TX_POWER_NOT_PRESENT)
        parcel.writeInt(ScanResult.PERIODIC_INTERVAL_NOT_PRESENT)
        parcel.setDataPosition(0)
        ScanResult.CREATOR.createFromParcel(parcel)
    } finally {
        parcel.recycle()
    }
}

private const val MAX_PACKET_BYTES = 1650

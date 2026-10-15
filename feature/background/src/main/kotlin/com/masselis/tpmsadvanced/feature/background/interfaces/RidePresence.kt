package com.masselis.tpmsadvanced.feature.background.interfaces

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat.getParcelableExtra
import androidx.core.content.getSystemService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

internal data class RidePresence(val present: Boolean, val message: String)

@OptIn(ExperimentalCoroutinesApi::class)
@SuppressLint("MissingPermission")
internal fun Context.ridePresence(trigger: RideTrigger): Flow<RidePresence> {
    val adapter = requireNotNull(getSystemService<BluetoothManager>()?.adapter) { "Bluetooth is unavailable" }
    return callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.STATE_OFF)
                trySend(state == BluetoothAdapter.STATE_ON)
            }
        }
        ContextCompat.registerReceiver(this@ridePresence, receiver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED),
            ContextCompat.RECEIVER_EXPORTED)
        try {
            send(adapter.isEnabled)
            awaitClose()
        } finally { unregisterReceiver(receiver) }
    }.distinctUntilChanged().flatMapLatest { enabled ->
        when {
            !enabled -> flowOf(RidePresence(false, "Waiting for Bluetooth"))
            trigger.beacon -> beaconPresence(adapter, trigger)
            else -> accessoryPresence(adapter, trigger.address)
        }
    }.flowOn(Dispatchers.Main).distinctUntilChanged()
}

@SuppressLint("MissingPermission")
@OptIn(DelicateCoroutinesApi::class)
private fun Context.accessoryPresence(adapter: BluetoothAdapter, address: String) = callbackFlow {
    var sawConnectionEvent = false
    val profiles = mutableMapOf<Int, BluetoothProfile>()
    val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val device = getParcelableExtra(intent, BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
            if (device?.address != address) return
            sawConnectionEvent = true
            val connected = intent.action == BluetoothDevice.ACTION_ACL_CONNECTED
            trySend(RidePresence(connected, if (connected) "Accessory connected" else "Waiting for accessory"))
        }
    }
    val filter = IntentFilter(BluetoothDevice.ACTION_ACL_CONNECTED).apply {
        addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
    }
    ContextCompat.registerReceiver(this@accessoryPresence, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
    val listener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            if (isClosedForSend) { adapter.closeProfileProxy(profile, proxy); return }
            profiles[profile] = proxy
            if (!sawConnectionEvent && proxy.connectedDevices.any { it.address == address }) {
                trySend(RidePresence(true, "Accessory connected"))
            }
        }
        override fun onServiceDisconnected(profile: Int) { profiles.remove(profile) }
    }
    try {
        send(RidePresence(false, "Waiting for accessory"))
        adapter.getProfileProxy(this@accessoryPresence, listener, BluetoothProfile.HEADSET)
        adapter.getProfileProxy(this@accessoryPresence, listener, BluetoothProfile.A2DP)
        awaitClose()
    } finally {
        unregisterReceiver(receiver)
        profiles.forEach { (profile, proxy) -> adapter.closeProfileProxy(profile, proxy) }
    }
}

@SuppressLint("MissingPermission")
@OptIn(ExperimentalCoroutinesApi::class)
private fun Context.beaconPresence(adapter: BluetoothAdapter, trigger: RideTrigger): Flow<RidePresence> {
    val presence = BeaconPresence(trigger.minRssi)
    return waitingScanModes().flatMapLatest { mode -> callbackFlow {
    send(RidePresence(presence.isPresent(SystemClock.elapsedRealtime()), "Watching for beacon"))
    delay(5.seconds) // Space starts even across Bluetooth toggles.
    val scanner = checkNotNull(adapter.bluetoothLeScanner) { "Bluetooth scanner unavailable" }
    val callback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            presence.received(SystemClock.elapsedRealtime(), result.rssi)
        }
        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            results.forEach { onScanResult(0, it) }
        }
        override fun onScanFailed(errorCode: Int) { close(BeaconScanFailure(errorCode)) }
    }
    try {
        scanner.startScan(listOf(ScanFilter.Builder().setDeviceAddress(trigger.address).build()),
            ScanSettings.Builder().setScanMode(mode).build(), callback)
        launch {
            while (true) {
                val present = presence.isPresent(SystemClock.elapsedRealtime())
                send(RidePresence(present, if (present) "Beacon nearby" else "Waiting for beacon"))
                delay(1.seconds)
            }
        }
        awaitClose()
    } finally { runCatching { scanner.stopScan(callback) } }
} }.retryWhen { cause, _ ->
    if ((cause is BeaconScanFailure && cause.code != ScanCallback.SCAN_FAILED_FEATURE_UNSUPPORTED) ||
        cause is IllegalStateException) {
        emit(RidePresence(false, "Beacon scan refused; retrying"))
        delay(30.seconds)
        true
    } else false
}
}

private class BeaconScanFailure(val code: Int) : Exception("Beacon scan failed: $code")

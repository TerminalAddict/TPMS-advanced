package com.masselis.tpmsadvanced.data.vehicle.interfaces.impl

import android.Manifest.permission.ACCESS_COARSE_LOCATION
import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.Manifest.permission.BLUETOOTH_CONNECT
import android.Manifest.permission.BLUETOOTH_SCAN
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.bluetooth.le.ScanSettings.MATCH_MODE_AGGRESSIVE
import android.bluetooth.le.ScanSettings.MATCH_NUM_ONE_ADVERTISEMENT
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.checkSelfPermission
import androidx.core.content.PermissionChecker.PERMISSION_GRANTED
import androidx.core.content.getSystemService
import co.touchlab.kermit.Logger
import com.masselis.tpmsadvanced.data.vehicle.interfaces.BluetoothLeScanner
import com.masselis.tpmsadvanced.data.vehicle.interfaces.BleDiagnostics
import com.masselis.tpmsadvanced.data.vehicle.interfaces.BluetoothLeScanner.Failure
import com.masselis.tpmsadvanced.data.vehicle.interfaces.BluetoothLeScanner.ScanStatus
import com.masselis.tpmsadvanced.data.vehicle.model.ScannerRecord
import com.masselis.tpmsadvanced.data.vehicle.model.BleAdvertisement
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus

@SuppressLint("MissingPermission")
internal class BluetoothLeScannerImpl(
    private val context: Context
) : BluetoothLeScanner {

    private val logger = Logger.withTag("BluetoothLeScannerImpl")

    private val startLimiter = ScanStartLimiter()
    private val status = MutableStateFlow<ScanStatus>(ScanStatus.Idle)
    override val scanStatus = status.asStateFlow()

    private val bluetoothAdapter get() = context.getSystemService<BluetoothManager>()?.adapter
    private val savedAddresses = context.getSharedPreferences("DISCOVERED_SENSORS", Context.MODE_PRIVATE)
    private val addressFilters = MutableStateFlow(savedAddresses.all.values.filterIsInstance<String>().toSet())

    private val bluetoothEnabled = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                    trySend(
                        intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.STATE_OFF) ==
                            BluetoothAdapter.STATE_ON
                    )
                }
            }
        }
        // Bluetooth broadcasts come from a privileged process, not necessarily the system UID.
        // ACTION_STATE_CHANGED is protected by Android; other apps cannot send it.
        ContextCompat.registerReceiver(
            context, receiver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED),
            ContextCompat.RECEIVER_EXPORTED
        )
        try {
            val adapter = bluetoothAdapter ?: throw Failure.Scan(ScanCallback.SCAN_FAILED_FEATURE_UNSUPPORTED)
            // Register first so a toggle between the snapshot and registration cannot be missed.
            send(adapter.isEnabled)
            awaitClose()
        } finally {
            context.unregisterReceiver(receiver)
        }
    }.buffer(Channel.UNLIMITED).flowOn(Dispatchers.Main)

    @SuppressLint("InlinedApi")
    @RequiresPermission("android.permission.BLUETOOTH_SCAN")
    private fun scan(scanFilters: List<ScanFilter>?) = callbackFlow<ScanEvent<ScanResult>> {
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                launch { send(ScanEvent.Reading(result)) }
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                launch { results.forEach { send(ScanEvent.Reading(it)) } }
            }

            override fun onScanFailed(errorCode: Int) {
                close(Failure.Scan(errorCode))
            }
        }

        startLimiter.awaitTurn()
        val leScanner = bluetoothAdapter?.bluetoothLeScanner
            ?: throw Failure.ScannerIsNull(bluetoothAdapter?.state)
        try {
            leScanner.startScan(
                scanFilters,
                ScanSettings
                    .Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                    .setLegacy(bluetoothAdapter?.isLeExtendedAdvertisingSupported != true)
                    .setMatchMode(MATCH_MODE_AGGRESSIVE)
                    .setNumOfMatches(MATCH_NUM_ONE_ADVERTISEMENT)
                    .build(),
                callback
            )
            send(ScanEvent.Started)
            awaitClose()
        } catch (cause: IllegalStateException) {
            // Bluetooth can turn off between looking up the scanner and invoking startScan.
            if (bluetoothAdapter?.isEnabled != true) throw Failure.ScannerIsNull(bluetoothAdapter?.state)
            throw cause
        } finally {
            try {
                leScanner.stopScan(callback)
            } catch (cause: IllegalStateException) {
                logger.w("Bluetooth stopped before scan cleanup", cause)
            } catch (cause: SecurityException) {
                logger.w("Bluetooth permission revoked before scan cleanup", cause)
            }
        }
    }.flowOn(Dispatchers.Main) // System's BluetoothLeScanner class as issues if called on a background thread
        .recoveringScan(bluetoothEnabled) { next ->
            status.value = next
            logger.i("Scan status: $next")
        }
        .withMockAdvertisements(context, scanFilters)
        .map {
            val packet = inspectAdvertisement(it)
            if (BleDiagnostics.enabled.value) {
                BleDiagnostics.record(it.scanRecord?.bytes, it.rssi, packet.address,
                    packet.decoder, packet.reading?.sensorId, packet.status)
            }
            packet
        }

    private val filters = SERVICES.map { ScanFilter.Builder().setServiceUuid(it).build() } +
        MANUFACTURER_IDS.map { ScanFilter.Builder().setManufacturerData(it, byteArrayOf()).build() }

    @OptIn(DelicateCoroutinesApi::class, ExperimentalCoroutinesApi::class)
    private val shared = SharedAdvertisementScan(GlobalScope + Dispatchers.Default) { unfiltered ->
        if (unfiltered) scan(null) else addressFilters.flatMapLatest { addresses ->
            scan(filters + addresses.map { ScanFilter.Builder().setDeviceAddress(it).build() })
        }
    }

    private val lowLatencyScanFlow = shared.readings.mapNotNull { it.reading }

    override fun highDutyScan(): Flow<ScannerRecord> = lowLatencyScanFlow

    override fun normalScan(): Flow<ScannerRecord> = lowLatencyScanFlow

    override fun discoverAdvertisements(): Flow<BleAdvertisement> = shared.discover()

    @SuppressLint("UseKtx") // Check commit's result; the KTX helper discards it.
    override suspend fun rememberDiscoveredSensor(sensorId: Int, address: String): Unit = withContext(Dispatchers.IO) {
        require(BluetoothAdapter.checkBluetoothAddress(address)) { "Invalid Bluetooth address" }
        check(savedAddresses.edit().putString(sensorId.toString(), address).commit()) {
            "Could not save discovery address. Retry before leaving discovery."
        }
        addressFilters.update { savedAddresses.all.values.filterIsInstance<String>().toSet() }
    }

    @SuppressLint("InlinedApi")
    @Suppress("MagicNumber")
    override fun missingPermission(): List<String> = when (Build.VERSION.SDK_INT) {
        in Int.MIN_VALUE..28 -> listOf(ACCESS_COARSE_LOCATION)
        in 29..30 -> listOf(ACCESS_FINE_LOCATION)
        in 31..Int.MAX_VALUE -> listOf(BLUETOOTH_CONNECT, BLUETOOTH_SCAN)
        else -> error("Unreachable condition")
    }.filter { checkSelfPermission(context, it) != PERMISSION_GRANTED }

    override val isBluetoothRequired = true

    companion object {
        private val SERVICES = listOf(
            RawSysgration.SERVICE_UUID,
            RawPecham.SERVICE_UUID,
            RawWicarlink.SERVICE_UUID,
            RawBekubeeKy.SERVICE_UUID,
            RawBekubeeTpms.SERVICE_UUID
        )

        private val MANUFACTURER_IDS = emptyList<Int>()
    }
}

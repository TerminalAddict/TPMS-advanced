package com.masselis.tpmsadvanced.feature.main.interfaces.composable

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import com.masselis.tpmsadvanced.core.test.MainDispatcherRule
import com.masselis.tpmsadvanced.data.vehicle.model.BleAdvertisement
import com.masselis.tpmsadvanced.data.vehicle.model.BleDiscoverySession
import com.masselis.tpmsadvanced.data.vehicle.model.Pressure
import com.masselis.tpmsadvanced.data.vehicle.model.Temperature
import com.masselis.tpmsadvanced.data.vehicle.model.Tyre
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle
import org.junit.Rule
import org.junit.Test

internal class DiscoveryLayoutTest {
    @get:Rule val dispatcher = MainDispatcherRule()
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig(screenWidth = 360, screenHeight = 800,
        density = Density.MEDIUM, softButtons = false), theme = "android:Theme.Material3.DayNight.NoActionBar")
    private val packet = BleAdvertisement(100.0, 100L, "AA:BB:CC:DD:EE:01", "TPMS", -56,
        "050854504d530cff0200af4500a7002d56a8b4030327a80000", mapOf("0x0002" to "af4500a7002d56a8b4"),
        listOf("0000a827-0000-1000-8000-00805f9b34fb"), emptyMap(), false, 0,
        "Bekubee TPMS (A827)", Tyre.Unlocated(100.0, -56, 5647616, Pressure(230f), Temperature(19f), 30u, false))

    @Test fun discoveryPortrait() = paparazzi.snapshot {
        val session = BleDiscoverySession().apply { markInstallation(); observe(packet) }
        MaterialTheme { Surface(Modifier.fillMaxSize()) { Column(Modifier.padding(12.dp)) {
            DiscoveryContent("Double axle trailer", Vehicle.Kind.DOUBLE_AXLE_TRAILER.locations,
                Vehicle.Kind.DOUBLE_AXLE_TRAILER.locations.first(), {}, session.devices(), true, true,
                "Scanning all BLE advertisements", 102.0, 1, true, null, "", {}, {}, {}, {}, {}, {}, {}, {})
        } } }
    }

    @Test fun decodedPacket() = paparazzi.snapshot {
        val session = BleDiscoverySession().apply { observe(packet) }
        MaterialTheme { Surface { DiscoveryDeviceCard(session.devices().single(), 101.0, true, {}, {}, {}) } }
    }

    @Test fun unknownUnnamedPacket() = paparazzi.snapshot {
        val session = BleDiscoverySession().apply { markInstallation(); observe(packet.copy(name = null,
            decoder = null, reading = null, serviceUuids = emptyList())) }
        MaterialTheme { Surface { DiscoveryDeviceCard(session.devices().single(), 101.0, true, {}, {}, {}) } }
    }
}

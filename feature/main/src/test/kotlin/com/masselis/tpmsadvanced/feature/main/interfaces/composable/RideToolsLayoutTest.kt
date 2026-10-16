package com.masselis.tpmsadvanced.feature.main.interfaces.composable

import androidx.compose.material3.MaterialTheme
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import com.masselis.tpmsadvanced.core.test.MainDispatcherRule
import com.masselis.tpmsadvanced.data.vehicle.model.MaintenanceReminder
import com.masselis.tpmsadvanced.data.vehicle.model.Pressure
import com.masselis.tpmsadvanced.data.vehicle.model.PressureProfile
import com.masselis.tpmsadvanced.data.vehicle.model.Temperature
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle
import com.masselis.tpmsadvanced.data.vehicle.model.wheelHealth
import com.masselis.tpmsadvanced.feature.main.usecase.FleetHealth
import com.masselis.tpmsadvanced.feature.main.usecase.VehicleHealth
import org.junit.Rule
import org.junit.Test
import java.util.UUID

internal class RideToolsLayoutTest {
    @get:Rule val dispatcher = MainDispatcherRule()
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig(screenWidth = 360, screenHeight = 740,
        density = Density.MEDIUM, softButtons = false), theme = "android:Theme.Material3.DayNight.NoActionBar")
    private val vehicle = Vehicle(UUID.randomUUID(), Vehicle.Kind.DOUBLE_AXLE_TRAILER, "Camping trailer",
        Pressure(180f), Pressure(300f), true, Pressure(200f), Pressure(350f), Temperature(-20f), Temperature(45f),
        Temperature(90f), null)

    @Test fun dashboardWaiting() = paparazzi.snapshot {
        FleetHealth.values.value = listOf(VehicleHealth(vehicle,
            vehicle.kind.locations.map { wheelHealth(vehicle, it, null, 100.0) }, null))
        MaterialTheme { ReadinessCard(vehicle, com.masselis.tpmsadvanced.data.vehicle.model.DrivingInsights()) }
    }
    @Test fun pressureProfile() = paparazzi.snapshot {
        MaterialTheme { ProfileEditor(PressureProfile(name = "Fully loaded", low = 180f, high = 300f,
            separateRear = true, rearLow = 200f, rearHigh = 350f), vehicle, {}, {}) }
    }
    @Test fun maintenanceRecord() = paparazzi.snapshot { MaterialTheme { MaintenanceEditor(vehicle, {}, {}) } }
    @Test fun maintenanceReminder() = paparazzi.snapshot {
        MaterialTheme { ReminderEditor(MaintenanceReminder(dueDate = "2026-11-15", dueKm = 15000f, enabled = true),
            {}, {}) }
    }
}

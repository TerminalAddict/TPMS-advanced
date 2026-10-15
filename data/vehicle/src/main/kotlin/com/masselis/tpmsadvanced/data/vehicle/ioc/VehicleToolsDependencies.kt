package com.masselis.tpmsadvanced.data.vehicle.ioc

import android.content.Context
import com.masselis.tpmsadvanced.data.vehicle.interfaces.CalibrationRepository
import com.masselis.tpmsadvanced.data.vehicle.interfaces.ConfigurationBackups
import com.masselis.tpmsadvanced.data.vehicle.Database
import com.masselis.tpmsadvanced.data.vehicle.interfaces.SensorDatabase
import com.masselis.tpmsadvanced.data.vehicle.interfaces.TyreDatabase
import com.masselis.tpmsadvanced.data.vehicle.interfaces.VehicleDatabase
import com.masselis.tpmsadvanced.data.vehicle.interfaces.BluetoothLeScanner
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

@ContributesTo(AppScope::class)
public interface VehicleToolsDependencies {
    public val toolsSensors: SensorDatabase
    public val toolsVehicles: VehicleDatabase
    public val toolsTyres: TyreDatabase
    public val toolsScanner: BluetoothLeScanner
    public val toolsCalibration: CalibrationRepository
    public val toolsBackups: ConfigurationBackups

    @Provides
    private fun backups(database: Database, context: Context): ConfigurationBackups = ConfigurationBackups(database,
        context)

    @Provides
    @SingleIn(AppScope::class)
    private fun calibration(context: Context): CalibrationRepository = CalibrationRepository(context)
}

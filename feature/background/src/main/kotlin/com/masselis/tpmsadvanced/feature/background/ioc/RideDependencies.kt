package com.masselis.tpmsadvanced.feature.background.ioc

import com.masselis.tpmsadvanced.data.vehicle.interfaces.VehicleDatabase
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo

@ContributesTo(AppScope::class)
public interface RideDependencies {
    public val rideVehicleDatabase: VehicleDatabase
}

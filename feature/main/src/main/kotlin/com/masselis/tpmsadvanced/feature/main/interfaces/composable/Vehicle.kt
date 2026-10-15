@file:Suppress("LongMethod")

package com.masselis.tpmsadvanced.feature.main.interfaces.composable

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.Dimension
import com.masselis.tpmsadvanced.core.ui.KeepScreenOn
import com.masselis.tpmsadvanced.data.vehicle.model.Location
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle.Kind
import com.masselis.tpmsadvanced.feature.main.R
import com.masselis.tpmsadvanced.feature.main.ioc.vehicle.VehicleComponent

@Composable
public fun CurrentVehicle(
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    Vehicle(
        component = LocalVehicleComponent.current,
        snackbarHostState = snackbarHostState,
        modifier = modifier
    )
}

@Composable
public fun Vehicle(
    component: VehicleComponent,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    KeepScreenOn()
    CompositionLocalProvider(LocalVehicleComponent provides component) {
        BoxWithConstraints(modifier) {
            if (maxWidth > maxHeight || component.vehicle.kind in setOf(Kind.CAR_WITH_SPARE, Kind.MONOWHEEL)) {
                LandscapeReadings(component.vehicle.kind.locations, Modifier.fillMaxSize(),
                    binding = { BindSensorButton(it) }) { location ->
                    TyreStat(location, Modifier.fillMaxWidth(), large = true)
                }
            } else when (component.vehicle.kind) {
                Kind.CAR, Kind.VAN, Kind.UTE -> Car(snackbarHostState, Modifier.fillMaxSize())
                Kind.CAR_WITH_SPARE, Kind.MONOWHEEL -> Unit // Handled by labelled cards above.
                Kind.SINGLE_AXLE_TRAILER -> SingleAxleTrailer(snackbarHostState, Modifier.fillMaxSize())
                Kind.DOUBLE_AXLE_TRAILER -> DoubleAxleTrailer(snackbarHostState, Modifier.fillMaxSize())
                Kind.MOTORCYCLE -> Motorcycle(snackbarHostState, Modifier.fillMaxSize())
                Kind.TADPOLE_THREE_WHEELER -> TadpoleThreadWheeler(snackbarHostState, Modifier.fillMaxSize())
                Kind.DELTA_THREE_WHEELER -> DeltaThreeWheeler(snackbarHostState, Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
private fun Car(
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier
) {
    ConstraintLayout(modifier = modifier) {
        val (carConst,
            tyreBox,
            frontLeft,
            frontLeftStats,
            frontLeftBinding,
            frontRight,
            frontRightStats,
            frontRightBinding,
            rearLeft,
            rearLeftStats,
            rearLeftBinding,
            rearRight,
            rearRightStats,
            rearRightBinding
        ) = createRefs()
        Image(
            bitmap = ImageBitmap.imageResource(id = R.drawable.schema_car_top_view),
            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onBackground),
            contentDescription = "Image of your car",
            modifier = Modifier
                .aspectRatio(208f / 462f)
                .constrainAs(carConst) {
                    centerTo(parent)
                    height = Dimension.percent(.7f)
                }
        )
        Box(
            Modifier
                .aspectRatio(235f / 462f)
                .constrainAs(tyreBox) {
                    centerTo(parent)
                    height = Dimension.percent(.55f)
                }
        )
        with(Location.Wheel.FRONT_LEFT) {
            Tyre(
                location = this,
                snackbarHostState = snackbarHostState,
                modifier = Modifier.constrainAs(frontLeft) {
                    top.linkTo(tyreBox.top)
                    start.linkTo(tyreBox.start)
                    width = Dimension.value(30.dp)
                    height = Dimension.value(100.dp)
                }
            )
            TyreStat(
                location = this,
                modifier = Modifier.constrainAs(frontLeftStats) {
                    top.linkTo(frontLeft.top)
                    end.linkTo(frontLeft.start, 8.dp)
                    // If not, the word "bar" for "1,50 bar" is not displayed 🤷
                    width = Dimension.value(100.dp)
                }
            )
            BindSensorButton(
                location = this,
                modifier = Modifier.constrainAs(frontLeftBinding) {
                    top.linkTo(frontLeft.top)
                    start.linkTo(frontLeft.end)
                }
            )
        }
        with(Location.Wheel.FRONT_RIGHT) {
            Tyre(
                location = this,
                snackbarHostState = snackbarHostState,
                modifier = Modifier.constrainAs(frontRight) {
                    top.linkTo(tyreBox.top)
                    end.linkTo(tyreBox.end)
                    width = Dimension.value(30.dp)
                    height = Dimension.value(100.dp)
                }
            )
            TyreStat(
                location = this,
                modifier = Modifier.constrainAs(frontRightStats) {
                    top.linkTo(frontRight.top)
                    start.linkTo(frontRight.end, 8.dp)
                }
            )
            BindSensorButton(
                location = this,
                modifier = Modifier.constrainAs(frontRightBinding) {
                    top.linkTo(frontRight.top)
                    end.linkTo(frontRight.start)
                }
            )
        }
        with(Location.Wheel.REAR_LEFT) {
            Tyre(
                location = this,
                snackbarHostState = snackbarHostState,
                modifier = Modifier.constrainAs(rearLeft) {
                    bottom.linkTo(tyreBox.bottom)
                    start.linkTo(tyreBox.start)
                    width = Dimension.value(30.dp)
                    height = Dimension.value(100.dp)
                }
            )
            TyreStat(
                location = this,
                modifier = Modifier.constrainAs(rearLeftStats) {
                    bottom.linkTo(rearLeft.bottom)
                    end.linkTo(rearLeft.start, 8.dp)
                    width = Dimension.value(100.dp)
                }
            )
            BindSensorButton(
                location = this,
                modifier = Modifier.constrainAs(rearLeftBinding) {
                    bottom.linkTo(rearLeft.bottom)
                    start.linkTo(rearLeft.end)
                }
            )
        }
        with(Location.Wheel.REAR_RIGHT) {
            Tyre(
                location = this,
                snackbarHostState = snackbarHostState,
                modifier = Modifier.constrainAs(rearRight) {
                    bottom.linkTo(tyreBox.bottom)
                    end.linkTo(tyreBox.end)
                    width = Dimension.value(30.dp)
                    height = Dimension.value(100.dp)
                }
            )
            TyreStat(
                location = this,
                modifier = Modifier.constrainAs(rearRightStats) {
                    bottom.linkTo(rearRight.bottom)
                    start.linkTo(rearRight.end, 8.dp)
                }
            )
            BindSensorButton(
                location = this,
                modifier = Modifier.constrainAs(rearRightBinding) {
                    bottom.linkTo(rearRight.bottom)
                    end.linkTo(rearRight.start)
                }
            )
        }
    }
}

@Composable
private fun SingleAxleTrailer(
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier
) {
    ConstraintLayout(modifier = modifier) {
        val (
            vehicleImage,
            tyreLeft,
            leftStats,
            leftBinding,
            tyreRight,
            rightStats,
            rightBinding,
        ) = createRefs()
        Image(
            bitmap = ImageBitmap.imageResource(id = R.drawable.schema_single_axle_trailer_top_view),
            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onBackground),
            contentDescription = "Image of your trailer",
            modifier = Modifier
                .aspectRatio(208f / 462f)
                .constrainAs(vehicleImage) {
                    centerTo(parent)
                    height = Dimension.percent(.7f)
                }
        )
        // The image is centered and takes 70% of the height, a wheel drawn at `y` (0..1) in the
        // image is at `.15f + .7f * y` in the parent.
        val axle = createGuidelineFromTop(.15f + .7f * .686f)
        with(Location.Side.LEFT) {
            Tyre(
                location = this,
                snackbarHostState = snackbarHostState,
                modifier = Modifier
                    .constrainAs(tyreLeft) {
                        start.linkTo(vehicleImage.start)
                        centerAround(axle)
                        width = Dimension.value(30.dp)
                        height = Dimension.value(100.dp)
                    }
            )
            TyreStat(
                location = this,
                modifier = Modifier.constrainAs(leftStats) {
                    top.linkTo(tyreLeft.top)
                    bottom.linkTo(tyreLeft.bottom)
                    end.linkTo(tyreLeft.start, 8.dp)
                    width = Dimension.value(100.dp)
                }
            )
            BindSensorButton(
                location = this,
                modifier = Modifier.constrainAs(leftBinding) {
                    top.linkTo(tyreLeft.top)
                    bottom.linkTo(tyreLeft.bottom)
                    start.linkTo(tyreLeft.end)
                }
            )
        }
        with(Location.Side.RIGHT) {
            Tyre(
                location = this,
                snackbarHostState = snackbarHostState,
                modifier = Modifier
                    .constrainAs(tyreRight) {
                        end.linkTo(vehicleImage.end)
                        centerAround(axle)
                        width = Dimension.value(30.dp)
                        height = Dimension.value(100.dp)
                    }
            )
            TyreStat(
                location = this,
                modifier = Modifier.constrainAs(rightStats) {
                    top.linkTo(tyreRight.top)
                    bottom.linkTo(tyreRight.bottom)
                    start.linkTo(tyreRight.end, 8.dp)
                }
            )
            BindSensorButton(
                location = this,
                modifier = Modifier
                    .constrainAs(rightBinding) {
                        top.linkTo(tyreRight.top)
                        bottom.linkTo(tyreRight.bottom)
                        end.linkTo(tyreRight.start)
                    }
            )
        }
    }
}

@Composable
private fun Motorcycle(
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier
) {
    ConstraintLayout(modifier = modifier) {
        val (
            vehicleImage,
            tyreBox,
            tyreFront,
            frontStats,
            frontBinding,
            tyreRear,
            rearStats,
            rearBinding,
        ) = createRefs()
        Image(
            bitmap = ImageBitmap.imageResource(id = R.drawable.schema_motorcycle_top_view),
            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onBackground),
            contentDescription = "Image of your motorcycle",
            modifier = Modifier
                .aspectRatio(208f / 462f)
                .constrainAs(vehicleImage) {
                    centerTo(parent)
                    height = Dimension.percent(.7f)
                }
        )
        Box(
            Modifier
                .aspectRatio(235f / 462f)
                .constrainAs(tyreBox) {
                    centerHorizontallyTo(parent)
                    centerVerticallyTo(parent, 0.4f)
                    height = Dimension.percent(.65f)
                }
        )
        with(Location.Axle.FRONT) {
            Tyre(
                location = this,
                snackbarHostState = snackbarHostState,
                modifier = Modifier
                    .constrainAs(tyreFront) {
                        start.linkTo(tyreBox.start)
                        end.linkTo(tyreBox.end)
                        top.linkTo(tyreBox.top)
                        width = Dimension.value(30.dp)
                        height = Dimension.value(100.dp)
                    }
            )
            TyreStat(
                location = this,
                modifier = Modifier.constrainAs(frontStats) {
                    centerHorizontallyTo(tyreFront)
                    bottom.linkTo(vehicleImage.top, 8.dp)
                }
            )
            BindSensorButton(
                location = this,
                modifier = Modifier.constrainAs(frontBinding) {
                    top.linkTo(tyreFront.top)
                    bottom.linkTo(tyreFront.bottom)
                    end.linkTo(tyreFront.start)
                }
            )
        }
        with(Location.Axle.REAR) {
            Tyre(
                location = this,
                snackbarHostState = snackbarHostState,
                modifier = Modifier
                    .constrainAs(tyreRear) {
                        start.linkTo(tyreBox.start)
                        end.linkTo(tyreBox.end)
                        bottom.linkTo(tyreBox.bottom)
                        width = Dimension.value(30.dp)
                        height = Dimension.value(100.dp)
                    }
            )
            TyreStat(
                location = this,
                modifier = Modifier.constrainAs(rearStats) {
                    centerHorizontallyTo(tyreRear)
                    top.linkTo(vehicleImage.bottom, 8.dp)
                }
            )
            BindSensorButton(
                location = this,
                modifier = Modifier
                    .constrainAs(rearBinding) {
                        top.linkTo(tyreRear.top)
                        bottom.linkTo(tyreRear.bottom)
                        end.linkTo(tyreRear.start)
                    }
            )
        }
    }
}

@Composable
private fun TadpoleThreadWheeler(
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    ConstraintLayout(modifier = modifier) {
        val (
            vehicleImage,
            frontTrack,
            frontLeft,
            frontLeftStats,
            frontLeftBinding,
            frontRight,
            frontRightStats,
            frontRightBinding,
            tyreRear,
            rearStats,
            rearBinding,
        ) = createRefs()
        Image(
            bitmap = ImageBitmap.imageResource(id = R.drawable.schema_tadpole_three_wheeler_top_view),
            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onBackground),
            contentDescription = "Image of your three-wheeler",
            modifier = Modifier
                .aspectRatio(208f / 462f)
                .constrainAs(vehicleImage) {
                    centerTo(parent)
                    height = Dimension.percent(.7f)
                }
        )
        // Spans between the centers of the two front wheels and is centered like the image.
        // Tyres are centered on its edges so they stay on the drawn wheels whatever the
        // screen width.
        Box(
            Modifier
                .aspectRatio(.58f * 208f / 462f)
                .constrainAs(frontTrack) {
                    centerTo(parent)
                    height = Dimension.percent(.7f)
                }
        )
        // The image is centered and takes 70% of the height, a wheel drawn at `y` (0..1) in the
        // image is at `.15f + .7f * y` in the parent.
        val frontAxle = createGuidelineFromTop(.15f + .7f * .1005f)
        val rearAxle = createGuidelineFromTop(.15f + .7f * .845f)
        with(Location.Wheel.FRONT_LEFT) {
            Tyre(
                location = this,
                snackbarHostState = snackbarHostState,
                modifier = Modifier.constrainAs(frontLeft) {
                    centerAround(frontTrack.start)
                    centerAround(frontAxle)
                    width = Dimension.value(30.dp)
                    height = Dimension.value(100.dp)
                }
            )
            TyreStat(
                location = this,
                modifier = Modifier.constrainAs(frontLeftStats) {
                    top.linkTo(frontLeft.top)
                    end.linkTo(vehicleImage.start, 8.dp)
                    // If not, the word "bar" for "1,50 bar" is not displayed 🤷
                    width = Dimension.value(100.dp)
                }
            )
            BindSensorButton(
                location = this,
                modifier = Modifier.constrainAs(frontLeftBinding) {
                    top.linkTo(frontLeft.bottom)
                    centerHorizontallyTo(frontLeft)
                }
            )
        }
        with(Location.Wheel.FRONT_RIGHT) {
            Tyre(
                location = this,
                snackbarHostState = snackbarHostState,
                modifier = Modifier.constrainAs(frontRight) {
                    centerAround(frontTrack.end)
                    centerAround(frontAxle)
                    width = Dimension.value(30.dp)
                    height = Dimension.value(100.dp)
                }
            )
            TyreStat(
                location = this,
                modifier = Modifier.constrainAs(frontRightStats) {
                    top.linkTo(frontRight.top)
                    start.linkTo(vehicleImage.end, 8.dp)
                }
            )
            BindSensorButton(
                location = this,
                modifier = Modifier.constrainAs(frontRightBinding) {
                    top.linkTo(frontRight.bottom)
                    centerHorizontallyTo(frontRight)
                }
            )
        }
        with(Location.Axle.REAR) {
            Tyre(
                location = this,
                snackbarHostState = snackbarHostState,
                modifier = Modifier
                    .constrainAs(tyreRear) {
                        centerHorizontallyTo(vehicleImage)
                        centerAround(rearAxle)
                        width = Dimension.value(30.dp)
                        height = Dimension.value(100.dp)
                    }
            )
            TyreStat(
                location = this,
                modifier = Modifier.constrainAs(rearStats) {
                    centerHorizontallyTo(tyreRear)
                    top.linkTo(vehicleImage.bottom, 8.dp)
                }
            )
            BindSensorButton(
                location = this,
                modifier = Modifier
                    .constrainAs(rearBinding) {
                        top.linkTo(tyreRear.top)
                        bottom.linkTo(tyreRear.bottom)
                        end.linkTo(tyreRear.start)
                    }
            )
        }
    }
}

@Composable
private fun DeltaThreeWheeler(
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    ConstraintLayout(modifier = modifier) {
        val (
            vehicleImage,
            rearTrack,
            tyreFront,
            frontStats,
            frontBinding,
            rearLeft,
            rearLeftStats,
            rearLeftBinding,
            rearRight,
            rearRightStats,
            rearRightBinding
        ) = createRefs()
        Image(
            bitmap = ImageBitmap.imageResource(id = R.drawable.schema_delta_three_wheeler_top_view),
            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onBackground),
            contentDescription = "Image of your three-wheeler",
            modifier = Modifier
                .aspectRatio(208f / 462f)
                .constrainAs(vehicleImage) {
                    centerTo(parent)
                    height = Dimension.percent(.7f)
                }
        )
        // Spans between the centers of the two rear fenders and is centered like the image.
        // Tyres are centered on its edges so they stay on the drawn wheels whatever the
        // screen width.
        Box(
            Modifier
                .aspectRatio(.713f * 208f / 462f)
                .constrainAs(rearTrack) {
                    centerTo(parent)
                    height = Dimension.percent(.7f)
                }
        )
        // The image is centered and takes 70% of the height, a wheel drawn at `y` (0..1) in the
        // image is at `.15f + .7f * y` in the parent.
        val frontAxle = createGuidelineFromTop(.15f + .7f * .0865f)
        val rearAxle = createGuidelineFromTop(.15f + .7f * .835f)
        with(Location.Axle.FRONT) {
            Tyre(
                location = this,
                snackbarHostState = snackbarHostState,
                modifier = Modifier
                    .constrainAs(tyreFront) {
                        centerHorizontallyTo(vehicleImage)
                        centerAround(frontAxle)
                        width = Dimension.value(30.dp)
                        height = Dimension.value(100.dp)
                    }
            )
            TyreStat(
                location = this,
                modifier = Modifier.constrainAs(frontStats) {
                    centerHorizontallyTo(tyreFront)
                    bottom.linkTo(tyreFront.top, 8.dp)
                }
            )
            BindSensorButton(
                location = this,
                modifier = Modifier.constrainAs(frontBinding) {
                    top.linkTo(tyreFront.top)
                    bottom.linkTo(tyreFront.bottom)
                    end.linkTo(tyreFront.start)
                }
            )
        }
        with(Location.Wheel.REAR_LEFT) {
            Tyre(
                location = this,
                snackbarHostState = snackbarHostState,
                modifier = Modifier.constrainAs(rearLeft) {
                    centerAround(rearTrack.start)
                    centerAround(rearAxle)
                    width = Dimension.value(30.dp)
                    height = Dimension.value(100.dp)
                }
            )
            TyreStat(
                location = this,
                modifier = Modifier.constrainAs(rearLeftStats) {
                    bottom.linkTo(rearLeft.bottom)
                    end.linkTo(vehicleImage.start, 8.dp)
                    width = Dimension.value(100.dp)
                }
            )
            BindSensorButton(
                location = this,
                modifier = Modifier.constrainAs(rearLeftBinding) {
                    bottom.linkTo(rearLeft.top)
                    centerHorizontallyTo(rearLeft)
                }
            )
        }
        with(Location.Wheel.REAR_RIGHT) {
            Tyre(
                location = this,
                snackbarHostState = snackbarHostState,
                modifier = Modifier.constrainAs(rearRight) {
                    centerAround(rearTrack.end)
                    centerAround(rearAxle)
                    width = Dimension.value(30.dp)
                    height = Dimension.value(100.dp)
                }
            )
            TyreStat(
                location = this,
                modifier = Modifier.constrainAs(rearRightStats) {
                    bottom.linkTo(rearRight.bottom)
                    start.linkTo(vehicleImage.end, 8.dp)
                }
            )
            BindSensorButton(
                location = this,
                modifier = Modifier.constrainAs(rearRightBinding) {
                    bottom.linkTo(rearRight.top)
                    centerHorizontallyTo(rearRight)
                }
            )
        }
    }
}

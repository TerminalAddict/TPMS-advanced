@file:Suppress("LongMethod")

package com.masselis.tpmsadvanced.feature.main.interfaces.composable

import android.content.res.Configuration
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.Dimension
import com.masselis.tpmsadvanced.core.ui.KeepScreenOn
import com.masselis.tpmsadvanced.core.ui.LocalReadability
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
        VehicleLayout(component.vehicle.kind, modifier,
            cards = { contentModifier ->
                LandscapeReadings(component.vehicle.kind.locations, contentModifier,
                    binding = { BindSensorButton(it) }) { location ->
                    TyreStat(location, Modifier.fillMaxWidth(), large = true)
                }
            },
            diagram = { contentModifier -> when (component.vehicle.kind) {
                Kind.CAR, Kind.VAN, Kind.UTE -> FourWheelVehicle(
                    component.vehicle.kind, snackbarHostState, contentModifier
                )
                Kind.CAR_WITH_SPARE, Kind.MONOWHEEL -> Unit // Handled by labelled cards above.
                Kind.SINGLE_AXLE_TRAILER -> SingleAxleTrailer(snackbarHostState, contentModifier)
                Kind.DOUBLE_AXLE_TRAILER -> DoubleAxleTrailer(snackbarHostState, contentModifier)
                Kind.MOTORCYCLE -> Motorcycle(snackbarHostState, contentModifier)
                Kind.TADPOLE_THREE_WHEELER -> TadpoleThreadWheeler(snackbarHostState, contentModifier)
                Kind.DELTA_THREE_WHEELER -> DeltaThreeWheeler(snackbarHostState, contentModifier)
            } },
        )
    }
}

/** Text size and dashboard controls must not silently replace a portrait vehicle diagram. */
@Composable
internal fun VehicleLayout(
    kind: Kind,
    modifier: Modifier = Modifier,
    diagram: @Composable (Modifier) -> Unit,
    cards: @Composable (Modifier) -> Unit,
) {
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val useCards = landscape || LocalReadability.current.readingCards ||
        kind == Kind.CAR_WITH_SPARE || kind == Kind.MONOWHEEL
    Box(modifier) {
        if (useCards) cards(Modifier.fillMaxSize()) else diagram(Modifier.fillMaxSize())
    }
}

@Composable
private fun FourWheelVehicle(
    kind: Kind,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier
) {
    FourWheelVehicleLayout(kind, modifier,
        tyre = { location, tyreModifier -> Tyre(location, snackbarHostState, tyreModifier) },
        reading = { location, readingModifier -> TyreStat(location, readingModifier) },
        binding = { location, bindingModifier -> BindSensorButton(location, bindingModifier) },
    )
}

@Composable
internal fun FourWheelVehicleLayout(
    kind: Kind,
    modifier: Modifier = Modifier,
    tyre: @Composable (Location, Modifier) -> Unit,
    reading: @Composable (Location, Modifier) -> Unit,
    binding: @Composable (Location, Modifier) -> Unit,
) {
    val (schema, description) = when (kind) {
        Kind.VAN -> R.drawable.schema_van_top_view to "Van viewed from above, front at the top"
        Kind.UTE -> R.drawable.schema_ute_top_view to "Ute viewed from above, front at the top"
        else -> R.drawable.schema_car_top_view to "Car viewed from above, front at the top"
    }
    BoxWithConstraints(modifier) {
        // Keep the two reading rows apart when dashboard controls leave little vertical space.
        val diagramHeight = maxOf(maxHeight, MIN_DIAGRAM_HEIGHT_DP.dp)
        ConstraintLayout(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).height(diagramHeight)) {
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
                bitmap = ImageBitmap.imageResource(id = schema),
                colorFilter = fourWheelSchemaFilter(kind),
                contentDescription = description,
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
                tyre(
                    this,
                    Modifier.constrainAs(frontLeft) {
                        top.linkTo(tyreBox.top)
                        start.linkTo(tyreBox.start)
                        width = Dimension.value(30.dp)
                        height = Dimension.value(100.dp)
                    }
                )
                reading(
                    this,
                    Modifier.constrainAs(frontLeftStats) {
                        top.linkTo(frontLeft.top)
                        end.linkTo(frontLeft.start, 8.dp)
                        start.linkTo(parent.start, 8.dp)
                        width = Dimension.fillToConstraints
                    }
                )
                binding(
                    this,
                    Modifier.constrainAs(frontLeftBinding) {
                        top.linkTo(frontLeft.top)
                        start.linkTo(frontLeft.end)
                    }
                )
            }
            with(Location.Wheel.FRONT_RIGHT) {
                tyre(
                    this,
                    Modifier.constrainAs(frontRight) {
                        top.linkTo(tyreBox.top)
                        end.linkTo(tyreBox.end)
                        width = Dimension.value(30.dp)
                        height = Dimension.value(100.dp)
                    }
                )
                reading(
                    this,
                    Modifier.constrainAs(frontRightStats) {
                        top.linkTo(frontRight.top)
                        start.linkTo(frontRight.end, 8.dp)
                        end.linkTo(parent.end, 8.dp)
                        width = Dimension.fillToConstraints
                    }
                )
                binding(
                    this,
                    Modifier.constrainAs(frontRightBinding) {
                        top.linkTo(frontRight.top)
                        end.linkTo(frontRight.start)
                    }
                )
            }
            with(Location.Wheel.REAR_LEFT) {
                tyre(
                    this,
                    Modifier.constrainAs(rearLeft) {
                        bottom.linkTo(tyreBox.bottom)
                        start.linkTo(tyreBox.start)
                        width = Dimension.value(30.dp)
                        height = Dimension.value(100.dp)
                    }
                )
                reading(
                    this,
                    Modifier.constrainAs(rearLeftStats) {
                        bottom.linkTo(rearLeft.bottom)
                        end.linkTo(rearLeft.start, 8.dp)
                        start.linkTo(parent.start, 8.dp)
                        width = Dimension.fillToConstraints
                    }
                )
                binding(
                    this,
                    Modifier.constrainAs(rearLeftBinding) {
                        bottom.linkTo(rearLeft.bottom)
                        start.linkTo(rearLeft.end)
                    }
                )
            }
            with(Location.Wheel.REAR_RIGHT) {
                tyre(
                    this,
                    Modifier.constrainAs(rearRight) {
                        bottom.linkTo(tyreBox.bottom)
                        end.linkTo(tyreBox.end)
                        width = Dimension.value(30.dp)
                        height = Dimension.value(100.dp)
                    }
                )
                reading(
                    this,
                    Modifier.constrainAs(rearRightStats) {
                        bottom.linkTo(rearRight.bottom)
                        start.linkTo(rearRight.end, 8.dp)
                        end.linkTo(parent.end, 8.dp)
                        width = Dimension.fillToConstraints
                    }
                )
                binding(
                    this,
                    Modifier.constrainAs(rearRightBinding) {
                        bottom.linkTo(rearRight.bottom)
                        end.linkTo(rearRight.start)
                    }
                )
            }
        }
    }
}

private const val MIN_DIAGRAM_HEIGHT_DP = 420

@Suppress("MagicNumber")
@Composable
private fun fourWheelSchemaFilter(kind: Kind): ColorFilter {
    val ink = MaterialTheme.colorScheme.onBackground
    if (kind == Kind.CAR) return ColorFilter.tint(ink)
    // Generated schematics mix transparent areas and white infill. Alpha minus red removes
    // white while keeping the transparent surroundings clear; only the ink takes the theme colour.
    return ColorFilter.colorMatrix(ColorMatrix(floatArrayOf(
        0f, 0f, 0f, 0f, ink.red * 255f,
        0f, 0f, 0f, 0f, ink.green * 255f,
        0f, 0f, 0f, 0f, ink.blue * 255f,
        -1f, 0f, 0f, 1f, 0f,
    )))
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

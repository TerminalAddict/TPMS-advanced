package com.masselis.tpmsadvanced.feature.main.interfaces.composable

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.Dimension
import com.masselis.tpmsadvanced.data.vehicle.model.Location
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle
import com.masselis.tpmsadvanced.feature.main.R

@Composable
internal fun DoubleAxleTrailer(snackbarHostState: SnackbarHostState, modifier: Modifier = Modifier) {
    DoubleAxleTrailerLayout(
        modifier = modifier,
        tyre = { location, tyreModifier -> Tyre(location, snackbarHostState, tyreModifier) },
        reading = { location, readingModifier -> TyreStat(location, readingModifier) },
        binding = { location, bindingModifier -> BindSensorButton(location, bindingModifier) },
    )
}

/** Front is the axle nearest the hitch. Slots share this layout with the local render tests. */
@Suppress("LongMethod", "MagicNumber")
@Composable
internal fun DoubleAxleTrailerLayout(
    modifier: Modifier = Modifier,
    tyre: @Composable (Location, Modifier) -> Unit,
    reading: @Composable (Location, Modifier) -> Unit,
    binding: @Composable (Location, Modifier) -> Unit,
) {
    BoxWithConstraints(modifier) {
        val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
        val contentHeight = maxHeight.coerceAtLeast(640.dp * fontScale)
        val imageWidth = maxWidth * .42f
        val imageHeight = imageWidth * (1526f / 688f)
        val imageTop = (contentHeight - imageHeight) / 2
        val readingWidth = (maxWidth - imageWidth) / 2 - 8.dp
        val ink = MaterialTheme.colorScheme.onBackground
        // The supplied artwork is opaque grayscale. Turn white into transparent and tint the
        // remaining ink at draw time so the original asset also works on a dark background.
        val lineArtFilter = ColorFilter.colorMatrix(ColorMatrix(floatArrayOf(
            0f, 0f, 0f, 0f, ink.red * 255f,
            0f, 0f, 0f, 0f, ink.green * 255f,
            0f, 0f, 0f, 0f, ink.blue * 255f,
            -1f, 0f, 0f, 0f, 255f,
        )))
        Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            ConstraintLayout(Modifier.fillMaxWidth().height(contentHeight)) {
                val diagram = createRef()
                val frontAxle = createGuidelineFromTop(imageTop + imageHeight * .625f)
                val rearAxle = createGuidelineFromTop(imageTop + imageHeight * .763f)
                Image(
                    painter = painterResource(R.drawable.schema_double_axle_trailer_top_view),
                    contentDescription = "Double axle trailer, hitch at the front",
                    colorFilter = lineArtFilter,
                    modifier = Modifier.constrainAs(diagram) {
                        centerTo(parent)
                        width = Dimension.value(imageWidth)
                        height = Dimension.value(imageHeight)
                    },
                )
                Vehicle.Kind.DOUBLE_AXLE_TRAILER.locations.forEach { location ->
                    val wheel = location as Location.Wheel
                    val isFront = wheel.toAxle() == Location.Axle.FRONT
                    val isLeft = wheel.toSide() == Location.Side.LEFT
                    val (wheelRef, statsRef, bindingRef) = createRefs()
                    tyre(location, Modifier.constrainAs(wheelRef) {
                        if (isLeft) start.linkTo(diagram.start) else end.linkTo(diagram.end)
                        centerAround(if (isFront) frontAxle else rearAxle)
                        width = Dimension.value(imageWidth * .09f)
                        height = Dimension.value(imageHeight * .115f)
                    })
                    Column(
                        modifier = Modifier.constrainAs(statsRef) {
                            if (isLeft) end.linkTo(wheelRef.start, 4.dp)
                            else start.linkTo(wheelRef.end, 4.dp)
                            // Readings extend away from the tandem axles, leaving room for
                            // battery/leak warnings without colliding with the other axle.
                            if (isFront) bottom.linkTo(wheelRef.top, 4.dp)
                            else top.linkTo(wheelRef.bottom, 4.dp)
                            width = Dimension.value(readingWidth)
                        },
                        horizontalAlignment = if (isLeft) Alignment.End else Alignment.Start,
                    ) {
                        Text(
                            buildString { appendLoc(location, withType = false, capitalized = true) },
                            style = MaterialTheme.typography.labelMedium,
                        )
                        reading(location, Modifier.fillMaxWidth())
                    }
                    binding(location, Modifier.constrainAs(bindingRef) {
                        if (isLeft) start.linkTo(wheelRef.end) else end.linkTo(wheelRef.start)
                        if (isFront) bottom.linkTo(wheelRef.top) else top.linkTo(wheelRef.bottom)
                    })
                }
            }
        }
    }
}

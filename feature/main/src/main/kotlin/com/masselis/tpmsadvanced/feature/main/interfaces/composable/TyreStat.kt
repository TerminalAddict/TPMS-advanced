package com.masselis.tpmsadvanced.feature.main.interfaces.composable

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.masselis.tpmsadvanced.core.common.now
import com.masselis.tpmsadvanced.core.ui.viewModel
import com.masselis.tpmsadvanced.data.unit.model.PressureUnit
import com.masselis.tpmsadvanced.data.unit.model.TemperatureUnit
import com.masselis.tpmsadvanced.data.vehicle.model.Location
import com.masselis.tpmsadvanced.data.vehicle.model.Location.Side.LEFT
import com.masselis.tpmsadvanced.data.vehicle.model.Location.Side.RIGHT
import com.masselis.tpmsadvanced.data.vehicle.model.Pressure.CREATOR.bar
import com.masselis.tpmsadvanced.data.vehicle.model.Temperature.CREATOR.celsius
import com.masselis.tpmsadvanced.feature.main.interfaces.viewmodel.TyreStatsViewModel
import com.masselis.tpmsadvanced.feature.main.ioc.tyre.TyreBindings.Companion.TyreStatsViewModel
import com.masselis.tpmsadvanced.feature.main.ioc.tyre.TyreComponent.Companion.TyreComponent
import com.masselis.tpmsadvanced.feature.main.ioc.tyre.TyreComponent.Companion.keyed
import com.masselis.tpmsadvanced.feature.main.ioc.vehicle.VehicleComponent
import com.masselis.tpmsadvanced.feature.main.usecase.TyreStatsStateFlow.State
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@Composable
internal fun TyreStat(
    location: Location,
    modifier: Modifier = Modifier,
    large: Boolean = false,
    vehicleComponent: VehicleComponent = LocalVehicleComponent.current,
    viewModel: TyreStatsViewModel = vehicleComponent
        .TyreComponent(location)
        .let { viewModel(it.keyed()) { it.TyreStatsViewModel() } },
) {
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()
    var enlarged by rememberSaveable(location) { mutableStateOf(false) }
    var manage by rememberSaveable(location) { mutableStateOf(false) }
    ReadingValues(location, state, modifier.clickable(onClickLabel = "Readings or assign sensor") {
        if (state == State.NotDetected) manage = true else enlarged = true
    },
        size = if (large) ReadingSize.CARD else ReadingSize.NORMAL)
    if (manage) SensorManagementDialog(vehicleComponent.vehicle, location) { manage = false }
    if (enlarged) {
        LaunchedEffect(Unit) { delay(5.seconds); enlarged = false }
        Dialog(onDismissRequest = { enlarged = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(shape = MaterialTheme.shapes.large, modifier = Modifier.padding(16.dp)) {
                MagnifiedReading(location, state, onClose = { enlarged = false })
            }
        }
    }
}

@Composable
internal fun MagnifiedReading(location: Location, state: State, onClose: () -> Unit) {
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Text(buildString { appendLoc(location, withType = false, capitalized = true) },
            style = MaterialTheme.typography.titleLarge)
        ReadingValues(location, state, size = ReadingSize.MAGNIFIED)
        TextButton(onClick = onClose) { Text("Close · closes after 5 seconds") }
    }
}

internal enum class ReadingSize { NORMAL, CARD, MAGNIFIED }

@Suppress("NAME_SHADOWING", "LongMethod", "CyclomaticComplexMethod")
@Composable
internal fun ReadingValues(
    location: Location,
    state: State,
    modifier: Modifier = Modifier,
    size: ReadingSize = ReadingSize.NORMAL,
) {
    val (pressure, temperature) = when (val state = state) {
        State.NotDetected -> null to null
        is State.Normal -> Pair(
            Pair(state.pressure, state.pressureUnit),
            Pair(state.temperature, state.temperatureUnit)
        )

        is State.Alerting -> Pair(
            Pair(state.pressure, state.pressureUnit),
            Pair(state.temperature, state.temperatureUnit)
        )
    }
    val sensorId = when (state) {
        State.NotDetected -> null
        is State.Normal -> state.sensorId
        is State.Alerting -> state.sensorId
    }
    val battery = when (state) {
        State.NotDetected -> null
        is State.Normal -> state.sensorBattery
        is State.Alerting -> state.sensorBattery
    }
    val timestamp = when (state) {
        State.NotDetected -> null
        is State.Normal -> state.timestamp
        is State.Alerting -> state.timestamp
    }
    val loss = when (state) {
        State.NotDetected -> null
        is State.Normal -> state.pressureLoss
        is State.Alerting -> state.pressureLoss
    }
    val color = when (state) {
        State.NotDetected, is State.Normal -> MaterialTheme.colorScheme.onSurface
        is State.Alerting -> MaterialTheme.colorScheme.error
    }
    var isVisible by remember { mutableStateOf(true) }
    if (state is State.Alerting) {
        LaunchedEffect(key1 = isVisible) {
            launch {
                repeat(Int.MAX_VALUE) {
                    delay(300.milliseconds)
                    isVisible = !isVisible
                }
            }
        }
    } else
        isVisible = true
    val alignment = remember(location, size) {
        if (size != ReadingSize.NORMAL) Alignment.CenterHorizontally else
        when (location) {
            is Location.Auxiliary -> Alignment.CenterHorizontally
            is Location.Axle -> Alignment.Start
            is Location.Wheel -> when (location.toSide()) {
                LEFT -> Alignment.End
                RIGHT -> Alignment.Start
            }

            is Location.Side -> when (location) {
                LEFT -> Alignment.End
                RIGHT -> Alignment.Start
            }
        }
    }
    Column(
        modifier = modifier.alpha(if (isVisible) 1f else 0f)
    ) {
        Text(
            pressure?.let { (value, unit) -> value.string(unit) } ?: "-.--",
            fontWeight = FontWeight.SemiBold,
            fontSize = when (size) {
                ReadingSize.NORMAL -> TextUnit.Unspecified
                ReadingSize.CARD -> 32.sp
                ReadingSize.MAGNIFIED -> 48.sp
            },
            maxLines = 1,
            color = color,
            modifier = Modifier.align(alignment),
        )

        Text(
            temperature?.let { (value, unit) -> value.string(unit) } ?: "-.-",
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            fontSize = if (size == ReadingSize.MAGNIFIED) 28.sp else 16.sp,
            color = color,
            modifier = Modifier.align(alignment),
        )

        battery?.let {
            Text(
                text = (if (it.isLow) "Low battery: " else "Battery: ") + it.label(),
                fontSize = if (size == ReadingSize.NORMAL) 12.sp else 16.sp,
                color = if (it.isLow) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.align(alignment),
            )
        }

        loss?.let {
            Text("Possible leak (experimental): −${it.drop.string(checkNotNull(pressure).second)}",
                fontSize = if (size == ReadingSize.NORMAL) 12.sp else 16.sp,
                color = MaterialTheme.colorScheme.error, modifier = Modifier.align(alignment))
        }

        if (sensorId != null && timestamp != null) {
            val displaySensorId =
                if ((sensorId ushr 24) == 0) {
                    "%02X%02X%02X".format(
                        sensorId and 0xFF,
                        (sensorId shr 8) and 0xFF,
                        (sensorId shr 16) and 0xFF,
                    )
                } else {
                    "0x%08X".format(sensorId)
                }

            val lastReceived = SimpleDateFormat(
                "dd/MM/yyyy h:mma",
                LocalLocale.current.platformLocale
            )
                .format(Date((timestamp * 1000).toLong()))
                .lowercase()

            Text(
                text = "$displaySensorId  Last: $lastReceived",
                fontSize = if (size == ReadingSize.NORMAL) 9.sp else 12.sp,
                maxLines = if (size == ReadingSize.NORMAL) 1 else 2,
                color = color,
                modifier = Modifier.align(alignment),
            )
            if (timestamp > 0.0) {
                Text(
                    text = "Last seen ${elapsedSinceUpdate(timestamp)} ago",
                    fontSize = if (size == ReadingSize.NORMAL) 9.sp else 12.sp,
                    maxLines = 1,
                    color = color,
                    modifier = Modifier.align(alignment),
                )
            }
        }
    }
}

/**
 * Updates the elapsed-time label independently of BLE sensor readings.
 * A new sensor timestamp restarts the timer.
 */
@Composable
private fun elapsedSinceUpdate(timestamp: Double): String {
    val label by produceState(
        initialValue = elapsedLabel(timestamp, now()),
        key1 = timestamp,
    ) {
        while (true) {
            val currentTime = now()

            value = elapsedLabel(timestamp, currentTime)

            delay(nextElapsedTick(timestamp, currentTime))
        }
    }

    return label
}

internal fun elapsedLabel(
    timestamp: Double,
    currentTime: Double,
): String {
    val minutes = totalMinutesSince(timestamp, currentTime)
    val hours = minutes / 60

    return when {
        minutes == 0L -> "<1 min"
        minutes < 120L -> "$minutes min"
        hours < 48L -> "$hours hours"
        else -> "${hours / 24} days"
    }
}

private fun totalMinutesSince(
    timestamp: Double,
    currentTime: Double,
): Long =
    ((currentTime - timestamp) / 60.0)
        .toLong()
        .coerceAtLeast(0L)

/**
 * Wake up at the next relevant boundary:
 * every minute, then every hour, then every day.
 */
internal fun nextElapsedTick(
    timestamp: Double,
    currentTime: Double,
): Duration {
    val minutes = totalMinutesSince(timestamp, currentTime)
    val hours = minutes / 60

    val nextBoundarySeconds = when {
        minutes < 120L ->
            (minutes + 1) * 60L

        hours < 48L ->
            (hours + 1) * 3600L

        else ->
            (hours / 24 + 1) * 86400L
    }

    return (timestamp + nextBoundarySeconds - currentTime)
        .seconds
        .coerceAtLeast(1.seconds)
}

@Preview
@Composable
internal fun TyreStatNotDetectedPreview() {
    ReadingValues(
        location = Location.Wheel.REAR_RIGHT,
        state = State.NotDetected,
    )
}


@Preview
@Composable
internal fun TyreStatNormalPreview() {
    ReadingValues(
        location = Location.Wheel.REAR_RIGHT,
        state =
            State.Normal(
                0.0,
                0,
                2f.bar,
                PressureUnit.BAR,
                30f.celsius,
                TemperatureUnit.CELSIUS
            ),
    )
}


@Preview
@Composable
internal fun TyreStatAlertingPreview() {
    ReadingValues(
        location = Location.Wheel.REAR_RIGHT,
        state = State.Alerting(
            0.0,
            0,
            0.5f.bar,
            PressureUnit.BAR,
            150f.celsius,
            TemperatureUnit.CELSIUS
        ),
    )
}

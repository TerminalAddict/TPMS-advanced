package com.masselis.tpmsadvanced.feature.main.interfaces.composable

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.masselis.tpmsadvanced.core.common.appGraph
import com.masselis.tpmsadvanced.core.common.now
import com.masselis.tpmsadvanced.data.vehicle.ioc.VehicleToolsDependencies
import com.masselis.tpmsadvanced.data.vehicle.interfaces.TyreDatabase
import com.masselis.tpmsadvanced.data.vehicle.model.ReadingSession
import com.masselis.tpmsadvanced.data.vehicle.model.Tyre
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle
import com.masselis.tpmsadvanced.data.vehicle.model.readingSessions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

@Composable
public fun ReadingHistoryButton(vehicle: Vehicle) {
    var show by remember { mutableStateOf(false) }
    TextButton(onClick = { show = true }) { Text("Pressure / temperature history and CSV") }
    if (show) ReadingHistoryDialog(vehicle) { show = false }
}

@Suppress("LongMethod", "CyclomaticComplexMethod", "TooGenericExceptionCaught")
// Report storage/provider failures in the dialog.
@Composable
private fun ReadingHistoryDialog(vehicle: Vehicle, dismiss: () -> Unit) {
    val context = LocalContext.current
    val database = remember { (appGraph as VehicleToolsDependencies).toolsTyres }
    val scope = rememberCoroutineScope()
    var hours by remember { mutableStateOf(24) }
    var until by remember { mutableStateOf(now()) }
    var readings by remember { mutableStateOf(emptyList<Tyre.Located>()) }
    var selected by remember { mutableStateOf<ReadingSession?>(null) }
    var message by remember { mutableStateOf("Loading…") }
    var exportRange by remember { mutableStateOf(0.0 to 0.0) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    checkNotNull(context.contentResolver.openOutputStream(uri)).bufferedWriter().use { writer ->
                        database.exportHistory(vehicle, exportRange.first, exportRange.second, writer)
                    }
                }
                message = "CSV exported. Values are raw kPa and °C, with UTC timestamps."
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { message = "Could not write the CSV. Choose another destination." }
        }
    }
    LaunchedEffect(hours, until) {
        selected = null
        try {
            readings = database.history(vehicle.uuid, until - hours * 3_600.0, until)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { readings = emptyList(); message = "Could not load history."; return@LaunchedEffect }
        message = if (readings.size > TyreDatabase.HISTORY_LIMIT)
            "Graph limited to the first 20,000 readings. Choose a shorter window. " +
                "CSV includes the full selected range."
        else "${readings.size} raw readings. Sessions are inferred from gaps longer than 15 minutes."
    }
    val window = selected
    val shown = readings.take(TyreDatabase.HISTORY_LIMIT)
        .filter { window == null || it.timestamp in window.start..window.end }
    Dialog(onDismissRequest = dismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(shape = MaterialTheme.shapes.large, modifier = Modifier.padding(12.dp)) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
                Text("${vehicle.name} · History", style = MaterialTheme.typography.titleLarge)
                FlowRow { listOf(1, 6, 24, 168).forEach { value ->
                    TextButton(onClick = { hours = value; until = now() }) {
                        Text(if (value == 168) "7 days" else "${value}h")
                    }
                } }
                FlowRow {
                    TextButton(onClick = { until -= hours * 3_600.0 }) { Text("Earlier window") }
                    TextButton(onClick = { until = (until + hours * 3_600.0).coerceAtMost(now()) }) {
                        Text("Later window")
                    }
                    TextButton(onClick = { until = now() }) { Text("Latest") }
                }
                Text(message)
                if (shown.isNotEmpty()) {
                    Text("${historyTime(shown.first().timestamp)} — ${historyTime(shown.last().timestamp)}")
                    HistoryPlot(shown, pressure = true)
                    HistoryPlot(shown, pressure = false)
                    shown.map { it.sensorId }.distinct().forEachIndexed { index, sensor ->
                        Text("● Sensor ${sensor.toUInt()}", color = HISTORY_COLORS[index % HISTORY_COLORS.size])
                    }
                } else Text("No readings recorded in this window. Monitoring will add readings when sensors broadcast.")
                TextButton(onClick = {
                    exportRange = (window?.start ?: (until - hours * 3_600.0)) to (window?.end ?: until)
                    export.launch("TyrePing-history.csv")
                }) { Text("Export selected range as CSV") }
                TextButton(onClick = { selected = null }) { Text("All sessions in window") }
                readingSessions(readings.take(TyreDatabase.HISTORY_LIMIT)).asReversed().forEach { session ->
                    TextButton(onClick = { selected = session }) {
                        Text("${historyTime(session.start)} · ${session.count} readings")
                    }
                }
                TextButton(onClick = dismiss) { Text("Close") }
            }
        }
    }
}

@Composable
internal fun HistoryPlot(readings: List<Tyre.Located>, pressure: Boolean) {
    fun value(reading: Tyre.Located): Float = if (pressure) reading.pressure.kpa else reading.temperature.celsius
    val low = readings.minOf(::value)
    val high = readings.maxOf(::value)
    val range = (high - low).coerceAtLeast(1f) * 1.1f
    val bottom = (high + low - range) / 2
    val start = readings.first().timestamp
    val duration = (readings.last().timestamp - start).coerceAtLeast(1.0)
    var selected by remember(readings) { mutableStateOf<Tyre.Located?>(null) }
    Text("${if (pressure) "Pressure (raw kPa)" else "Temperature (raw °C)"}: ${low.toInt()}–${high.toInt()}")
    Canvas(Modifier.fillMaxWidth().height(150.dp).pointerInput(readings) {
        detectTapGestures { point ->
            selected = readings.minByOrNull {
                val dx = ((it.timestamp - start) / duration * size.width).toFloat() - point.x
                val dy = size.height - (value(it) - bottom) / range * size.height - point.y
                dx * dx + dy * dy
            }
        }
    }) {
        repeat(3) { index ->
            val y = size.height * index / 2
            drawLine(Color.Gray.copy(alpha = .25f), Offset(0f, y), Offset(size.width, y))
        }
        readings.groupBy { it.sensorId }.values.forEachIndexed { index, points ->
            val color = HISTORY_COLORS[index % HISTORY_COLORS.size]
            fun position(reading: Tyre.Located) = Offset(
                ((reading.timestamp - start) / duration * size.width).toFloat(),
                size.height - (value(reading) - bottom) / range * size.height)
            // Keep min/max in each horizontal bucket so short pressure drops remain visible.
            val sampled = points.groupBy { ((it.timestamp - start) / duration * size.width / 2).toInt() }.values
                .flatMap {
                    listOf(it.first(), it.minBy(::value), it.maxBy(::value), it.last())
                        .distinct().sortedBy { p -> p.timestamp }
                }
            sampled.zipWithNext().forEach { (a, b) ->
                if (b.timestamp - a.timestamp <= 900) drawLine(color, position(a), position(b), strokeWidth = 2f)
            }
            if (sampled.size == 1) drawCircle(color, 4f, position(sampled.first()))
        }
    }
    selected?.let { Text("${historyTime(it.timestamp)} · ${it.sensorId.toUInt()}: ${value(it)}") }
}

private fun historyTime(seconds: Double): String =
    DateFormat.getDateTimeInstance().format(Date(java.time.Duration.ofSeconds(seconds.toLong()).toMillis()))
@Suppress("MagicNumber") // Chart palette ARGB values.
private val HISTORY_COLORS = listOf(
    Color(0xFF1976D2), Color(0xFFE65100), Color(0xFF2E7D32), Color(0xFF9C27B0), Color(0xFF00838F)
)

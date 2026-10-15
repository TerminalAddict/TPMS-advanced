package com.masselis.tpmsadvanced.data.vehicle.interfaces

import com.masselis.tpmsadvanced.core.common.appContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.io.Writer
import java.util.concurrent.atomic.AtomicInteger

/** Opt-in, bounded capture. The BLE callback never waits for disk I/O. */
@Suppress("TooGenericExceptionCaught") // Disk failures disable optional capture, never the sensor scanner.
public object BleDiagnostics {
    public val enabled: MutableStateFlow<Boolean> = MutableStateFlow(false)
    private val mutableStatus = MutableStateFlow("Capture is off")
    public val status: StateFlow<String> = mutableStatus
    private val mutableLatest = MutableStateFlow("")
    public val latest: StateFlow<String> = mutableLatest
    private val generation = AtomicInteger()
    private val dropped = AtomicInteger()
    private const val QUEUE_CAPACITY = 256
    private val queue = Channel<Task>(QUEUE_CAPACITY)
    private val file: File get() = File(appContext.filesDir, "ble-capture.jsonl")
    private val previous: File get() = File(appContext.filesDir, "ble-capture-previous.jsonl")

    init {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            for (task in queue) {
                try {
                    when (task) {
                        is Task.Packet -> if (task.generation == generation.get()) {
                            if (file.length() >= MAX_BYTES) {
                                previous.delete()
                                check(file.renameTo(previous)) { "Could not rotate capture" }
                            }
                            file.appendText(task.json + "\n")
                            mutableLatest.value = task.json
                            mutableStatus.value =
                                "Capture saved · ${file.length() + previous.length()} bytes · ${dropped.get()} dropped"
                        }
                        is Task.Export -> {
                            listOf(previous, file).filter { it.exists() }.forEach { source ->
                                source.bufferedReader().use { it.copyTo(task.writer) }
                            }
                            task.done.complete(Unit)
                        }
                        is Task.Clear -> {
                            check(!file.exists() || file.delete()) { "Could not clear capture" }
                            check(!previous.exists() || previous.delete()) { "Could not clear previous capture" }
                            mutableLatest.value = ""
                            task.done.complete(Unit)
                        }
                    }
                } catch (failure: Exception) {
                    enabled.value = false
                    mutableStatus.value = "Capture stopped: ${failure.message}"
                    when (task) {
                        is Task.Export -> task.done.completeExceptionally(failure)
                        is Task.Clear -> task.done.completeExceptionally(failure)
                        is Task.Packet -> Unit
                    }
                }
            }
        }
    }

    public fun record(bytes: ByteArray?, rssi: Int, address: String, decoder: String?, sensorId: Int?, flags: String?) {
        if (!enabled.value || bytes == null) return
        val packet = JSONObject().put("timestampUtc", java.time.Instant.now().toString())
            .put("address", address).put("rssi", rssi).put("decoder", decoder ?: "unrecognised")
            .put("sensorId", sensorId?.toUInt()?.toString() ?: JSONObject.NULL)
            .put("status", flags ?: "Not decoded").put("rawHex", bytes.toHexString()).toString()
        if (queue.trySend(Task.Packet(generation.get(), packet)).isFailure) dropped.incrementAndGet()
    }

    public suspend fun export(writer: Writer): Unit = withContext(NonCancellable) {
        val done = CompletableDeferred<Unit>()
        queue.send(Task.Export(writer, done)); done.await()
    }

    public suspend fun clear() {
        enabled.value = false
        generation.incrementAndGet()
        val done = CompletableDeferred<Unit>()
        queue.send(Task.Clear(done)); done.await()
        dropped.set(0)
        mutableStatus.value = "Saved capture cleared"
    }

    private sealed interface Task {
        data class Packet(val generation: Int, val json: String) : Task
        data class Export(val writer: Writer, val done: CompletableDeferred<Unit>) : Task
        data class Clear(val done: CompletableDeferred<Unit>) : Task
    }
    private const val MAX_BYTES = 2_000_000L
}

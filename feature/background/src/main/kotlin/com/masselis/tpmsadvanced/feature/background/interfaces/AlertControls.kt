package com.masselis.tpmsadvanced.feature.background.interfaces

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.core.content.edit
import com.masselis.tpmsadvanced.core.common.appContext
import com.masselis.tpmsadvanced.core.common.observableStateFlow
import kotlinx.coroutines.flow.MutableStateFlow

@Suppress("MagicNumber") // Persisted interval defaults and allowed UI ranges.
internal object AlertControls {
    private val preferences by lazy { appContext.getSharedPreferences("ALERTS", Context.MODE_PRIVATE) }
    val options by lazy {
        observableStateFlow(AlertOptions(
            runCatching { AlertSound.valueOf(preferences.getString("sound", "NONE")!!) }.getOrDefault(AlertSound.NONE),
            preferences.getBoolean("vibrate", false), preferences.getInt("repeat", 120).coerceIn(30, 600),
            preferences.getInt("critical", 20).coerceIn(10, 120)
        )) { _, next ->
            preferences.edit {
                putString("sound", next.sound.name); putBoolean("vibrate", next.vibrate)
                putInt("repeat", next.repeatSeconds); putInt("critical", next.criticalSeconds)
            }
        }
    }
    val silencedUntil = MutableStateFlow(0L)
    val active = MutableStateFlow(false)
    val fleetActive = MutableStateFlow(false)
    fun silence() { silencedUntil.value = SystemClock.elapsedRealtime() + SILENCE_MS }
    private const val SILENCE_MS = 600_000L
}

internal class SilenceAlertsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) { AlertControls.silence() }
}

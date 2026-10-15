package com.masselis.tpmsadvanced.feature.background.interfaces

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.VibrationEffect
import android.os.Vibrator
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.core.content.getSystemService
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import co.touchlab.kermit.Logger
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale

@Suppress("MagicNumber", "DEPRECATION")
internal class AlertPlayer(private val context: Context) {
    private val vibrator = context.getSystemService<Vibrator>()
    private val audio = context.getSystemService<AudioManager>()
    private val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(attributes).setOnAudioFocusChangeListener { if (it < 0) stop() }.build()
    private var speech: TextToSpeech? = null
    private var tone: ToneGenerator? = null
    private var initialized: CompletableDeferred<Boolean>? = null

    @Suppress("TooGenericExceptionCaught") // Optional platform audio must not terminate monitoring.
    suspend fun play(cues: List<AlertCue>, options: AlertOptions) {
        val critical = cues.any { it.critical }
        try {
            if (options.vibrate) vibrator?.vibrate(VibrationEffect.createWaveform(
                if (critical) longArrayOf(0, 250, 100, 250, 100, 250, 250, 500)
                else longArrayOf(0, 150, 250, 150, 250, 150), -1), attributes)
            if (options.sound != AlertSound.NONE &&
                audio?.requestAudioFocus(focus) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                if (options.sound != AlertSound.SPEECH || !speak(cues.joinToString(". ") { it.phrase })) {
                    tone = ToneGenerator(AudioManager.STREAM_ALARM, 80)
                    repeat(if (critical) 5 else 3) {
                        tone?.startTone(ToneGenerator.TONE_PROP_BEEP, if (critical) 250 else 150)
                        delay(if (critical) 350 else 400)
                    }
                }
            }
            delay(1_800) // Allow vibration-only patterns to finish before cleanup.
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: RuntimeException) {
            Logger.withTag("AlertPlayer").w("Android could not play an alert", failure)
        } finally {
            stop()
            audio?.abandonAudioFocusRequest(focus)
        }
    }

    @Suppress("ReturnCount") // Early exits handle missing engines and languages.
    private suspend fun speak(message: String): Boolean {
        if (speech == null) {
            val ready = CompletableDeferred<Boolean>()
            initialized = ready
            speech = TextToSpeech(context) { ready.complete(it == TextToSpeech.SUCCESS) }
        }
        if (withTimeoutOrNull(3_000) { initialized?.await() } != true) return false
        val engine = checkNotNull(speech)
        if (engine.setLanguage(Locale.ENGLISH) < TextToSpeech.LANG_AVAILABLE) return false
        engine.setAudioAttributes(attributes)
        val done = CompletableDeferred<Boolean>()
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) { done.complete(true) }
            override fun onError(utteranceId: String?) { done.complete(false) }
        })
        if (engine.speak(message, TextToSpeech.QUEUE_FLUSH, null, "tyre-alert") == TextToSpeech.ERROR) return false
        val succeeded = withTimeoutOrNull(20_000) { done.await() } == true
        if (!succeeded) engine.stop()
        return succeeded
    }

    private fun stop() { speech?.stop(); tone?.release(); tone = null; vibrator?.cancel() }
    fun close() { stop(); speech?.shutdown() }
}



package iad1tya.echo.music.recognition

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import androidx.core.content.ContextCompat
import com.music.shazamkit.Shazam
import com.music.shazamkit.models.RecognitionStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.nio.ByteOrder


object MusicRecognitionService {
    
    
    private const val RECORDING_SAMPLE_RATE = 44100
    private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
    private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT


    // First pass: the proven ~10s window. Re-capture pass (only on a clean NoMatch): a longer ~12s
    // window catches what a short/noisy first grab missed. Bounded to ONE extra pass (battery/heat rule).
    private const val RECORDING_DURATION_FIRST_MS = 10000L
    private const val RECORDING_DURATION_RETRY_MS = 12000L
    
    private val _recognitionStatus = MutableStateFlow<RecognitionStatus>(RecognitionStatus.Ready)
    val recognitionStatus: StateFlow<RecognitionStatus> = _recognitionStatus.asStateFlow()

    // Prevents two concurrent mic sessions (e.g. the in-app Recognition screen + the widget service at once)
    // fighting over the microphone and this shared status flow.
    private val inProgress = java.util.concurrent.atomic.AtomicBoolean(false)

    // Job of the session currently holding the mic, so Cancel (or a retry) can actually abort it
    // instead of leaving a zombie ~10s recording that blocks every new attempt.
    @Volatile
    private var activeSessionJob: Job? = null

    // How long a new attempt waits for a cancelled old session to release the mic before giving up.
    private const val TAKEOVER_TIMEOUT_MS = 3_000L

    fun hasRecordPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    /** True while a session is recording/processing — lets the UI avoid clobbering a live session. */
    fun isInProgress(): Boolean = inProgress.get()

    /**
     * Actually cancels the in-flight recognition session (if any) and returns the status to Ready.
     * Unlike reset(), which only changes the UI state, this releases the microphone so an immediate
     * retry works instead of being swallowed by the in-progress guard.
     *
     * Pass a [context] to also stop [RecognitionForegroundService]: cancelling only the session
     * would leave the mic FGS and its ongoing "Listening…" notification orphaned (the FGS also
     * self-terminates when it observes Ready, but every cancel path should kill it directly too).
     */
    fun cancel(context: Context? = null) {
        activeSessionJob?.cancel()
        _recognitionStatus.value = RecognitionStatus.Ready
        context?.stopService(Intent(context, RecognitionForegroundService::class.java))
    }

    @SuppressLint("MissingPermission")
    suspend fun recognize(context: Context): RecognitionStatus = withContext(Dispatchers.IO) {
        if (!hasRecordPermission(context)) {
            return@withContext RecognitionStatus.Error("Microphone permission not granted")
        }
        if (!inProgress.compareAndSet(false, true)) {
            // A previous session still holds the mic (e.g. a zombie recording after a Cancel that only
            // reset the UI). Don't silently swallow this attempt: cancel the old session and take over.
            activeSessionJob?.cancel()
            val takeoverDeadline = System.currentTimeMillis() + TAKEOVER_TIMEOUT_MS
            while (!inProgress.compareAndSet(false, true)) {
                if (System.currentTimeMillis() >= takeoverDeadline) {
                    return@withContext RecognitionStatus.Error("Recognition is busy. Please try again.")
                }
                delay(50)
            }
        }
        activeSessionJob = coroutineContext[Job]

        _recognitionStatus.value = RecognitionStatus.Listening

        try {
            // Attempt 1: proven ~10s window.
            var result = runRecognitionPass(context, RECORDING_DURATION_FIRST_MS)

            // Re-capture ONCE on a clean NoMatch: record FRESH audio (never resend the same signature)
            // with a longer window and try again. Bounded to a single extra pass — battery/heat rule.
            // Network/service errors are NOT re-captured here: the Shazam layer already retries those
            // (and the provider cascade already tried the relay), so re-recording wouldn't help.
            ensureActive()
            if (result is RecognitionStatus.NoMatch) {
                _recognitionStatus.value = RecognitionStatus.Listening
                result = runRecognitionPass(context, RECORDING_DURATION_RETRY_MS)
            }

            _recognitionStatus.value = result
            result
        } catch (e: CancellationException) {
            // Cancelled by the user (or a new attempt taking over) — cancel() already set the status
            // to Ready; don't overwrite it with a scary error. Rethrow to finish cancellation cleanly.
            throw e
        } catch (e: Exception) {
            // Surface the exception type so a systematic failure (e.g. a network/TLS error) is diagnosable.
            _recognitionStatus.value = RecognitionStatus.Error("${e.javaClass.simpleName}: ${e.message ?: "Recognition failed"}")
            _recognitionStatus.value
        } finally {
            inProgress.set(false)
        }
    }
    
    /**
     * One recognition pass: record [durationMs] of fresh audio → resample → signature → provider
     * cascade. Returns a terminal [RecognitionStatus] (Success / NoMatch / Error). Publishes the
     * intermediate Processing state itself; the caller owns the surrounding Listening state and the
     * final publish. Kept separate so the NoMatch re-capture can run it a second time cheaply.
     */
    private suspend fun runRecognitionPass(context: Context, durationMs: Long): RecognitionStatus {
        val audioData = recordAudio(context, durationMs)

        // If we were cancelled mid-recording, recordAudio() returns partial data without throwing
        // (its loop just exits on !isActive) — bail out before publishing bogus "Processing" state.
        currentCoroutineContext().ensureActive()

        _recognitionStatus.value = RecognitionStatus.Processing

        val decodedAudio = DecodedAudio(
            data = audioData,
            channelCount = 1,
            sampleRate = RECORDING_SAMPLE_RATE,
            pcmEncoding = AUDIO_FORMAT
        )

        val resampledAudio = AudioResampler.resample(
            decodedAudio,
            VibraSignature.REQUIRED_SAMPLE_RATE
        ).getOrElse { error ->
            return RecognitionStatus.Error("Failed to resample audio: ${error.message}")
        }

        require(
            resampledAudio.channelCount == 1 &&
                resampledAudio.sampleRate == VibraSignature.REQUIRED_SAMPLE_RATE &&
                resampledAudio.pcmEncoding == AudioFormat.ENCODING_PCM_16BIT &&
                ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN &&
                resampledAudio.data.isNotEmpty() &&
                resampledAudio.data.size % 2 == 0
        ) { "Invalid audio format for fingerprint generation" }

        val signature = try {
            VibraSignature.fromI16(resampledAudio.data)
        } catch (e: Exception) {
            return RecognitionStatus.Error("Failed to generate fingerprint: ${e.message}")
        }

        val sampleDurationMs = (resampledAudio.data.size / 2) * 1000L / VibraSignature.REQUIRED_SAMPLE_RATE

        // Provider cascade: direct amp.shazam.com → Aura Worker relay probe (inert until deployed).
        val result = Shazam.recognizeWithFallback(signature, sampleDurationMs)

        return result.fold(
            onSuccess = { recognitionResult -> RecognitionStatus.Success(recognitionResult) },
            onFailure = { error ->
                val message = error.message ?: "Unknown error"
                if (message.contains("No match", ignoreCase = true)) {
                    RecognitionStatus.NoMatch("No se encontraron coincidencias. Prueba de nuevo con audio más claro.")
                } else {
                    RecognitionStatus.Error(message)
                }
            }
        )
    }

    @SuppressLint("MissingPermission")
    private suspend fun recordAudio(context: Context, durationMs: Long): ByteArray = withContext(Dispatchers.IO) {
        val bufferSize = AudioRecord.getMinBufferSize(
            RECORDING_SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT
        )
        // Cheap devices can return ERROR(-1)/ERROR_BAD_VALUE(-2) for this format combo; a negative size would
        // crash AudioRecord/ByteArray. Fail with a clear message instead of an obscure crash.
        if (bufferSize <= 0) {
            throw IllegalStateException("Micrófono no compatible con 44.1kHz mono 16-bit (code $bufferSize)")
        }

        // CAPTURE-SOURCE CASCADE: AudioSource.MIC applies AGC + noise suppression tuned for SPEECH, which
        // smears the musical spectrum the Shazam fingerprint relies on. Prefer the cleanest source the
        // device offers, falling back only if construction fails.
        val audioRecord = createCleanAudioRecord(context, bufferSize)

        // Belt-and-suspenders: even on VOICE_RECOGNITION/UNPROCESSED, some OEMs attach AGC/NS to the
        // capture session. Disable both if present — we want RAW music, not voice-cleaned audio. The
        // effect objects are kept alive for the whole capture (releasing them can revert the change).
        val agc = disableAutomaticGainControl(audioRecord.audioSessionId)
        val ns = disableNoiseSuppressor(audioRecord.audioSessionId)

        val outputStream = ByteArrayOutputStream()
        val buffer = ByteArray(bufferSize)
        val startTime = System.currentTimeMillis()

        try {
            audioRecord.startRecording()

            while (System.currentTimeMillis() - startTime < durationMs && isActive) {
                val bytesRead = audioRecord.read(buffer, 0, bufferSize)
                if (bytesRead > 0) {
                    outputStream.write(buffer, 0, bytesRead)
                } else if (bytesRead < 0) {
                    // AudioRecord.ERROR_* — stop instead of spinning uselessly on empty reads.
                    break
                }
            }
        } finally {
            runCatching { audioRecord.stop() }
            audioRecord.release()
            runCatching { agc?.release() }
            runCatching { ns?.release() }
        }

        outputStream.toByteArray()
    }

    /**
     * Build an AudioRecord from the cleanest available capture source, in cascade:
     *   1. UNPROCESSED — only when the device HARDWARE declares support (no DSP at all; cleanest).
     *   2. VOICE_RECOGNITION — framework guarantees no AGC/NS on this path (unlike MIC).
     *   3. MIC — always available last resort (may apply voice DSP on some OEMs).
     * Returns an INITIALIZED record, or throws if every source failed to construct/initialize.
     */
    @SuppressLint("MissingPermission")
    private fun createCleanAudioRecord(context: Context, bufferSize: Int): AudioRecord {
        val sources = buildList {
            if (deviceSupportsUnprocessed(context)) add(MediaRecorder.AudioSource.UNPROCESSED)
            add(MediaRecorder.AudioSource.VOICE_RECOGNITION)
            add(MediaRecorder.AudioSource.MIC)
        }.distinct()

        var lastError: Exception? = null
        for (source in sources) {
            try {
                val record = AudioRecord(
                    source,
                    RECORDING_SAMPLE_RATE,
                    CHANNEL_CONFIG,
                    AUDIO_FORMAT,
                    bufferSize
                )
                if (record.state == AudioRecord.STATE_INITIALIZED) return record
                record.release()
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw lastError
            ?: IllegalStateException("No se pudo inicializar el micrófono (¿lo está usando otra app?)")
    }

    /** True if the device declares hardware support for the UNPROCESSED (no-DSP) capture source. */
    private fun deviceSupportsUnprocessed(context: Context): Boolean = try {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        am?.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) == "true"
    } catch (e: Exception) {
        false
    }

    /** Best-effort: attach + disable AGC on the capture session; keep the handle alive to hold it off. */
    private fun disableAutomaticGainControl(sessionId: Int): AutomaticGainControl? = try {
        if (AutomaticGainControl.isAvailable()) {
            // AudioEffect.setEnabled() returns an int status (not void), so Kotlin exposes no writable
            // `enabled` property — call the method explicitly.
            AutomaticGainControl.create(sessionId)?.apply { setEnabled(false) }
        } else null
    } catch (e: Exception) {
        null
    }

    /** Best-effort: attach + disable noise suppression on the capture session; keep the handle alive. */
    private fun disableNoiseSuppressor(sessionId: Int): NoiseSuppressor? = try {
        if (NoiseSuppressor.isAvailable()) {
            NoiseSuppressor.create(sessionId)?.apply { setEnabled(false) }
        } else null
    } catch (e: Exception) {
        null
    }

    fun reset() {
        _recognitionStatus.value = RecognitionStatus.Ready
    }
}

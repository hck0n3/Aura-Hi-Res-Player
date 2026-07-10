

package iad1tya.echo.music.recognition

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import com.music.shazamkit.Shazam
import com.music.shazamkit.models.RecognitionStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
    
    
    
    private const val RECORDING_DURATION_MS = 10000L
    
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

            val audioData = recordAudio()

            // If we were cancelled mid-recording, recordAudio() returns partial data without throwing
            // (its loop just exits on !isActive) — bail out before publishing bogus "Processing" state.
            ensureActive()

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
                _recognitionStatus.value = RecognitionStatus.Error("Failed to resample audio: ${error.message}")
                return@withContext _recognitionStatus.value
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
                _recognitionStatus.value = RecognitionStatus.Error("Failed to generate fingerprint: ${e.message}")
                return@withContext _recognitionStatus.value
            }
            
            
            val sampleDurationMs = (resampledAudio.data.size / 2) * 1000L / VibraSignature.REQUIRED_SAMPLE_RATE
            
            val result = Shazam.recognize(signature, sampleDurationMs)
            
            result.fold(
                onSuccess = { recognitionResult ->
                    _recognitionStatus.value = RecognitionStatus.Success(recognitionResult)
                },
                onFailure = { error ->
                    val message = error.message ?: "Unknown error"
                    _recognitionStatus.value = if (message.contains("No match", ignoreCase = true)) {
                        RecognitionStatus.NoMatch("No matches found. Try again with clearer audio.")
                    } else {
                        RecognitionStatus.Error(message)
                    }
                }
            )
            
            _recognitionStatus.value
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
    
    @SuppressLint("MissingPermission")
    private suspend fun recordAudio(): ByteArray = withContext(Dispatchers.IO) {
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

        val audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            RECORDING_SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            bufferSize
        )
        if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
            audioRecord.release()
            throw IllegalStateException("No se pudo inicializar el micrófono (¿lo está usando otra app?)")
        }

        val outputStream = ByteArrayOutputStream()
        val buffer = ByteArray(bufferSize)
        val startTime = System.currentTimeMillis()

        try {
            audioRecord.startRecording()

            while (System.currentTimeMillis() - startTime < RECORDING_DURATION_MS && isActive) {
                val bytesRead = audioRecord.read(buffer, 0, bufferSize)
                if (bytesRead > 0) {
                    outputStream.write(buffer, 0, bytesRead)
                } else if (bytesRead < 0) {
                    // AudioRecord.ERROR_* — stop instead of spinning uselessly on empty reads.
                    break
                }
            }
        } finally {
            audioRecord.stop()
            audioRecord.release()
        }
        
        outputStream.toByteArray()
    }
    
    fun reset() {
        _recognitionStatus.value = RecognitionStatus.Ready
    }
}

package com.example.pathy.walkietalkie

import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.ActivityCompat
import com.google.android.gms.nearby.connection.ConnectionsClient
import com.google.android.gms.nearby.connection.Payload
import kotlinx.coroutines.*
import java.io.OutputStream

class AudioSender(
    private val context: Context,
    private val connectionsClient: ConnectionsClient,
    private val endpointId: String
) {
    @Volatile private var isSending = false

    private val sampleRate = 16_000
    private val minBuf = AudioRecord.getMinBufferSize(
        sampleRate,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT
    )
    private val readBufferSize = (minBuf * 2).coerceAtLeast(2048)
    private lateinit var recorder: AudioRecord
    // 20ms @ 16kHz mono PCM16 => 320 samples => 640 bytes
    private val readChunkBytes = 640
    private var aec: AcousticEchoCanceler? = null
    private var agc: AutomaticGainControl? = null
    private var ns: NoiseSuppressor? = null

    private var streamOut: OutputStream? = null
    private var sendJob: Job? = null

    // ===== VAD / Envelope / Limiter params =====
    // RMS threshold for VAD (on 20ms frames). Tuned for outdoor; adjustable if needed.
    private val vadRmsThreshold = 500.0 // ~-42 dBFS approx for PCM16
    private val vadHangoverFrames = 5    // keep sending ~100ms after falling below threshold
    @Volatile private var vadHangCount = 0

    // Short attack/release envelope to avoid clicks when starting/stopping speech bursts
    private val envelopeRampSamples = 64 // ~4ms ramp at 16kHz
    private var envelopeState: EnvelopeState = EnvelopeState.Idle
    private var envelopeSamplePos = 0

    // Simple peak limiter to reduce harsh clipping outdoors
    private val limiterThreshold = 0.85f // as a fraction of 32767
    private val limiterThresholdShort = (limiterThreshold * 32767f).toInt().coerceIn(1, 32767)

    private enum class EnvelopeState { Idle, Attack, Sustain, Release }

    fun startSending() {
        if (ActivityCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e("AudioSender", "Missing RECORD_AUDIO permission")
            return
        }
        if (isSending) return
        isSending = true

        recorder = try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                readBufferSize
            )
        } catch (t: Throwable) {
            Log.w("AudioSender", "VOICE_COMMUNICATION failed, fallback to MIC: ${t.message}")
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                readBufferSize
            )
        }

        try {
            val sessionId = recorder.audioSessionId
            if (AcousticEchoCanceler.isAvailable()) {
                aec = AcousticEchoCanceler.create(sessionId)?.apply { enabled = true }
            }
            if (AutomaticGainControl.isAvailable()) {
                agc = AutomaticGainControl.create(sessionId)?.apply { enabled = true }
            }
            if (NoiseSuppressor.isAvailable()) {
                ns = NoiseSuppressor.create(sessionId)?.apply { enabled = true }
            }
        } catch (t: Throwable) {
            Log.w("AudioSender", "Audio effects init failed: ${t.message}")
        }

        val pipe = ParcelFileDescriptor.createPipe()
        val readSide = pipe[0]
        val writeSide = pipe[1]

        try {
            val payload = Payload.fromStream(readSide)
            connectionsClient.sendPayload(endpointId, payload)
        } catch (t: Throwable) {
            Log.e("AudioSender", "sendPayload(stream) failed: ${t.message}", t)
            closeQuietly(readSide)
            closeQuietly(writeSide)
            stopSending()
            return
        }

        streamOut = ParcelFileDescriptor.AutoCloseOutputStream(writeSide)

        try {
            recorder.startRecording()
        } catch (t: Throwable) {
            Log.e("AudioSender", "startRecording failed: ${t.message}", t)
            stopSending()
            return
        }

        // Reset state for VAD/envelope
        vadHangCount = 0
        envelopeState = EnvelopeState.Attack
        envelopeSamplePos = 0

        sendJob = CoroutineScope(Dispatchers.IO).launch {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO)
            val chunk = ByteArray(readChunkBytes)
            try {
                while (isActive && isSending) {
                    val read = recorder.read(chunk, 0, chunk.size, AudioRecord.READ_BLOCKING)
                    if (read > 0) {
                        // Convert to PCM16 samples
                        val samples = read / 2
                        // Compute RMS for VAD
                        var sumSq = 0.0
                        var i = 0
                        while (i < read) {
                            val lo = chunk[i].toInt() and 0xFF
                            val hi = chunk[i + 1].toInt()
                            val s = (hi shl 8) or lo
                            sumSq += (s.toShort().toInt() * s.toShort().toInt()).toDouble()
                            i += 2
                        }
                        val rms = kotlin.math.sqrt(sumSq / samples.coerceAtLeast(1))

                        val voiceDetected = rms >= vadRmsThreshold
                        if (voiceDetected) {
                            vadHangCount = vadHangoverFrames
                            if (envelopeState == EnvelopeState.Idle || envelopeState == EnvelopeState.Release) {
                                envelopeState = EnvelopeState.Attack
                                envelopeSamplePos = 0
                            }
                        } else {
                            if (vadHangCount > 0) vadHangCount--
                            if (vadHangCount == 0 && envelopeState != EnvelopeState.Idle) {
                                envelopeState = EnvelopeState.Release
                                envelopeSamplePos = 0
                            }
                        }

                        // Apply envelope and limiter into a temp buffer when needed
                        val outBuf: ByteArray?
                        if (voiceDetected || vadHangCount > 0 || envelopeState != EnvelopeState.Idle) {
                            outBuf = if (envelopeState == EnvelopeState.Sustain && rms < vadRmsThreshold * 0.6 && vadHangCount == 0) {
                                // quiet tail; still may need release
                                chunk.copyOf(read)
                            } else {
                                // Apply envelope and limiter
                                val processed = ByteArray(read)
                                var j = 0
                                var sampleIdx = 0
                                while (j < read) {
                                    val lo = chunk[j].toInt() and 0xFF
                                    val hi = chunk[j + 1].toInt()
                                    var s = ((hi shl 8) or lo).toShort().toInt()

                                    // Envelope gain
                                    val gain = when (envelopeState) {
                                        EnvelopeState.Attack -> {
                                            val g = (envelopeSamplePos.toFloat() / envelopeRampSamples).coerceIn(0f, 1f)
                                            envelopeSamplePos++
                                            if (envelopeSamplePos >= envelopeRampSamples) {
                                                envelopeState = EnvelopeState.Sustain
                                                envelopeSamplePos = 0
                                            }
                                            g
                                        }
                                        EnvelopeState.Sustain -> 1f
                                        EnvelopeState.Release -> {
                                            val g = 1f - (envelopeSamplePos.toFloat() / envelopeRampSamples).coerceIn(0f, 1f)
                                            envelopeSamplePos++
                                            if (envelopeSamplePos >= envelopeRampSamples) {
                                                envelopeState = EnvelopeState.Idle
                                                envelopeSamplePos = 0
                                            }
                                            g
                                        }
                                        else -> 0f
                                    }

                                    var fs = (s * gain).toInt()

                                    // Limiter: soft clip scaling when exceeding threshold
                                    val abs = kotlin.math.abs(fs)
                                    if (abs > limiterThresholdShort) {
                                        val sign = if (fs >= 0) 1 else -1
                                        // simple soft knee: scale above threshold
                                        val over = abs - limiterThresholdShort
                                        val headroom = 32767 - limiterThresholdShort
                                        val scale = 1f - (over.toFloat() / headroom.toFloat()).coerceIn(0f, 0.9f)
                                        fs = (limiterThresholdShort + (over * scale)).toInt() * sign
                                    }

                                    processed[j] = (fs and 0xFF).toByte()
                                    processed[j + 1] = ((fs ushr 8) and 0xFF).toByte()
                                    j += 2
                                    sampleIdx++
                                }
                                processed
                            }
                        } else {
                            outBuf = null // no transmission during clear silence
                        }

                        if (outBuf != null) {
                            streamOut?.write(outBuf, 0, outBuf.size)
                        }
                    } else if (read < 0) {
                        Log.w("AudioSender", "Audio read error: $read")
                        break
                    }
                }
            } catch (t: Throwable) {
                Log.w("AudioSender", "stream write loop ended: ${t.message}")
            } finally {
                runCatching { streamOut?.flush() }
                closeOutput()
            }
        }
    }

    fun stopSending() {
        isSending = false
        try { sendJob?.cancel() } catch (_: Throwable) {}
        sendJob = null

        try {
            // Short release envelope before stopping to avoid click (best-effort)
            // Note: stream may already be closed; ignore errors.
            recorder.stop()
        } catch (_: Throwable) {}
        try {
            recorder.release()
        } catch (_: Throwable) {}

        try { aec?.release() } catch (_: Throwable) {}
        try { agc?.release() } catch (_: Throwable) {}
        try { ns?.release() } catch (_: Throwable) {}
        aec = null; agc = null; ns = null

        closeOutput()
    }

    private fun closeOutput() {
        try { streamOut?.close() } catch (_: Throwable) {}
        streamOut = null
    }

    private fun closeQuietly(pfd: ParcelFileDescriptor?) {
        try { pfd?.close() } catch (_: Throwable) {}
    }
}

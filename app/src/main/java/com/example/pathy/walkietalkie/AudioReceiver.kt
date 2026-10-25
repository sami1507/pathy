package com.example.pathy.walkietalkie

import android.content.Context
import android.media.*
import android.os.Build
import androidx.annotation.RequiresApi
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import java.io.BufferedInputStream
import java.io.InputStream
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max
import kotlin.random.Random

@RequiresApi(Build.VERSION_CODES.O)
class AudioReceiver(private val context: Context) : PayloadCallback() {

    var onControlMessage: ((type: String, name: String?) -> Unit)? = null

    // ===== פרמטרים =====
    private val sampleRate = 16_000
    private val frameMs = 20
    private val bytesPerSample = 2 // PCM16
    private val frameBytes = (sampleRate * bytesPerSample * frameMs) / 1000 // 640B

    private val targetQueueFrames = 3          // ~60ms יעד
    private val maxQueueFramesBeforeDrop = 6   // ~120ms סף "חתוך עודפים"
    private var requirePrebufferOnNextSpeaker = false
    private var comfortNoiseEnabled = false    // off by default

    // ===== AudioTrack =====
    private val minBuffer = AudioTrack.getMinBufferSize(
        sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
    )
    private val trackBuffer = (minBuffer * 2).coerceAtLeast(8 * 1024)
    private var audioTrack: AudioTrack? = null

    // ===== תורים/שרשורים =====
    private val netExecutor = Executors.newSingleThreadExecutor()
    private val playExecutor = Executors.newSingleThreadExecutor()
    private val running = AtomicBoolean(false)
    private val frameQueue = ArrayBlockingQueue<ByteArray>(64) // ~1.2s מקסימום
    @Volatile private var playLoopStarted = false

    init {
        routeOutputPreferHeadsetOrSpeaker()

        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(sampleRate)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .build()

        val builder = AudioTrack.Builder()
            .setAudioAttributes(attrs)
            .setAudioFormat(format)
            .setBufferSizeInBytes(trackBuffer)
            .setTransferMode(AudioTrack.MODE_STREAM)
        runCatching { builder.setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY) }

        audioTrack = builder.build()
        setTrackPreferredOutputDevice()
    }

    private fun routeOutputPreferHeadsetOrSpeaker() {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val outputs = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val preferred = outputs.firstOrNull { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO }
            ?: outputs.firstOrNull { it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES || it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET }
            ?: outputs.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
        if (Build.VERSION.SDK_INT >= 31) {
            preferred?.let { am.setCommunicationDevice(it) }
        } else {
            @Suppress("DEPRECATION") am.mode = AudioManager.MODE_IN_COMMUNICATION
            @Suppress("DEPRECATION") am.isSpeakerphoneOn = preferred?.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
            if (preferred?.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO) {
                @Suppress("DEPRECATION") am.startBluetoothSco()
                @Suppress("DEPRECATION") am.isBluetoothScoOn = true
            } else {
                @Suppress("DEPRECATION") am.stopBluetoothSco()
                @Suppress("DEPRECATION") am.isBluetoothScoOn = false
            }
        }
    }
    private fun setTrackPreferredOutputDevice() {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val outputs = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val preferred = outputs.firstOrNull { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO }
            ?: outputs.firstOrNull { it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES || it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET }
            ?: outputs.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
        preferred?.let { runCatching { audioTrack?.preferredDevice = it } }
    }

    // ==== RX ====
    override fun onPayloadReceived(endpointId: String, payload: Payload) {
        when (payload.type) {
            Payload.Type.BYTES  -> handleBytes(payload.asBytes())
            Payload.Type.STREAM -> handleStream(payload.asStream()?.asInputStream())
            else -> Unit
        }
    }

    private fun handleBytes(bytes: ByteArray?) {
        if (bytes == null) return
        if (bytes.size >= 5 &&
            bytes[0]=='C'.code.toByte() && bytes[1]=='T'.code.toByte() &&
            bytes[2]=='R'.code.toByte() && bytes[3]=='L'.code.toByte() && bytes[4]=='|'.code.toByte()
        ) {
            val text = bytes.toString(Charsets.UTF_8)
            val parts = text.split('|', limit = 3)
            val type = parts.getOrNull(1)
            val name = parts.getOrNull(2)
            // תגובה מיידית להודעות בקרה
            when (type) {
                "SPEAKER_ON"  -> { requirePrebufferOnNextSpeaker = true; flushQueueAndRestart() }  // מתחילים נקי + דרישת פרה-באפר
                "SPEAKER_OFF" -> flushQueueAndStop()     // חותכים "זנב" → אין דיליי אחרי כיבוי מיקרופון
            }
            if (!type.isNullOrBlank()) onControlMessage?.invoke(type, name?.ifBlank { null })
            return
        }
        // תאימות: אם יגיעו PCM בבייטים – חותכים לפריימים ואל התור
        feedPcmToQueue(bytes)
        ensurePlaybackLoop()
    }

    private fun handleStream(input: InputStream?) {
        if (input == null) return
        netExecutor.execute {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO)
            running.set(true)
            val bis = BufferedInputStream(input, 4 * 1024)
            var carry = ByteArray(0)
            val ioBuf = ByteArray(4 * 1024)
            try {
                while (running.get()) {
                    val read = bis.read(ioBuf)
                    if (read <= 0) break

                    // מרכיבים carry + נתונים לבלוק אחד וחותכים לפריימים קבועים
                    var chunk = if (carry.isNotEmpty()) {
                        val all = ByteArray(carry.size + read)
                        System.arraycopy(carry, 0, all, 0, carry.size)
                        System.arraycopy(ioBuf, 0, all, carry.size, read)
                        carry = ByteArray(0)
                        all
                    } else {
                        ioBuf.copyOf(read)
                    }

                    var off = 0
                    while (chunk.size - off >= frameBytes) {
                        val frame = ByteArray(frameBytes)
                        System.arraycopy(chunk, off, frame, 0, frameBytes)
                        off += frameBytes
                        offerFrame(frame)
                    }
                    val rem = chunk.size - off
                    if (rem > 0) {
                        carry = ByteArray(rem)
                        System.arraycopy(chunk, off, carry, 0, rem)
                    }

                    ensurePlaybackLoop()
                }
            } finally {
                running.set(false)
                runCatching { bis.close() }
            }
        }
    }

    private fun feedPcmToQueue(bytes: ByteArray) {
        var off = 0
        while (bytes.size - off >= frameBytes) {
            val frame = ByteArray(frameBytes)
            System.arraycopy(bytes, off, frame, 0, frameBytes)
            off += frameBytes
            offerFrame(frame)
        }
    }

    private fun offerFrame(frame: ByteArray) {
        // בקרת לטנציה: אם התור חורג מ-~120ms, נחתוך עודפים עד 3 פריימים (≈60ms)
        if (frameQueue.size >= maxQueueFramesBeforeDrop) {
            while (frameQueue.size > targetQueueFrames) frameQueue.poll()
        }
        if (!frameQueue.offer(frame)) {
            // אם מלא – זורקים הכי ישן ושמים חדש
            frameQueue.poll()
            frameQueue.offer(frame)
        }
    }

    @Synchronized private fun ensurePlaybackLoop() {
        if (playLoopStarted) return
        playLoopStarted = true
        playExecutor.execute {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO)
            val track = audioTrack ?: return@execute
            val frameNanos = frameMs * 1_000_000L
            var nextTs = System.nanoTime()
            var started = false
            val silence = ByteArray(frameBytes)
            try {
                while (running.get() || frameQueue.isNotEmpty()) {
                    if (!started) {
                        val needPrebuffer = requirePrebufferOnNextSpeaker
                        val ready = frameQueue.size >= targetQueueFrames
                        if (!needPrebuffer || ready) {
                            runCatching { track.play() }
                            started = true
                            nextTs = System.nanoTime()
                            requirePrebufferOnNextSpeaker = false
                        } else {
                            Thread.sleep(2)
                            continue
                        }
                    }
                    var frame = frameQueue.poll()
                    if (frame == null) {
                        frame = if (comfortNoiseEnabled) genComfortNoise(frameBytes) else silence
                    }
                    track.write(frame, 0, frame.size, AudioTrack.WRITE_BLOCKING)

                    // סידור קצב 20ms
                    nextTs += frameNanos
                    val now = System.nanoTime()
                    val sleepNs = nextTs - now
                    if (sleepNs > 0) {
                        try { Thread.sleep(sleepNs / 1_000_000L, (sleepNs % 1_000_000L).toInt()) } catch (_: Throwable) {}
                    } else {
                        // אם פיגרנו – מדלגים על השהיות כדי להדביק קצב
                        nextTs = now
                    }
                }
            } finally {
                runCatching { track.stop() }
                playLoopStarted = false
            }
        }
    }

    private fun flushQueueAndRestart() {
        frameQueue.clear()
        audioTrack?.pause()
        audioTrack?.flush()
        // לא מפעילים מיד; הלולאה תתחיל כשיהיו targetQueueFrames
    }
    private fun flushQueueAndStop() {
        frameQueue.clear()
        audioTrack?.pause()
        audioTrack?.flush()
        // לא מפעילים מחדש עד שיגיעו פריימים חדשים/CTRL הבא
    }

    // ===== Comfort noise (optional) =====
    private fun genComfortNoise(size: Int): ByteArray {
        // Very low-level white noise to avoid perceived dead air; RMS ~ -50 dBFS
        val out = ByteArray(size)
        var i = 0
        while (i < size) {
            val s = (Random.nextInt(-300, 300)).toShort()
            out[i] = (s.toInt() and 0xFF).toByte()
            out[i + 1] = ((s.toInt() ushr 8) and 0xFF).toByte()
            i += 2
        }
        return out
    }

    override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {}

    fun stop() {
        running.set(false)
        frameQueue.clear()
        runCatching { audioTrack?.stop() }
        runCatching { audioTrack?.release() }
        audioTrack = null
        runCatching { netExecutor.shutdownNow() }
        runCatching { playExecutor.shutdownNow() }
        playLoopStarted = false
    }
}

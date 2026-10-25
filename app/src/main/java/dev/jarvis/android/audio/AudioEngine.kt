package dev.jarvis.android.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.*
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.sqrt

class AudioEngine(private val context: Context) : AutoCloseable {
    interface Listener { fun onPcm(pcm: ByteArray); fun onSpeech(); fun onLevel(level: Float); fun onError(message: String) }
    private val running = AtomicBoolean(false)
    private val epoch = AtomicInteger(0)
    private data class Chunk(val bytes: ByteArray, val epoch: Int)
    private val queue = LinkedBlockingQueue<Chunk>(120)
    private val playbackLock = Any()
    @Volatile private var recorder: AudioRecord? = null
    @Volatile private var track: AudioTrack? = null
    @Volatile private var micThread: Thread? = null
    @Volatile private var playThread: Thread? = null
    @Volatile var lastPlaybackAt: Long = 0
        private set
    private val manager = context.getSystemService(AudioManager::class.java)
    private var previousMode = AudioManager.MODE_NORMAL
    private var previousSpeaker = false
    private var changedSpeaker = false
    private var focus: AudioFocusRequest? = null
    private var listener: Listener? = null
    @SuppressLint("MissingPermission")
    fun start(callbacks: Listener) {
        if (!running.compareAndSet(false, true)) return
        listener = callbacks
        previousMode = manager.mode
        previousSpeaker = manager.isSpeakerphoneOn
        val attrs = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
        focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(attrs).setOnAudioFocusChangeListener { change ->
                if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) callbacks.onError("Otra aplicación necesita el audio. La conversación se detuvo.")
            }.build()
        if (manager.requestAudioFocus(focus!!) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            running.set(false); callbacks.onError("El audio está ocupado por otra aplicación."); return
        }
        manager.mode = AudioManager.MODE_IN_COMMUNICATION
        val externalTypes = mutableSetOf(AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLUETOOTH_SCO, AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_HEARING_AID)
        if (android.os.Build.VERSION.SDK_INT >= 31) { externalTypes.add(AudioDeviceInfo.TYPE_BLE_HEADSET); externalTypes.add(AudioDeviceInfo.TYPE_BLE_SPEAKER) }
        if (manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).none { it.type in externalTypes }) {
            if (android.os.Build.VERSION.SDK_INT >= 31) {
                manager.availableCommunicationDevices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                    ?.let { changedSpeaker = manager.setCommunicationDevice(it) }
            } else { manager.isSpeakerphoneOn = true; changedSpeaker = true }
        }
        playThread = Thread({
            try {
                val size = AudioTrack.getMinBufferSize(24000, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
                check(size > 0)
                val output = AudioTrack.Builder().setAudioAttributes(attrs)
                    .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(24000).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                    .setTransferMode(AudioTrack.MODE_STREAM).setBufferSizeInBytes(maxOf(size, 9600)).build()
                synchronized(playbackLock) { track = output; if (running.get()) output.play() }
                while (running.get()) {
                    val chunk = queue.poll(100, TimeUnit.MILLISECONDS) ?: continue
                    var offset = 0
                    while (offset < chunk.bytes.size && running.get() && chunk.epoch == epoch.get()) {
                        synchronized(playbackLock) {
                            if (running.get() && chunk.epoch == epoch.get()) {
                                val n = output.write(chunk.bytes, offset, minOf(2400, chunk.bytes.size - offset), AudioTrack.WRITE_BLOCKING)
                                check(n > 0); offset += n; lastPlaybackAt = System.currentTimeMillis()
                            }
                        }
                    }
                }
            } catch (_: Exception) { if (running.get()) callbacks.onError("No se pudo reproducir la voz. Revisa la salida de audio.") }
            finally { synchronized(playbackLock) { runCatching { track?.stop() }; track?.release(); track = null } }
        }, "jarvis-playback").apply { start() }
        micThread = Thread({
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO)
            var aec: AcousticEchoCanceler? = null
            var ns: NoiseSuppressor? = null
            try {
                SileroVad(context).use { vad ->
                    val min = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
                    check(min > 0)
                    val input = AudioRecord.Builder().setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
                        .setAudioFormat(AudioFormat.Builder().setSampleRate(16000).setChannelMask(AudioFormat.CHANNEL_IN_MONO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
                        .setBufferSizeInBytes(maxOf(min, 8192)).build()
                    recorder = input
                    check(input.state == AudioRecord.STATE_INITIALIZED)
                    if (AcousticEchoCanceler.isAvailable()) aec = AcousticEchoCanceler.create(input.audioSessionId)?.apply { enabled = true }
                    if (NoiseSuppressor.isAvailable()) ns = NoiseSuppressor.create(input.audioSessionId)?.apply { enabled = true }
                    if (!running.get()) return@use
                    input.startRecording()
                    val gate = SpeechGate(); val samples = ShortArray(512); var filled = 0; var lastLevel = 0L
                    while (running.get()) {
                        val n = input.read(samples, filled, samples.size - filled, AudioRecord.READ_BLOCKING)
                        if (n <= 0) { if (running.get()) error("Microphone read failed"); break }
                        filled += n; if (filled != samples.size) continue; filled = 0
                        var energy = 0.0
                        val pcm = ByteArray(1024)
                        for (i in samples.indices) { val v = samples[i].toInt(); pcm[i * 2] = v.toByte(); pcm[i * 2 + 1] = (v shr 8).toByte(); energy += v.toDouble() * v }
                        val rms = (sqrt(energy / samples.size) / 32768).toFloat()
                        val output = gate.process(pcm, vad.probability(samples), rms)
                        if (output.speech) callbacks.onSpeech()
                        callbacks.onPcm(output.pcm)
                        val now = System.currentTimeMillis()
                        if (now - lastLevel > 100) { callbacks.onLevel(if (output.speech) (rms * 8).coerceIn(0f, 1f) else 0f); lastLevel = now }
                    }
                }
            } catch (_: Exception) { if (running.get()) callbacks.onError("No se pudo iniciar el micrófono o el filtro de voz. Revisa el permiso de audio.") }
            finally { aec?.release(); ns?.release(); runCatching { recorder?.stop() }; recorder?.release(); recorder = null }
        }, "jarvis-microphone").apply { start() }
    }
    fun enqueue(pcm: ByteArray) {
        if (running.get() && !queue.offer(Chunk(pcm.copyOf(), epoch.get()))) listener?.onError("La reproducción se retrasó demasiado. Vuelve a conectar.")
    }
    fun interrupt() {
        epoch.incrementAndGet(); queue.clear()
        synchronized(playbackLock) { track?.let { runCatching { it.pause(); it.flush(); if (running.get()) it.play() } } }
    }
    fun isPlaying(): Boolean = queue.isNotEmpty() || System.currentTimeMillis() - lastPlaybackAt < 500
    override fun close() {
        running.set(false)
        runCatching { recorder?.stop() }
        interrupt()
        focus?.let { manager.abandonAudioFocusRequest(it) }; focus = null
        runCatching {
            if (changedSpeaker) {
                if (android.os.Build.VERSION.SDK_INT >= 31) manager.clearCommunicationDevice()
                else manager.isSpeakerphoneOn = previousSpeaker
            }
            manager.mode = previousMode
        }
    }
}

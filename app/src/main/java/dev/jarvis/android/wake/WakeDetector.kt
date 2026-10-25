package dev.jarvis.android.wake

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.LibVosk
import org.vosk.LogLevel
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.ZipInputStream

/** Vosk runs locally on Android 10+. No network client or microphone recording files. */
class WakeDetector(private val context: Context) : AutoCloseable {
    interface Listener { fun onListening(); fun onWake(); fun onError(message: String) }
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val generation = AtomicInteger(0)
    private var model: Model? = null
    private var recognizer: Recognizer? = null
    private var service: SpeechService? = null
    @Volatile private var closed = false
    fun start(listener: Listener) {
        if (closed) return
        val id = generation.incrementAndGet()
        worker.execute {
            stopService()
            try {
                if (id != generation.get()) return@execute
                LibVosk.setLogLevel(LogLevel.WARNINGS)
                val loaded = model ?: Model(prepareModel().absolutePath).also { model = it }
                if (id != generation.get()) return@execute
                // Jarvis is present in this Spanish model's vocabulary (word 98499).
                val rec = Recognizer(loaded, 16000f, WakePhrase.GRAMMAR)
                rec.setWords(true)
                recognizer = rec
                val speech = SpeechService(rec, 16000f)
                service = speech
                if (id != generation.get()) { stopService(); return@execute }
                val callbacks = object : RecognitionListener {
                    override fun onPartialResult(hypothesis: String?) = Unit
                    override fun onResult(hypothesis: String?) = inspect(hypothesis)
                    override fun onFinalResult(hypothesis: String?) = inspect(hypothesis)
                    private fun inspect(hypothesis: String?) {
                        if (id != generation.get() || hypothesis == null) return
                        try {
                            val json = JSONObject(hypothesis)
                            val words = json.optJSONArray("result") ?: return
                            val confirmed = (0 until words.length()).any { i ->
                                val word = words.getJSONObject(i)
                                WakePhrase.matches(word.optString("word")) && word.optDouble("conf") >= .80 &&
                                    word.optDouble("end") - word.optDouble("start") >= .15
                            }
                            if (confirmed && generation.compareAndSet(id, id + 1)) {
                                worker.execute { stopService(); main.post { if (!closed) listener.onWake() } }
                            }
                        } catch (_: Exception) { /* Incomplete recognition does not activate anything. */ }
                    }
                    override fun onError(exception: Exception?) { if (id == generation.get()) main.post { listener.onError("No se pudo escuchar JARVIS. Revisa el permiso y que otra app no use el micrófono.") } }
                    override fun onTimeout() { if (id == generation.get()) main.post { listener.onError("La escucha local terminó. Vuelve a activarla.") } }
                }
                speech.startListening(callbacks)
                main.post { if (id == generation.get()) listener.onListening() }
            } catch (_: Exception) {
                stopService()
                main.post { if (id == generation.get()) listener.onError("No se pudo preparar la activación local. Puedes usar Conversar ahora.") }
            }
        }
    }
    private fun prepareModel(): File {
        val folder = File(context.noBackupFilesDir, "vosk-model-small-es-0.42")
        val marker = File(folder, ".ready-v1")
        if (marker.exists() && File(folder, "am/final.mdl").isFile) return folder
        val base = context.noBackupFilesDir.canonicalFile
        var bytes = 0L
        context.assets.open("wake-model.zip").use { stream ->
            ZipInputStream(stream).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val target = File(base, entry.name).canonicalFile
                    require(target.path.startsWith(base.path + File.separator))
                    if (entry.isDirectory) target.mkdirs() else {
                        target.parentFile?.mkdirs()
                        target.outputStream().use { out ->
                            val buffer = ByteArray(16384)
                            while (true) { val n = zip.read(buffer); if (n < 0) break; bytes += n; require(bytes <= 250_000_000); out.write(buffer, 0, n) }
                        }
                    }
                    zip.closeEntry()
                }
            }
        }
        check(File(folder, "am/final.mdl").isFile)
        marker.writeText("ready")
        return folder
    }
    private fun stopService() {
        service?.let { runCatching { it.cancel() }; runCatching { it.shutdown() } }; service = null
        recognizer?.close(); recognizer = null
    }
    fun stop() { generation.incrementAndGet(); if (!closed) worker.execute { stopService() } }
    override fun close() {
        if (closed) return
        closed = true; generation.incrementAndGet()
        worker.execute { stopService(); model?.close(); model = null }
        worker.shutdown()
    }
}

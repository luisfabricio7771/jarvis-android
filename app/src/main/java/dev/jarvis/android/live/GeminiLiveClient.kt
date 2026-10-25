package dev.jarvis.android.live

import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okio.ByteString
import org.json.JSONObject
import java.util.Base64
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class GeminiLiveClient(
    private val listener: Listener,
    private val client: OkHttpClient = OkHttpClient.Builder().readTimeout(0, TimeUnit.MILLISECONDS)
        .connectTimeout(20, TimeUnit.SECONDS).pingInterval(20, TimeUnit.SECONDS).build(),
    private val endpoint: HttpUrl = LiveProtocol.ENDPOINT.toHttpUrl()
) {
    interface Listener {
        fun onReady()
        fun onAudio(pcm: ByteArray)
        fun onTranscript(role: String, text: String)
        fun onInterrupted()
        fun onTurnComplete()
        fun onError(message: String)
    }
    private val ready = AtomicBoolean(false)
    private val closed = AtomicBoolean(false)
    private var socket: WebSocket? = null
    fun connect(apiKey: String) {
        check(socket == null) { "Create a new client for each connection" }
        require(apiKey.isNotBlank())
        // URL authentication follows the official Live protocol. Never log requests or exceptions.
        val url = endpoint.newBuilder().addQueryParameter("key", apiKey).build()
        socket = client.newWebSocket(Request.Builder().url(url).build(), object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (closed.get()) { webSocket.close(1000, "Stopped"); return }
                webSocket.send(LiveProtocol.setup())
            }
            override fun onMessage(webSocket: WebSocket, text: String) = receive(text)
            override fun onMessage(webSocket: WebSocket, bytes: ByteString) = receive(bytes.utf8())
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                fail(when (response?.code) {
                    401, 403 -> "Gemini rechazó la clave o el acceso al modelo. Revisa tu clave en AI Studio."
                    429 -> "Gemini no tiene cuota disponible. Revisa los límites de tu cuenta."
                    else -> "No se pudo conectar con Gemini. Revisa Internet y el acceso a Gemini 3.8 Live."
                })
            }
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(code, null)
                if (!closed.get()) fail(when(code) {
                    1008 -> "Gemini rechazó la sesión. Revisa la clave, cuota y acceso al modelo."
                    else -> "La sesión de Gemini terminó. Puedes volver a conectar."
                })
            }
        })
    }
    private fun receive(text: String) {
        if (closed.get()) return
        try {
            val json = JSONObject(text)
            if (json.has("error")) {
                val code = json.getJSONObject("error").optInt("code")
                fail(if (code == 429) "Gemini no tiene cuota disponible." else "Gemini rechazó la sesión. Revisa la clave y el acceso al modelo.")
                return
            }
            if (json.has("setupComplete") && ready.compareAndSet(false, true)) listener.onReady()
            if (json.has("goAway")) { fail("Gemini está por cerrar la sesión. Vuelve a conectar para continuar."); return }
            val content = json.optJSONObject("serverContent") ?: return
            if (!ready.get()) return
            if (content.optBoolean("interrupted")) listener.onInterrupted()
            val parts = content.optJSONObject("modelTurn")?.optJSONArray("parts")
            if (parts != null) for (i in 0 until parts.length()) {
                val data = parts.optJSONObject(i)?.optJSONObject("inlineData") ?: continue
                if (data.optString("mimeType").startsWith("audio/pcm")) {
                    val pcm = Base64.getDecoder().decode(data.getString("data"))
                    if (pcm.size % 2 == 0) listener.onAudio(pcm)
                }
            }
            for ((field, role) in listOf("inputTranscription" to "user", "outputTranscription" to "assistant")) {
                content.optJSONObject(field)?.optString("text")?.takeIf { it.isNotBlank() }
                    ?.let { listener.onTranscript(role, it) }
            }
            if (content.optBoolean("turnComplete")) listener.onTurnComplete()
            if (json.has("goAway")) fail("Gemini está por cerrar la sesión. Vuelve a conectar para continuar.")
        } catch (_: Exception) { fail("Gemini envió una respuesta de audio que no se pudo procesar.") }
    }
    private fun fail(message: String) {
        if (closed.compareAndSet(false, true)) {
            ready.set(false); socket?.cancel(); listener.onError(message)
        }
    }
    fun sendAudio(pcm: ByteArray): Boolean {
        if (!ready.get() || closed.get()) return false
        val ws = socket ?: return false
        if (ws.queueSize() > 512_000) { fail("La conexión es demasiado lenta para audio en vivo. Vuelve a conectar."); return false }
        return ws.send(LiveProtocol.audio(pcm))
    }
    fun greet() { if (ready.get() && !closed.get()) socket?.send(LiveProtocol.greeting()) }
    fun close() {
        if (closed.compareAndSet(false, true)) {
            if (ready.getAndSet(false)) socket?.send(LiveProtocol.endAudio())
            socket?.close(1000, "Stopped")
        }
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
    }
}
package dev.jarvis.android

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.*
import dev.jarvis.android.audio.AudioEngine
import dev.jarvis.android.data.HistoryStore
import dev.jarvis.android.data.SecureSettings
import dev.jarvis.android.live.GeminiLiveClient
import dev.jarvis.android.ui.OrbView
import dev.jarvis.android.wake.WakeDetector

class MainActivity : Activity() {
    private enum class Phase { OFF, PREPARING, WAKE, CONNECTING, LIVE }
    private var phase = Phase.OFF
    @Volatile private var session = 0
    private var visible = false
    @Volatile private var lastActivityAt = 0L
    private val main = Handler(Looper.getMainLooper())
    private lateinit var settings: SecureSettings
    private lateinit var history: HistoryStore
    private lateinit var wake: WakeDetector
    private var audio: AudioEngine? = null
    private var live: GeminiLiveClient? = null
    private lateinit var title: TextView
    private lateinit var status: TextView
    private lateinit var detail: TextView
    private lateinit var badge: TextView
    private lateinit var orb: OrbView
    private lateinit var primary: Button
    private lateinit var manual: Button
    private lateinit var transcript: LinearLayout
    private lateinit var chatScroll: ScrollView
    private val pending = linkedMapOf<String, StringBuilder>()
    private val bubbles = mutableMapOf<String, TextView>()
    private val bg = Color.rgb(10, 16, 24)
    private val panel = Color.rgb(17, 27, 39)
    private val muted = Color.rgb(146, 163, 180)
    private val light = Color.rgb(235, 242, 248)
    private var accent = Color.rgb(101, 220, 212)
    private val timeout = Runnable {
        if (phase == Phase.CONNECTING) stopAll("La conexión tardó demasiado. Revisa Internet y vuelve a intentar.")
    }
    private val idle = object : Runnable {
        override fun run() {
            if (phase != Phase.LIVE) return
            if (audio?.isPlaying() == true) lastActivityAt = System.currentTimeMillis()
            if (System.currentTimeMillis() - lastActivityAt > 60_000) {
                stopAll()
                if (settings.wakeEnabled && visible) armWake() else setStatus("En pausa", "La conversación terminó por inactividad.")
            } else main.postDelayed(this, 1000)
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = SecureSettings(this); history = HistoryStore(this); wake = WakeDetector(applicationContext)
        buildUi()
    }
    override fun onStart() { super.onStart(); visible = true }
    override fun onStop() { visible = false; stopAll(); super.onStop() }
    override fun onDestroy() { main.removeCallbacksAndMessages(null); wake.close(); history.close(); super.onDestroy() }

    private fun buildUi() {
        accent = when(settings.accent) { "blue" -> Color.rgb(130, 176, 255); "amber" -> Color.rgb(239, 191, 109); else -> Color.rgb(101, 220, 212) }
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(bg); setPadding(dp(22), dp(12), dp(22), dp(12)) }
        root.setOnApplyWindowInsetsListener { view, insets ->
            view.setPadding(dp(22) + insets.systemWindowInsetLeft, dp(12) + insets.systemWindowInsetTop,
                dp(22) + insets.systemWindowInsetRight, dp(12) + insets.systemWindowInsetBottom)
            insets
        }
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val brand = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        brand.addView(text("J A R V I S", 23f, light, true))
        brand.addView(text("ASISTENTE PERSONAL", 10f, muted).apply { letterSpacing = .12f })
        header.addView(brand, LinearLayout.LayoutParams(0, dp(56), 1f))
        header.addView(button("Ajustes", false).apply { contentDescription = "Abrir ajustes"; setOnClickListener { openSettings() } }, LinearLayout.LayoutParams(dp(98), dp(48)))
        root.addView(header)
        badge = text("GEMINI LIVE · VOZ PREDETERMINADA", 10f, accent).apply { letterSpacing = .06f; gravity = Gravity.CENTER; setPadding(0,dp(18),0,0) }
        root.addView(badge)
        orb = OrbView(this).apply { contentDescription = "Estado visual de JARVIS"; this.accent = this@MainActivity.accent }
        root.addView(orb, LinearLayout.LayoutParams(-1, dp(156)))
        title = text("A tu disposición", 24f, light, true).apply { gravity = Gravity.CENTER }
        status = text("En pausa", 12f, accent).apply { gravity = Gravity.CENTER; setPadding(0,dp(8),0,0) }
        detail = text("Configura tu clave de Gemini y activa la escucha.", 12f, muted).apply { gravity = Gravity.CENTER; setPadding(dp(8),dp(8),dp(8),dp(14)); minHeight = dp(52) }
        root.addView(title); root.addView(status); root.addView(detail)
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        row.addView(text("CONVERSACIÓN", 11f, muted, true), LinearLayout.LayoutParams(0, dp(28), 1f))
        row.addView(text("Ver historial", 12f, accent).apply { contentDescription = "Ver historial local"; setPadding(dp(12),dp(10),0,dp(10)); setOnClickListener { showHistory() } })
        root.addView(row)
        chatScroll = ScrollView(this).apply { isFillViewport = true; background = background(panel, dp(18).toFloat()); overScrollMode = View.OVER_SCROLL_NEVER }
        transcript = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16),dp(12),dp(16),dp(12)) }
        transcript.addView(text("Tus conversaciones aparecerán aquí.\nEl historial permanece en este teléfono.", 13f, muted).apply { setPadding(0,dp(8),0,dp(8)) })
        chatScroll.addView(transcript)
        root.addView(chatScroll, LinearLayout.LayoutParams(-1, 0, 1f).apply { bottomMargin = dp(16); topMargin = dp(4) })
        primary = button("Activar escucha", true).apply { contentDescription = "Activar o detener la escucha"; setOnClickListener { primaryAction() } }
        root.addView(primary, LinearLayout.LayoutParams(-1, dp(54)))
        manual = button("Conversar ahora", false).apply { setOnClickListener { startRequested(false) } }
        root.addView(manual, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(6) })
        root.addView(text("Android 10+  ·  La escucha se pausa al salir de la app", 10f, muted).apply { gravity = Gravity.CENTER; setPadding(0,dp(8),0,0) })
        setContentView(root)
        if (settings.hasKey()) detail.text = "Activa la escucha y di «JARVIS», o pulsa Conversar ahora."
        updateButtons()
    }
    private fun primaryAction() {
        if (phase != Phase.OFF) stopAll() else startRequested(settings.wakeEnabled)
    }
    private var requestedWake = true
    private fun startRequested(useWake: Boolean) {
        if (phase != Phase.OFF) stopAll()
        if (!settings.hasKey()) { openSettings(); return }
        requestedWake = useWake
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 7); return
        }
        if (useWake) armWake() else connectLive()
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 7) {
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
                // Permission dialogs can briefly pause this Activity. A fresh button press is safe on every Android version.
                setStatus("Micrófono autorizado", "Pulsa Activar escucha o Conversar ahora para comenzar.")
            } else setStatus("Micrófono sin permiso", "Permite el micrófono desde los ajustes de Android para conversar.")
        }
    }
    private fun armWake() {
        if (!visible || phase != Phase.OFF) return
        phase = Phase.PREPARING; val id = ++session
        setStatus("Preparando escucha local", "La primera vez se prepara el modelo incluido en la app.")
        updateButtons()
        wake.start(object : WakeDetector.Listener {
            override fun onListening() { if (id == session && visible) { phase = Phase.WAKE; setStatus("Esperando «JARVIS»", "La activación se procesa en este teléfono. Di JARVIS y espera el saludo."); updateButtons() } }
            override fun onWake() { if (id == session && visible) { phase = Phase.OFF; connectLive() } }
            override fun onError(message: String) { if (id == session) stopAll(message) }
        })
    }
    private fun connectLive() {
        if (!visible) return
        val key = try { settings.readKey() } catch (_: Exception) {
            stopAll("No se pudo abrir tu clave protegida. Introdúcela nuevamente en Ajustes."); return
        }
        if (key.isBlank()) { openSettings(); return }
        phase = Phase.CONNECTING; val id = ++session
        setStatus("Conectando con JARVIS", "Preparando la conversación de voz…")
        updateButtons(); window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val client = GeminiLiveClient(object : GeminiLiveClient.Listener {
            private fun ui(action: () -> Unit) { main.post { if (id == session && visible) action() } }
            override fun onReady() = ui {
                volumeControlStream = android.media.AudioManager.STREAM_VOICE_CALL
                main.removeCallbacks(timeout); phase = Phase.LIVE; lastActivityAt = System.currentTimeMillis()
                setStatus("Conversación activa", "Puedes hablar e intervenir con naturalidad. Pulsa Detener para terminar.")
                audio = AudioEngine(applicationContext).also { engine -> engine.start(object : AudioEngine.Listener {
                    override fun onPcm(pcm: ByteArray) { if (id == session) live?.sendAudio(pcm) }
                    override fun onSpeech() { lastActivityAt = System.currentTimeMillis() }
                    override fun onLevel(level: Float) = ui { orb.level = level }
                    override fun onError(message: String) = ui { stopAll(message) }
                }) }
                live?.greet(); main.postDelayed(idle, 1000); updateButtons()
            }
            override fun onAudio(pcm: ByteArray) { if (id == session) { lastActivityAt = System.currentTimeMillis(); audio?.enqueue(pcm) } }
            override fun onTranscript(role: String, text: String) = ui { addTranscript(role, text) }
            override fun onInterrupted() = ui { audio?.interrupt(); flushTurn() }
            override fun onTurnComplete() = ui { flushTurn() }
            override fun onError(message: String) = ui { stopAll(message) }
        })
        live = client
        try { client.connect(key); main.postDelayed(timeout, 25_000) } catch (_: Exception) { stopAll("No se pudo iniciar la conexión. Revisa tu configuración.") }
    }
    private fun stopAll(error: String? = null) {
        ++session; main.removeCallbacks(timeout); main.removeCallbacks(idle)
        live?.close(); live = null; audio?.close(); audio = null; wake.stop()
        flushTurn(); phase = Phase.OFF
        volumeControlStream = android.media.AudioManager.STREAM_MUSIC
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (::status.isInitialized) {
            setStatus(if (error == null) "En pausa" else "No se pudo continuar", error ?: "El micrófono está apagado. Actívalo cuando lo necesites.")
            orb.level = 0f; updateButtons()
        }
    }
    private fun updateButtons() {
        primary.text = when(phase) { Phase.OFF -> if (settings.wakeEnabled) "Activar escucha" else "Iniciar conversación"; else -> "Detener" }
        manual.visibility = if (phase == Phase.OFF && settings.wakeEnabled) View.VISIBLE else View.GONE
        orb.active = phase != Phase.OFF; orb.invalidate()
    }
    private fun setStatus(label: String, info: String) { status.text = label; detail.text = info }
    private fun addTranscript(role: String, segment: String) {
        if (pending.isEmpty() && bubbles.isEmpty() && transcript.childCount == 1) transcript.removeAllViews()
        val buffer = pending.getOrPut(role) { StringBuilder() }.append(segment)
        if (buffer.length > 16000) buffer.delete(0, buffer.length - 16000)
        val contentText = buffer.toString()
        val bubble = bubbles.getOrPut(role) {
            text("", 14f, light).apply {
                setPadding(0,dp(10),0,dp(12)); transcript.addView(this)
            }
        }
        bubble.text = (if (role == "user") "TÚ\n" else "JARVIS\n") + contentText
        chatScroll.post { chatScroll.fullScroll(View.FOCUS_DOWN) }
    }
    private fun flushTurn() {
        if (!::history.isInitialized) return
        val iterator = pending.iterator()
        var failed = false
        while (iterator.hasNext()) {
            val entry = iterator.next()
            try { history.append(entry.key, entry.value.toString()); iterator.remove() } catch (_: Exception) { failed = true }
        }
        if (failed) Toast.makeText(this, "No se pudo guardar el historial. Revisa el espacio disponible.", Toast.LENGTH_LONG).show()
        bubbles.clear()
    }
    private fun openSettings() {
        stopAll()
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(22),dp(8),dp(22),0) }
        box.addView(text("Gemini 3.8 Live · voz predeterminada", 14f, light))
        box.addView(text("Tu clave se protege con Android Keystore. Durante la conversación, el audio se envía a Google. La cuota depende de tu cuenta.", 12f, muted).apply { setPadding(0,dp(8),0,dp(14)) })
        val key = EditText(this).apply { hint = if (settings.hasKey()) "Clave guardada · deja vacío para conservarla" else "Clave API de Google AI Studio"; inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD; setSingleLine(); importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS; setTextColor(light); setHintTextColor(muted) }
        box.addView(key)
        val wakeToggle = Switch(this).apply { text = "Activar por «JARVIS»"; isChecked = settings.wakeEnabled; setTextColor(light); minHeight = dp(56) }
        box.addView(wakeToggle)
        box.addView(text("Color de la interfaz", 12f, muted))
        val colors = Spinner(this).apply { adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, arrayOf("Cian", "Azul", "Ámbar")); setSelection(listOf("cyan","blue","amber").indexOf(settings.accent).coerceAtLeast(0)) }
        box.addView(colors)
        box.addView(button("Licencias de código abierto", false).apply { setOnClickListener {
            val notices = assets.open("licenses/NOTICES.txt").bufferedReader().use { it.readText() }
            AlertDialog.Builder(this@MainActivity).setTitle("Licencias").setMessage(notices).setPositiveButton("Cerrar", null).show()
        } })
        box.addView(text("La escucha funciona con la app abierta. Al salir o bloquear el teléfono se detiene. El historial local no se envía a Gemini.", 12f, muted).apply { setPadding(0,dp(12),0,dp(12)) })
        val dialog = AlertDialog.Builder(this).setTitle("Ajustes de JARVIS").setView(box).setNegativeButton("Cancelar", null)
            .setNeutralButton("Eliminar clave", null).setPositiveButton("Guardar", null).create()
        dialog.setOnShowListener {
            dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                try {
                    val value = key.text.toString().trim()
                    if (value.isNotEmpty()) settings.saveKey(value)
                    settings.wakeEnabled = wakeToggle.isChecked
                    settings.accent = listOf("cyan","blue","amber")[colors.selectedItemPosition]
                    key.text.clear(); dialog.dismiss(); buildUi()
                    setStatus("Configuración guardada", if (settings.hasKey()) "Activa la escucha o pulsa Conversar ahora." else "Añade una clave de Gemini para comenzar.")
                } catch (_: Exception) { key.error = "No se pudo guardar. Revisa el formato de la clave y vuelve a intentarlo." }
            }
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                settings.removeKey(); key.text.clear(); dialog.dismiss(); buildUi(); setStatus("Clave eliminada", "Añade una clave de Gemini en Ajustes para comenzar.")
            }
        }
        dialog.setOnDismissListener { key.text.clear() }
        dialog.show()
    }
    private fun showHistory() {
        val rows = history.recent()
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(22),dp(12),dp(22),dp(12)) }
        if (rows.isEmpty()) content.addView(text("Todavía no hay conversaciones guardadas.", 14f, muted))
        rows.forEach { item -> content.addView(text((if(item.role == "user") "TÚ" else "JARVIS") + "\n" + item.text, 14f, light).apply { setPadding(0,dp(8),0,dp(12)) }) }
        val scroll = ScrollView(this).apply { addView(content) }
        val dialog = AlertDialog.Builder(this).setTitle("Historial en este teléfono").setView(scroll).setPositiveButton("Cerrar", null)
            .setNeutralButton("Borrar historial", null).create()
        dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
            AlertDialog.Builder(this).setTitle("¿Borrar el historial local?").setMessage("Se eliminarán las conversaciones guardadas en este teléfono.")
                .setNegativeButton("Cancelar", null).setPositiveButton("Borrar") { _, _ -> history.clear(); dialog.dismiss() }.show()
        } }
        dialog.show()
    }
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
    private fun text(value: String, size: Float, color: Int, bold: Boolean = false): TextView = TextView(this).apply {
        text = value; textSize = size; setTextColor(color); if(bold) setTypeface(typeface, Typeface.BOLD)
        setLineSpacing(dp(3).toFloat(), 1f)
    }
    private fun background(color: Int, radius: Float) = GradientDrawable().apply { setColor(color); cornerRadius = radius }
    private fun button(label: String, filled: Boolean) = Button(this).apply {
        text = label; isAllCaps = false; textSize = 14f; setTextColor(if(filled) bg else accent)
        background = background(if(filled) accent else panel, dp(14).toFloat())
        stateListAnimator = null; minHeight = dp(48)
    }
}

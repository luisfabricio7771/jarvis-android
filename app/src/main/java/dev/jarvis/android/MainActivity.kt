package dev.jarvis.android

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.*
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.*
import dev.jarvis.android.data.HistoryStore
import dev.jarvis.android.data.SecureSettings
import dev.jarvis.android.ui.OrbView
import dev.jarvis.android.session.ConversationService
import dev.jarvis.android.session.SessionController

class MainActivity: Activity() {
    private lateinit var settings: SecureSettings
    private lateinit var history: HistoryStore
    private var sessionController: SessionController?=null
    private var binding=false
    private var visible=false
    private var lastChatVersion=-1
    private var actionDialog: AlertDialog?=null
    private var dialogId: String?=null
    private lateinit var title: TextView
    private lateinit var status: TextView
    private lateinit var detail: TextView
    private lateinit var badge: TextView
    private lateinit var orb: OrbView
    private lateinit var primary: Button
    private lateinit var manual: Button
    private lateinit var transcript: LinearLayout
    private lateinit var chatScroll: ScrollView
    private val bg=Color.rgb(10,16,24)
    private val panel=Color.rgb(17,27,39)
    private val muted=Color.rgb(146,163,180)
    private val light=Color.rgb(235,242,248)
    private var accent=Color.rgb(101,220,212)
    private val observer: (SessionController.State)->Unit = {renderState(it)}
    private val connection=object: ServiceConnection {
        override fun onServiceConnected(name: ComponentName?,binder: IBinder?) {
            if(!visible) return
            val local=binder as? ConversationService.LocalBinder ?: return
            sessionController=local.controller
            sessionController?.observe(observer);sessionController?.attachHost(this@MainActivity)
        }
        override fun onServiceDisconnected(name: ComponentName?) { sessionController=null;renderState(null) }
    }
    override fun onCreate(savedInstanceState: Bundle?) {super.onCreate(savedInstanceState);settings=SecureSettings(this);history=HistoryStore(this);buildUi()}
    override fun onStart() {
        super.onStart();visible=true
        binding=bindService(Intent(this,ConversationService::class.java),connection,Context.BIND_AUTO_CREATE)
    }
    override fun onNewIntent(intent: Intent) {super.onNewIntent(intent);sessionController?.attachHost(this)}
    override fun onStop() {
        visible=false;actionDialog?.dismiss();actionDialog=null;dialogId=null
        sessionController?.removeObserver(observer);sessionController?.detachHost(this)
        if(!settings.phoneControlEnabled) sessionController?.stop()
        if(binding) {unbindService(connection);binding=false}
        sessionController=null;window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        super.onStop()
    }
    override fun onDestroy() {history.close();super.onDestroy()}
    private fun buildUi() {
        lastChatVersion=-1
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
        root.addView(button("Control del teléfono", false).apply { setOnClickListener { startActivity(android.content.Intent(this@MainActivity, dev.jarvis.android.actions.ControlActivity::class.java)) } }, LinearLayout.LayoutParams(-1,dp(42)).apply { topMargin=dp(8) })
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
        root.addView(text("Android 10+  ·  Control opcional · Detener desde la notificación", 10f, muted).apply { gravity = Gravity.CENTER; setPadding(0,dp(8),0,0) })
        setContentView(root)
        if (settings.hasKey()) detail.text = "Activa la escucha y di «JARVIS», o pulsa Conversar ahora."
        renderState(sessionController?.state())
    }

    private fun primaryAction() {
        if(sessionController?.isActive()==true) stopAll() else startRequested(settings.wakeEnabled)
    }
    private fun startRequested(useWake: Boolean) {
        if(!settings.hasKey()) {openSettings();return}
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO),7);return
        }
        val prefs=getSharedPreferences("jarvis_settings",MODE_PRIVATE)
        if(Build.VERSION.SDK_INT>=33 && settings.phoneControlEnabled && !prefs.getBoolean("notification_prompted",false) &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) {
            prefs.edit().putBoolean("notification_prompted",true).apply()
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS),8);return
        }
        try {startForegroundService(Intent(this,ConversationService::class.java).setAction(ConversationService.START).putExtra("wake",useWake))}
        catch(_:Exception) {setStatus("No se pudo iniciar","Android no permitió comenzar. Mantén JARVIS abierta y vuelve a intentar.")}
    }
    override fun onRequestPermissionsResult(requestCode: Int,permissions: Array<out String>,grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode,permissions,grantResults)
        if(requestCode==7) setStatus(if(grantResults.firstOrNull()==PackageManager.PERMISSION_GRANTED) "Micrófono autorizado" else "Micrófono sin permiso","Pulsa Activar escucha o Conversar ahora para comenzar. Puedes revisar permisos desde Ajustes de Android.")
        if(requestCode==8) setStatus("Preferencia de notificaciones guardada","Pulsa de nuevo para comenzar. Con notificaciones puedes revisar acciones y detener la escucha al usar otras apps.")
    }
    private fun stopAll(error: String?=null) {
        sessionController?.stop(error)
        setStatus("En pausa",error ?: "El micrófono está apagado.")
    }
    private fun renderState(snapshot: SessionController.State?) {
        if(!::primary.isInitialized) return
        val active=snapshot?.phase?.let {it!=SessionController.Phase.OFF} ?: false
        primary.text=if(active) "Detener" else if(settings.wakeEnabled) "Activar escucha" else "Iniciar conversación"
        manual.visibility=if(!active && settings.wakeEnabled) View.VISIBLE else View.GONE
        orb.active=active;orb.level=snapshot?.level ?: 0f;orb.invalidate()
        if(active) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        volumeControlStream=if(snapshot?.phase==SessionController.Phase.LIVE) android.media.AudioManager.STREAM_VOICE_CALL else android.media.AudioManager.STREAM_MUSIC
        snapshot?.let {
            setStatus(it.status,it.detail)
            if(it.chatVersion!=lastChatVersion) {
                lastChatVersion=it.chatVersion
                if(it.lines.isNotEmpty()) {
                    transcript.removeAllViews()
                    it.lines.forEach { line -> transcript.addView(text((if(line.role=="user") "TÚ\n" else "JARVIS\n")+line.text,14f,light).apply {setPadding(0,dp(8),0,dp(12))}) }
                    chatScroll.post {chatScroll.fullScroll(View.FOCUS_DOWN)}
                }
            }
        }
        val entry=snapshot?.pending
        if(dialogId!=entry?.call?.id) {actionDialog?.dismiss();actionDialog=null;dialogId=null}
        if(visible && entry!=null && dialogId==null) {
            dialogId=entry.call.id
            actionDialog=AlertDialog.Builder(this).setTitle(entry.plan.title).setMessage(entry.plan.summary)
                .setNegativeButton("Cancelar") {_,_->sessionController?.reject(entry.call.id)}
                .setPositiveButton("Continuar") {_,_->sessionController?.approve(entry.call.id)}
                .setOnCancelListener {sessionController?.reject(entry.call.id)}.create()
            actionDialog?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            actionDialog?.show()
        }
    }
    private fun setStatus(label: String,info: String) {status.text=label;detail.text=info}
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
        box.addView(text("Con control habilitado puedes conversar al usar otras apps. Detener, bloquear la pantalla o cerrar la tarea apagan la escucha. El historial local no se envía a Gemini.", 12f, muted).apply { setPadding(0,dp(12),0,dp(12)) })
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

package dev.jarvis.android.session

import android.Manifest
import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import dev.jarvis.android.actions.*
import dev.jarvis.android.screen.ScreenAccessService
import dev.jarvis.android.audio.AudioEngine
import dev.jarvis.android.data.HistoryStore
import dev.jarvis.android.data.SecureSettings
import dev.jarvis.android.live.GeminiLiveClient
import dev.jarvis.android.wake.WakeDetector
import org.json.JSONObject
import java.lang.ref.WeakReference

class SessionController(private val context: Context,private val changed: ()->Unit,private val finished: ()->Unit) : AutoCloseable {
    enum class Phase { OFF, PREPARING, WAKE, CONNECTING, LIVE }
    data class Line(val role: String,var text: String)
    data class State(val phase: Phase,val status: String,val detail: String,val level: Float,val chatVersion: Int,val lines: List<Line>,val pending: ApprovalQueue.Entry?)
    private val main=Handler(Looper.getMainLooper())
    private val settings=SecureSettings(context)
    private val history=HistoryStore(context)
    private val wake=WakeDetector(context)
    private val actions=AndroidActions(context)
    private val approvals=ApprovalQueue()
    private val screenRequests=mutableSetOf<String>()
    private val observers=linkedSetOf<(State)->Unit>()
    private var host=WeakReference<Activity>(null)
    private var phase=Phase.OFF
    private var label="En pausa"
    private var detail="El micrófono está apagado."
    private var level=0f
    private var chatVersion=0
    private val lines=mutableListOf<Line>()
    private val pendingLines=linkedMapOf<String,Line>()
    @Volatile private var generation=0
    @Volatile private var lastActivityAt=0L
    private var live: GeminiLiveClient?=null
    private var audio: AudioEngine?=null
    private var closed=false
    private val timeout=Runnable { if(phase==Phase.CONNECTING) stop("La conexión tardó demasiado. Revisa Internet y vuelve a intentar.") }
    private val tick=object: Runnable {
        override fun run() {
            approvals.expired().forEach { live?.respondTool(it.call,PhoneTools.ok("expired","La acción expiró sin aprobación. No se ejecutó.")) }
            if(isLocked()) { stop("La escucha se detuvo al bloquear el teléfono.");return }
            if(phase==Phase.LIVE) {
                if(audio?.isPlaying()==true || approvals.current()!=null || screenRequests.isNotEmpty()) lastActivityAt=System.currentTimeMillis()
                if(System.currentTimeMillis()-lastActivityAt>60_000) {
                    stopResources();if(settings.wakeEnabled) armWake() else stop("La conversación terminó por inactividad.")
                }
            }
            if(phase!=Phase.OFF) { emit();main.postDelayed(this,1000) }
        }
    }
    fun state()=State(phase,label,detail,level,chatVersion,lines.map { it.copy() },approvals.current())
    fun observe(observer: (State)->Unit) { observers.add(observer);observer(state()) }
    fun removeObserver(observer: (State)->Unit) { observers.remove(observer) }
    fun attachHost(activity: Activity) { host=WeakReference(activity);processActions();emit() }
    fun detachHost(activity: Activity) { if(host.get()===activity) host.clear() }
    fun isActive()=phase!=Phase.OFF
    fun isLocked()=context.getSystemService(KeyguardManager::class.java).isDeviceLocked
    fun start(useWake: Boolean) {
        if(closed) return
        stopResources()
        if(context.checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED) { stop("Autoriza el micrófono desde JARVIS para comenzar.");return }
        if(isLocked()) { stop("Desbloquea el teléfono para comenzar.");return }
        if(!settings.hasKey()) { stop("Añade tu clave de Gemini en Ajustes.");return }
        if(useWake) armWake() else connectLive()
        main.removeCallbacks(tick);main.postDelayed(tick,1000)
    }
    private fun armWake() {
        phase=Phase.PREPARING;val id=++generation
        setStatus("Preparando escucha local","Di JARVIS y espera el saludo.")
        wake.start(object: WakeDetector.Listener {
            override fun onListening() { if(id==generation) { phase=Phase.WAKE;setStatus("Esperando «JARVIS»","Activación local. Puedes detenerla desde la notificación.") } }
            override fun onWake() { if(id==generation) connectLive() }
            override fun onError(message: String) { if(id==generation) stop(message) }
        })
    }
    private fun connectLive() {
        if(isLocked()) { stop("Desbloquea el teléfono para continuar.");return }
        val key=try { settings.readKey() } catch(_:Exception) { stop("No se pudo abrir tu clave protegida. Configúrala nuevamente.");return }
        if(key.isBlank()) { stop("Añade tu clave en Ajustes.");return }
        phase=Phase.CONNECTING;val id=++generation
        setStatus("Conectando con JARVIS","Preparando la conversación de voz…")
        val client=GeminiLiveClient(object: GeminiLiveClient.Listener {
            private fun ui(action: ()->Unit) { main.post { if(id==generation && !closed) action() } }
            override fun onReady()=ui {
                main.removeCallbacks(timeout);phase=Phase.LIVE;lastActivityAt=System.currentTimeMillis()
                setStatus("Conversación activa",if(settings.phoneControlEnabled) "Control del teléfono habilitado. Detener apaga la escucha." else "Puedes intervenir por voz. La escucha se pausa al salir de JARVIS.")
                audio=AudioEngine(context).also { engine -> engine.start(object: AudioEngine.Listener {
                    override fun onPcm(pcm: ByteArray) { if(id==generation) live?.sendAudio(pcm) }
                    override fun onSpeech() { lastActivityAt=System.currentTimeMillis() }
                    override fun onLevel(value: Float)=ui { level=value;emit(false) }
                    override fun onError(message: String)=ui { stop(message) }
                }) }
                live?.greet()
            }
            override fun onAudio(pcm: ByteArray) { if(id==generation) { lastActivityAt=System.currentTimeMillis();audio?.enqueue(pcm) } }
            override fun onTranscript(role: String,text: String)=ui { append(role,text) }
            override fun onInterrupted()=ui { audio?.interrupt();flushTurn() }
            override fun onTurnComplete()=ui { flushTurn() }
            override fun onToolCall(call: ToolCall)=ui { handleTool(call) }
            override fun onToolCancelled(toolId: String)=ui { approvals.cancel(toolId);ScreenAccessService.instance?.cancel(toolId);screenRequests.remove(toolId);processActions();emit() }
            override fun onError(message: String)=ui { stop(message) }
        },toolsEnabled=settings.phoneControlEnabled)
        live=client
        try {client.connect(key);main.postDelayed(timeout,25_000)} catch(_:Exception) {stop("No se pudo conectar. Revisa tu configuración.")}
    }
    private fun handleTool(call: ToolCall) {
        if(!canAct(call)) return
        lastActivityAt=System.currentTimeMillis()
        if(call.name.startsWith("screen_")) {
            val service=ScreenAccessService.instance
            if(service==null) {live?.respondTool(call,PhoneTools.ok("permission_required","Activa Control de pantalla de JARVIS en los ajustes de accesibilidad de Android."));return}
            val id=generation
            screenRequests.add(call.id)
            service.execute(call,{id==generation && canAct(call)}) {result->
                screenRequests.remove(call.id)
                if(id==generation && live?.isToolPending(call.id)==true) live?.respondTool(call,result)
                detail=result.optString("message");emit()
            }
            return
        }
        val plan=actions.plan(call)
        if(!canAct(call)) return
        when(plan) {
            is ActionPlan.Result -> live?.respondTool(call,plan.value)
            is ActionPlan.Launch -> {
                if(!approvals.add(call,plan)) live?.respondTool(call,PhoneTools.ok("busy","Hay otras acciones pendientes. Espera a que se revisen."))
                processActions()
            }
        }
        emit()
    }
    private fun canAct(call: ToolCall): Boolean {
        if(live?.isToolPending(call.id)!=true) return false
        if(phase!=Phase.LIVE || !settings.phoneControlEnabled || isLocked()) {
            live?.respondTool(call,PhoneTools.ok("blocked","El control está desactivado o el teléfono está bloqueado. No se ejecutó la acción."));return false
        }
        return true
    }
    private fun processActions() {
        val first=approvals.current() ?: return
        if(live?.isToolPending(first.call.id)!=true) { approvals.cancel(first.call.id);processActions();return }
        if(host.get()!=null) {
            if(!first.plan.review) approve(first.call.id)
        } else {
            val service=ScreenAccessService.instance
            if(service!=null && settings.screenControlEnabled) {
                val entry=approvals.take(first.call.id) ?: return
                val id=generation
                screenRequests.add(entry.call.id)
                service.launchExternal(entry.call,entry.plan,{id==generation && canAct(entry.call)}) {result->
                    screenRequests.remove(entry.call.id)
                    if(id==generation && live?.isToolPending(entry.call.id)==true) live?.respondTool(entry.call,result)
                    detail=result.optString("message");emit();main.postDelayed({processActions();emit()},350)
                }
            }
        }
    }
    fun approve(id: String) {
        val activity=host.get() ?: return
        val pending=approvals.take(id) ?: return
        if(!canAct(pending.call)) {emit();return}
        val result=try {
            activity.startActivity(pending.plan.intent)
            PhoneTools.ok("opened","Android aceptó abrir la pantalla solicitada. El usuario debe verificar y completar la operación en esa app; no se confirma un envío, llamada o guardado.")
        } catch(_: android.content.ActivityNotFoundException) { PhoneTools.ok("unavailable","No hay una app compatible para esta acción.") }
        catch(_:SecurityException) { PhoneTools.ok("permission_denied","Android no permitió abrir esa pantalla.") }
        catch(_:Exception) { PhoneTools.error("No se pudo abrir la pantalla solicitada.") }
        live?.respondTool(pending.call,result)
        detail=result.optString("message");emit()
        main.postDelayed({processActions();emit()},500)
    }
    fun reject(id: String) {
        val entry=approvals.cancel(id) ?: return
        live?.respondTool(entry.call,PhoneTools.ok("cancelled","El usuario canceló. No se ejecutó la acción."))
        processActions();emit()
    }
    private fun append(role: String,text: String) {
        val line=pendingLines.getOrPut(role) { Line(role,"").also { lines.add(it) } }
        line.text=(line.text+text).takeLast(16000)
        while(lines.size>30) lines.removeAt(0)
        chatVersion++;emit(false)
    }
    private fun flushTurn() {
        val iterator=pendingLines.iterator()
        while(iterator.hasNext()) {
            val entry=iterator.next()
            try {history.append(entry.key,entry.value.text);iterator.remove()} catch(_:Exception) {detail="No se pudo guardar el historial. Revisa el espacio disponible."}
        }
    }
    private fun stopResources() {
        generation++;main.removeCallbacks(timeout);main.removeCallbacks(tick)
        ScreenAccessService.instance?.cancelAll();screenRequests.clear()
        approvals.clear().forEach { live?.respondTool(it.call,PhoneTools.ok("cancelled","La sesión terminó. No se ejecutó la acción pendiente.")) }
        live?.close();live=null;audio?.close();audio=null;wake.stop();flushTurn();level=0f;phase=Phase.OFF
    }
    fun stop(message: String?=null) {
        stopResources();label=if(message==null) "En pausa" else "Sesión detenida";detail=message ?: "El micrófono está apagado."
        emit();finished()
    }
    private fun setStatus(value: String,info: String) {label=value;detail=info;emit()}
    private fun emit(updateNotification: Boolean=true) {
        val snapshot=state();observers.toList().forEach { it(snapshot) };if(updateNotification) changed()
    }
    override fun close() {
        if(closed) return
        stopResources();closed=true;wake.close();history.close();observers.clear();host.clear();main.removeCallbacksAndMessages(null)
    }
}

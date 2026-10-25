package dev.jarvis.android.actions

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.*
import dev.jarvis.android.data.SecureSettings
import dev.jarvis.android.session.ConversationService
import dev.jarvis.android.screen.ScreenAccessService
import dev.jarvis.android.screen.ScreenPolicy
import org.json.JSONObject
import java.util.UUID

class ControlActivity: Activity() {
    private lateinit var output: TextView
    private val light=Color.rgb(235,242,248)
    private val muted=Color.rgb(146,163,180)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val settings=SecureSettings(this)
        val box=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL;setPadding(dp(22),dp(18),dp(22),dp(24));setBackgroundColor(Color.rgb(10,16,24))}
        box.setOnApplyWindowInsetsListener {view,insets->view.setPadding(dp(22),dp(18)+insets.systemWindowInsetTop,dp(22),dp(24)+insets.systemWindowInsetBottom);insets}
        fun note(value: String,size: Float=14f)=TextView(this).apply {text=value;textSize=size;setTextColor(if(size>=20) light else muted);setPadding(0,dp(8),0,dp(12))}
        fun button(label: String,action: ()->Unit)=Button(this).apply {text=label;isAllCaps=false;setOnClickListener {action()}}
        box.addView(note("Control del teléfono",24f))
        box.addView(note("Actívalo para pedir acciones a JARVIS por voz. Solo se usarán las herramientas disponibles; Android y las apps muestran las pantallas que debas completar."))
        val enable=Switch(this).apply {text="Permitir acciones por voz";isChecked=settings.phoneControlEnabled;setTextColor(light);minHeight=dp(56)}
        enable.setOnCheckedChangeListener {_,checked->
            settings.phoneControlEnabled=checked
            if(!checked) stopConversation()
            Toast.makeText(this,"La preferencia se aplica al iniciar la siguiente conversación.",Toast.LENGTH_LONG).show()
        }
        box.addView(enable)
        box.addView(note("Al cambiar de app la conversación continúa con una notificación para detenerla. Bloquear la pantalla o cerrar la tarea apagan la escucha. Durante una sesión, Gemini recibe resultados de las acciones: hora, batería y coincidencias de apps solicitadas. El control de pantalla tiene permisos y consentimiento separados abajo. No se consulta la libreta de contactos ni se guarda una grabación de audio."))
        box.addView(note("Dentro de otras apps",20f))
        box.addView(note("Autoriza solo las apps que quieras controlar. JARVIS puede tocar controles accesibles, escribir y desplazar listas. El texto visible puede incluir nombres o datos personales; compartirlo con Google Gemini es opcional. No se envían capturas ni el texto actual de campos editables. Enviar, pagar, borrar, instalar y conceder permisos quedan para que tú pulses el botón final."))
        val screen=Switch(this).apply {text="Permitir control de pantalla";isChecked=settings.screenControlEnabled;setTextColor(light);minHeight=dp(52)}
        screen.setOnCheckedChangeListener {_,checked->settings.screenControlEnabled=checked;if(!checked) ScreenAccessService.instance?.cancelAll()}
        box.addView(screen)
        val share=Switch(this).apply {text="Compartir etiquetas visibles con Gemini";isChecked=settings.shareScreenLabels;setTextColor(light);minHeight=dp(52)}
        share.setOnCheckedChangeListener {_,checked->
            if(!checked) settings.shareScreenLabels=false
            else if(!settings.shareScreenLabels) AlertDialog.Builder(this).setTitle("Compartir controles con Google Gemini")
                .setMessage("Cuando solicites control, JARVIS podrá enviar a Google los nombres, etiquetas y descripciones de controles visibles en las apps que autorices. Pueden incluir nombres de personas u otros datos personales. Se excluyen contraseñas y el texto de campos editables; no se envían capturas. Esta opción no comparte automáticamente toda una conversación o biblioteca.")
                .setNegativeButton("Cancelar") {_,_->share.isChecked=false}
                .setPositiveButton("Permitir") {_,_->settings.shareScreenLabels=true}
                .setOnCancelListener {share.isChecked=false}.show()
        }
        box.addView(share)
        val confirm=Switch(this).apply {text="Revisar cada toque y desplazamiento";isChecked=settings.confirmScreenTouches;setTextColor(light);minHeight=dp(52)}
        confirm.setOnCheckedChangeListener {_,checked->settings.confirmScreenTouches=checked}
        box.addView(confirm)
        box.addView(note("La escritura siempre muestra el texto para revisión. Si desactivas Revisar cada toque, la navegación común en tus apps autorizadas puede ejecutarse directamente. Los controles finales y sensibles siguen siendo manuales."))
        box.addView(button("Elegir apps autorizadas") {
            val apps=AndroidActions(this).apps().filter {ScreenPolicy.allowedApp(it.packageName,packageName,setOf(it.packageName))}.sortedBy {it.label}
            val selected=settings.screenApps.toMutableSet()
            AlertDialog.Builder(this).setTitle("Apps que JARVIS puede controlar")
                .setMultiChoiceItems(apps.map {it.label}.toTypedArray(),apps.map {it.packageName in selected}.toBooleanArray()) {_,which,checked->if(checked) selected.add(apps[which].packageName) else selected.remove(apps[which].packageName)}
                .setNegativeButton("Cancelar",null).setPositiveButton("Guardar") {_,_->settings.screenApps=selected;ScreenAccessService.instance?.cancelAll();Toast.makeText(this,"Apps autorizadas: ${selected.size}",Toast.LENGTH_SHORT).show()}.show()
        })
        box.addView(button("Activar servicio en Android") {startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))})
        box.addView(button("Estado del servicio") {Toast.makeText(this,if(ScreenAccessService.instance==null) "Accesibilidad desactivada" else "Accesibilidad activa · ${settings.screenApps.size} apps autorizadas",Toast.LENGTH_LONG).show()})
        box.addView(button("Desactivar servicio de pantalla") {ScreenAccessService.instance?.disableSelf();settings.screenControlEnabled=false;screen.isChecked=false})
        box.addView(note("Acciones disponibles",20f))
        box.addView(note("Abrir apps, Wi-Fi/Bluetooth y otros ajustes; volumen; preparar alarmas y temporizadores; preparar llamadas y SMS; mapas y páginas HTTPS. Llamadas y mensajes requieren revisar sus datos y pulsar la acción final en la app correspondiente."))
        output=note("Puedes probar estas acciones sin una clave de Gemini.")
        box.addView(button("Ver estado del teléfono") {test("phone_status",JSONObject())})
        box.addView(button("Abrir ajustes de Wi-Fi") {test("open_settings",JSONObject().put("section","wifi"))})
        box.addView(button("Abrir ajustes de Bluetooth") {test("open_settings",JSONObject().put("section","bluetooth"))})
        box.addView(button("Preparar temporizador de 1 minuto") {test("prepare_timer",JSONObject().put("seconds",60).put("label","Prueba JARVIS"))})
        val app=EditText(this).apply {hint="Nombre de una app, por ejemplo YouTube";setSingleLine();setTextColor(light);setHintTextColor(muted)}
        box.addView(app)
        box.addView(button("Abrir esa app") {test("open_app",JSONObject().put("app",app.text.toString()))})
        box.addView(output)
        if(Build.VERSION.SDK_INT>=33) box.addView(button("Revisar notificaciones de JARVIS") {startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,packageName))})
        box.addView(button("Volver a JARVIS") {finish()})
        val scroll=ScrollView(this).apply {addView(box)};setContentView(scroll)
    }
    @android.annotation.SuppressLint("ImplicitSamInstance")
    private fun stopConversation() {
        // Context.stopService identifies a service by Intent component, not listener identity.
        stopService(Intent(this,ConversationService::class.java))
    }
    private fun test(name: String,args: JSONObject) {
        when(val plan=AndroidActions(this).plan(ToolCall("manual-"+UUID.randomUUID(),name,args))) {
            is ActionPlan.Result -> output.text=plan.value.toString(2)
            is ActionPlan.Launch -> {
                fun launch() {
                    try {startActivity(plan.intent);output.text="Android aceptó abrir la pantalla. Completa la operación allí."}
                    catch(_:Exception) {output.text="No hay una app compatible o Android no permitió abrirla."}
                }
                if(plan.review) AlertDialog.Builder(this).setTitle(plan.title).setMessage(plan.summary).setNegativeButton("Cancelar",null).setPositiveButton("Continuar") {_,_->launch()}.show()
                else launch()
            }
        }
    }
    private fun dp(value: Int)=(value*resources.displayMetrics.density).toInt()
}

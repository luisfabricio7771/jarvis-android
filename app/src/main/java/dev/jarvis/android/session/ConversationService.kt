package dev.jarvis.android.session

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.*
import dev.jarvis.android.MainActivity
import dev.jarvis.android.R

class ConversationService: Service() {
    inner class LocalBinder: Binder() { val controller get()=session }
    private val binder=LocalBinder()
    lateinit var session: SessionController
        private set
    private var foreground=false
    private val screenOff=object: BroadcastReceiver() { override fun onReceive(context: Context,intent: Intent) {session.stop("La escucha se detuvo al bloquear la pantalla.")} }
    override fun onCreate() {
        super.onCreate()
        session=SessionController(applicationContext,{if(foreground) updateNotification()},{stopForeground(STOP_FOREGROUND_REMOVE);foreground=false;stopSelf()})
        getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(CHANNEL,"Conversación de JARVIS",NotificationManager.IMPORTANCE_LOW))
        if(Build.VERSION.SDK_INT>=33) registerReceiver(screenOff,IntentFilter(Intent.ACTION_SCREEN_OFF),RECEIVER_NOT_EXPORTED)
        else registerReceiver(screenOff,IntentFilter(Intent.ACTION_SCREEN_OFF))
    }
    override fun onBind(intent: Intent?)=binder
    override fun onStartCommand(intent: Intent?,flags: Int,startId: Int): Int {
        if(intent?.action==STOP) {session.stop();return START_NOT_STICKY}
        if(intent?.action!=START) {stopSelf();return START_NOT_STICKY}
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED) {session.stop("Autoriza el micrófono desde la app.");return START_NOT_STICKY}
        try {
            val type=ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK or if(Build.VERSION.SDK_INT>=30) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0
            startForeground(ID,notification(),type);foreground=true
            session.start(intent.getBooleanExtra("wake",true))
        } catch(_:Exception) {session.stop("Android no permitió iniciar la escucha. Abre JARVIS y vuelve a intentarlo.")}
        return START_NOT_STICKY
    }
    private fun notification(): Notification {
        val open=PendingIntent.getActivity(this,10,Intent(this,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val stop=PendingIntent.getService(this,11,Intent(this,ConversationService::class.java).setAction(STOP),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val state=session.state()
        val text=state.pending?.let {"Acción pendiente: ${it.plan.title}. Toca para revisar."} ?: state.status
        return Notification.Builder(this,CHANNEL).setSmallIcon(R.drawable.ic_jarvis).setContentTitle("JARVIS · Micrófono activo")
            .setContentText(text).setContentIntent(open).setVisibility(Notification.VISIBILITY_PRIVATE).setOngoing(true).setOnlyAlertOnce(true).setCategory(Notification.CATEGORY_SERVICE)
            .addAction(Notification.Action.Builder(null,"Detener",stop).build()).build()
    }
    private fun updateNotification() { getSystemService(NotificationManager::class.java).notify(ID,notification()) }
    override fun onTaskRemoved(rootIntent: Intent?) {session.stop();super.onTaskRemoved(rootIntent)}
    override fun onDestroy() {runCatching {unregisterReceiver(screenOff)};session.close();stopForeground(STOP_FOREGROUND_REMOVE);super.onDestroy()}
    companion object {const val START="dev.jarvis.android.START";const val STOP="dev.jarvis.android.STOP";private const val CHANNEL="jarvis_voice";private const val ID=71}
}

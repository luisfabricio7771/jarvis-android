package dev.jarvis.android.screen

import dev.jarvis.android.actions.ActionArguments
import java.text.Normalizer
import java.util.Locale

object ScreenPolicy {
    private val blockedPackages=setOf("com.android.settings","com.android.systemui","com.android.permissioncontroller","com.android.packageinstaller",
        "com.google.android.permissioncontroller","com.google.android.packageinstaller","com.google.android.gms","com.miui.securitycenter")
    fun allowedApp(packageName: String,ownPackage: String,selected: Set<String>): Boolean = packageName in selected && packageName!=ownPackage && packageName !in blockedPackages &&
        !packageName.startsWith("com.samsung.android.settings") && !packageName.endsWith(".permissioncontroller")
    private fun words(value: String)=Normalizer.normalize(value.lowercase(Locale.ROOT),Normalizer.Form.NFD).replace(Regex("\\p{M}+"),"")
    fun manualOnly(label: String,resourceId: String): Boolean {
        val text=words(label)
        val critical=Regex("\\b(enviar|mandar|send|pagar|pago|pay|transferir|transfer|comprar|buy|purchase|checkout|confirmar|confirm|eliminar|borrar|delete|remove|aceptar|accept|agree|permitir|allow|instalar|install|desinstalar|uninstall|firmar|sign|publicar|publish|llamar|call|autorizar|authorize)\\b")
        return critical.containsMatchIn(text) || Regex("send|payment|checkout|transfer|delete|confirm|permission|install").containsMatchIn(resourceId.lowercase(Locale.ROOT))
    }
    fun sensitiveField(label: String)=Regex("\\b(password|contrasena|pin|otp|cvv|cvc|tarjeta|card|clave|verificacion|verification|autenticacion|authentication)\\b").containsMatchIn(words(label))
}

data class ControlDescriptor(val token: String,val path: List<Int>,val label: String,val resourceId: String,val className: String,
    val left: Int,val top: Int,val right: Int,val bottom: Int,val editable: Boolean,val clickable: Boolean,val scrollable: Boolean,val manualOnly: Boolean)

class ScreenSnapshotCache(private val now: ()->Long=System::currentTimeMillis) {
    data class Snapshot(val id: String,val packageName: String,val capturedAt: Long,val controls: List<ControlDescriptor>)
    private var snapshot: Snapshot?=null
    fun save(packageName: String,controls: List<ControlDescriptor>): Snapshot = Snapshot(java.util.UUID.randomUUID().toString(),packageName,now(),controls).also { snapshot=it }
    fun bySnapshot(id: String,packageName: String): Snapshot? = snapshot?.takeIf { it.id==id && it.packageName==packageName && now()-it.capturedAt in 0..60000 }
    fun byControl(id: String,packageName: String): ControlDescriptor? = snapshot?.takeIf {it.packageName==packageName && now()-it.capturedAt in 0..60000}?.controls?.firstOrNull {it.token==id}
    fun clear() {snapshot=null}
}

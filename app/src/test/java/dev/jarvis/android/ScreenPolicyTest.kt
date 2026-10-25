package dev.jarvis.android
import dev.jarvis.android.screen.*
import org.junit.Assert.*
import org.junit.Test

class ScreenPolicyTest {
    @Test fun onlySelectedAppsAreControllableAndSystemPermissionsStayManual() {
        assertTrue(ScreenPolicy.allowedApp("com.android.chrome","dev.jarvis.android",setOf("com.android.chrome")))
        for(app in listOf("com.android.settings","com.android.systemui","com.google.android.permissioncontroller","dev.jarvis.android")) assertFalse(app,ScreenPolicy.allowedApp(app,"dev.jarvis.android",setOf(app)))
        assertFalse(ScreenPolicy.allowedApp("other.app","dev.jarvis.android",emptySet()))
    }
    @Test fun finalActionsCannotBeClickedAutomatically() {
        for(label in listOf("Enviar","Confirmar pago","Transferir","Delete","Instalar","Permitir acceso","Comprar ahora")) assertTrue(label,ScreenPolicy.manualOnly(label,""))
        assertTrue(ScreenPolicy.manualOnly("", "com.app:id/sendMessageButton"))
        assertFalse(ScreenPolicy.manualOnly("Buscar","search_icon"));assertFalse(ScreenPolicy.manualOnly("Reproducir","play_button"))
    }
    @Test fun authenticationAndPaymentFieldsAreExcluded() {
        for(label in listOf("Contraseña","Código de verificación","PIN","CVV","Card number","Clave")) assertTrue(label,ScreenPolicy.sensitiveField(label))
        assertFalse(ScreenPolicy.sensitiveField("Buscar en YouTube"))
    }
    @Test fun snapshotsExpireAndNeverCrossAppBoundaries() {
        var now=100L;val cache=ScreenSnapshotCache {now}
        val snapshot=cache.save("app.a",emptyList())
        assertNotNull(cache.bySnapshot(snapshot.id,"app.a"));assertNull(cache.bySnapshot(snapshot.id,"app.b"));now+=60001
        assertNull(cache.bySnapshot(snapshot.id,"app.a"))
    }
    @Test fun replacingOrClearingSnapshotInvalidatesOldIds() {
        val cache=ScreenSnapshotCache {100L};val old=cache.save("app.a",emptyList());val fresh=cache.save("app.a",emptyList())
        assertNull(cache.bySnapshot(old.id,"app.a"));assertNotNull(cache.bySnapshot(fresh.id,"app.a"));cache.clear();assertNull(cache.bySnapshot(fresh.id,"app.a"))
    }
}

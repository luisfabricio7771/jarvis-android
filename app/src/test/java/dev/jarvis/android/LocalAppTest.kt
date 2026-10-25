package dev.jarvis.android
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import dev.jarvis.android.data.HistoryStore
import dev.jarvis.android.data.SecureSettings
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29,35])
class LocalAppTest {
    @Test fun historySurvivesDatabaseCloseAndReopen() {
        val context=RuntimeEnvironment.getApplication()
        context.deleteDatabase("jarvis_history.db")
        HistoryStore(context).use { it.append("user","¿Qué decidimos?");it.append("assistant","Falta información.") }
        HistoryStore(context).use {
            assertEquals(listOf("user","assistant"),it.recent().map { m->m.role })
            assertEquals("¿Qué decidimos?",it.recent()[0].text)
            it.clear();assertTrue(it.recent().isEmpty())
        }
    }
    @Test fun invalidRolesAreNotSaved() {
        val store=HistoryStore(RuntimeEnvironment.getApplication())
        try { store.append("system","Invalid");fail("Unknown role accepted") } catch (_: IllegalArgumentException) {} finally { store.close() }
    }
    @Test fun preferencesSurviveNewSettingsInstance() {
        val context=RuntimeEnvironment.getApplication();context.getSharedPreferences("jarvis_settings",Context.MODE_PRIVATE).edit().clear().commit()
        SecureSettings(context).apply { accent="amber";wakeEnabled=false }
        SecureSettings(context).apply {assertEquals("amber",accent);assertFalse(wakeEnabled);assertFalse(hasKey())}
    }
    @Test fun launchDoesNotRequestMicrophoneOrConnectAutomatically() {
        val controller=Robolectric.buildActivity(MainActivity::class.java).create().start().resume().visible()
        assertEquals(PackageManager.PERMISSION_DENIED,controller.get().checkSelfPermission(Manifest.permission.RECORD_AUDIO))
        controller.pause().stop().destroy()
    }
}

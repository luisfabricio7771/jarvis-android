package dev.jarvis.android
import android.content.Intent
import dev.jarvis.android.session.*
import dev.jarvis.android.actions.ControlActivity
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29,35])
class ConversationServiceTest {
    @Test fun bindingDoesNotActivateAudioAndServiceDoesNotRestartItself() {
        val controller=Robolectric.buildService(ConversationService::class.java).create()
        val service=controller.get();assertFalse(service.session.isActive())
        assertEquals(android.app.Service.START_NOT_STICKY,service.onStartCommand(null,0,1))
        controller.destroy()
    }
    @Test fun screenOffClosesSessionAndPendingActions() {
        val controller=Robolectric.buildService(ConversationService::class.java).create()
        RuntimeEnvironment.getApplication().sendBroadcast(Intent(Intent.ACTION_SCREEN_OFF))
        org.robolectric.shadows.ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
        assertFalse(controller.get().session.isActive());assertNull(controller.get().session.state().pending)
        assertTrue(controller.get().session.state().detail.contains("bloquear"));controller.destroy()
    }
    @Test fun controlPanelOpensWithoutMicOrApiKey() {
        val controller=Robolectric.buildActivity(ControlActivity::class.java).create().start().resume().visible()
        assertNotNull(controller.get().window.decorView);controller.pause().stop().destroy()
    }
}

package dev.jarvis.android
import android.content.Intent
import android.media.AudioManager
import android.provider.AlarmClock
import dev.jarvis.android.actions.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29,35])
class AndroidActionsTest {
    private fun plan(name: String,args: JSONObject=JSONObject())=AndroidActions(RuntimeEnvironment.getApplication()).plan(ToolCall("test",name,args))
    @Test fun callsUseDialerAndRequireReview() {
        val value=plan("prepare_call",JSONObject().put("number","+52 55 1234 5678")) as ActionPlan.Launch
        assertEquals(Intent.ACTION_DIAL,value.intent.action);assertEquals("tel",value.intent.data?.scheme);assertEquals("+525512345678",value.intent.data?.schemeSpecificPart);assertTrue(value.review)
    }
    @Test fun smsIsDraftedWithoutSendPermission() {
        val value=plan("prepare_message",JSONObject().put("number","5551234").put("body","Hola\n¿Nos vemos?")) as ActionPlan.Launch
        assertEquals(Intent.ACTION_SENDTO,value.intent.action);assertEquals("smsto:5551234",value.intent.dataString)
        assertEquals("Hola\n¿Nos vemos?",value.intent.getStringExtra("sms_body"));assertTrue(value.review)
    }
    @Test fun clocksNeverSkipTheirOwnInterface() {
        val alarm=plan("prepare_alarm",JSONObject().put("hour",7).put("minute",30)) as ActionPlan.Launch
        assertEquals(AlarmClock.ACTION_SET_ALARM,alarm.intent.action);assertEquals(7,alarm.intent.getIntExtra(AlarmClock.EXTRA_HOUR,-1));assertFalse(alarm.intent.getBooleanExtra(AlarmClock.EXTRA_SKIP_UI,true))
        val timer=plan("prepare_timer",JSONObject().put("seconds",60)) as ActionPlan.Launch
        assertEquals(60,timer.intent.getIntExtra(AlarmClock.EXTRA_LENGTH,-1));assertFalse(timer.intent.getBooleanExtra(AlarmClock.EXTRA_SKIP_UI,true))
    }
    @Test fun malformedAndUnknownActionsHaveNoIntent() {
        for((name,args) in listOf("prepare_alarm" to JSONObject().put("hour",25).put("minute",30),"prepare_timer" to JSONObject().put("seconds",0),"prepare_call" to JSONObject().put("number","*123#"),"delete_files" to JSONObject(),"open_settings" to JSONObject().put("section","root"))) {
            val value=plan(name,args) as ActionPlan.Result;assertEquals("error",value.value.getString("status"))
        }
    }
    @Test fun webIsBrowsableAndRequiresReview() {
        val value=plan("open_web",JSONObject().put("url","https://example.com")) as ActionPlan.Launch
        assertTrue(value.review);assertTrue(value.intent.hasCategory(Intent.CATEGORY_BROWSABLE));assertEquals("https",value.intent.data?.scheme)
    }
    @Test fun mapsEncodeSearchWithoutReadingLocation() {
        val value=plan("open_map",JSONObject().put("query","Museo & café")) as ActionPlan.Launch
        assertEquals("geo:0,0?q=Museo%20%26%20caf%C3%A9",value.intent.dataString)
    }
    @Test fun statusContainsDeviceClockButNoPersonalInventory() {
        val value=(plan("phone_status") as ActionPlan.Result).value
        assertEquals("ok",value.getString("status"));assertTrue(value.has("local_time"));assertTrue(value.has("timezone"));assertFalse(value.has("contacts"));assertFalse(value.has("apps"))
    }
    @Test fun volumeResultReflectsActualAndroidState() {
        val value=(plan("set_volume",JSONObject().put("percent",50).put("stream","media")) as ActionPlan.Result).value
        val manager=RuntimeEnvironment.getApplication().getSystemService(AudioManager::class.java)
        assertEquals("applied",value.getString("status"));assertEquals(manager.getStreamVolume(AudioManager.STREAM_MUSIC),value.getInt("level"))
    }
    @Test fun deniedOrExpiredApprovalCannotExecute() {
        var now=100L;val queue=ApprovalQueue {now}
        val intent=ActionPlan.Launch("Prueba","Revisar",Intent(Intent.ACTION_DIAL))
        val call=ToolCall("id","prepare_call",JSONObject())
        assertTrue(queue.add(call,intent));assertNotNull(queue.cancel("id"));assertNull(queue.take("id"))
        queue.add(call,intent);now+=90000;assertNull(queue.take("id"));assertEquals(1,queue.expired().size);assertNull(queue.current())
    }
    @Test fun approvalsAreBoundedOrderedAndClearedOnStop() {
        val queue=ApprovalQueue {100L};val intent=ActionPlan.Launch("Prueba","Revisar",Intent())
        repeat(4) {assertTrue(queue.add(ToolCall("$it","open_app",JSONObject()),intent))}
        assertFalse(queue.add(ToolCall("0","open_app",JSONObject()),intent));assertFalse(queue.add(ToolCall("fifth","open_app",JSONObject()),intent))
        assertNull(queue.take("2"));assertEquals("0",queue.take("0")?.call?.id);assertEquals(3,queue.clear().size);assertNull(queue.current())
    }
}

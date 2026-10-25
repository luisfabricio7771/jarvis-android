package dev.jarvis.android

import android.content.ComponentName
import android.graphics.Rect
import android.os.Looper
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.Button
import dev.jarvis.android.actions.ToolCall
import dev.jarvis.android.data.SecureSettings
import dev.jarvis.android.screen.ScreenAccessService
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.Config
import org.robolectric.android.controller.ServiceController
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowWindowManagerImpl
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29,35])
class ScreenAccessServiceTest {
    private lateinit var lifecycle: ServiceController<ScreenAccessService>
    private lateinit var service: ScreenAccessService
    private lateinit var root: AccessibilityNodeInfo
    private lateinit var button: AccessibilityNodeInfo
    private lateinit var field: AccessibilityNodeInfo
    private lateinit var settings: SecureSettings
    @Before fun prepare() {
        settings=SecureSettings(RuntimeEnvironment.getApplication()).apply {phoneControlEnabled=true;screenControlEnabled=true;shareScreenLabels=true;confirmScreenTouches=false;screenApps=setOf("test.authorized")}
        lifecycle=Robolectric.buildService(ScreenAccessService::class.java).create();service=lifecycle.get()
        fun node(label: String,clickable: Boolean=false)=AccessibilityNodeInfo.obtain().apply {
            packageName="test.authorized";className="android.widget.Button";text=label;isVisibleToUser=true;isEnabled=true;isClickable=clickable
            setBoundsInScreen(Rect(10,20,300,80));Shadows.shadowOf(this).setRefreshReturnValue(true)
            Shadows.shadowOf(this).setOnPerformActionListener {_,_->true}
        }
        root=node("");root.className="android.widget.LinearLayout";root.setBoundsInScreen(Rect(0,0,1080,1920))
        button=node("Abrir",true);button.viewIdResourceName="test.authorized:id/open"
        field=node("INPUT_CONTENT_MUST_STAY_PRIVATE",true).apply {isEditable=true;hintText="Buscar";inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI;viewIdResourceName="test.authorized:id/search"}
        val password=node("PASSWORD_MUST_STAY_PRIVATE",true).apply {isEditable=true;isPassword=true;hintText="Contraseña"}
        val body=node("NONINTERACTIVE_PRIVATE_MESSAGE")
        Shadows.shadowOf(root).addChild(button);Shadows.shadowOf(root).addChild(field);Shadows.shadowOf(root).addChild(password);Shadows.shadowOf(root).addChild(body)
        val window=AccessibilityWindowInfo.obtain();Shadows.shadowOf(window).setType(AccessibilityWindowInfo.TYPE_APPLICATION)
        Shadows.shadowOf(window).setFocused(true);Shadows.shadowOf(window).setRoot(root);Shadows.shadowOf(service).setWindows(listOf(window))
    }
    @After fun cleanup() {service.cancelAll();lifecycle.destroy()}
    private fun run(name: String,args: JSONObject=JSONObject()): JSONObject? {
        var result: JSONObject?=null;service.execute(ToolCall("test-$name",name,args),{true}) {result=it};return result
    }
    private fun info()=run("screen_info")!!
    private fun id(label: String): String {
        val controls=info().getJSONArray("controls")
        return (0 until controls.length()).map {controls.getJSONObject(it)}.first {it.getString("label")==label}.getString("id")
    }
    @Test fun screenDataRequiresExplicitSharingConsent() {
        settings.shareScreenLabels=false;val value=info();assertEquals("consent_required",value.getString("status"));assertFalse(value.has("controls"))
    }
    @Test fun unselectedAppsAreNotRead() {
        settings.screenApps=emptySet();val value=info();assertEquals("blocked",value.getString("status"));assertFalse(value.has("controls"))
    }
    @Test fun snapshotsExcludePasswordsInputContentsAndNoninteractiveText() {
        val value=info();assertEquals("ok",value.getString("status"));val text=value.toString()
        assertFalse(text.contains("MUST_STAY_PRIVATE"));assertFalse(text.contains("NONINTERACTIVE_PRIVATE_MESSAGE"));assertTrue(text.contains("Buscar"));assertTrue(text.contains("Abrir"))
    }
    @Test fun tapUsesExactSnapshotControlAndRecordsNativeClick() {
        val value=run("screen_tap",JSONObject().put("id",id("Abrir")))!!
        assertEquals("dispatched",value.getString("status"));assertTrue(Shadows.shadowOf(button).performedActions.contains(AccessibilityNodeInfo.ACTION_CLICK))
    }
    @Test fun finalSendButtonsRequireManualIntervention() {
        button.text="Enviar";button.viewIdResourceName="test.authorized:id/send"
        val value=run("screen_tap",JSONObject().put("id",id("Enviar")))!!
        assertEquals("manual_required",value.getString("status"));assertTrue(Shadows.shadowOf(button).performedActions.isEmpty())
    }
    @Test fun movingAControlInvalidatesTheProposedTap() {
        val token=id("Abrir");button.setBoundsInScreen(Rect(20,30,310,90))
        assertEquals("stale",run("screen_tap",JSONObject().put("id",token))!!.getString("status"));assertTrue(Shadows.shadowOf(button).performedActions.isEmpty())
    }
    @Test fun scrollingUsesNativeListActionAndInvalidatesSnapshot() {
        root.isScrollable=true;val snapshot=info().getString("snapshot_id")
        assertEquals("dispatched",run("screen_scroll",JSONObject().put("snapshot_id",snapshot).put("direction","down"))!!.getString("status"))
        assertTrue(Shadows.shadowOf(root).performedActions.contains(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD))
        assertEquals("stale",run("screen_scroll",JSONObject().put("snapshot_id",snapshot).put("direction","down"))!!.getString("status"))
    }
    @Test fun writingWaitsForLocalReviewAndCancellationPreventsIt() {
        val token=id("Buscar");var result: JSONObject?=null
        service.execute(ToolCall("write","screen_type",JSONObject().put("id",token).put("text","Consulta del usuario")),{true}) {result=it}
        assertFalse(Shadows.shadowOf(field).performedActions.contains(AccessibilityNodeInfo.ACTION_SET_TEXT))
        service.cancel("write");assertEquals("cancelled",result!!.getString("status"));assertTrue(Shadows.shadowOf(field).performedActions.isEmpty())
    }
    @Test fun approvedWritingPerformsSetTextWithTheReviewedValue() {
        val token=id("Buscar");var result: JSONObject?=null
        service.execute(ToolCall("write-approved","screen_type",JSONObject().put("id",token).put("text","Consulta del usuario")),{true}) {result=it}
        val wm=Shadow.extract<ShadowWindowManagerImpl>(service.getSystemService(WindowManager::class.java))
        fun find(view: View): Button? {
            if(view is Button && view.text.toString()=="Continuar") return view
            if(view is ViewGroup) for(i in 0 until view.childCount) find(view.getChildAt(i))?.let {return it}
            return null
        }
        val approve=wm.views.mapNotNull {find(it)}.firstOrNull();assertNotNull("No review overlay",approve);approve!!.performClick()
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(200,TimeUnit.MILLISECONDS)
        assertEquals("dispatched",result!!.getString("status"))
        val performed=Shadows.shadowOf(field).performedActionsWithArgs.last()
        assertEquals(AccessibilityNodeInfo.ACTION_SET_TEXT,performed.first.toInt())
        assertEquals("Consulta del usuario",performed.second.getCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE).toString())
    }
    @Test fun manualScrollingInvalidatesPreviouslyObservedControls() {
        val token=id("Abrir")
        val event=android.view.accessibility.AccessibilityEvent.obtain(android.view.accessibility.AccessibilityEvent.TYPE_VIEW_SCROLLED).apply {packageName="test.authorized"}
        service.onAccessibilityEvent(event)
        assertEquals("stale",run("screen_tap",JSONObject().put("id",token))!!.getString("status"));assertTrue(Shadows.shadowOf(button).performedActions.isEmpty())
    }
    @Test fun accessibilityServiceRequiresSystemBindingPermission() {
        val info=service.packageManager.getServiceInfo(ComponentName(service,ScreenAccessService::class.java),0)
        assertEquals("android.permission.BIND_ACCESSIBILITY_SERVICE",info.permission)
    }
}

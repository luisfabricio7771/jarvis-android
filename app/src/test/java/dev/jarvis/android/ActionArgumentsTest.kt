package dev.jarvis.android
import dev.jarvis.android.actions.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
class ActionArgumentsTest {
    @Test fun phoneNumbersAreCanonicalizedWithoutChangingDigits() {assertEquals("+525512345678",ActionArguments.phone("+52 (55) 1234-5678"))}
    @Test fun ussdExtensionsAndIntentInjectionAreRejected() {
        for(value in listOf("*123#","555;123","tel:5551234","555,111","++521234567890","555%2312","call mom")) {
            try {ActionArguments.phone(value);fail(value)} catch(_:IllegalArgumentException) {}
        }
    }
    @Test fun onlyCredentialFreeHttpsUrlsAreAccepted() {
        assertEquals("https://example.com/path?q=hola",ActionArguments.https("https://example.com/path?q=hola"))
        for(value in listOf("http://example.com","intent://example.com","file:///sdcard/test","https://user:pass@example.com","https://example.com:8443","https://localhost/test")) {
            try {ActionArguments.https(value);fail(value)} catch(_:IllegalArgumentException) {}
        }
    }
    @Test fun integersAreStrictAndBounded() {
        assertEquals(23,ActionArguments(JSONObject().put("hour",23),setOf("hour")).integer("hour",0..23))
        for(value in listOf<Any>(24,-1,12.5,"12",Long.MAX_VALUE)) {
            try {ActionArguments(JSONObject().put("hour",value),setOf("hour")).integer("hour",0..23);fail(value.toString())} catch(_:IllegalArgumentException) {}
        }
    }
    @Test(expected=IllegalArgumentException::class) fun unknownArgumentsAreRejected() {ActionArguments(JSONObject().put("shell","rm"),setOf("app"))}
    @Test fun messagesKeepNewlinesButRejectControlCharacters() {
        assertEquals("Hola\nHasta mañana",ActionArguments(JSONObject().put("body","Hola\nHasta mañana"),setOf("body")).text("body",3000))
        try {ActionArguments(JSONObject().put("body","hola\u0000"),setOf("body")).text("body",3000);fail()} catch(_:IllegalArgumentException) {}
    }
    @Test fun ambiguousAppSearchDoesNotChooseArbitrarily() {
        val apps=listOf(LaunchableApp("Banco Uno","bank.one"),LaunchableApp("Banco Dos","bank.two"))
        assertEquals(2,AndroidActions.findApps(apps,"banco").size)
        assertEquals("bank.two",AndroidActions.findApps(apps,"Banco Dos").single().packageName)
        assertTrue(AndroidActions.findApps(apps,"a").isEmpty())
    }
    @Test fun aliasesPreferExactAppNames() {
        val apps=listOf(LaunchableApp("WhatsApp","com.whatsapp"),LaunchableApp("WhatsApp Business","com.whatsapp.w4b"))
        assertEquals("com.whatsapp",AndroidActions.findApps(apps,"wasap").single().packageName)
    }
}

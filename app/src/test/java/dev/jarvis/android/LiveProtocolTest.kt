package dev.jarvis.android
import dev.jarvis.android.live.LiveProtocol
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.Base64

class LiveProtocolTest {
    @Test fun defaultVoiceAndSupportedModelSettings() {
        val json = JSONObject(LiveProtocol.setup()).getJSONObject("setup")
        assertEquals("models/gemini-3.8-live", json.getString("model"))
        assertEquals("AUDIO", json.getJSONObject("generationConfig").getJSONArray("responseModalities").getString(0))
        for (field in listOf("speechConfig", "thinkingConfig", "enableAffectiveDialog", "proactivity")) {
            assertFalse(json.has(field)); assertFalse(json.getJSONObject("generationConfig").has(field))
        }
        assertFalse(json.has("tools"))
    }
    @Test fun toolsAreExplicitlyEnabledAndUseBlockingResults() {
        val declarations=JSONObject(LiveProtocol.setup(true)).getJSONObject("setup").getJSONArray("tools").getJSONObject(0).getJSONArray("functionDeclarations")
        assertEquals(16,declarations.length())
        for(i in 0 until declarations.length()) {
            val tool=declarations.getJSONObject(i)
            assertEquals("BLOCKING",tool.getString("behavior"))
            val parameters=tool.getJSONObject("parameters")
            for(j in 0 until parameters.getJSONArray("required").length()) assertTrue(parameters.getJSONObject("properties").has(parameters.getJSONArray("required").getString(j)))
        }
    }
    @Test fun conservativeSpeechDetectionAndNaturalTurns() {
        val json = JSONObject(LiveProtocol.setup()).getJSONObject("setup").getJSONObject("realtimeInputConfig")
        assertEquals("START_OF_ACTIVITY_INTERRUPTS",json.getString("activityHandling"))
        val detection=json.getJSONObject("automaticActivityDetection")
        assertFalse(detection.getBoolean("disabled"))
        assertEquals("START_SENSITIVITY_LOW",detection.getString("startOfSpeechSensitivity"))
        assertEquals(600,detection.getInt("silenceDurationMs"))
    }
    @Test fun rawAudioRoundTripsWithoutChangingBytes() {
        val bytes=byteArrayOf(0,0,127,1,-1,-1)
        val audio=JSONObject(LiveProtocol.audio(bytes)).getJSONObject("realtimeInput").getJSONObject("audio")
        assertEquals("audio/pcm;rate=16000",audio.getString("mimeType"))
        assertArrayEquals(bytes,Base64.getDecoder().decode(audio.getString("data")))
    }
    @Test(expected=IllegalArgumentException::class) fun oddByteCountIsRejected() { LiveProtocol.audio(byteArrayOf(1)) }
    @Test fun audioEndUsesRealtimeStreamEnd() { assertTrue(JSONObject(LiveProtocol.endAudio()).getJSONObject("realtimeInput").getBoolean("audioStreamEnd")) }
}

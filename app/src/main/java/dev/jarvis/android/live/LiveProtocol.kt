package dev.jarvis.android.live

import org.json.JSONArray
import org.json.JSONObject
import java.util.Base64

object LiveProtocol {
    const val MODEL = "models/gemini-3.8-live"
    const val ENDPOINT = "https://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent"
    fun setup(): String = JSONObject().put("setup", JSONObject()
        .put("model", MODEL)
        .put("generationConfig", JSONObject().put("responseModalities", JSONArray().put("AUDIO")))
        .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text",
            "Eres JARVIS, el asistente personal del usuario. Habla en español natural, de manera breve y amable. " +
            "Puedes conversar mientras el usuario interviene. Reconoce si te falta información. " +
            "Esta versión Android solo conversa: no controla la PC, otras aplicaciones ni dispositivos. " +
            "No afirmes haber realizado acciones para las que no tienes herramientas. " +
            "No prometas recordar cosas fuera de esta sesión: el historial local no se envía automáticamente."))))
        .put("inputAudioTranscription", JSONObject())
        .put("outputAudioTranscription", JSONObject())
        .put("realtimeInputConfig", JSONObject()
            .put("activityHandling", "START_OF_ACTIVITY_INTERRUPTS")
            .put("automaticActivityDetection", JSONObject().put("disabled", false)
                .put("startOfSpeechSensitivity", "START_SENSITIVITY_LOW")
                .put("endOfSpeechSensitivity", "END_SENSITIVITY_LOW")
                .put("prefixPaddingMs", 240).put("silenceDurationMs", 600)))) .toString()

    fun audio(pcm: ByteArray): String {
        require(pcm.size % 2 == 0) { "PCM must be 16-bit mono" }
        return JSONObject().put("realtimeInput", JSONObject().put("audio", JSONObject()
            .put("mimeType", "audio/pcm;rate=16000")
            .put("data", Base64.getEncoder().encodeToString(pcm)))).toString()
    }
    fun endAudio(): String = "{\"realtimeInput\":{\"audioStreamEnd\":true}}"
    fun greeting(): String = JSONObject().put("realtimeInput", JSONObject().put("text",
        "El usuario acaba de activar JARVIS. Saluda brevemente con: Aquí estoy, ¿en qué te ayudo?" )).toString()
}

package dev.jarvis.android
import dev.jarvis.android.live.GeminiLiveClient
import okhttp3.*
import okhttp3.mockwebserver.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.*

class GeminiLiveClientTest {
    private class Events : GeminiLiveClient.Listener {
        val ready=CountDownLatch(1); val turn=CountDownLatch(1); val stopped=CountDownLatch(1)
        val errors=LinkedBlockingQueue<String>(); val audios=LinkedBlockingQueue<ByteArray>(); val texts=LinkedBlockingQueue<String>()
        override fun onReady() { ready.countDown() }
        override fun onAudio(pcm: ByteArray) { audios.offer(pcm) }
        override fun onTranscript(role: String,text: String) { texts.offer("$role:$text") }
        override fun onInterrupted() { stopped.countDown() }
        override fun onTurnComplete() { turn.countDown() }
        override fun onError(message: String) { errors.offer(message) }
    }
    @Test fun audioWaitsForSetupAndInterruptionReachesPlayer() {
        val server=MockWebServer();val incoming=LinkedBlockingQueue<String>();val connected=LinkedBlockingQueue<WebSocket>()
        server.enqueue(MockResponse().withWebSocketUpgrade(object: WebSocketListener() {
            override fun onOpen(webSocket: WebSocket,response: Response) { connected.offer(webSocket) }
            override fun onMessage(webSocket: WebSocket,text: String) { incoming.offer(text) }
        }));server.start()
        val events=Events(); val client=GeminiLiveClient(events,endpoint=server.url("/live"))
        try {
            assertFalse(client.sendAudio(ByteArray(1024)));client.connect("test-key-never-real")
            val ws=connected.poll(5,TimeUnit.SECONDS)!!
            assertTrue(JSONObject(incoming.poll(5,TimeUnit.SECONDS)!!).has("setup"))
            assertFalse(client.sendAudio(ByteArray(1024)))
            ws.send("{\"setupComplete\":{}}")
            assertTrue(events.ready.await(5,TimeUnit.SECONDS));assertTrue(client.sendAudio(byteArrayOf(1,0)))
            assertTrue(JSONObject(incoming.poll(5,TimeUnit.SECONDS)!!).has("realtimeInput"))
            ws.send("{\"serverContent\":{\"interrupted\":true,\"inputTranscription\":{\"text\":\"Hola\"},\"modelTurn\":{\"parts\":[{\"inlineData\":{\"mimeType\":\"audio/pcm;rate=24000\",\"data\":\"AQACAA==\"}}]},\"turnComplete\":true}}")
            assertTrue(events.stopped.await(5,TimeUnit.SECONDS));assertTrue(events.turn.await(5,TimeUnit.SECONDS))
            assertEquals("user:Hola",events.texts.poll(5,TimeUnit.SECONDS));assertArrayEquals(byteArrayOf(1,0,2,0),events.audios.poll(5,TimeUnit.SECONDS))
            client.close();assertFalse(client.sendAudio(ByteArray(1024)))
        } finally { client.close(); server.shutdown() }
    }
    @Test fun remoteErrorsNeverExposeProviderPayloadOrApiKey() {
        val server=MockWebServer();server.enqueue(MockResponse().withWebSocketUpgrade(object: WebSocketListener() {
            override fun onMessage(webSocket: WebSocket,text: String) { webSocket.send("{\"error\":{\"code\":429,\"message\":\"leaked secret test-key-never-real\"}}") }
        }));server.start()
        val events=Events();val client=GeminiLiveClient(events,endpoint=server.url("/live"))
        try { client.connect("test-key-never-real");val error=events.errors.poll(5,TimeUnit.SECONDS)!!;assertTrue(error.contains("cuota"));assertFalse(error.contains("test-key"));assertFalse(client.sendAudio(ByteArray(2))) }
        finally { client.close();server.shutdown() }
    }
    @Test fun malformedMessagesFailClosed() {
        val server=MockWebServer();server.enqueue(MockResponse().withWebSocketUpgrade(object: WebSocketListener() {
            override fun onMessage(webSocket: WebSocket,text: String) { webSocket.send("not json") }
        }));server.start();val events=Events();val client=GeminiLiveClient(events,endpoint=server.url("/live"))
        try {client.connect("test-key-never-real");assertNotNull(events.errors.poll(5,TimeUnit.SECONDS));assertFalse(client.sendAudio(ByteArray(2))) }
        finally { client.close();server.shutdown() }
    }
    @Test fun goAwayWithoutServerContentIsHandled() {
        val server=MockWebServer();server.enqueue(MockResponse().withWebSocketUpgrade(object: WebSocketListener() {
            override fun onMessage(webSocket: WebSocket,text: String) { webSocket.send("{\"goAway\":{\"timeLeft\":\"10s\"}}") }
        }));server.start();val events=Events();val client=GeminiLiveClient(events,endpoint=server.url("/live"))
        try { client.connect("test-key-never-real");assertNotNull(events.errors.poll(5,TimeUnit.SECONDS)) } finally {client.close();server.shutdown()}
    }
}

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
        val calls=LinkedBlockingQueue<dev.jarvis.android.actions.ToolCall>();val cancelled=LinkedBlockingQueue<String>()
        val errors=LinkedBlockingQueue<String>(); val audios=LinkedBlockingQueue<ByteArray>(); val texts=LinkedBlockingQueue<String>()
        override fun onReady() { ready.countDown() }
        override fun onAudio(pcm: ByteArray) { audios.offer(pcm) }
        override fun onTranscript(role: String,text: String) { texts.offer("$role:$text") }
        override fun onInterrupted() { stopped.countDown() }
        override fun onTurnComplete() { turn.countDown() }
        override fun onError(message: String) { errors.offer(message) }
        override fun onToolCall(call: dev.jarvis.android.actions.ToolCall) {calls.offer(call)}
        override fun onToolCancelled(id: String) {cancelled.offer(id)}
    }
    @Test fun audioWaitsForSetupAndInterruptionReachesPlayer() {
        val server=MockWebServer();val incoming=LinkedBlockingQueue<String>();val connected=LinkedBlockingQueue<WebSocket>()
        server.enqueue(MockResponse().withWebSocketUpgrade(object: WebSocketListener() {
            override fun onClosing(webSocket: WebSocket,code: Int,reason: String) {webSocket.close(code,reason)}
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
            override fun onClosing(webSocket: WebSocket,code: Int,reason: String) {webSocket.close(code,reason)}
            override fun onMessage(webSocket: WebSocket,text: String) { webSocket.send("{\"error\":{\"code\":429,\"message\":\"leaked secret test-key-never-real\"}}") }
        }));server.start()
        val events=Events();val client=GeminiLiveClient(events,endpoint=server.url("/live"))
        try { client.connect("test-key-never-real");val error=events.errors.poll(5,TimeUnit.SECONDS)!!;assertTrue(error.contains("cuota"));assertFalse(error.contains("test-key"));assertFalse(client.sendAudio(ByteArray(2))) }
        finally { client.close();server.shutdown() }
    }
    @Test fun malformedMessagesFailClosed() {
        val server=MockWebServer();server.enqueue(MockResponse().withWebSocketUpgrade(object: WebSocketListener() {
            override fun onClosing(webSocket: WebSocket,code: Int,reason: String) {webSocket.close(code,reason)}
            override fun onMessage(webSocket: WebSocket,text: String) { webSocket.send("not json") }
        }));server.start();val events=Events();val client=GeminiLiveClient(events,endpoint=server.url("/live"))
        try {client.connect("test-key-never-real");assertNotNull(events.errors.poll(5,TimeUnit.SECONDS));assertFalse(client.sendAudio(ByteArray(2))) }
        finally { client.close();server.shutdown() }
    }
    @Test fun goAwayWithoutServerContentIsHandled() {
        val server=MockWebServer();server.enqueue(MockResponse().withWebSocketUpgrade(object: WebSocketListener() {
            override fun onClosing(webSocket: WebSocket,code: Int,reason: String) {webSocket.close(code,reason)}
            override fun onMessage(webSocket: WebSocket,text: String) { webSocket.send("{\"goAway\":{\"timeLeft\":\"10s\"}}") }
        }));server.start();val events=Events();val client=GeminiLiveClient(events,endpoint=server.url("/live"))
        try { client.connect("test-key-never-real");assertNotNull(events.errors.poll(5,TimeUnit.SECONDS)) } finally {client.close();server.shutdown()}
    }

    @Test fun toolResultsKeepTheirIdsAndCancellationPreventsExecution() {
        val server=MockWebServer();val incoming=LinkedBlockingQueue<String>();val connected=LinkedBlockingQueue<WebSocket>()
        server.enqueue(MockResponse().withWebSocketUpgrade(object: WebSocketListener() {
            override fun onClosing(webSocket: WebSocket,code: Int,reason: String) {webSocket.close(code,reason)}
            override fun onOpen(webSocket: WebSocket,response: Response) {connected.offer(webSocket)}
            override fun onMessage(webSocket: WebSocket,text: String) {incoming.offer(text)}
        }));server.start();val events=Events();val client=GeminiLiveClient(events,endpoint=server.url("/live"),toolsEnabled=true)
        try {
            client.connect("test-key-never-real");val ws=connected.poll(5,TimeUnit.SECONDS)!!
            val setup=JSONObject(incoming.poll(5,TimeUnit.SECONDS)!!).getJSONObject("setup");assertTrue(setup.has("tools"))
            ws.send("{\"setupComplete\":{}}");assertTrue(events.ready.await(5,TimeUnit.SECONDS))
            val request="{\"toolCall\":{\"functionCalls\":[{\"id\":\"a\",\"name\":\"phone_status\",\"args\":{}},{\"id\":\"b\",\"name\":\"open_app\",\"args\":{\"app\":\"YouTube\"}}]}}"
            ws.send(request);val a=events.calls.poll(5,TimeUnit.SECONDS)!!;val b=events.calls.poll(5,TimeUnit.SECONDS)!!
            assertEquals("a",a.id);assertEquals("b",b.id);assertTrue(client.respondTool(a,JSONObject().put("status","ok")))
            val result=JSONObject(incoming.poll(5,TimeUnit.SECONDS)!!).getJSONObject("toolResponse").getJSONArray("functionResponses").getJSONObject(0)
            assertEquals("a",result.getString("id"));assertEquals("phone_status",result.getString("name"));assertFalse(client.respondTool(a,JSONObject()))
            ws.send("{\"toolCallCancellation\":{\"ids\":[\"b\"]}}")
            assertEquals("b",events.cancelled.poll(5,TimeUnit.SECONDS));assertFalse(client.isToolPending("b"));assertFalse(client.respondTool(b,JSONObject()))
            ws.send(request);assertNull(events.calls.poll(200,TimeUnit.MILLISECONDS))
        } finally {client.close();server.shutdown()}
    }
    @Test fun disabledToolsRejectUnexpectedRemoteCalls() {
        val server=MockWebServer();val responses=LinkedBlockingQueue<String>()
        server.enqueue(MockResponse().withWebSocketUpgrade(object: WebSocketListener() {
            override fun onClosing(webSocket: WebSocket,code: Int,reason: String) {webSocket.close(code,reason)}
            override fun onMessage(webSocket: WebSocket,text: String) {
                if(JSONObject(text).has("setup")) {webSocket.send("{\"setupComplete\":{}}");webSocket.send("{\"toolCall\":{\"functionCalls\":[{\"id\":\"x\",\"name\":\"open_app\",\"args\":{\"app\":\"YouTube\"}}]}}")}
                else responses.offer(text)
            }
        }));server.start();val events=Events();val client=GeminiLiveClient(events,endpoint=server.url("/live"))
        try {
            client.connect("test-key-never-real")
            val value=JSONObject(responses.poll(5,TimeUnit.SECONDS)!!).getJSONObject("toolResponse").getJSONArray("functionResponses").getJSONObject(0)
            assertEquals("disabled",value.getJSONObject("response").getString("status"));assertTrue(events.calls.isEmpty())
        } finally {client.close();server.shutdown()}
    }

}

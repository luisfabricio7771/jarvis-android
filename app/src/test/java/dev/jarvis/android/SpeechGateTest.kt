package dev.jarvis.android
import dev.jarvis.android.audio.SpeechGate
import org.junit.Assert.*
import org.junit.Test

class SpeechGateTest {
    private val voice=ByteArray(1024) { 10 }
    @Test fun silenceAndSustainedLoudNoiseStayClosed() {
        val gate=SpeechGate()
        repeat(100) { assertTrue(gate.process(voice,.1f,.7f).pcm.all { it==0.toByte() }) }
    }
    @Test fun oneLoudClickNeverOpensGate() {
        val gate=SpeechGate()
        repeat(4) { gate.process(voice,.05f,.01f) }
        assertFalse(gate.process(voice,.99f,.9f).speech)
        repeat(20) { assertFalse(gate.process(voice,.05f,.01f).speech) }
    }
    @Test fun sustainedVoiceIncludesPreroll() {
        val gate=SpeechGate()
        repeat(3) { assertFalse(gate.process(voice,.9f,.05f).speech) }
        val output=gate.process(voice,.9f,.05f)
        assertTrue(output.speech); assertEquals(4096,output.pcm.size)
    }
    @Test fun gateClosesAfterSilenceThenRequiresFreshVoice() {
        val gate=SpeechGate()
        repeat(4) { gate.process(voice,.9f,.05f) }
        repeat(12) { gate.process(voice,.05f,.0f) }
        assertFalse(gate.process(voice,.9f,.05f).speech)
    }
    @Test fun nearSilentClassifierFalsePositiveIsRejected() {
        val gate=SpeechGate(); repeat(20) { assertFalse(gate.process(voice,.99f,.00001f).speech) }
    }
    @Test(expected=IllegalArgumentException::class) fun partialFramesAreRejected() { SpeechGate().process(ByteArray(4),.9f,.5f) }
}

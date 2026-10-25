package dev.jarvis.android
import dev.jarvis.android.wake.WakePhrase
import org.junit.Assert.*
import org.junit.Test
class WakePhraseTest {
    @Test fun matchesWholeWakeWordWithPunctuation() { for(value in listOf("JARVIS","hola, Jarvis.","Oye Járvis")) assertTrue(value,WakePhrase.matches(value)) }
    @Test fun unrelatedWordsAndSubstringsDoNotActivate() { for(value in listOf("hola","llaves","jarvista","superjarvis","", "[unk]")) assertFalse(value,WakePhrase.matches(value)) }
}

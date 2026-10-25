package dev.jarvis.android.wake

import java.text.Normalizer
import java.util.Locale

object WakePhrase {
    // Competing phrases prevent a constrained recognizer from forcing similar sounds into Jarvis.
    const val GRAMMAR = "[\"jarvis\",\"hola jarvis\",\"oye jarvis\",\"ya vi\",\"ya ves\",\"llaves\",\"javier\",\"jueves\",\"archivos\",\"[unk]\"]"
    fun matches(text: String): Boolean {
        val normalized = Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
        return Regex("\\b(?:jarvis|yarvis)\\b").containsMatchIn(normalized)
    }
}

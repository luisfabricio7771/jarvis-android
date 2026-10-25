package dev.jarvis.android.audio

/** Gate decisions use a speech classifier, duration and energy together. Frame: 32 ms. */
class SpeechGate {
    data class Output(val pcm: ByteArray, val speech: Boolean)
    private val prefix = ArrayDeque<ByteArray>()
    private val votes = ArrayDeque<Boolean>()
    private var open = false
    private var quiet = 0
    fun process(pcm: ByteArray, probability: Float, rms: Float): Output {
        require(pcm.size == 1024)
        val voice = probability >= .70f && rms >= .002f
        if (!open) {
            prefix.addLast(pcm.copyOf()); if (prefix.size > 9) prefix.removeFirst()
            votes.addLast(voice); if (votes.size > 6) votes.removeFirst()
            if (voice && votes.count { it } >= 4) {
                open = true; quiet = 0
                val result = ByteArray(prefix.size * 1024)
                prefix.forEachIndexed { i, bytes -> bytes.copyInto(result, i * 1024) }
                prefix.clear()
                return Output(result, true)
            }
            return Output(ByteArray(1024), false)
        }
        val continuing = probability >= .35f && rms >= .002f
        quiet = if (continuing) 0 else quiet + 1
        if (quiet >= 12) { open = false; votes.clear(); prefix.clear(); return Output(ByteArray(1024), false) }
        return Output(pcm, continuing)
    }
}

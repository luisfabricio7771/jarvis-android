package dev.jarvis.android.audio

import android.content.Context
import ai.onnxruntime.*
import java.nio.FloatBuffer
import java.nio.LongBuffer

class SileroVad(context: Context) : AutoCloseable {
    private val env = OrtEnvironment.getEnvironment()
    private val session = OrtSession.SessionOptions().use { options ->
        options.setIntraOpNumThreads(1); options.setInterOpNumThreads(1)
        env.createSession(context.assets.open("silero_vad.onnx").use { it.readBytes() }, options)
    }
    private var state = FloatArray(256)
    private val previous = FloatArray(64)
    fun probability(samples: ShortArray): Float {
        require(samples.size == 512)
        val input = FloatArray(576)
        previous.copyInto(input)
        for (i in samples.indices) input[i + 64] = samples[i] / 32768f
        for (i in previous.indices) previous[i] = input[512 + i]
        OnnxTensor.createTensor(env, FloatBuffer.wrap(input), longArrayOf(1, 576)).use { audio ->
            OnnxTensor.createTensor(env, FloatBuffer.wrap(state), longArrayOf(2, 1, 128)).use { memory ->
                OnnxTensor.createTensor(env, LongBuffer.wrap(longArrayOf(16000)), longArrayOf()).use { rate ->
                    session.run(mapOf("input" to audio, "state" to memory, "sr" to rate)).use { out ->
                        val score = (out[0] as OnnxTensor).floatBuffer.get(0)
                        val next = (out[1] as OnnxTensor).floatBuffer
                        state = FloatArray(256).also { next.get(it) }
                        return score
                    }
                }
            }
        }
    }
    override fun close() = session.close()
}

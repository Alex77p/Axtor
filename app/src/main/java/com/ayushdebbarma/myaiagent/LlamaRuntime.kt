package com.ayushdebbarma.myaiagent

import android.content.Context
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import dev.ffmpegkit.llama.Llama
import dev.ffmpegkit.llama.LlamaConfig

/**
 * Real local GGUF inference bridge.
 *
 * Models are copied into app-private storage and are never sent to a server.
 * The runtime is llama.cpp based and executes on-device.
 */
object LlamaRuntime {
    interface Callback {
        fun onSuccess(text: String, tokensPerSecond: Double)
        fun onError(message: String)
    }

    fun generate(context: Context, modelPath: String, prompt: String, systemPrompt: String, maxTokens: Int, callback: Callback) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val file = File(modelPath)
                require(file.exists()) { "Model file does not exist: $modelPath" }
                require(file.length() > 0) { "Model file is empty" }
                val model = Llama.loadModel(
                    modelPath = file.absolutePath,
                    config = LlamaConfig(
                        contextSize = 4096,
                        threads = maxOf(2, Runtime.getRuntime().availableProcessors().coerceAtMost(8))
                    )
                )
                try {
                    val result = Llama.complete(
                        model,
                        prompt = prompt,
                        systemPrompt = systemPrompt,
                        maxTokens = maxTokens
                    )
                    callback.onSuccess(result.text, result.tokensPerSecond)
                } finally {
                    Llama.releaseModel(model)
                }
            } catch (t: Throwable) {
                callback.onError(t.message ?: t.javaClass.simpleName)
            }
        }
    }

    fun copyModel(context: Context, input: InputStream, displayName: String): File {
        val dir = File(context.filesDir, "models")
        if (!dir.exists()) dir.mkdirs()
        val safe = displayName.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val target = File(dir, if (safe.endsWith(".gguf", true)) safe else "$safe.gguf")
        input.use { src -> target.outputStream().use { dst -> src.copyTo(dst, 1024 * 1024) } }
        return target
    }

    fun isGguf(file: File): Boolean {
        if (!file.exists() || file.length() < 4) return false
        FileInputStream(file).use { input ->
            val magic = ByteArray(4)
            if (input.read(magic) != 4) return false
            return magic[0] == 'G'.code.toByte() && magic[1] == 'G'.code.toByte() &&
                    magic[2] == 'U'.code.toByte() && magic[3] == 'F'.code.toByte()
        }
    }
}

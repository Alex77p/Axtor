package com.ayushdebbarma.myaiagent

import android.content.Context
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import dev.ffmpegkit.llama.Llama
import dev.ffmpegkit.llama.LlamaConfig

/**
 * Real local GGUF inference bridge.
 *
 * Models are copied into app-private storage and are never sent to a server.
 * The runtime is llama.cpp based and executes on-device.
 */
object LlamaRuntime {
    // Keep the active model resident while the app process is alive. Reloading a
    // multi-GB GGUF file for every message is a major source of perceived latency.
    private val modelLock = Mutex()
    private var cachedPath: String? = null
    private var cachedModel: dev.ffmpegkit.llama.LlamaModel? = null

    interface Callback {
        fun onSuccess(text: String, tokensPerSecond: Double)
        fun onError(message: String)
    }

    @JvmStatic
    fun generate(context: Context, modelPath: String, prompt: String, systemPrompt: String, maxTokens: Int, callback: Callback) {
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val file = File(modelPath)
                require(file.exists()) { "Model file does not exist: $modelPath" }
                require(file.length() > 0) { "Model file is empty" }

                modelLock.withLock {
                    // Reuse the loaded native model for subsequent requests.
                    // A single LlamaModel is not thread-safe, so requests are serialized.
                    val model = if (cachedModel?.isLoaded == true && cachedPath == file.absolutePath) {
                        cachedModel!!
                    } else {
                        cachedModel?.let { if (it.isLoaded) Llama.releaseModel(it) }
                        val threads = Runtime.getRuntime().availableProcessors().coerceIn(2, 4)
                        Llama.loadModel(
                            modelPath = file.absolutePath,
                            config = LlamaConfig(
                                contextSize = 1024,
                                threads = threads,
                                temperature = 0.2f,
                                topP = 0.9f,
                                topK = 20
                            )
                        ).also {
                            cachedModel = it
                            cachedPath = file.absolutePath
                        }
                    }

                    val result = Llama.complete(
                        model,
                        prompt = prompt,
                        systemPrompt = systemPrompt,
                        maxTokens = maxTokens.coerceIn(16, 192)
                    )
                    callback.onSuccess(result.text.trim(), result.tokensPerSecond.toDouble())
                }
            } catch (t: Throwable) {
                callback.onError(t.message ?: t.javaClass.simpleName)
            }
        }
    }

    @JvmStatic
    fun releaseCachedModel() {
        kotlinx.coroutines.runBlocking {
            modelLock.withLock {
                cachedModel?.let { if (it.isLoaded) Llama.releaseModel(it) }
                cachedModel = null
                cachedPath = null
            }
        }
    }

    @JvmStatic
    fun copyModel(context: Context, input: InputStream, displayName: String): File {
        val dir = File(context.filesDir, "models")
        if (!dir.exists()) dir.mkdirs()
        val safe = displayName.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val target = File(dir, if (safe.endsWith(".gguf", true)) safe else "$safe.gguf")
        input.use { src -> target.outputStream().use { dst -> src.copyTo(dst, 1024 * 1024) } }
        return target
    }

    @JvmStatic
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

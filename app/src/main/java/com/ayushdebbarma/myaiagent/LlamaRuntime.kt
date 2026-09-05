package com.ayushdebbarma.myaiagent

import android.app.ActivityManager
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

/** Real local GGUF inference bridge with bounded memory usage and diagnostics. */
object LlamaRuntime {
    private val modelLock = Mutex()
    private var cachedPath: String? = null
    private var cachedModel: dev.ffmpegkit.llama.LlamaModel? = null

    interface Callback { fun onSuccess(text: String, tokensPerSecond: Double); fun onError(message: String) }

    @JvmStatic
    fun generate(context: Context, modelPath: String, prompt: String, systemPrompt: String, maxTokens: Int, callback: Callback) {
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val file = File(modelPath)
                require(file.isFile) { "Model file does not exist: $modelPath" }
                require(isGguf(file)) { "Selected model is not a valid GGUF file." }
                require(file.length() > 4096) { "Selected GGUF model is empty or truncated." }
                val app = context.applicationContext
                modelLock.withLock {
                    val model = if (cachedModel?.isLoaded == true && cachedPath == file.absolutePath) cachedModel!! else {
                        cachedModel?.let { if (it.isLoaded) Llama.releaseModel(it) }
                        cachedModel = null
                        cachedPath = null
                        val threads = Runtime.getRuntime().availableProcessors().coerceIn(2, 4)
                        Llama.loadModel(modelPath=file.absolutePath, config=LlamaConfig(contextSize=1024, threads=threads, temperature=0.2f, topP=0.9f, topK=20)).also { cachedModel=it; cachedPath=file.absolutePath }
                    }
                    val result=Llama.complete(model,prompt=prompt.takeLast(24000),systemPrompt=systemPrompt.take(8000),maxTokens=maxTokens.coerceIn(16,192))
                    callback.onSuccess(result.text.trim(),result.tokensPerSecond.toDouble())
                }
            } catch (t: Throwable) {
                if (t is OutOfMemoryError) {
                    try { releaseCachedModel() } catch (_: Throwable) {}
                    callback.onError("The model needs more memory. Try a smaller quantized GGUF model.")
                } else callback.onError(t.message ?: t.javaClass.simpleName)
            }
        }
    }

    @JvmStatic fun releaseCachedModel() {
        kotlinx.coroutines.runBlocking { modelLock.withLock { cachedModel?.let { if (it.isLoaded) Llama.releaseModel(it) }; cachedModel=null; cachedPath=null } }
    }

    @JvmStatic fun isModelLoaded(): Boolean = cachedModel?.isLoaded == true

    @JvmStatic
    fun modelReadiness(context: Context, modelPath: String): String {
        val file = File(modelPath)
        if (!file.isFile) return "missing"
        if (!isGguf(file)) return "invalid-gguf"
        if (file.length() <= 4096) return "truncated"
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val info = ActivityManager.MemoryInfo()
        am?.getMemoryInfo(info)
        return if (info.lowMemory) "valid-but-low-memory" else "ready"
    }

    @JvmStatic
    fun copyModel(context: Context,input: InputStream,displayName: String): File {
        val dir=File(context.filesDir,"models"); if(!dir.exists())dir.mkdirs()
        val safe=displayName.replace(Regex("[^A-Za-z0-9._-]"),"_")
        val target=File(dir,if(safe.endsWith(".gguf",true))safe else "$safe.gguf")
        input.use { src -> target.outputStream().use { dst -> src.copyTo(dst,1024*1024) } }
        require(isGguf(target)) { target.delete(); "Imported file is not a valid GGUF model." }
        require(target.length() > 4096) { target.delete(); "Imported GGUF model is empty or truncated." }
        return target
    }

    @JvmStatic fun isGguf(file: File): Boolean {
        if(!file.isFile||file.length()<4)return false
        FileInputStream(file).use { input -> val magic=ByteArray(4); if(input.read(magic)!=4)return false; return magic[0]=='G'.code.toByte()&&magic[1]=='G'.code.toByte()&&magic[2]=='U'.code.toByte()&&magic[3]=='F'.code.toByte() }
    }
}

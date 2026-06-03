package com.movieenglish.assistant.llama

import android.content.Context

class LlamaEngine(context: Context) {

    private var nativePtr: Long = 0
    private val modelPath: String

    init {
        System.loadLibrary("llama-bridge")

        val modelFile = context.getFileStreamPath("qwen2.5-1.5b-q4_k_m.gguf")
        if (!modelFile.exists()) {
            context.assets.open("models/qwen2.5-1.5b-q4_k_m.gguf").use { input ->
                modelFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }
        modelPath = modelFile.absolutePath
    }

    fun init(nThreads: Int = 4) {
        nativePtr = nativeInit(modelPath, nThreads)
        if (nativePtr == 0L) throw RuntimeException("Failed to initialize llama")
    }

    fun generate(prompt: String, maxTokens: Int = 256): String {
        if (nativePtr == 0L) init()
        return nativeGenerate(nativePtr, prompt, maxTokens)
    }

    fun close() {
        if (nativePtr != 0L) {
            nativeFree(nativePtr)
            nativePtr = 0
        }
    }

    private external fun nativeInit(modelPath: String, nThreads: Int): Long
    private external fun nativeGenerate(ptr: Long, prompt: String, maxTokens: Int): String
    private external fun nativeFree(ptr: Long)
}

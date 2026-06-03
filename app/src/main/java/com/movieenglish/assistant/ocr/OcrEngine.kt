package com.movieenglish.assistant.ocr

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class OcrEngine(context: Context) {

    private var interpreter: Interpreter? = null
    private val alphabet = "abcdefghijklmnopqrstuvwxyz0123456789.,!?-'\" "
    private val charToIndex = alphabet.withIndex().associate { (i, c) -> c to i }

    init {
        interpreter = Interpreter(loadModelFile(context, "models/ocr_model.tflite"))
    }

    fun recognize(bitmap: Bitmap): OcrResult {
        val input = preprocess(bitmap)
        val output = Array(1) {
            Array(64) { FloatArray(alphabet.length + 1) } // +1 for blank token
        }
        interpreter?.run(input, output)
        val text = decodeCtc(output[0])
        val confidence = calculateConfidence(output[0])
        return OcrResult(text, confidence)
    }

    private fun preprocess(bitmap: Bitmap): ByteBuffer {
        // 缩放到固定高度 32px，保持宽高比
        val targetHeight = 32
        val aspectRatio = bitmap.width.toFloat() / bitmap.height.toFloat()
        val targetWidth = (targetHeight * aspectRatio).toInt().coerceAtMost(320)

        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)

        // 灰度化 + 归一化
        val buffer = ByteBuffer.allocateDirect(targetWidth * targetHeight * 4)
        buffer.order(ByteOrder.nativeOrder())
        val pixels = IntArray(targetWidth * targetHeight)
        scaledBitmap.getPixels(pixels, 0, targetWidth, 0, 0, targetWidth, targetHeight)

        for (pixel in pixels) {
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            val gray = (0.299f * r + 0.587f * g + 0.114f * b) / 255.0f
            buffer.putFloat(gray)
        }

        return buffer
    }

    private fun decodeCtc(probs: Array<FloatArray>): String {
        val sb = StringBuilder()
        var lastChar = -1

        for (t in probs.indices) {
            val maxIdx = probs[t].indices.maxByOrNull { probs[t][it] } ?: continue
            // last index is blank token
            if (maxIdx != alphabet.length && maxIdx != lastChar) {
                sb.append(alphabet[maxIdx])
            }
            lastChar = maxIdx
        }

        return sb.toString().trim()
    }

    private fun calculateConfidence(probs: Array<FloatArray>): Float {
        var sum = 0f
        var count = 0
        for (t in probs.indices) {
            val maxProb = probs[t].maxOrNull() ?: 0f
            sum += maxProb
            count++
        }
        return if (count > 0) sum / count else 0f
    }

    private fun loadModelFile(context: Context, path: String): MappedByteBuffer {
        val fd = context.assets.openFd(path)
        val inputStream = FileInputStream(fd.fileDescriptor)
        return inputStream.channel.map(
            FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength
        )
    }

    fun close() {
        interpreter?.close()
    }
}

data class OcrResult(val text: String, val confidence: Float)

package com.movieenglish.assistant.floating

import android.content.Context
import android.graphics.Bitmap
import android.widget.FrameLayout
import android.widget.TextView

class SubtitleBarView(context: Context) : FrameLayout(context) {

    private val textView: TextView
    private var lastRecognizedText: String = ""

    init {
        setBackgroundColor(0xDD0F172A.toInt())
        textView = TextView(context).apply {
            textSize = 15f
            setTextColor(0xFFE2E8F0.toInt())
            setPadding(16, 12, 16, 12)
        }
        addView(textView)
    }

    fun onNewBitmap(bitmap: Bitmap) {
        // Will be wired to OCR engine in Task 6
        // For now, placeholder - display will update when OCR is integrated
    }
}

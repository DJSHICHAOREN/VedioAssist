package com.movieenglish.assistant.capture

import android.graphics.Bitmap
import android.graphics.Rect

object SubtitleRegionCropper {

    /** 裁剪屏幕底部 1/5 区域作为字幕候选区 */
    fun cropSubtitleRegion(fullFrame: Bitmap): Bitmap {
        val width = fullFrame.width
        val height = fullFrame.height
        val subtitleHeight = height / 5
        val rect = Rect(0, height - subtitleHeight, width, height)
        return Bitmap.createBitmap(fullFrame, rect.left, rect.top, rect.width(), rect.height())
    }
}

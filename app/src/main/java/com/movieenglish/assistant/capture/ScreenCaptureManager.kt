package com.movieenglish.assistant.capture

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.WindowManager
import java.nio.ByteBuffer

class ScreenCaptureManager(private val context: Context) {

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var frameCallback: ((Bitmap) -> Unit)? = null
    private var lastFrameHash: Int = 0
    private var unchangedFrameCount: Int = 0

    private val handler = Handler(Looper.getMainLooper())
    private var captureRunnable: Runnable? = null

    fun startCapture(resultCode: Int, data: Intent, onFrame: (Bitmap) -> Unit) {
        frameCallback = onFrame

        val metrics = DisplayMetrics()
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        wm.defaultDisplay.getMetrics(metrics)

        val projectionManager = context.getSystemService(
            Context.MEDIA_PROJECTION_SERVICE
        ) as MediaProjectionManager
        mediaProjection = projectionManager.getMediaProjection(resultCode, data)

        imageReader = ImageReader.newInstance(
            metrics.widthPixels, metrics.heightPixels,
            PixelFormat.RGBA_8888, 2
        )

        virtualDisplay = mediaProjection!!.createVirtualDisplay(
            "ScreenCapture",
            metrics.widthPixels, metrics.heightPixels, metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader!!.surface, null, null
        )

        // 初始采样间隔 1s
        startSampling(1000L)
    }

    private fun startSampling(intervalMs: Long) {
        captureRunnable = object : Runnable {
            override fun run() {
                captureFrame()
                handler.postDelayed(this, intervalMs)
            }
        }
        handler.post(captureRunnable!!)
    }

    private fun captureFrame() {
        val image: Image = imageReader?.acquireLatestImage() ?: return
        try {
            val bitmap = imageToBitmap(image)
            val croppedBitmap = SubtitleRegionCropper.cropSubtitleRegion(bitmap)

            val frameHash = croppedBitmap.hashCode()
            if (frameHash == lastFrameHash) {
                unchangedFrameCount++
            } else {
                unchangedFrameCount = 0
                lastFrameHash = frameHash
                frameCallback?.invoke(croppedBitmap)
            }
            bitmap.recycle()
        } finally {
            image.close()
        }
    }

    private fun imageToBitmap(image: Image): Bitmap {
        val planes = image.planes
        val buffer: ByteBuffer = planes[0].buffer
        val pixelStride = planes[0].pixelStride
        val rowStride = planes[0].rowStride
        val rowPadding = rowStride - pixelStride * image.width

        val bitmap = Bitmap.createBitmap(
            image.width + rowPadding / pixelStride,
            image.height, Bitmap.Config.ARGB_8888
        )
        bitmap.copyPixelsFromBuffer(buffer)
        return Bitmap.createBitmap(bitmap, 0, 0, image.width, image.height)
    }

    fun stopCapture() {
        handler.removeCallbacksAndMessages(null)
        virtualDisplay?.release()
        imageReader?.close()
        mediaProjection?.stop()
    }
}

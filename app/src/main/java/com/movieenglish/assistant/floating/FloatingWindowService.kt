package com.movieenglish.assistant.floating

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import android.widget.LinearLayout
import androidx.core.app.NotificationCompat
import com.movieenglish.assistant.App
import com.movieenglish.assistant.capture.ScreenCaptureManager

class FloatingWindowService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var subtitleBarView: SubtitleBarView
    private lateinit var screenCaptureManager: ScreenCaptureManager

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        screenCaptureManager = ScreenCaptureManager(this)
        subtitleBarView = SubtitleBarView(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundNotification()
        addSubtitleBarToWindow()

        val resultCode = intent?.getIntExtra("resultCode", -1) ?: -1
        val data = intent?.getParcelableExtra<Intent>("data")

        if (resultCode != -1 && resultCode != 0 && data != null) {
            screenCaptureManager.startCapture(resultCode, data) { bitmap ->
                subtitleBarView.onNewBitmap(bitmap)
            }
        }

        return START_STICKY
    }

    private fun startForegroundNotification() {
        val notification = NotificationCompat.Builder(this, App.CHANNEL_ID)
            .setContentTitle("字幕助手")
            .setContentText("正在运行...")
            .setSmallIcon(android.R.drawable.ic_menu_search)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        startForeground(1, notification)
    }

    private fun addSubtitleBarToWindow() {
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            120,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM
            y = 0
        }
        windowManager.addView(subtitleBarView, params)
    }

    override fun onDestroy() {
        screenCaptureManager.stopCapture()
        windowManager.removeView(subtitleBarView)
        super.onDestroy()
    }
}

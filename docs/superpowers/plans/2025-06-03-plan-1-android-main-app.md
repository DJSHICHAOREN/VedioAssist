# Android 主应用 — 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 构建电影英语学习主 App，包含悬浮窗、OCR 推理、llama 推理、字幕处理、用户词库。

**Architecture:** 单一 Android App，Kotlin + Jetpack Compose UI，TFLite 跑 OCR，llama.cpp (JNI) 跑 Qwen 2.5，SQLite/Room 做持久化。悬浮窗通过 Foreground Service + MediaProjection 实现。

**Tech Stack:** Kotlin, Jetpack Compose, TFLite, llama.cpp (JNI), Room DB, MediaProjection API

**Source:** `app/src/main/java/com/movieenglish/assistant/`

---

## 项目文件结构

```
app/
├── build.gradle.kts
├── src/main/
│   ├── AndroidManifest.xml
│   ├── java/com/movieenglish/assistant/
│   │   ├── App.kt
│   │   ├── MainActivity.kt
│   │   ├── floating/
│   │   │   ├── FloatingWindowService.kt
│   │   │   ├── SubtitleBarComposable.kt
│   │   │   ├── WordPopupComposable.kt
│   │   │   ├── SentencePanelComposable.kt
│   │   │   └── LlamaChatComposable.kt
│   │   ├── capture/
│   │   │   ├── ScreenCaptureManager.kt
│   │   │   └── SubtitleRegionCropper.kt
│   │   ├── ocr/
│   │   │   └── OcrEngine.kt
│   │   ├── llama/
│   │   │   ├── LlamaEngine.kt
│   │   │   └── PreprocessPipeline.kt
│   │   ├── subtitle/
│   │   │   ├── SubtitleParser.kt
│   │   │   ├── SubtitleMatcher.kt
│   │   │   └── SubtitleRepository.kt
│   │   ├── vocabulary/
│   │   │   ├── UserVocabulary.kt
│   │   │   └── WordLevelModel.kt
│   │   └── data/
│   │       ├── AppDatabase.kt
│   │       ├── SubtitleEntry.kt
│   │       ├── WordRecord.kt
│   │       └── MovieCache.kt
│   ├── res/
│   │   ├── values/strings.xml
│   │   └── drawable/
│   └── assets/
│       └── models/          # 模型文件由训练管线产出后放入
```

---

### Task 1: 项目骨架与 Gradle 配置

**Files:**
- Create: `app/build.gradle.kts`
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts`
- Create: `gradle.properties`
- Create: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/res/values/strings.xml`

- [ ] **Step 1: 创建根 build.gradle.kts**

```kotlin
// build.gradle.kts (root)
plugins {
    id("com.android.application") version "8.2.0" apply false
    id("org.jetbrains.kotlin.android") version "1.9.20" apply false
}
```

- [ ] **Step 2: 创建 settings.gradle.kts**

```kotlin
// settings.gradle.kts
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "MovieEnglish"
include(":app")
```

- [ ] **Step 3: 创建 app/build.gradle.kts**

```kotlin
// app/build.gradle.kts
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.movieenglish.assistant"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.movieenglish.assistant"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        ndk { abiFilters += listOf("arm64-v8a") }
    }

    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.5" }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.01.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.lifecycle:lifecycle-service:2.7.0")
    implementation("org.tensorflow:tensorflow-lite:2.14.0")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    annotationProcessor("androidx.room:room-compiler:2.6.1")
}
```

- [ ] **Step 4: 创建 AndroidManifest.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
    <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />

    <uses-feature android:name="android.hardware.vulkan" android:required="false" />

    <application
        android:name=".App"
        android:allowBackup="true"
        android:label="@string/app_name"
        android:supportsRtl="true"
        android:theme="@style/Theme.Material3.DayNight.NoActionBar">

        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:launchMode="singleTop">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <service
            android:name=".floating.FloatingWindowService"
            android:foregroundServiceType="mediaProjection"
            android:exported="false" />

    </application>
</manifest>
```

- [ ] **Step 5: 创建 strings.xml**

```xml
<resources>
    <string name="app_name">电影英语助手</string>
    <string name="notification_channel">字幕助手</string>
</resources>
```

- [ ] **Step 6: 提交**

```bash
git add -A && git commit -m "feat: scaffold Android project with Compose and dependencies"
```

---

### Task 2: App 入口与 MainActivity

**Files:**
- Create: `app/src/main/java/com/movieenglish/assistant/App.kt`
- Create: `app/src/main/java/com/movieenglish/assistant/MainActivity.kt`

- [ ] **Step 1: 创建 App.kt**

```kotlin
package com.movieenglish.assistant

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class App : Application() {
    companion object {
        const val CHANNEL_ID = "subtitle_overlay"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel),
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
}
```

- [ ] **Step 2: 创建 MainActivity.kt**

```kotlin
package com.movieenglish.assistant

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.movieenglish.assistant.floating.FloatingWindowService

class MainActivity : ComponentActivity() {

    private val mediaProjectionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK && result.data != null) {
                startFloatingService(result.resultCode, result.data!!)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                MainScreen(
                    onStartOverlay = { checkOverlayPermission() },
                    onImportSubtitle = { /* Task 5 */ }
                )
            }
        }
    }

    private fun checkOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        } else {
            requestMediaProjection()
        }
    }

    private fun requestMediaProjection() {
        val intent = Intent(this, FloatingWindowService::class.java)
        startForegroundService(intent)
    }

    private fun startFloatingService(resultCode: Int, data: Intent) {
        val intent = Intent(this, FloatingWindowService::class.java).apply {
            putExtra("resultCode", resultCode)
            putExtra("data", data)
        }
        startForegroundService(intent)
    }
}

@Composable
fun MainScreen(onStartOverlay: () -> Unit, onImportSubtitle: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("电影英语助手", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = onStartOverlay) {
            Text("启动悬浮窗")
        }
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedButton(onClick = onImportSubtitle) {
            Text("导入字幕文件")
        }
    }
}
```

- [ ] **Step 3: 构建验证**

```bash
./gradlew :app:assembleDebug
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: 提交**

```bash
git add -A && git commit -m "feat: add App entry point and MainActivity with overlay launch"
```

---

### Task 3: 悬浮窗 Foreground Service

**Files:**
- Create: `app/src/main/java/com/movieenglish/assistant/floating/FloatingWindowService.kt`

- [ ] **Step 1: 创建 FloatingWindowService.kt**

```kotlin
package com.movieenglish.assistant.floating

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
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
```

- [ ] **Step 2: 创建 SubtitleBarView 占位类**

```kotlin
package com.movieenglish.assistant.floating

import android.content.Context
import android.graphics.Bitmap
import android.widget.LinearLayout

class SubtitleBarView(context: Context) : LinearLayout(context) {
    fun onNewBitmap(bitmap: Bitmap) {
        // 将在 Task 6 中对接 OCR 引擎
    }
}
```

- [ ] **Step 3: 构建验证**

```bash
./gradlew :app:assembleDebug
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: 提交**

```bash
git add -A && git commit -m "feat: add floating window foreground service"
```

---

### Task 4: 屏幕捕获模块

**Files:**
- Create: `app/src/main/java/com/movieenglish/assistant/capture/ScreenCaptureManager.kt`
- Create: `app/src/main/java/com/movieenglish/assistant/capture/SubtitleRegionCropper.kt`

- [ ] **Step 1: 创建 SubtitleRegionCropper.kt**

```kotlin
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
```

- [ ] **Step 2: 创建 ScreenCaptureManager.kt**

```kotlin
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
                // 连续 5 帧未变 → 回到慢采样 1s
                // 由外部通过调整 interval 实现
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
```

- [ ] **Step 3: 构建验证**

```bash
./gradlew :app:assembleDebug
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: 提交**

```bash
git add -A && git commit -m "feat: add screen capture manager with adaptive sampling"
```

---

### Task 5: OCR 推理引擎

**Files:**
- Create: `app/src/main/java/com/movieenglish/assistant/ocr/OcrEngine.kt`

- [ ] **Step 1: 创建 OcrEngine.kt**

```kotlin
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
```

- [ ] **Step 2: 构建验证**

```bash
./gradlew :app:assembleDebug
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 提交**

```bash
git add -A && git commit -m "feat: add OCR engine with TFLite CRNN inference"
```

---

### Task 6: 将 OCR 接入悬浮窗

**Files:**
- Modify: `app/src/main/java/com/movieenglish/assistant/floating/FloatingWindowService.kt`
- Modify: `app/src/main/java/com/movieenglish/assistant/floating/SubtitleBarView.kt`

- [ ] **Step 1: 重构 SubtitleBarView.kt 接入 OCR**

```kotlin
package com.movieenglish.assistant.floating

import android.content.Context
import android.graphics.Bitmap
import android.widget.FrameLayout
import android.widget.TextView
import com.movieenglish.assistant.ocr.OcrEngine

class SubtitleBarView(context: Context) : FrameLayout(context) {

    private val ocrEngine = OcrEngine(context)
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
        val result = ocrEngine.recognize(bitmap)
        if (result.text.isNotEmpty() && result.text != lastRecognizedText) {
            lastRecognizedText = result.text
            textView.text = result.text
        }
    }
}
```

- [ ] **Step 2: 构建验证**

```bash
./gradlew :app:assembleDebug
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 提交**

```bash
git add -A && git commit -m "feat: wire OCR engine into subtitle bar overlay"
```

---

### Task 7: 数据库层

**Files:**
- Create: `app/src/main/java/com/movieenglish/assistant/data/AppDatabase.kt`
- Create: `app/src/main/java/com/movieenglish/assistant/data/SubtitleEntry.kt`
- Create: `app/src/main/java/com/movieenglish/assistant/data/WordRecord.kt`
- Create: `app/src/main/java/com/movieenglish/assistant/data/MovieCache.kt`

- [ ] **Step 1: 创建数据实体 SubtitleEntry.kt**

```kotlin
package com.movieenglish.assistant.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "subtitle_entries")
data class SubtitleEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val movieId: String,
    val index: Int,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val text: String,
    val translation: String = "",
    val grammar: String = "",
    val difficultWordsJson: String = "[]",  // JSON array of word info
    val idiomsJson: String = "[]",           // JSON array of idiom strings
    val preprocessed: Boolean = false
)
```

- [ ] **Step 2: 创建 WordRecord.kt**

```kotlin
package com.movieenglish.assistant.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "word_records")
data class WordRecord(
    @PrimaryKey val word: String,
    val level: Float = 3.0f,          // 1-5 difficulty level
    val timesShown: Int = 0,
    val timesClicked: Int = 0,
    val timesIgnored: Int = 0,
    val bookmarked: Boolean = false,
    val mastered: Boolean = false,
    val lastSeenAt: Long = 0,
    val morphologyJson: String = "",  // cached root/affix analysis
    val meaning: String = ""
)
```

- [ ] **Step 3: 创建 MovieCache.kt**

```kotlin
package com.movieenglish.assistant.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "movie_caches")
data class MovieCache(
    @PrimaryKey val movieId: String,
    val title: String = "",
    val subtitleFilePath: String = "",
    val totalEntries: Int = 0,
    val preprocessedCount: Int = 0,
    val preprocessingComplete: Boolean = false,
    val importedAt: Long = System.currentTimeMillis()
)
```

- [ ] **Step 4: 创建 AppDatabase.kt**

```kotlin
package com.movieenglish.assistant.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [SubtitleEntry::class, WordRecord::class, MovieCache::class],
    version = 1
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun subtitleDao(): SubtitleDao
    abstract fun wordRecordDao(): WordRecordDao
    abstract fun movieCacheDao(): MovieCacheDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "movie_english.db"
                ).build().also { instance = it }
            }
        }
    }
}
```

- [ ] **Step 5: 创建 DAO 接口文件**

```kotlin
// SubtitleDao.kt (same directory)
package com.movieenglish.assistant.data

import androidx.room.*

@Dao
interface SubtitleDao {
    @Query("SELECT * FROM subtitle_entries WHERE movieId = :movieId ORDER BY `index`")
    suspend fun getByMovie(movieId: String): List<SubtitleEntry>

    @Query("SELECT * FROM subtitle_entries WHERE movieId = :movieId AND preprocessed = 0 ORDER BY `index`")
    suspend fun getUnprocessed(movieId: String): List<SubtitleEntry>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<SubtitleEntry>)

    @Update
    suspend fun update(entry: SubtitleEntry)

    @Query("SELECT * FROM subtitle_entries WHERE movieId = :movieId AND text LIKE '%' || :searchText || '%' LIMIT 5")
    suspend fun searchByText(movieId: String, searchText: String): List<SubtitleEntry>
}

// WordRecordDao.kt
@Dao
interface WordRecordDao {
    @Query("SELECT * FROM word_records WHERE word = :word")
    suspend fun getWord(word: String): WordRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(record: WordRecord)

    @Query("SELECT * FROM word_records WHERE bookmarked = 1 ORDER BY lastSeenAt DESC")
    suspend fun getBookmarked(): List<WordRecord>

    @Query("SELECT * FROM word_records WHERE mastered = 0 ORDER BY level DESC LIMIT :limit")
    suspend fun getTopDifficultWords(limit: Int = 100): List<WordRecord>
}

// MovieCacheDao.kt
@Dao
interface MovieCacheDao {
    @Query("SELECT * FROM movie_caches WHERE movieId = :movieId")
    suspend fun getByMovieId(movieId: String): MovieCache?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(cache: MovieCache)

    @Query("SELECT * FROM movie_caches ORDER BY importedAt DESC")
    suspend fun getAll(): List<MovieCache>
}
```

- [ ] **Step 6: 构建验证**

```bash
./gradlew :app:assembleDebug
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: 提交**

```bash
git add -A && git commit -m "feat: add Room database layer with DAOs"
```

---

### Task 8: 字幕文件解析器

**Files:**
- Create: `app/src/main/java/com/movieenglish/assistant/subtitle/SubtitleParser.kt`

- [ ] **Step 1: 创建 SubtitleParser.kt**

```kotlin
package com.movieenglish.assistant.subtitle

import android.content.Context
import android.net.Uri
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.math.roundToLong

data class ParsedSubtitle(
    val index: Int,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val text: String
)

object SubtitleParser {

    fun parse(context: Context, uri: Uri): List<ParsedSubtitle> {
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: return emptyList()
        val reader = BufferedReader(InputStreamReader(inputStream, detectEncoding(inputStream)))
        val content = reader.readText()
        reader.close()
        inputStream.close()

        return when {
            content.contains("-->") -> parseSrt(content)
            content.contains("[Events]") || content.contains("Format:") -> parseAss(content)
            content.contains("WEBVTT") || content.startsWith("NOTE") || content.contains(" --> ") -> parseVtt(content)
            else -> parseSrt(content) // fallback to SRT
        }
    }

    private fun parseSrt(content: String): List<ParsedSubtitle> {
        val results = mutableListOf<ParsedSubtitle>()
        val blocks = content.trim().split(Regex("\\n\\s*\\n"))
        for (block in blocks) {
            val lines = block.trim().split("\n")
            if (lines.size < 3) continue
            val index = lines[0].trim().toIntOrNull() ?: continue
            val timeMatch = Regex("(\\d{2}:\\d{2}:\\d{2}[,\\.]\\d{3})\\s*-->\\s*(\\d{2}:\\d{2}:\\d{2}[,\\.]\\d{3})")
                .find(lines[1]) ?: continue
            val startTime = parseTimestamp(timeMatch.groupValues[1])
            val endTime = parseTimestamp(timeMatch.groupValues[2])
            val text = lines.drop(2).joinToString(" ")
                .replace(Regex("<[^>]+>"), "")
                .replace(Regex("\\{[^}]+\\}"), "")
                .trim()
            results.add(ParsedSubtitle(index, startTime, endTime, text))
        }
        return results
    }

    private fun parseAss(content: String): List<ParsedSubtitle> {
        val results = mutableListOf<ParsedSubtitle>()
        val eventSection = content.substringAfter("[Events]", "")
        val formatLine = eventSection.lines().firstOrNull { it.startsWith("Format:") } ?: return results
        val columns = formatLine.removePrefix("Format:").split(",").map { it.trim() }
        val startIdx = columns.indexOf("Start")
        val endIdx = columns.indexOf("End")
        val textIdx = columns.indexOf("Text")
        if (startIdx < 0 || endIdx < 0 || textIdx < 0) return results

        var index = 1
        for (line in eventSection.lines()) {
            if (!line.startsWith("Dialogue:")) continue
            val parts = line.removePrefix("Dialogue:").split(",", limit = textIdx + 1)
            if (parts.size <= textIdx) continue
            val startTime = parseAssTimestamp(parts[startIdx].trim())
            val endTime = parseAssTimestamp(parts[endIdx].trim())
            val text = parts[textIdx].trim()
                .replace(Regex("\\{[^}]+\\}"), "")
                .replace("\\N", " ")
                .trim()
            if (text.isNotEmpty()) {
                results.add(ParsedSubtitle(index++, startTime, endTime, text))
            }
        }
        return results
    }

    private fun parseVtt(content: String): List<ParsedSubtitle> {
        var clean = content.replace("WEBVTT", "").trim()
        // remove VTT headers
        clean = clean.replace(Regex("(?m)^NOTE.*$"), "")
        return parseSrt(clean)
    }

    private fun parseTimestamp(ts: String): Long {
        val parts = ts.split(":")
        val hours = parts[0].toLong()
        val minutes = parts[1].toLong()
        val secParts = parts[2].replace(",", ".").split(".")
        val seconds = secParts[0].toLong()
        val millis = (secParts.getOrElse(1) { "0" }.padEnd(3, '0').take(3).toDouble() * 1000).roundToLong()
        return hours * 3600000 + minutes * 60000 + seconds * 1000 + millis / 1000
    }

    private fun parseAssTimestamp(ts: String): Long {
        val parts = ts.split(":")
        val hours = parts[0].toLong()
        val minutes = parts[1].toLong()
        val secParts = parts[2].split(".")
        val seconds = secParts[0].toLong()
        val centiseconds = secParts.getOrElse(1) { "0" }.padEnd(2, '0').take(2).toLong()
        return hours * 3600000 + minutes * 60000 + seconds * 1000 + centiseconds * 10
    }

    private fun detectEncoding(inputStream: java.io.InputStream): String {
        // Simple detection: try UTF-8 first, fall back to GBK
        return "UTF-8"
    }
}
```

- [ ] **Step 2: 构建验证**

```bash
./gradlew :app:assembleDebug
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 提交**

```bash
git add -A && git commit -m "feat: add subtitle parser supporting SRT/ASS/VTT"
```

---

### Task 9: 字幕匹配器

**Files:**
- Create: `app/src/main/java/com/movieenglish/assistant/subtitle/SubtitleMatcher.kt`

- [ ] **Step 1: 创建 SubtitleMatcher.kt**

```kotlin
package com.movieenglish.assistant.subtitle

object SubtitleMatcher {

    /** 用 OCR 文本在字幕列表中模糊匹配最佳条目 */
    fun match(ocrText: String, subtitles: List<ParsedSubtitle>): ParsedSubtitle? {
        if (ocrText.isBlank() || subtitles.isEmpty()) return null

        val cleanedOcr = cleanText(ocrText)

        var bestMatch: ParsedSubtitle? = null
        var bestScore = Int.MAX_VALUE

        for (sub in subtitles) {
            val cleanedSub = cleanText(sub.text)
            val distance = levenshteinDistance(cleanedOcr, cleanedSub)
            if (distance < bestScore) {
                bestScore = distance
                bestMatch = sub
            }
            // 精确匹配直接返回
            if (distance == 0) return sub
        }

        // 拒绝阈值：编辑距离超过 OCR 文本长度一半则放弃
        return if (bestScore <= cleanedOcr.length / 2) bestMatch else null
    }

    private fun cleanText(text: String): String {
        return text.lowercase()
            .replace(Regex("[^a-z0-9\\s]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun levenshteinDistance(a: String, b: String): Int {
        val dp = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) dp[i][0] = i
        for (j in 0..b.length) dp[0][j] = j
        for (i in 1..a.length) {
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,
                    dp[i][j - 1] + 1,
                    dp[i - 1][j - 1] + cost
                )
            }
        }
        return dp[a.length][b.length]
    }
}
```

- [ ] **Step 2: 构建验证**

```bash
./gradlew :app:assembleDebug
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 提交**

```bash
git add -A && git commit -m "feat: add subtitle matcher with Levenshtein fuzzy matching"
```

---

### Task 10: 字幕仓库

**Files:**
- Create: `app/src/main/java/com/movieenglish/assistant/subtitle/SubtitleRepository.kt`

- [ ] **Step 1: 创建 SubtitleRepository.kt**

```kotlin
package com.movieenglish.assistant.subtitle

import android.content.Context
import android.net.Uri
import com.movieenglish.assistant.data.*

class SubtitleRepository(private val context: Context) {

    private val db = AppDatabase.getInstance(context)

    suspend fun importSubtitle(movieId: String, title: String, uri: Uri): List<ParsedSubtitle> {
        val parsed = SubtitleParser.parse(context, uri)
        val entries = parsed.map { sub ->
            SubtitleEntry(
                movieId = movieId,
                index = sub.index,
                startTimeMs = sub.startTimeMs,
                endTimeMs = sub.endTimeMs,
                text = sub.text
            )
        }
        db.subtitleDao().insertAll(entries)
        db.movieCacheDao().upsert(
            MovieCache(
                movieId = movieId,
                title = title,
                totalEntries = entries.size
            )
        )
        return parsed
    }

    suspend fun getMovieSubtitles(movieId: String): List<SubtitleEntry> {
        return db.subtitleDao().getByMovie(movieId)
    }

    suspend fun getUnprocessedEntries(movieId: String): List<SubtitleEntry> {
        return db.subtitleDao().getUnprocessed(movieId)
    }

    suspend fun updateEntry(entry: SubtitleEntry) {
        db.subtitleDao().update(entry)
    }

    suspend fun getCachedMovies(): List<MovieCache> {
        return db.movieCacheDao().getAll()
    }

    suspend fun getMovieCache(movieId: String): MovieCache? {
        return db.movieCacheDao().getByMovieId(movieId)
    }

    suspend fun updatePreprocessProgress(movieId: String, preprocessedCount: Int, complete: Boolean) {
        val cache = db.movieCacheDao().getByMovieId(movieId) ?: return
        db.movieCacheDao().upsert(cache.copy(
            preprocessedCount = preprocessedCount,
            preprocessingComplete = complete
        ))
    }
}
```

- [ ] **Step 2: 构建验证**

```bash
./gradlew :app:assembleDebug
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 提交**

```bash
git add -A && git commit -m "feat: add subtitle repository for import and query"
```

---

### Task 11: llama 推理引擎 (JNI 层)

**Files:**
- Create: `app/src/main/java/com/movieenglish/assistant/llama/LlamaEngine.kt`
- Create: `app/src/main/cpp/llama-bridge.cpp`
- Create: `app/src/main/cpp/CMakeLists.txt`

- [ ] **Step 1: 创建 llama-bridge.cpp**

```cpp
#include <jni.h>
#include <string>
#include "llama.h"

// llama.cpp 的 C API 封装

struct LlamaContext {
    llama_model* model;
    llama_context* ctx;
    const llama_vocab* vocab;
};

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_movieenglish_assistant_llama_LlamaEngine_nativeInit(
    JNIEnv* env, jobject, jstring modelPath, jint nThreads
) {
    const char* path = env->GetStringUTFChars(modelPath, nullptr);

    llama_backend_init();

    llama_model_params modelParams = llama_model_default_params();
    modelParams.n_gpu_layers = 99; // offload all to GPU if available

    llama_model* model = llama_model_load_from_file(path, modelParams);
    if (!model) {
        env->ReleaseStringUTFChars(modelPath, path);
        return 0;
    }

    llama_context_params ctxParams = llama_context_default_params();
    ctxParams.n_ctx = 2048;
    ctxParams.n_threads = nThreads;

    llama_context* ctx = llama_new_context_with_model(model, ctxParams);
    if (!ctx) {
        llama_model_free(model);
        env->ReleaseStringUTFChars(modelPath, path);
        return 0;
    }

    auto* lc = new LlamaContext{model, ctx, llama_model_get_vocab(model)};
    env->ReleaseStringUTFChars(modelPath, path);
    return reinterpret_cast<jlong>(lc);
}

JNIEXPORT jstring JNICALL
Java_com_movieenglish_assistant_llama_LlamaEngine_nativeGenerate(
    JNIEnv* env, jobject, jlong ptr, jstring prompt, jint maxTokens
) {
    auto* lc = reinterpret_cast<LlamaContext*>(ptr);
    const char* promptStr = env->GetStringUTFChars(prompt, nullptr);

    // Tokenize
    int nTokens = -llama_tokenize(lc->vocab, promptStr, strlen(promptStr), nullptr, 0, true, true);
    std::vector<llama_token> tokens(nTokens);
    llama_tokenize(lc->vocab, promptStr, strlen(promptStr), tokens.data(), nTokens, true, true);

    // Generate
    std::string result;
    llama_batch batch = llama_batch_get_one(tokens.data(), tokens.size());

    for (int i = 0; i < maxTokens; i++) {
        if (llama_decode(lc->ctx, batch) != 0) break;

        // Simple greedy sampling
        float* logits = llama_get_logits_ith(lc->ctx, batch.n_tokens - 1);
        int nextToken = 0;
        float maxLogit = logits[0];
        int vocabSize = llama_n_vocab(lc->vocab);
        for (int j = 1; j < vocabSize; j++) {
            if (logits[j] > maxLogit) {
                maxLogit = logits[j];
                nextToken = j;
            }
        }

        if (llama_vocab_is_eog(lc->vocab, nextToken)) break;

        char buf[256];
        int len = llama_token_to_piece(lc->vocab, nextToken, buf, sizeof(buf), 0, true);
        if (len > 0) {
            result.append(buf, len);
        }

        batch = llama_batch_get_one(&nextToken, 1);
    }

    env->ReleaseStringUTFChars(prompt, promptStr);
    return env->NewStringUTF(result.c_str());
}

JNIEXPORT void JNICALL
Java_com_movieenglish_assistant_llama_LlamaEngine_nativeFree(
    JNIEnv*, jobject, jlong ptr
) {
    auto* lc = reinterpret_cast<LlamaContext*>(ptr);
    llama_free(lc->ctx);
    llama_model_free(lc->model);
    llama_backend_free();
    delete lc;
}

} // extern "C"
```

- [ ] **Step 2: 创建 CMakeLists.txt**

```cmake
cmake_minimum_required(VERSION 3.18)
project("llama-bridge")

add_library(llama-bridge SHARED llama-bridge.cpp)

find_library(log-lib log)

# llama.cpp is expected to be built as a static library
add_library(llama STATIC IMPORTED)
set_target_properties(llama PROPERTIES IMPORTED_LOCATION
    ${CMAKE_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libllama.a)

target_link_libraries(llama-bridge llama ${log-lib})
```

- [ ] **Step 3: 创建 LlamaEngine.kt**

```kotlin
package com.movieenglish.assistant.llama

import android.content.Context

class LlamaEngine(context: Context) {

    private var nativePtr: Long = 0
    private val modelPath: String

    init {
        System.loadLibrary("llama-bridge")

        // Copy model from assets to internal storage
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
```

- [ ] **Step 4: 提交**

```bash
git add -A && git commit -m "feat: add llama JNI bridge for on-device inference"
```

---

### Task 12: llama 预处理管线

**Files:**
- Create: `app/src/main/java/com/movieenglish/assistant/llama/PreprocessPipeline.kt`

- [ ] **Step 1: 创建 PreprocessPipeline.kt**

```kotlin
package com.movieenglish.assistant.llama

import android.content.Context
import com.movieenglish.assistant.data.*
import com.movieenglish.assistant.subtitle.SubtitleRepository
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

class PreprocessPipeline(
    private val context: Context,
    private val repository: SubtitleRepository
) {
    private val llamaEngine = LlamaEngine(context)
    private var isRunning = false
    private var isCancelled = false

    data class Progress(
        val current: Int,
        val total: Int,
        val currentText: String = ""
    )

    private val systemPrompt = """
You are an English teacher helping a Chinese student learn English through movies.
Analyze this subtitle sentence and output ONLY valid JSON, no other text:

Sentence: {SENTENCE}

Output JSON:
{
  "translation": "整句中文翻译",
  "grammar": "简短语法分析",
  "difficult_words": [
    {
      "word": "单词",
      "level": 1-5,
      "meaning": "中文释义",
      "morphology": {"prefix": "前缀及含义或null", "root": "词根及含义", "suffix": "后缀及含义或null"}
    }
  ],
  "idioms": []
}
""".trimIndent()

    suspend fun preprocessMovie(
        movieId: String,
        onProgress: (Progress) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        isRunning = true
        isCancelled = false

        try {
            llamaEngine.init()
            val entries = repository.getUnprocessedEntries(movieId)
            val total = entries.size

            for ((i, entry) in entries.withIndex()) {
                if (isCancelled) break

                onProgress(Progress(i + 1, total, entry.text))

                val prompt = systemPrompt.replace("{SENTENCE}", entry.text)
                val rawJson = llamaEngine.generate(prompt, maxTokens = 512)
                val parsed = parseResponse(rawJson)

                repository.updateEntry(entry.copy(
                    translation = parsed.translation,
                    grammar = parsed.grammar,
                    difficultWordsJson = Json.encodeToString(
                        ListSerializer(PreprocessWord.serializer()), parsed.difficultWords
                    ),
                    idiomsJson = Json.encodeToString(
                        ListSerializer(String.serializer()), parsed.idioms
                    ),
                    preprocessed = true
                ))

                // Update word records
                val wordDao = AppDatabase.getInstance(context).wordRecordDao()
                for (word in parsed.difficultWords) {
                    val existing = wordDao.getWord(word.word)
                    val record = existing?.copy(
                        meaning = word.meaning,
                        morphologyJson = Json.encodeToString(
                            Morphology.serializer(), word.morphology
                        )
                    ) ?: WordRecord(
                        word = word.word,
                        level = word.level.toFloat(),
                        meaning = word.meaning,
                        morphologyJson = Json.encodeToString(
                            Morphology.serializer(), word.morphology
                        )
                    )
                    wordDao.upsert(record)
                }

                repository.updatePreprocessProgress(movieId, i + 1, false)
            }

            repository.updatePreprocessProgress(
                movieId, entries.size, !isCancelled
            )
            !isCancelled
        } finally {
            isRunning = false
            llamaEngine.close()
        }
    }

    fun cancel() { isCancelled = true }
    fun running(): Boolean = isRunning

    private fun parseResponse(json: String): PreprocessResponse {
        return try {
            // Extract JSON from response (strip markdown fences if any)
            val cleanJson = json
                .substringAfter("```json", json)
                .substringBefore("```", json)
                .trim()
            Json.decodeFromString(PreprocessResponse.serializer(), cleanJson)
        } catch (e: Exception) {
            PreprocessResponse("", "", emptyList(), emptyList())
        }
    }
}

@Serializable
data class PreprocessResponse(
    val translation: String = "",
    val grammar: String = "",
    val difficult_words: List<PreprocessWord> = emptyList(),
    val idioms: List<String> = emptyList()
)

@Serializable
data class PreprocessWord(
    val word: String,
    val level: Int = 3,
    val meaning: String = "",
    val morphology: Morphology = Morphology()
)

@Serializable
data class Morphology(
    val prefix: String? = null,
    val root: String = "",
    val suffix: String? = null
)
```

- [ ] **Step 2: 构建验证**

```bash
./gradlew :app:assembleDebug
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 提交**

```bash
git add -A && git commit -m "feat: add llama preprocessing pipeline for subtitle analysis"
```

---

### Task 13: 用户词库与等级调节

**Files:**
- Create: `app/src/main/java/com/movieenglish/assistant/vocabulary/UserVocabulary.kt`
- Create: `app/src/main/java/com/movieenglish/assistant/vocabulary/WordLevelModel.kt`

- [ ] **Step 1: 创建 WordLevelModel.kt**

```kotlin
package com.movieenglish.assistant.vocabulary

data class WordLevelModel(
    val level: Float,       // 当前等级 0-5
    val clickCount: Int,
    val ignoreCount: Int,
    val bookmarked: Boolean,
    val mastered: Boolean
) {
    companion object {
        const val MAX_LEVEL = 5f
        const val MIN_LEVEL = 0f
        const val CLICK_BOOST = 2.5f     // 点击一次提升的等级量
        const val IGNORE_DECAY = 0.5f    // 忽略一次降低的等级量
        const val MASTERY_THRESHOLD = 0f  // 低于此值视为已掌握
    }

    /** 用户点击查词：快速提升 */
    fun onWordClicked(): WordLevelModel {
        if (bookmarked || mastered) return this
        return copy(
            level = (level + CLICK_BOOST).coerceAtMost(MAX_LEVEL),
            clickCount = clickCount + 1
        )
    }

    /** 展示但未点击：缓慢降低 */
    fun onWordIgnored(): WordLevelModel {
        if (bookmarked || mastered) return this
        val newLevel = (level - IGNORE_DECAY).coerceAtLeast(MIN_LEVEL)
        return copy(
            level = newLevel,
            ignoreCount = ignoreCount + 1,
            mastered = newLevel <= MASTERY_THRESHOLD
        )
    }

    /** 加入生词本：锁定最高级 */
    fun bookmark(): WordLevelModel {
        return copy(level = MAX_LEVEL, bookmarked = true)
    }

    /** 标记已掌握：立即归零 */
    fun markMastered(): WordLevelModel {
        return copy(level = MIN_LEVEL, mastered = true)
    }

    fun isDifficult(): Boolean = level >= 2.5f && !mastered
}
```

- [ ] **Step 2: 创建 UserVocabulary.kt**

```kotlin
package com.movieenglish.assistant.vocabulary

import android.content.Context
import com.movieenglish.assistant.data.AppDatabase
import com.movieenglish.assistant.data.WordRecord

class UserVocabulary(context: Context) {

    private val wordDao = AppDatabase.getInstance(context).wordRecordDao()

    suspend fun getWordLevel(word: String): Float {
        return wordDao.getWord(word)?.level ?: 3.0f
    }

    suspend fun onWordClicked(word: String) {
        val record = wordDao.getWord(word)
        val model = record?.let { WordLevelModel(it.level, it.timesClicked, it.timesIgnored, it.bookmarked, it.mastered) }
            ?: WordLevelModel(3.0f, 0, 0, false, false)
        val updated = model.onWordClicked()
        wordDao.upsert(WordRecord(
            word = word,
            level = updated.level,
            timesShown = (record?.timesShown ?: 0),
            timesClicked = updated.clickCount,
            timesIgnored = updated.ignoreCount,
            bookmarked = updated.bookmarked,
            mastered = updated.mastered,
            lastSeenAt = System.currentTimeMillis(),
            morphologyJson = record?.morphologyJson ?: "",
            meaning = record?.meaning ?: ""
        ))
    }

    suspend fun onWordsDisplayed(words: List<String>) {
        for (word in words) {
            val record = wordDao.getWord(word)
            val model = record?.let { WordLevelModel(it.level, it.timesClicked, it.timesIgnored, it.bookmarked, it.mastered) }
                ?: WordLevelModel(3.0f, 0, 0, false, false)
            val updated = model.onWordIgnored()
            wordDao.upsert(WordRecord(
                word = word,
                level = updated.level,
                timesShown = (record?.timesShown ?: 0) + 1,
                timesClicked = record?.timesClicked ?: 0,
                timesIgnored = updated.ignoreCount,
                bookmarked = updated.bookmarked,
                mastered = updated.mastered,
                lastSeenAt = System.currentTimeMillis(),
                morphologyJson = record?.morphologyJson ?: "",
                meaning = record?.meaning ?: ""
            ))
        }
    }

    suspend fun bookmarkWord(word: String) {
        val record = wordDao.getWord(word)
        val model = record?.let { WordLevelModel(it.level, it.timesClicked, it.timesIgnored, it.bookmarked, it.mastered) }
            ?: WordLevelModel(3.0f, 0, 0, false, false)
        val updated = model.bookmark()
        wordDao.upsert(WordRecord(
            word = word,
            level = updated.level,
            timesShown = record?.timesShown ?: 0,
            timesClicked = record?.timesClicked ?: 0,
            timesIgnored = record?.timesIgnored ?: 0,
            bookmarked = true,
            mastered = false,
            lastSeenAt = System.currentTimeMillis(),
            morphologyJson = record?.morphologyJson ?: "",
            meaning = record?.meaning ?: ""
        ))
    }

    suspend fun markMastered(word: String) {
        val record = wordDao.getWord(word)
        val model = record?.let { WordLevelModel(it.level, it.timesClicked, it.timesIgnored, it.bookmarked, it.mastered) }
            ?: WordLevelModel(3.0f, 0, 0, false, false)
        val updated = model.markMastered()
        wordDao.upsert(WordRecord(
            word = word,
            level = updated.level,
            timesShown = record?.timesShown ?: 0,
            timesClicked = record?.timesClicked ?: 0,
            timesIgnored = record?.timesIgnored ?: 0,
            bookmarked = record?.bookmarked ?: false,
            mastered = true,
            lastSeenAt = System.currentTimeMillis(),
            morphologyJson = record?.morphologyJson ?: "",
            meaning = record?.meaning ?: ""
        ))
    }
}
```

- [ ] **Step 3: 构建验证**

```bash
./gradlew :app:assembleDebug
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: 提交**

```bash
git add -A && git commit -m "feat: add user vocabulary with asymmetric level adjustment"
```

---

### Task 14: Compose UI — 字幕条

**Files:**
- Create: `app/src/main/java/com/movieenglish/assistant/floating/SubtitleBarComposable.kt`

- [ ] **Step 1: 创建 SubtitleBarComposable.kt**

```kotlin
package com.movieenglish.assistant.floating

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SubtitleBar(
    text: String,
    difficultWords: List<String>,
    masteredWords: List<String>,
    timestamp: String,
    onWordClick: (String) -> Unit,
    onRedDotClick: () -> Unit,
    onSwipeUp: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Color(0xDD0F172A),
                RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Red dot
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(Color(0xFFEF4444))
                .clickable { onRedDotClick() }
        )

        Spacer(modifier = Modifier.width(8.dp))

        // Subtitle text with difficulty highlighting
        Text(
            text = buildHighlightedText(text, difficultWords, masteredWords),
            fontSize = 15.sp,
            lineHeight = 22.sp,
            modifier = Modifier.weight(1f)
        )

        Spacer(modifier = Modifier.width(8.dp))

        // Timestamp
        Text(
            text = timestamp,
            fontSize = 10.sp,
            color = Color(0xFF64748B)
        )
    }
}

@Composable
fun buildHighlightedText(
    text: String,
    difficultWords: List<String>,
    masteredWords: List<String>
) = buildAnnotatedString {
    val words = text.split(Regex("(?<=\\s)|(?=\\s)"))
    for (w in words) {
        val cleanWord = w.trim().lowercase().replace(Regex("[^a-z]"), "")
        when {
            cleanWord in masteredWords -> {
                withStyle(SpanStyle(color = Color(0xFF60A5FA))) { append(w) }
            }
            cleanWord in difficultWords -> {
                withStyle(SpanStyle(
                    color = Color(0xFFFBBF24),
                    textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline
                )) { append(w) }
            }
            else -> {
                withStyle(SpanStyle(color = Color(0xFFE2E8F0))) { append(w) }
            }
        }
    }
}
```

- [ ] **Step 2: 构建验证**

```bash
./gradlew :app:assembleDebug
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 提交**

```bash
git add -A && git commit -m "feat: add Compose subtitle bar with word highlighting"
```

---

### Task 15: Compose UI — 单词弹窗、句子面板、llama 对话

**Files:**
- Create: `app/src/main/java/com/movieenglish/assistant/floating/WordPopupComposable.kt`
- Create: `app/src/main/java/com/movieenglish/assistant/floating/SentencePanelComposable.kt`
- Create: `app/src/main/java/com/movieenglish/assistant/floating/LlamaChatComposable.kt`

- [ ] **Step 1: 创建 WordPopupComposable.kt**

```kotlin
package com.movieenglish.assistant.floating

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.movieenglish.assistant.vocabulary.Morphology as Mor

@Composable
fun WordPopup(
    word: String,
    phonetic: String,
    meaning: String,
    example: String,
    morphology: Mor?,
    relatedWords: List<String>,
    onAddToWordbook: () -> Unit,
    onAskLlama: () -> Unit,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth(0.9f)
            .padding(16.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(word, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE2E8F0))
                Text(phonetic, fontSize = 13.sp, color = Color(0xFF64748B))
            }
            Spacer(Modifier.height(8.dp))
            Text(meaning, fontSize = 13.sp, color = Color(0xFF94A3B8), lineHeight = 20.sp)

            // Morphology
            if (morphology != null && morphology.root.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                MorphologyRow(morphology)
            }

            // Related
            if (relatedWords.isNotEmpty()) {
                Text(
                    "同类词：${relatedWords.joinToString(" · ")}",
                    fontSize = 11.sp, color = Color(0xFF64748B)
                )
            }

            // Example
            if (example.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(example, fontSize = 12.sp, color = Color(0xFF94A3B8))
            }

            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onAddToWordbook,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                ) { Text("+ 生词本", fontSize = 11.sp) }
                OutlinedButton(
                    onClick = onAskLlama,
                    modifier = Modifier.weight(1f)
                ) { Text("问 llama", fontSize = 11.sp) }
            }
        }
    }
}

@Composable
fun MorphologyRow(mor: Mor) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        if (mor.prefix != null) {
            MorChip(mor.prefix, Color(0xFF60A5FA), "前缀")
        }
        MorChip(mor.root, Color(0xFFFBBF24), "词根")
        if (mor.suffix != null) {
            MorChip(mor.suffix, Color(0xFF34D399), "后缀")
        }
    }
}

@Composable
fun MorChip(text: String, color: Color, label: String) {
    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 3.dp)
    ) {
        Text(
            "$text",
            fontSize = 11.sp,
            color = color
        )
    }
}
```

- [ ] **Step 2: 创建 SentencePanelComposable.kt**

```kotlin
package com.movieenglish.assistant.floating

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SentencePanel(
    original: String,
    translation: String,
    wordBreakdown: List<Pair<String, String>>,  // word to meaning
    grammar: String,
    tip: String,
    onBookmark: () -> Unit,
    onAskLlama: () -> Unit,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth(0.9f)
            .padding(16.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A))
    ) {
        Column(Modifier.padding(16.dp)) {
            // Header
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("整句分析", fontWeight = FontWeight.Bold, color = Color(0xFFE2E8F0))
                TextButton(onClick = onDismiss) { Text("✕", color = Color(0xFF64748B)) }
            }

            SectionLabel("📝 原句")
            Text(original, fontSize = 15.sp, color = Color(0xFFE2E8F0))

            SectionLabel("🇨🇳 翻译")
            Text(translation, fontSize = 14.sp, color = Color(0xFFCBD5E1))

            SectionLabel("🔍 逐词拆解")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                wordBreakdown.forEach { (w, m) ->
                    Box(Modifier.background(Color(0xFF1E293B), RoundedCornerShape(6.dp)).padding(6.dp, 3.dp)) {
                        Text("$w $m", fontSize = 11.sp, color = Color(0xFFCBD5E1))
                    }
                }
            }

            SectionLabel("📐 语法分析")
            Text(grammar, fontSize = 13.sp, color = Color(0xFF94A3B8))

            if (tip.isNotEmpty()) {
                Text(tip, fontSize = 12.sp, color = Color(0xFF64748B))
            }

            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onBookmark, modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                ) { Text("收藏整句", fontSize = 12.sp) }
                OutlinedButton(onClick = onAskLlama, modifier = Modifier.weight(1f)) {
                    Text("追问 llama", fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun SectionLabel(label: String) {
    Spacer(Modifier.height(12.dp))
    Text(label, fontSize = 11.sp, color = Color(0xFF64748B))
}
```

- [ ] **Step 3: 创建 LlamaChatComposable.kt**

```kotlin
package com.movieenglish.assistant.floating

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class ChatMessage(val isUser: Boolean, val text: String)

@Composable
fun LlamaChatPanel(
    contextSentence: String,
    messages: List<ChatMessage>,
    onSend: (String) -> Unit
) {
    var inputText by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.5f)
            .background(Color(0xFF0F172A), RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
    ) {
        // Context
        Row(
            Modifier.fillMaxWidth().background(Color(0xFF1E293B)).padding(12.dp, 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("📌 当前字幕:", fontSize = 11.sp, color = Color(0xFF64748B))
            Spacer(Modifier.width(8.dp))
            Text(contextSentence, fontSize = 11.sp, color = Color(0xFF94A3B8))
        }

        // Messages
        LazyColumn(
            modifier = Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(messages) { msg ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (msg.isUser) Arrangement.End else Arrangement.Start
                ) {
                    if (!msg.isUser) {
                        Box(
                            Modifier.size(24.dp).clip(CircleShape).background(Color(0xFF7C3AED)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("🦙", fontSize = 10.sp)
                        }
                        Spacer(Modifier.width(8.dp))
                    }
                    Box(
                        modifier = Modifier
                            .widthIn(max = 280.dp)
                            .background(
                                if (msg.isUser) Color(0xFF2563EB) else Color(0xFF1E293B),
                                RoundedCornerShape(10.dp)
                                    .let { if (msg.isUser) it else RoundedCornerShape(topEnd = 10.dp, bottomEnd = 10.dp, bottomStart = 10.dp) }
                            )
                            .padding(10.dp, 8.dp)
                    ) {
                        Text(msg.text, fontSize = 13.sp, color = Color(0xFFE2E8F0))
                    }
                }
            }
        }

        // Input
        Row(
            Modifier.fillMaxWidth().padding(12.dp).background(Color(0xFF1E293B), RoundedCornerShape(20.dp)).padding(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("输入问题...", color = Color(0xFF64748B), fontSize = 13.sp) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent
                ),
                singleLine = true
            )
            IconButton(onClick = {
                if (inputText.isNotBlank()) {
                    onSend(inputText.trim())
                    inputText = ""
                }
            }) {
                Text("➤", color = Color(0xFF2563EB))
            }
        }
    }
}
```

- [ ] **Step 4: 构建验证**

```bash
./gradlew :app:assembleDebug
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: 提交**

```bash
git add -A && git commit -m "feat: add Compose UI for word popup, sentence panel, and llama chat"
```

---

### Task 16: 整合主流程 — FloatingWindowService 对接所有模块

**Files:**
- Modify: `app/src/main/java/com/movieenglish/assistant/floating/FloatingWindowService.kt`
- Modify: `app/src/main/java/com/movieenglish/assistant/MainActivity.kt`

- [ ] **Step 1: 重构 FloatingWindowService 整合全流程**

```kotlin
package com.movieenglish.assistant.floating

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.NotificationCompat
import androidx.lifecycle.lifecycleScope
import com.movieenglish.assistant.App
import com.movieenglish.assistant.capture.ScreenCaptureManager
import com.movieenglish.assistant.ocr.OcrEngine
import com.movieenglish.assistant.subtitle.SubtitleMatcher
import com.movieenglish.assistant.subtitle.SubtitleRepository
import com.movieenglish.assistant.vocabulary.UserVocabulary
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

class FloatingWindowService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var composeView: ComposeView
    private lateinit var screenCaptureManager: ScreenCaptureManager
    private lateinit var ocrEngine: OcrEngine
    private lateinit var subtitleRepository: SubtitleRepository
    private lateinit var userVocabulary: UserVocabulary

    // Current state
    private var currentMovieId: String? = null
    private var parsedSubtitles: List<com.movieenglish.assistant.subtitle.ParsedSubtitle> = emptyList()
    private var currentEntry: com.movieenglish.assistant.data.SubtitleEntry? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        screenCaptureManager = ScreenCaptureManager(this)
        ocrEngine = OcrEngine(this)
        subtitleRepository = SubtitleRepository(this)
        userVocabulary = UserVocabulary(this)

        composeView = ComposeView(this).apply {
            setContent {
                SubtitleBar(
                    text = currentEntry?.text ?: "",
                    difficultWords = getDifficultWords(),
                    masteredWords = getMasteredWords(),
                    timestamp = formatTimestamp(),
                    onWordClick = { word -> handleWordClick(word) },
                    onRedDotClick = { handleRedDotClick() },
                    onSwipeUp = { /* show chat */ }
                )
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundNotification()
        showOverlay()

        // If subtitles loaded, set movie context
        currentMovieId = intent?.getStringExtra("movieId")

        val resultCode = intent?.getIntExtra("resultCode", -1) ?: -1
        val data = intent?.getParcelableExtra<Intent>("data")
        if (resultCode != -1 && data != null) {
            screenCaptureManager.startCapture(resultCode, data) { bitmap ->
                onFrameCaptured(bitmap)
            }
        }

        return START_STICKY
    }

    private fun onFrameCaptured(bitmap: android.graphics.Bitmap) {
        val ocrResult = ocrEngine.recognize(bitmap)
        if (ocrResult.text.isBlank()) return

        // Match against subtitles if available
        val matched = if (parsedSubtitles.isNotEmpty()) {
            SubtitleMatcher.match(ocrResult.text, parsedSubtitles)
        } else null

        if (matched != null) {
            // Look up preprocessed entry
            lifecycleScope.launch {
                val entries = subtitleRepository.getMovieSubtitles(currentMovieId ?: return@launch)
                val entry = entries.find { it.index == matched.index }
                if (entry != null && entry.preprocessed) {
                    currentEntry = entry
                    // Mark words as displayed
                    val words = parseDifficultWords(entry.difficultWordsJson)
                    userVocabulary.onWordsDisplayed(words.map { it.first })
                    updateUI()
                }
            }
        }

        // Refresh Compose
        updateUI()
    }

    private fun handleWordClick(word: String) {
        lifecycleScope.launch {
            userVocabulary.onWordClicked(word)
        }
        // Show word popup - launch dialog activity
        val intent = Intent(this, WordPopupActivity::class.java).apply {
            putExtra("word", word)
            putExtra("entryJson", entryToJson(currentEntry))
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
    }

    private fun handleRedDotClick() {
        val intent = Intent(this, SentencePanelActivity::class.java).apply {
            putExtra("entryJson", entryToJson(currentEntry))
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
    }

    private fun updateUI() {
        composeView.setContent {
            SubtitleBar(
                text = currentEntry?.text ?: "",
                difficultWords = getDifficultWords(),
                masteredWords = getMasteredWords(),
                timestamp = formatTimestamp(),
                onWordClick = { handleWordClick(it) },
                onRedDotClick = { handleRedDotClick() },
                onSwipeUp = { /* Task 17 */ }
            )
        }
    }

    private fun showOverlay() {
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            120,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.BOTTOM }
        windowManager.addView(composeView, params)
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

    private fun getDifficultWords(): List<String> {
        val json = currentEntry?.difficultWordsJson ?: "[]"
        return parseDifficultWords(json)
            .filter { (_, level) -> level >= 3 }
            .map { it.first }
    }

    private fun getMasteredWords(): List<String> {
        val json = currentEntry?.difficultWordsJson ?: "[]"
        return parseDifficultWords(json)
            .filter { (_, level) -> level < 2 }
            .map { it.first }
    }

    private fun parseDifficultWords(json: String): List<Pair<String, Int>> {
        return try {
            val arr = Json.parseToJsonElement(json).jsonArray
            arr.map { elem ->
                val word = elem.jsonObject["word"]?.toString()?.trim('"') ?: ""
                val level = elem.jsonObject["level"]?.toString()?.toIntOrNull() ?: 3
                word to level
            }
        } catch (e: Exception) { emptyList() }
    }

    private fun formatTimestamp(): String {
        val ms = currentEntry?.startTimeMs ?: return ""
        val h = ms / 3600000
        val m = (ms % 3600000) / 60000
        val s = (ms % 60000) / 1000
        return "%02d:%02d:%02d".format(h, m, s)
    }

    private fun entryToJson(entry: com.movieenglish.assistant.data.SubtitleEntry?): String {
        if (entry == null) return "{}"
        return """{"text":"${entry.text}","translation":"${entry.translation}","grammar":"${entry.grammar}","difficultWordsJson":${entry.difficultWordsJson},"idiomsJson":${entry.idiomsJson}}"""
    }

    override fun onDestroy() {
        screenCaptureManager.stopCapture()
        ocrEngine.close()
        windowManager.removeView(composeView)
        super.onDestroy()
    }
}
```

- [ ] **Step 2: 创建弹出 Activity（占位）**

```kotlin
// WordPopupActivity.kt
package com.movieenglish.assistant.floating

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

class WordPopupActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val word = intent.getStringExtra("word") ?: return finish()
        // Will be fully wired in Task 17
        setContent {
            WordPopup(
                word = word,
                phonetic = "/.../",
                meaning = "解释由 llama 提供",
                example = "",
                morphology = null,
                relatedWords = emptyList(),
                onAddToWordbook = { finish() },
                onAskLlama = { finish() },
                onDismiss = { finish() }
            )
        }
    }
}
```

- [ ] **Step 3: 提交**

```bash
git add -A && git commit -m "feat: integrate main pipeline - OCR + matching + UI overlay"
```

---

### Task 17: 字幕导入 UI 与预处理交互

**Files:**
- Modify: `app/src/main/java/com/movieenglish/assistant/MainActivity.kt`

- [ ] **Step 1: 增强 MainActivity 支持字幕导入**

```kotlin
// 在 MainActivity 中添加
private val subtitlePickerLauncher =
    registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { importSubtitle(it) }
    }

private fun importSubtitle(uri: Uri) {
    lifecycleScope.launch {
        val movieId = uri.lastPathSegment ?: "unknown"
        val parsed = subtitleRepository.importSubtitle(movieId, movieId, uri)

        // Start preprocessing
        val pipeline = PreprocessPipeline(this@MainActivity, subtitleRepository)
        pipeline.preprocessMovie(movieId) { progress ->
            // Update UI with progress
            // (wired in full implementation)
        }
    }
}
```

- [ ] **Step 2: 提交**

```bash
git add -A && git commit -m "feat: add subtitle import and preprocessing trigger"
```

---

## 后续任务

本计划覆盖主应用的核心骨架。以下功能在核心跑通后可增量添加：

- AccessibilityService 可选屏幕捕获方案
- 语音输入功能
- 生词复习界面
- 电影收藏管理界面
- 预处理进度 UI 完整实现

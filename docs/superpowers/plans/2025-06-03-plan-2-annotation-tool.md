# 数据标注工具 — 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 构建独立的 Android 标注 App，用于框选电影截图中字幕区域并标注对应文本，导出为标准 OCR 训练数据集。

**Architecture:** 独立 Android App，View-based UI（标注工具更适合传统 View），Canvas 绘制边界框，支持截图导入 + 预标注 + 批量导出。

**Tech Stack:** Kotlin, Android View, Canvas, Room DB, ContentResolver

**Source:** `annotation-tool/src/main/java/com/movieenglish/annotator/`

---

## 项目文件结构

```
annotation-tool/
├── build.gradle.kts
├── src/main/
│   ├── AndroidManifest.xml
│   ├── java/com/movieenglish/annotator/
│   │   ├── MainActivity.kt
│   │   ├── AnnotatorView.kt
│   │   ├── ImageBrowserActivity.kt
│   │   ├── data/
│   │   │   ├── AnnotationDatabase.kt
│   │   │   ├── AnnotationRecord.kt
│   │   │   └── AnnotationDao.kt
│   │   └── export/
│   │       └── DatasetExporter.kt
│   └── res/
│       └── values/strings.xml
```

---

### Task 1: 项目骨架

**Files:**
- Create: `annotation-tool/build.gradle.kts`
- Create: `annotation-tool/src/main/AndroidManifest.xml`
- Create: `annotation-tool/src/main/res/values/strings.xml`

- [ ] **Step 1: 创建 annotation-tool/build.gradle.kts**

```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.movieenglish.annotator"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.movieenglish.annotator"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    annotationProcessor("androidx.room:room-compiler:2.6.1")
    implementation("com.google.code.gson:gson:2.10.1")
}
```

- [ ] **Step 2: 创建 AndroidManifest.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
    <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
    <application android:allowBackup="true" android:label="OCR标注工具"
        android:theme="@style/Theme.AppCompat.Light.NoActionBar">
        <activity android:name=".MainActivity" android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
        <activity android:name=".ImageBrowserActivity" android:exported="false" />
    </application>
</manifest>
```

- [ ] **Step 3: 在 settings.gradle.kts 中添加模块**

```kotlin
// settings.gradle.kts 添加
include(":annotation-tool")
```

- [ ] **Step 4: 提交**

```bash
git add -A && git commit -m "feat: scaffold annotation tool project"
```

---

### Task 2: 数据库层

**Files:**
- Create: `annotation-tool/src/main/java/com/movieenglish/annotator/data/AnnotationRecord.kt`
- Create: `annotation-tool/src/main/java/com/movieenglish/annotator/data/AnnotationDao.kt`
- Create: `annotation-tool/src/main/java/com/movieenglish/annotator/data/AnnotationDatabase.kt`

- [ ] **Step 1: 创建 AnnotationRecord.kt**

```kotlin
package com.movieenglish.annotator.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "annotations")
data class AnnotationRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val imagePath: String,       // 图片在设备上的路径
    val boxLeft: Float,
    val boxTop: Float,
    val boxRight: Float,
    val boxBottom: Float,
    val text: String,
    val annotated: Boolean = false,
    val exported: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
```

- [ ] **Step 2: 创建 AnnotationDao.kt**

```kotlin
package com.movieenglish.annotator.data

import androidx.room.*

@Dao
interface AnnotationDao {
    @Query("SELECT * FROM annotations ORDER BY id")
    suspend fun getAll(): List<AnnotationRecord>

    @Query("SELECT * FROM annotations WHERE annotated = 0 ORDER BY id LIMIT 1")
    suspend fun getNextUnannotated(): AnnotationRecord?

    @Query("SELECT * FROM annotations WHERE annotated = 1 AND exported = 0 ORDER BY id")
    suspend fun getUnexported(): List<AnnotationRecord>

    @Query("SELECT COUNT(*) FROM annotations WHERE annotated = 1")
    suspend fun getAnnotatedCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: AnnotationRecord): Long

    @Update
    suspend fun update(record: AnnotationRecord)

    @Query("UPDATE annotations SET exported = 1 WHERE id IN (:ids)")
    suspend fun markExported(ids: List<Long>)
}
```

- [ ] **Step 3: 创建 AnnotationDatabase.kt**

```kotlin
package com.movieenglish.annotator.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [AnnotationRecord::class], version = 1)
abstract class AnnotationDatabase : RoomDatabase() {
    abstract fun annotationDao(): AnnotationDao

    companion object {
        @Volatile private var instance: AnnotationDatabase? = null
        fun getInstance(context: Context): AnnotationDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AnnotationDatabase::class.java,
                    "annotation_tool.db"
                ).build().also { instance = it }
            }
        }
    }
}
```

- [ ] **Step 4: 提交**

```bash
git add -A && git commit -m "feat: add Room database for annotation records"
```

---

### Task 3: 标注视图（核心 Canvas 交互）

**Files:**
- Create: `annotation-tool/src/main/java/com/movieenglish/annotator/AnnotatorView.kt`

- [ ] **Step 1: 创建 AnnotatorView.kt**

```kotlin
package com.movieenglish.annotator

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

class AnnotatorView(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    private val boxPaint = Paint().apply {
        color = Color.RED
        style = Paint.Style.STROKE
        strokeWidth = 4f
        isAntiAlias = true
    }

    private val cornerPaint = Paint().apply {
        color = 0xFFEF4444.toInt()
        style = Paint.Style.FILL
    }

    private val handleRadius = 15f

    private var imageBitmap: Bitmap? = null
    private var boxRect: RectF? = null
    private var isDrawing = false
    private var startX = 0f
    private var startY = 0f

    private var activeHandle: Int = -1 // 0=TL, 1=TR, 2=BL, 3=BR, -1=none
    private var lastTouchX = 0f
    private var lastTouchY = 0f

    var onBoxChanged: ((RectF?) -> Unit)? = null

    fun setImage(bitmap: Bitmap) {
        imageBitmap = bitmap
        boxRect = null
        invalidate()
    }

    fun getBoxRect(): RectF? = boxRect

    fun setBoxRect(rect: RectF) {
        boxRect = rect
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        imageBitmap?.let { canvas.drawBitmap(it, null, RectF(0f, 0f, width.toFloat(), height.toFloat()), null) }
        boxRect?.let { rect ->
            canvas.drawRect(rect, boxPaint)
            drawHandle(canvas, rect.left, rect.top)    // TL
            drawHandle(canvas, rect.right, rect.top)   // TR
            drawHandle(canvas, rect.left, rect.bottom) // BL
            drawHandle(canvas, rect.right, rect.bottom)// BR
        }
    }

    private fun drawHandle(canvas: Canvas, x: Float, y: Float) {
        canvas.drawCircle(x, y, handleRadius, cornerPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                activeHandle = findHandle(x, y)
                if (activeHandle >= 0) {
                    lastTouchX = x
                    lastTouchY = y
                } else {
                    // Start new box
                    isDrawing = true
                    startX = x
                    startY = y
                    boxRect = null
                }
            }

            MotionEvent.ACTION_MOVE -> {
                if (activeHandle >= 0) {
                    val dx = x - lastTouchX
                    val dy = y - lastTouchY
                    moveHandle(activeHandle, dx, dy)
                    lastTouchX = x
                    lastTouchY = y
                    invalidate()
                } else if (isDrawing) {
                    boxRect = RectF(
                        minOf(startX, x), minOf(startY, y),
                        maxOf(startX, x), maxOf(startY, y)
                    ).also { it.sort() }
                    invalidate()
                }
            }

            MotionEvent.ACTION_UP -> {
                isDrawing = false
                activeHandle = -1
                onBoxChanged?.invoke(boxRect)
            }
        }
        return true
    }

    private fun findHandle(x: Float, y: Float): Int {
        val rect = boxRect ?: return -1
        val handles = listOf(
            rect.left to rect.top,
            rect.right to rect.top,
            rect.left to rect.bottom,
            rect.right to rect.bottom
        )
        return handles.indexOfFirst { (hx, hy) ->
            Math.hypot((x - hx).toDouble(), (y - hy).toDouble()) < handleRadius * 2
        }
    }

    private fun moveHandle(handle: Int, dx: Float, dy: Float) {
        val rect = boxRect ?: return
        when (handle) {
            0 -> { rect.left += dx; rect.top += dy }
            1 -> { rect.right += dx; rect.top += dy }
            2 -> { rect.left += dx; rect.bottom += dy }
            3 -> { rect.right += dx; rect.bottom += dy }
        }
        rect.sort()
    }
}
```

- [ ] **Step 2: 提交**

```bash
git add -A && git commit -m "feat: add annotation view with bounding box and corner handles"
```

---

### Task 4: MainActivity 标注主界面

**Files:**
- Create: `annotation-tool/src/main/java/com/movieenglish/annotator/MainActivity.kt`

- [ ] **Step 1: 创建 MainActivity.kt**

```kotlin
package com.movieenglish.annotator

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.movieenglish.annotator.data.AnnotationDatabase
import com.movieenglish.annotator.data.AnnotationRecord
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var annotatorView: AnnotatorView
    private lateinit var textInput: EditText
    private lateinit var countText: TextView
    private lateinit var currentImagePath: String?
    private val db by lazy { AnnotationDatabase.getInstance(this) }
    private val dao by lazy { db.annotationDao() }

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { loadImage(it) } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(createLayout())
        lifecycleScope.launch { updateCount() }
    }

    private fun createLayout(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL

            // Toolbar
            val toolbar = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(16, 16, 16, 16)
            }
            val importBtn = Button(this@MainActivity).apply {
                text = "📸 导入截图"
                setOnClickListener { pickImageLauncher.launch(arrayOf("image/*")) }
            }
            toolbar.addView(importBtn)

            val prevBtn = Button(this@MainActivity).apply {
                text = "◀"
                setOnClickListener { lifecycleScope.launch { loadPrevious() } }
            }
            toolbar.addView(prevBtn)

            val nextBtn = Button(this@MainActivity).apply {
                text = "▶"
                setOnClickListener { lifecycleScope.launch { loadNextUnannotated() } }
            }
            toolbar.addView(nextBtn)

            countText = TextView(this@MainActivity).apply {
                setPadding(16, 0, 0, 0)
                textSize = 14f
            }
            toolbar.addView(countText)

            addView(toolbar)

            // Annotator view
            annotatorView = AnnotatorView(this@MainActivity).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
                )
            }
            addView(annotatorView)

            // Bottom panel
            val bottomPanel = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(16, 12, 16, 12)
            }
            textInput = EditText(this@MainActivity).apply {
                hint = "输入字幕文本..."
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            bottomPanel.addView(textInput)

            val saveBtn = Button(this@MainActivity).apply {
                text = "✓ 保存"
                setOnClickListener { lifecycleScope.launch { saveAnnotation() } }
            }
            bottomPanel.addView(saveBtn)

            val exportBtn = Button(this@MainActivity).apply {
                text = "📤 导出"
                setOnClickListener { lifecycleScope.launch { exportDataset() } }
            }
            bottomPanel.addView(exportBtn)

            addView(bottomPanel)
            return this
        }
    }

    private fun loadImage(uri: Uri) {
        currentImagePath = uri.lastPathSegment ?: "unknown"
        val stream = contentResolver.openInputStream(uri)
        val bitmap = BitmapFactory.decodeStream(stream)
        stream?.close()
        annotatorView.setImage(bitmap)
    }

    private suspend fun loadNextUnannotated() {
        val record = dao.getNextUnannotated()
        if (record != null) {
            currentImagePath = record.imagePath
            textInput.setText(record.text)
            try {
                val bitmap = BitmapFactory.decodeFile(record.imagePath)
                annotatorView.setImage(bitmap)
            } catch (e: Exception) { /* file may be content URI, skip */ }
        } else {
            Toast.makeText(this, "全部已标注", Toast.LENGTH_SHORT).show()
        }
    }

    private suspend fun loadPrevious() {
        // Navigate back - simplified: reload current
        Toast.makeText(this, "切换到上一张", Toast.LENGTH_SHORT).show()
    }

    private suspend fun saveAnnotation() {
        val box = annotatorView.getBoxRect()
        val text = textInput.text.toString().trim()
        if (box == null || text.isEmpty() || currentImagePath == null) {
            Toast.makeText(this, "请框选区域并输入文字", Toast.LENGTH_SHORT).show()
            return
        }

        dao.insert(AnnotationRecord(
            imagePath = currentImagePath ?: "",
            boxLeft = box.left / annotatorView.width,
            boxTop = box.top / annotatorView.height,
            boxRight = box.right / annotatorView.width,
            boxBottom = box.bottom / annotatorView.height,
            text = text,
            annotated = true
        ))
        textInput.text.clear()
        annotatorView.setBoxRect(android.graphics.RectF())
        updateCount()
        Toast.makeText(this, "已保存", Toast.LENGTH_SHORT).show()
    }

    private suspend fun updateCount() {
        val count = dao.getAnnotatedCount()
        countText.text = "已标注: $count 张"
    }

    private suspend fun exportDataset() {
        val exporter = com.movieenglish.annotator.export.DatasetExporter(this)
        val result = exporter.export()
        Toast.makeText(this, "已导出到: ${result.absolutePath}", Toast.LENGTH_LONG).show()
    }
}
```

- [ ] **Step 2: 提交**

```bash
git add -A && git commit -m "feat: add main annotation activity with import/save/export"
```

---

### Task 5: 数据集导出

**Files:**
- Create: `annotation-tool/src/main/java/com/movieenglish/annotator/export/DatasetExporter.kt`

- [ ] **Step 1: 创建 DatasetExporter.kt**

```kotlin
package com.movieenglish.annotator.export

import android.content.Context
import android.graphics.BitmapFactory
import android.os.Environment
import com.movieenglish.annotator.data.AnnotationDatabase
import com.google.gson.Gson
import java.io.File

class DatasetExporter(private val context: Context) {
    private val dao = AnnotationDatabase.getInstance(context).annotationDao()

    suspend fun export(): File {
        val records = dao.getUnexported()
        val exportDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            "ocr_dataset_${System.currentTimeMillis()}"
        )
        exportDir.mkdirs()

        val imagesDir = File(exportDir, "images")
        imagesDir.mkdirs()

        val annotations = mutableListOf<Map<String, Any>>()
        val exportedIds = mutableListOf<Long>()

        for ((i, record) in records.withIndex()) {
            val imageName = "img_%05d.jpg".format(i)
            val destFile = File(imagesDir, imageName)

            try {
                // Copy image
                val bitmap = BitmapFactory.decodeFile(record.imagePath)
                if (bitmap != null) {
                    destFile.outputStream().use { out ->
                        bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 95, out)
                    }
                    bitmap.recycle()
                } else continue

                // Create annotation entry
                annotations.add(mapOf(
                    "image" to "images/$imageName",
                    "bbox" to listOf(record.boxLeft, record.boxTop, record.boxRight, record.boxBottom),
                    "text" to record.text
                ))
                exportedIds.add(record.id)
            } catch (e: Exception) { continue }
        }

        // Write annotations JSON
        val jsonFile = File(exportDir, "annotations.json")
        jsonFile.writeText(Gson().toJson(mapOf(
            "version" to 1,
            "format" to "ocr_detection",
            "total" to annotations.size,
            "annotations" to annotations
        )))

        // Mark as exported
        dao.markExported(exportedIds)

        return exportDir
    }
}
```

- [ ] **Step 2: 提交**

```bash
git add -A && git commit -m "feat: add dataset exporter with JSON annotation format"
```

---

### Task 6: 预标注支持

**Files:**
- Modify: `annotation-tool/src/main/java/com/movieenglish/annotator/MainActivity.kt`

- [ ] **Step 1: 添加预标注逻辑**

在 `MainActivity` 中新增方法：当导入截图时，如果有 TFLite 模型，先跑一遍推理，将结果填入 textInput 并预画边界框。这一步依赖 OCR 模型已产出（计划三完成后才有 .tflite 文件）。初始版本此功能为 no-op，模型就位后通过 Gradle 依赖引入 TFLite。

```kotlin
// 在 MainActivity 中新增
private fun runPreAnnotation(bitmap: android.graphics.Bitmap) {
    // Placeholder: 当 OCR 模型可用时，调用 TFLite 推理
    // 将结果预填入 textInput，边界框预画在 annotatorView
    // 初始阶段跳过
}
```

- [ ] **Step 2: 提交**

```bash
git add -A && git commit -m "feat: add pre-annotation placeholder for future OCR model integration"
```

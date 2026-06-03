package com.movieenglish.annotator

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
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
    private var currentImagePath: String? = null
    private val db by lazy { AnnotationDatabase.getInstance(this) }
    private val dao by lazy { db.annotationDao() }

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { loadImage(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(createLayout())
        lifecycleScope.launch { updateCount() }
    }

    private fun createLayout(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val toolbar = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL; setPadding(16, 16, 16, 16)
            }
            toolbar.addView(Button(this@MainActivity).apply {
                text = "📸 导入截图"; setOnClickListener { pickImageLauncher.launch(arrayOf("image/*")) }
            })
            toolbar.addView(Button(this@MainActivity).apply {
                text = "◀"; setOnClickListener { lifecycleScope.launch { loadNextUnannotated() } }
            })
            toolbar.addView(Button(this@MainActivity).apply {
                text = "▶"; setOnClickListener { lifecycleScope.launch { loadNextUnannotated() } }
            })
            countText = TextView(this@MainActivity).apply { setPadding(16, 0, 0, 0); textSize = 14f }
            toolbar.addView(countText)
            addView(toolbar)

            annotatorView = AnnotatorView(this@MainActivity).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
            }
            addView(annotatorView)

            val bottomPanel = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL; setPadding(16, 12, 16, 12)
            }
            textInput = EditText(this@MainActivity).apply {
                hint = "输入字幕文本..."; layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            bottomPanel.addView(textInput)
            bottomPanel.addView(Button(this@MainActivity).apply {
                text = "✓ 保存"; setOnClickListener { lifecycleScope.launch { saveAnnotation() } }
            })
            bottomPanel.addView(Button(this@MainActivity).apply {
                text = "📤 导出"; setOnClickListener { lifecycleScope.launch { exportDataset() } }
            })
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
            } catch (e: Exception) { /* skip */ }
        } else {
            Toast.makeText(this, "全部已标注", Toast.LENGTH_SHORT).show()
        }
    }

    private suspend fun saveAnnotation() {
        val box = annotatorView.getBoxRect()
        val text = textInput.text.toString().trim()
        if (box == null || text.isEmpty() || currentImagePath == null) {
            Toast.makeText(this, "请框选区域并输入文字", Toast.LENGTH_SHORT).show(); return
        }
        dao.insert(AnnotationRecord(
            imagePath = currentImagePath ?: "",
            boxLeft = box.left / annotatorView.width, boxTop = box.top / annotatorView.height,
            boxRight = box.right / annotatorView.width, boxBottom = box.bottom / annotatorView.height,
            text = text, annotated = true
        ))
        textInput.text.clear(); annotatorView.setBoxRect(android.graphics.RectF())
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

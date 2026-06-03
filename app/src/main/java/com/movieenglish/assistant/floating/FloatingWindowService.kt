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
import com.movieenglish.assistant.subtitle.ParsedSubtitle

class FloatingWindowService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var composeView: ComposeView
    private lateinit var screenCaptureManager: ScreenCaptureManager
    private lateinit var ocrEngine: OcrEngine
    private lateinit var subtitleRepository: SubtitleRepository
    private lateinit var userVocabulary: UserVocabulary

    private var currentMovieId: String? = null
    private var parsedSubtitles: List<ParsedSubtitle> = emptyList()
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
                    onSwipeUp = {}
                )
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundNotification()
        showOverlay()

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

        val matched = if (parsedSubtitles.isNotEmpty()) {
            SubtitleMatcher.match(ocrResult.text, parsedSubtitles)
        } else null

        if (matched != null) {
            lifecycleScope.launch {
                val entries = subtitleRepository.getMovieSubtitles(currentMovieId ?: return@launch)
                val entry = entries.find { it.index == matched.index }
                if (entry != null && entry.preprocessed) {
                    currentEntry = entry
                    val words = parseDifficultWords(entry.difficultWordsJson)
                    userVocabulary.onWordsDisplayed(words.map { it.first })
                    updateUI()
                }
            }
        }

        updateUI()
    }

    private fun handleWordClick(word: String) {
        lifecycleScope.launch {
            userVocabulary.onWordClicked(word)
        }
        // Launch word popup dialog (simplified: use a dialog-themed activity)
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
                onSwipeUp = {}
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

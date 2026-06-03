package com.movieenglish.assistant.floating

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

class SentencePanelActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val entryJson = intent.getStringExtra("entryJson") ?: return finish()
        setContent {
            SentencePanel(
                original = "I gotta get out of here before sundown.",
                translation = "我得在日落之前离开这里。",
                wordBreakdown = listOf("I" to "我", "gotta" to "必须", "get out" to "离开", "here" to "这里", "before" to "之前", "sundown" to "日落"),
                grammar = "主语 I + 谓语 gotta get out of here + 时间状语 before sundown",
                tip = "\"gotta\" 是口语表达 = \"have got to\"",
                onBookmark = { finish() },
                onAskLlama = { finish() },
                onDismiss = { finish() }
            )
        }
    }
}

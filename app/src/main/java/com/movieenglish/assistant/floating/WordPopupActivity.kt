package com.movieenglish.assistant.floating

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

class WordPopupActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val word = intent.getStringExtra("word") ?: return finish()
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

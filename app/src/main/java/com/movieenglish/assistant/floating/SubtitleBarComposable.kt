package com.movieenglish.assistant.floating

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextDecoration
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
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(Color(0xFFEF4444))
                .clickable { onRedDotClick() }
        )

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = buildHighlightedText(text, difficultWords, masteredWords),
            fontSize = 15.sp,
            lineHeight = 22.sp,
            modifier = Modifier.weight(1f)
        )

        Spacer(modifier = Modifier.width(8.dp))

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
                    textDecoration = TextDecoration.Underline
                )) { append(w) }
            }
            else -> {
                withStyle(SpanStyle(color = Color(0xFFE2E8F0))) { append(w) }
            }
        }
    }
}

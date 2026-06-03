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

            if (morphology != null && morphology.root.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                MorphologyRow(morphology)
            }

            if (relatedWords.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "同类词：${relatedWords.joinToString(" · ")}",
                    fontSize = 11.sp, color = Color(0xFF64748B)
                )
            }

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
        Text("$text", fontSize = 11.sp, color = color)
    }
}

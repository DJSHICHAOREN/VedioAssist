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
    wordBreakdown: List<Pair<String, String>>,
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
                    Box(Modifier
                        .background(Color(0xFF1E293B), RoundedCornerShape(6.dp))
                        .padding(6.dp, 3.dp)
                    ) {
                        Text("$w $m", fontSize = 11.sp, color = Color(0xFFCBD5E1))
                    }
                }
            }

            SectionLabel("📐 语法分析")
            Text(grammar, fontSize = 13.sp, color = Color(0xFF94A3B8))

            if (tip.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(tip, fontSize = 12.sp, color = Color(0xFF64748B))
            }

            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onBookmark, modifier = Modifier.weight(1f),
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

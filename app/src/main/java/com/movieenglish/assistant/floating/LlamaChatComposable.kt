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
            .background(
                Color(0xFF0F172A),
                RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
            )
    ) {
        Row(
            Modifier.fillMaxWidth().background(Color(0xFF1E293B)).padding(12.dp, 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("📌 当前字幕:", fontSize = 11.sp, color = Color(0xFF64748B))
            Spacer(Modifier.width(8.dp))
            Text(contextSentence, fontSize = 11.sp, color = Color(0xFF94A3B8))
        }

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
                        ) { Text("🦙", fontSize = 10.sp) }
                        Spacer(Modifier.width(8.dp))
                    }
                    Box(
                        modifier = Modifier
                            .widthIn(max = 280.dp)
                            .background(
                                if (msg.isUser) Color(0xFF2563EB) else Color(0xFF1E293B),
                                RoundedCornerShape(10.dp)
                            )
                            .padding(10.dp, 8.dp)
                    ) {
                        Text(msg.text, fontSize = 13.sp, color = Color(0xFFE2E8F0))
                    }
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(12.dp)
                .background(Color(0xFF1E293B), RoundedCornerShape(20.dp)).padding(4.dp),
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
            }) { Text("➤", color = Color(0xFF2563EB)) }
        }
    }
}

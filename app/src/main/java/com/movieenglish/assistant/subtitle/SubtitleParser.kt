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
            content.contains("-->") && !content.contains("[Events]") -> parseSrt(content)
            content.contains("[Events]") || content.contains("Format:") -> parseAss(content)
            content.contains("WEBVTT") -> parseVtt(content)
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
        return "UTF-8"
    }
}

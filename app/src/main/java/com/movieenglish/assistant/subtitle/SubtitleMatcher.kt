package com.movieenglish.assistant.subtitle

object SubtitleMatcher {

    /** Use OCR text to fuzzy-match the best entry in a subtitle list */
    fun match(ocrText: String, subtitles: List<ParsedSubtitle>): ParsedSubtitle? {
        if (ocrText.isBlank() || subtitles.isEmpty()) return null

        val cleanedOcr = cleanText(ocrText)

        var bestMatch: ParsedSubtitle? = null
        var bestScore = Int.MAX_VALUE

        for (sub in subtitles) {
            val cleanedSub = cleanText(sub.text)
            val distance = levenshteinDistance(cleanedOcr, cleanedSub)
            if (distance < bestScore) {
                bestScore = distance
                bestMatch = sub
            }
            if (distance == 0) return sub
        }

        return if (bestScore <= cleanedOcr.length / 2) bestMatch else null
    }

    private fun cleanText(text: String): String {
        return text.lowercase()
            .replace(Regex("[^a-z0-9\\s]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun levenshteinDistance(a: String, b: String): Int {
        val dp = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) dp[i][0] = i
        for (j in 0..b.length) dp[0][j] = j
        for (i in 1..a.length) {
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,
                    dp[i][j - 1] + 1,
                    dp[i - 1][j - 1] + cost
                )
            }
        }
        return dp[a.length][b.length]
    }
}

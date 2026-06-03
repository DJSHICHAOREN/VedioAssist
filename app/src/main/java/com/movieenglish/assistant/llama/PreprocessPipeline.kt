package com.movieenglish.assistant.llama

import android.content.Context
import com.movieenglish.assistant.data.*
import com.movieenglish.assistant.subtitle.SubtitleRepository
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.builtins.ListSerializer

class PreprocessPipeline(
    private val context: Context,
    private val repository: SubtitleRepository
) {
    private val llamaEngine = LlamaEngine(context)
    private var isRunning = false
    private var isCancelled = false

    data class Progress(
        val current: Int,
        val total: Int,
        val currentText: String = ""
    )

    private val systemPrompt = """
You are an English teacher helping a Chinese student learn English through movies.
Analyze this subtitle sentence and output ONLY valid JSON, no other text:

Sentence: {SENTENCE}

Output JSON:
{
  "translation": "整句中文翻译",
  "grammar": "简短语法分析",
  "difficult_words": [
    {
      "word": "单词",
      "level": 1-5,
      "meaning": "中文释义",
      "morphology": {"prefix": "前缀及含义或null", "root": "词根及含义", "suffix": "后缀及含义或null"}
    }
  ],
  "idioms": []
}
""".trimIndent()

    suspend fun preprocessMovie(
        movieId: String,
        onProgress: (Progress) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        isRunning = true
        isCancelled = false

        try {
            llamaEngine.init()
            val entries = repository.getUnprocessedEntries(movieId)
            val total = entries.size

            for ((i, entry) in entries.withIndex()) {
                if (isCancelled) break

                onProgress(Progress(i + 1, total, entry.text))

                val prompt = systemPrompt.replace("{SENTENCE}", entry.text)
                val rawJson = llamaEngine.generate(prompt, maxTokens = 512)
                val parsed = parseResponse(rawJson)

                repository.updateEntry(entry.copy(
                    translation = parsed.translation,
                    grammar = parsed.grammar,
                    difficultWordsJson = Json.encodeToString(
                        ListSerializer(PreprocessWord.serializer()), parsed.difficultWords
                    ),
                    idiomsJson = Json.encodeToString(
                        ListSerializer(String.serializer()), parsed.idioms
                    ),
                    preprocessed = true
                ))

                val wordDao = AppDatabase.getInstance(context).wordRecordDao()
                for (word in parsed.difficultWords) {
                    val existing = wordDao.getWord(word.word)
                    val record = existing?.copy(
                        meaning = word.meaning,
                        morphologyJson = Json.encodeToString(
                            Morphology.serializer(), word.morphology
                        )
                    ) ?: WordRecord(
                        word = word.word,
                        level = word.level.toFloat(),
                        meaning = word.meaning,
                        morphologyJson = Json.encodeToString(
                            Morphology.serializer(), word.morphology
                        )
                    )
                    wordDao.upsert(record)
                }

                repository.updatePreprocessProgress(movieId, i + 1, false)
            }

            repository.updatePreprocessProgress(
                movieId, entries.size, !isCancelled
            )
            !isCancelled
        } finally {
            isRunning = false
            llamaEngine.close()
        }
    }

    fun cancel() { isCancelled = true }
    fun running(): Boolean = isRunning

    private fun parseResponse(json: String): PreprocessResponse {
        return try {
            val cleanJson = json
                .substringAfter("```json", json)
                .substringBefore("```", json)
                .trim()
            Json.decodeFromString(PreprocessResponse.serializer(), cleanJson)
        } catch (e: Exception) {
            PreprocessResponse("", "", emptyList(), emptyList())
        }
    }
}

@Serializable
data class PreprocessResponse(
    val translation: String = "",
    val grammar: String = "",
    val difficult_words: List<PreprocessWord> = emptyList(),
    val idioms: List<String> = emptyList()
)

@Serializable
data class PreprocessWord(
    val word: String,
    val level: Int = 3,
    val meaning: String = "",
    val morphology: Morphology = Morphology()
)

@Serializable
data class Morphology(
    val prefix: String? = null,
    val root: String = "",
    val suffix: String? = null
)

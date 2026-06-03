package com.movieenglish.assistant.vocabulary

import android.content.Context
import com.movieenglish.assistant.data.AppDatabase
import com.movieenglish.assistant.data.WordRecord

class UserVocabulary(context: Context) {

    private val wordDao = AppDatabase.getInstance(context).wordRecordDao()

    suspend fun getWordLevel(word: String): Float {
        return wordDao.getWord(word)?.level ?: 3.0f
    }

    suspend fun onWordClicked(word: String) {
        val record = wordDao.getWord(word)
        val model = record?.let { WordLevelModel(it.level, it.timesClicked, it.timesIgnored, it.bookmarked, it.mastered) }
            ?: WordLevelModel(3.0f, 0, 0, false, false)
        val updated = model.onWordClicked()
        wordDao.upsert(WordRecord(
            word = word,
            level = updated.level,
            timesShown = (record?.timesShown ?: 0),
            timesClicked = updated.clickCount,
            timesIgnored = updated.ignoreCount,
            bookmarked = updated.bookmarked,
            mastered = updated.mastered,
            lastSeenAt = System.currentTimeMillis(),
            morphologyJson = record?.morphologyJson ?: "",
            meaning = record?.meaning ?: ""
        ))
    }

    suspend fun onWordsDisplayed(words: List<String>) {
        for (word in words) {
            val record = wordDao.getWord(word)
            val model = record?.let { WordLevelModel(it.level, it.timesClicked, it.timesIgnored, it.bookmarked, it.mastered) }
                ?: WordLevelModel(3.0f, 0, 0, false, false)
            val updated = model.onWordIgnored()
            wordDao.upsert(WordRecord(
                word = word,
                level = updated.level,
                timesShown = (record?.timesShown ?: 0) + 1,
                timesClicked = record?.timesClicked ?: 0,
                timesIgnored = updated.ignoreCount,
                bookmarked = updated.bookmarked,
                mastered = updated.mastered,
                lastSeenAt = System.currentTimeMillis(),
                morphologyJson = record?.morphologyJson ?: "",
                meaning = record?.meaning ?: ""
            ))
        }
    }

    suspend fun bookmarkWord(word: String) {
        val record = wordDao.getWord(word)
        val model = record?.let { WordLevelModel(it.level, it.timesClicked, it.timesIgnored, it.bookmarked, it.mastered) }
            ?: WordLevelModel(3.0f, 0, 0, false, false)
        val updated = model.bookmark()
        wordDao.upsert(WordRecord(
            word = word,
            level = updated.level,
            timesShown = record?.timesShown ?: 0,
            timesClicked = record?.timesClicked ?: 0,
            timesIgnored = record?.timesIgnored ?: 0,
            bookmarked = true,
            mastered = false,
            lastSeenAt = System.currentTimeMillis(),
            morphologyJson = record?.morphologyJson ?: "",
            meaning = record?.meaning ?: ""
        ))
    }

    suspend fun markMastered(word: String) {
        val record = wordDao.getWord(word)
        val model = record?.let { WordLevelModel(it.level, it.timesClicked, it.timesIgnored, it.bookmarked, it.mastered) }
            ?: WordLevelModel(3.0f, 0, 0, false, false)
        val updated = model.markMastered()
        wordDao.upsert(WordRecord(
            word = word,
            level = updated.level,
            timesShown = record?.timesShown ?: 0,
            timesClicked = record?.timesClicked ?: 0,
            timesIgnored = record?.timesIgnored ?: 0,
            bookmarked = record?.bookmarked ?: false,
            mastered = true,
            lastSeenAt = System.currentTimeMillis(),
            morphologyJson = record?.morphologyJson ?: "",
            meaning = record?.meaning ?: ""
        ))
    }
}

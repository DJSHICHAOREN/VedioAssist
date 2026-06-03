package com.movieenglish.assistant.data

import androidx.room.*

@Dao
interface WordRecordDao {
    @Query("SELECT * FROM word_records WHERE word = :word")
    suspend fun getWord(word: String): WordRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(record: WordRecord)

    @Query("SELECT * FROM word_records WHERE bookmarked = 1 ORDER BY lastSeenAt DESC")
    suspend fun getBookmarked(): List<WordRecord>

    @Query("SELECT * FROM word_records WHERE mastered = 0 ORDER BY level DESC LIMIT :limit")
    suspend fun getTopDifficultWords(limit: Int = 100): List<WordRecord>
}

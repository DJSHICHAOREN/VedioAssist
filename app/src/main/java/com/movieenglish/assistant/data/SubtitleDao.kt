package com.movieenglish.assistant.data

import androidx.room.*

@Dao
interface SubtitleDao {
    @Query("SELECT * FROM subtitle_entries WHERE movieId = :movieId ORDER BY `index`")
    suspend fun getByMovie(movieId: String): List<SubtitleEntry>

    @Query("SELECT * FROM subtitle_entries WHERE movieId = :movieId AND preprocessed = 0 ORDER BY `index`")
    suspend fun getUnprocessed(movieId: String): List<SubtitleEntry>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<SubtitleEntry>)

    @Update
    suspend fun update(entry: SubtitleEntry)

    @Query("SELECT * FROM subtitle_entries WHERE movieId = :movieId AND text LIKE '%' || :searchText || '%' LIMIT 5")
    suspend fun searchByText(movieId: String, searchText: String): List<SubtitleEntry>
}

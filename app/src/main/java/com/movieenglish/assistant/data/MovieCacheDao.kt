package com.movieenglish.assistant.data

import androidx.room.*

@Dao
interface MovieCacheDao {
    @Query("SELECT * FROM movie_caches WHERE movieId = :movieId")
    suspend fun getByMovieId(movieId: String): MovieCache?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(cache: MovieCache)

    @Query("SELECT * FROM movie_caches ORDER BY importedAt DESC")
    suspend fun getAll(): List<MovieCache>
}

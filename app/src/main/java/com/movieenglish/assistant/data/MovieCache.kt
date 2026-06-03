package com.movieenglish.assistant.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "movie_caches")
data class MovieCache(
    @PrimaryKey val movieId: String,
    val title: String = "",
    val subtitleFilePath: String = "",
    val totalEntries: Int = 0,
    val preprocessedCount: Int = 0,
    val preprocessingComplete: Boolean = false,
    val importedAt: Long = System.currentTimeMillis()
)

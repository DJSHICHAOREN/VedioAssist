package com.movieenglish.assistant.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "subtitle_entries")
data class SubtitleEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val movieId: String,
    val index: Int,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val text: String,
    val translation: String = "",
    val grammar: String = "",
    val difficultWordsJson: String = "[]",
    val idiomsJson: String = "[]",
    val preprocessed: Boolean = false
)

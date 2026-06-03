package com.movieenglish.assistant.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "word_records")
data class WordRecord(
    @PrimaryKey val word: String,
    val level: Float = 3.0f,
    val timesShown: Int = 0,
    val timesClicked: Int = 0,
    val timesIgnored: Int = 0,
    val bookmarked: Boolean = false,
    val mastered: Boolean = false,
    val lastSeenAt: Long = 0,
    val morphologyJson: String = "",
    val meaning: String = ""
)

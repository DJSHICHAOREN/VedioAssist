package com.movieenglish.annotator.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "annotations")
data class AnnotationRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val imagePath: String,
    val boxLeft: Float,
    val boxTop: Float,
    val boxRight: Float,
    val boxBottom: Float,
    val text: String,
    val annotated: Boolean = false,
    val exported: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

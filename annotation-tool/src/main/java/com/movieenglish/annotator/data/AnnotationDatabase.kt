package com.movieenglish.annotator.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [AnnotationRecord::class], version = 1)
abstract class AnnotationDatabase : RoomDatabase() {
    abstract fun annotationDao(): AnnotationDao

    companion object {
        @Volatile private var instance: AnnotationDatabase? = null
        fun getInstance(context: Context): AnnotationDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AnnotationDatabase::class.java,
                    "annotation_tool.db"
                ).build().also { instance = it }
            }
        }
    }
}

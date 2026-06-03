package com.movieenglish.assistant.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [SubtitleEntry::class, WordRecord::class, MovieCache::class],
    version = 1
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun subtitleDao(): SubtitleDao
    abstract fun wordRecordDao(): WordRecordDao
    abstract fun movieCacheDao(): MovieCacheDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "movie_english.db"
                ).build().also { instance = it }
            }
        }
    }
}

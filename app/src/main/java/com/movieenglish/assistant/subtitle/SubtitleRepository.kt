package com.movieenglish.assistant.subtitle

import android.content.Context
import android.net.Uri
import com.movieenglish.assistant.data.*

class SubtitleRepository(private val context: Context) {

    private val db = AppDatabase.getInstance(context)

    suspend fun importSubtitle(movieId: String, title: String, uri: Uri): List<ParsedSubtitle> {
        val parsed = SubtitleParser.parse(context, uri)
        val entries = parsed.map { sub ->
            SubtitleEntry(
                movieId = movieId,
                index = sub.index,
                startTimeMs = sub.startTimeMs,
                endTimeMs = sub.endTimeMs,
                text = sub.text
            )
        }
        db.subtitleDao().insertAll(entries)
        db.movieCacheDao().upsert(
            MovieCache(
                movieId = movieId,
                title = title,
                totalEntries = entries.size
            )
        )
        return parsed
    }

    suspend fun getMovieSubtitles(movieId: String): List<SubtitleEntry> {
        return db.subtitleDao().getByMovie(movieId)
    }

    suspend fun getUnprocessedEntries(movieId: String): List<SubtitleEntry> {
        return db.subtitleDao().getUnprocessed(movieId)
    }

    suspend fun updateEntry(entry: SubtitleEntry) {
        db.subtitleDao().update(entry)
    }

    suspend fun getCachedMovies(): List<MovieCache> {
        return db.movieCacheDao().getAll()
    }

    suspend fun getMovieCache(movieId: String): MovieCache? {
        return db.movieCacheDao().getByMovieId(movieId)
    }

    suspend fun updatePreprocessProgress(movieId: String, preprocessedCount: Int, complete: Boolean) {
        val cache = db.movieCacheDao().getByMovieId(movieId) ?: return
        db.movieCacheDao().upsert(cache.copy(
            preprocessedCount = preprocessedCount,
            preprocessingComplete = complete
        ))
    }
}

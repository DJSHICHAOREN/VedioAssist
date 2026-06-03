package com.movieenglish.annotator.data

import androidx.room.*

@Dao
interface AnnotationDao {
    @Query("SELECT * FROM annotations ORDER BY id")
    suspend fun getAll(): List<AnnotationRecord>

    @Query("SELECT * FROM annotations WHERE annotated = 0 ORDER BY id LIMIT 1")
    suspend fun getNextUnannotated(): AnnotationRecord?

    @Query("SELECT * FROM annotations WHERE annotated = 1 AND exported = 0 ORDER BY id")
    suspend fun getUnexported(): List<AnnotationRecord>

    @Query("SELECT COUNT(*) FROM annotations WHERE annotated = 1")
    suspend fun getAnnotatedCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: AnnotationRecord): Long

    @Update
    suspend fun update(record: AnnotationRecord)

    @Query("UPDATE annotations SET exported = 1 WHERE id IN (:ids)")
    suspend fun markExported(ids: List<Long>)
}

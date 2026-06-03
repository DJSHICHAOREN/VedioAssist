package com.movieenglish.annotator.export

import android.content.Context
import android.graphics.BitmapFactory
import android.os.Environment
import com.movieenglish.annotator.data.AnnotationDatabase
import com.google.gson.Gson
import java.io.File

class DatasetExporter(private val context: Context) {
    private val dao = AnnotationDatabase.getInstance(context).annotationDao()

    suspend fun export(): File {
        val records = dao.getUnexported()
        val exportDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            "ocr_dataset_${System.currentTimeMillis()}"
        )
        exportDir.mkdirs()
        val imagesDir = File(exportDir, "images"); imagesDir.mkdirs()

        val annotations = mutableListOf<Map<String, Any>>()
        val exportedIds = mutableListOf<Long>()

        for ((i, record) in records.withIndex()) {
            val imageName = "img_%05d.jpg".format(i)
            val destFile = File(imagesDir, imageName)
            try {
                val bitmap = BitmapFactory.decodeFile(record.imagePath)
                if (bitmap != null) {
                    destFile.outputStream().use { out ->
                        bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 95, out)
                    }
                    bitmap.recycle()
                } else continue
                annotations.add(mapOf(
                    "image" to "images/$imageName",
                    "bbox" to listOf(record.boxLeft, record.boxTop, record.boxRight, record.boxBottom),
                    "text" to record.text
                ))
                exportedIds.add(record.id)
            } catch (e: Exception) { continue }
        }

        val jsonFile = File(exportDir, "annotations.json")
        jsonFile.writeText(Gson().toJson(mapOf(
            "version" to 1, "format" to "ocr_detection",
            "total" to annotations.size, "annotations" to annotations
        )))

        dao.markExported(exportedIds)
        return exportDir
    }
}

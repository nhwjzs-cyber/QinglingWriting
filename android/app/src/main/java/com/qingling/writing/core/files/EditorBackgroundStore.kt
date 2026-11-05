package com.qingling.writing.core.files

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlin.coroutines.coroutineContext

/** Bounded private copy: no broad storage permission and no persisted external URI dependency. */
class EditorBackgroundStore(context: Context) {
    private val resolver = context.applicationContext.contentResolver
    private val directory = File(context.filesDir, "editor-backgrounds")

    suspend fun importImage(uri: Uri): String = withContext(Dispatchers.IO) {
        directory.mkdirs()
        val input = File.createTempFile("image-", ".tmp", directory)
        val output = File(directory, "${UUID.randomUUID()}.jpg")
        try {
            resolver.openInputStream(uri)?.use { source ->
                input.outputStream().use { target ->
                    val buffer = ByteArray(8192)
                    var total = 0L
                    while (true) {
                        coroutineContext.ensureActive()
                        val count = source.read(buffer)
                        if (count < 0) break
                        total += count
                        require(total <= 12L * 1024 * 1024) { "IMAGE_TOO_LARGE" }
                        target.write(buffer, 0, count)
                    }
                }
            } ?: error("IMAGE_UNREADABLE")
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(input.path, bounds)
            require(bounds.outWidth > 0 && bounds.outHeight > 0) { "IMAGE_UNREADABLE" }
            var sample = 1
            while (bounds.outWidth / sample > 1600 || bounds.outHeight / sample > 1600) sample *= 2
            val bitmap = BitmapFactory.decodeFile(input.path, BitmapFactory.Options().apply { inSampleSize = sample })
                ?: error("IMAGE_UNREADABLE")
            try {
                output.outputStream().use { require(bitmap.compress(Bitmap.CompressFormat.JPEG, 88, it)) }
            } finally { bitmap.recycle() }
            coroutineContext.ensureActive()
            output.absolutePath
        } catch (failure: Throwable) {
            output.delete()
            throw failure
        } finally { input.delete() }
    }

    suspend fun read(path: String): Bitmap? = withContext(Dispatchers.IO) {
        if (path.isBlank()) return@withContext null
        val file = File(path)
        if (file.parentFile?.canonicalFile != directory.canonicalFile) return@withContext null
        BitmapFactory.decodeFile(file.path)
    }

    suspend fun remove(path: String) = withContext(Dispatchers.IO) {
        if (path.isNotBlank()) {
            val file = File(path)
            if (file.parentFile?.canonicalFile == directory.canonicalFile) file.delete()
        }
    }
}

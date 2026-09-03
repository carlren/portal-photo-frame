package com.carlren.photoframe

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

/** Creates a compact, correctly oriented display copy so slideshow transitions never decode originals. */
object DisplayPhotoOptimizer {
    const val MAX_EDGE = 2560
    const val CACHE_DIR = "smb_photos_display"
    private const val JPEG_QUALITY = 92
    private const val TAG = "PhotoFrame"

    internal fun targetSize(width: Int, height: Int, maxEdge: Int = MAX_EDGE): Pair<Int, Int> {
        require(width > 0 && height > 0 && maxEdge > 0)
        val longest = max(width, height)
        if (longest <= maxEdge) return width to height
        val scale = maxEdge.toFloat() / longest
        return (width * scale).roundToInt().coerceAtLeast(1) to
            (height * scale).roundToInt().coerceAtLeast(1)
    }

    fun prepare(context: Context, source: File): File? {
        val outputDir = File(context.cacheDir, CACHE_DIR).apply { mkdirs() }
        val output = File(outputDir, "${source.name}.display.jpg")
        if (
            output.exists() &&
                output.length() > 1_024 &&
                output.lastModified() >= source.lastModified()
        ) return output

        val startedAt = System.currentTimeMillis()
        val partial = File(outputDir, "${source.name}.display.jpg.part").apply { delete() }
        return try {
            val sourceExif = runCatching { ExifInterface(source.absolutePath) }.getOrNull()
            val dateOriginal = sourceExif?.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
            val date = sourceExif?.getAttribute(ExifInterface.TAG_DATETIME)
            val sourceDecoder = ImageDecoder.createSource(source)
            val bitmap = ImageDecoder.decodeBitmap(sourceDecoder) { decoder, info, _ ->
                val (width, height) = targetSize(info.size.width, info.size.height)
                decoder.setTargetSize(width, height)
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.memorySizePolicy = ImageDecoder.MEMORY_POLICY_LOW_RAM
            }
            try {
                FileOutputStream(partial).use { out ->
                    if (!bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)) {
                        throw IllegalStateException("JPEG encoding failed")
                    }
                }
            } finally {
                bitmap.recycle()
            }

            runCatching {
                ExifInterface(partial.absolutePath).apply {
                    setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
                    if (dateOriginal != null) setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, dateOriginal)
                    if (date != null) setAttribute(ExifInterface.TAG_DATETIME, date)
                    saveAttributes()
                }
            }

            if (output.exists() && !output.delete()) {
                throw IllegalStateException("Unable to replace display cache")
            }
            if (!partial.renameTo(output)) {
                throw IllegalStateException("Unable to finalize display cache")
            }
            Log.i(
                TAG,
                "Prepared ${output.length() / 1024} KiB display photo in " +
                    "${System.currentTimeMillis() - startedAt} ms"
            )
            output
        } catch (e: Exception) {
            partial.delete()
            Log.w(TAG, "Display optimization failed (${e.javaClass.simpleName})")
            null
        }
    }
}

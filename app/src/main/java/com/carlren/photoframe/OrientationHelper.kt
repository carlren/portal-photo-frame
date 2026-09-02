package com.carlren.photoframe

import android.content.Context
import android.content.res.Configuration
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import java.io.File

object OrientationHelper {
    private const val TAG = "PhotoFrame"

    /**
     * Determines if the current display is in portrait orientation.
     * Uses Configuration.orientation as primary signal (reacts to rotation / device default),
     * falls back to displayMetrics width vs height for robustness on Portal hardware.
     *
     * Portal+ (landscape hardware) → width > height → false
     * Portal Plus kept in portrait → height > width → true
     * This also handles the case where both models report same Build.MODEL but are physically rotated.
     */
    fun isPortraitDisplay(context: Context): Boolean {
        val configPortrait = context.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
        val dm = context.resources.displayMetrics
        val metricsPortrait = dm.heightPixels > dm.widthPixels
        // Log for diagnostics; config and metrics should agree, but prefer config (Compose reacts to it)
        if (configPortrait != metricsPortrait) {
            Log.w(TAG, "Orientation mismatch: configPortrait=$configPortrait metricsPortrait=$metricsPortrait (w=${dm.widthPixels} h=${dm.heightPixels})")
        }
        return configPortrait
    }

    /**
     * Logs detailed device info to help verify auto-detection.
     * Call once at startup and on orientation change.
     */
    fun logDeviceInfo(context: Context, isPortraitDisplay: Boolean) {
        val dm = context.resources.displayMetrics
        Log.i(
            TAG,
            "Device auto-detect: model=${Build.MODEL} manufacturer=${Build.MANUFACTURER} " +
                "product=${Build.PRODUCT} device=${Build.DEVICE} brand=${Build.BRAND} " +
                "display=${dm.widthPixels}x${dm.heightPixels} " +
                "configOrientation=${if (isPortraitDisplay) "portrait" else "landscape"} " +
                "willShow=${if (isPortraitDisplay) "portrait" else "landscape"} photos"
        )
    }

    /**
     * Returns true if image file is portrait (height > width after EXIF rotation correction),
     * false if landscape (width > height), null if square or unknown (treated as universal).
     * Uses BitmapFactory inJustDecodeBounds + EXIF orientation to avoid loading full bitmap.
     */
    fun isPortraitImage(file: File): Boolean? {
        return try {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, opts)
            var w = opts.outWidth
            var h = opts.outHeight

            // Fallback to EXIF dimensions if BitmapFactory failed (e.g., heic)
            if (w <= 0 || h <= 0) {
                try {
                    val exif = ExifInterface(file.absolutePath)
                    val exifW = exif.getAttributeInt(ExifInterface.TAG_IMAGE_WIDTH, 0)
                    val exifH = exif.getAttributeInt(ExifInterface.TAG_IMAGE_LENGTH, 0)
                    if (exifW > 0 && exifH > 0) {
                        w = exifW
                        h = exifH
                    }
                } catch (_: Exception) {}
            }
            if (w <= 0 || h <= 0) return null

            // Correct for EXIF rotation (90/270 swaps dimensions)
            try {
                val exif = ExifInterface(file.absolutePath)
                val orientation = exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
                val isRotated = orientation == ExifInterface.ORIENTATION_ROTATE_90 ||
                    orientation == ExifInterface.ORIENTATION_ROTATE_270 ||
                    orientation == ExifInterface.ORIENTATION_TRANSPOSE ||
                    orientation == ExifInterface.ORIENTATION_TRANSVERSE
                if (isRotated) {
                    val tmp = w
                    w = h
                    h = tmp
                }
            } catch (_: Exception) {
                // ignore EXIF errors, use raw dimensions
            }

            when {
                h > w -> true
                w > h -> false
                else -> null // square
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to determine orientation for ${file.name}: ${e.message}")
            null
        }
    }

    /**
     * Filters a list of image files to only those matching the display orientation.
     * Squares (null) are included on both displays to maximize available content.
     */
    fun filterByDisplayOrientation(files: List<File>, isPortraitDisplay: Boolean): List<File> {
        if (files.isEmpty()) return files
        val filtered = files.filter { file ->
            val isPortrait = isPortraitImage(file)
            when {
                isPortrait == null -> true // square → show on both
                isPortraitDisplay -> isPortrait // portrait display wants portrait images
                else -> !isPortrait // landscape display wants landscape images
            }
        }
        Log.i(
            TAG,
            "Orientation filter: ${files.size} total → ${filtered.size} ${if (isPortraitDisplay) "portrait" else "landscape"} " +
                "(squares included)"
        )
        return filtered
    }
}

package com.carlren.photoframe

import androidx.test.core.app.ApplicationProvider
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.exifinterface.media.ExifInterface
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class OrientationHelperTest {

    private lateinit var tmpDir: File
    private lateinit var portraitFile: File
    private lateinit var landscapeFile: File
    private lateinit var squareFile: File
    private lateinit var rotatedFile: File // landscape bytes but EXIF 90 -> should be portrait

    @Before
    fun setUp() {
        tmpDir = File(ApplicationProvider.getApplicationContext<android.content.Context>().cacheDir, "orientation_test_${System.nanoTime()}")
        tmpDir.mkdirs()

        portraitFile = File(tmpDir, "portrait.jpg")
        landscapeFile = File(tmpDir, "landscape.jpg")
        squareFile = File(tmpDir, "square.jpg")
        rotatedFile = File(tmpDir, "rotated.jpg")

        // Create bitmaps with distinct dimensions
        createJpeg(portraitFile, 400, 600, AndroidColor.RED)      // portrait 400x600
        createJpeg(landscapeFile, 600, 400, AndroidColor.BLUE)    // landscape 600x400
        createJpeg(squareFile, 500, 500, AndroidColor.GREEN)      // square 500x500
        createJpeg(rotatedFile, 600, 400, AndroidColor.YELLOW)    // physically 600x400 landscape, but will tag EXIF 6 = 90°

        // Tag rotated file with EXIF orientation 90 (ORIENTATION_ROTATE_90 = 6)
        // ExifInterface requires file exists; edit and save
        val exif = ExifInterface(rotatedFile.absolutePath)
        exif.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
        exif.saveAttributes()

        // Log actual file dimensions for diagnostics
        println("Test files created in ${tmpDir.absolutePath}")
        listOf(portraitFile, landscapeFile, squareFile, rotatedFile).forEach { f ->
            val isP = OrientationHelper.isPortraitImage(f)
            println("${f.name}: isPortrait=${isP} size=${f.length()} port via helper=${isP}")
        }
    }

    private fun createJpeg(file: File, w: Int, h: Int, color: Int) {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(color)
        FileOutputStream(file).use { out ->
            bmp.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }
        bmp.recycle()
        assertTrue("Failed to create ${file.name}", file.exists() && file.length() > 0)
    }

    @Test
    fun isPortraitImage_portrait_returnsTrue() {
        val result = OrientationHelper.isPortraitImage(portraitFile)
        println("portrait.jpg isPortrait=$result (expected true)")
        assertEquals(true, result)
    }

    @Test
    fun isPortraitImage_landscape_returnsFalse() {
        val result = OrientationHelper.isPortraitImage(landscapeFile)
        println("landscape.jpg isPortrait=$result (expected false)")
        assertEquals(false, result)
    }

    @Test
    fun isPortraitImage_square_returnsNull() {
        val result = OrientationHelper.isPortraitImage(squareFile)
        println("square.jpg isPortrait=$result (expected null)")
        assertNull(result)
    }

    @Test
    fun isPortraitImage_rotated_returnsPortrait() {
        val result = OrientationHelper.isPortraitImage(rotatedFile)
        println("rotated.jpg (600x400 + EXIF 90) isPortrait=$result (expected true)")
        assertEquals(true, result)
    }

    @Test
    fun filterByDisplayOrientation_portraitDisplay_keepPortraitAndSquareAndRotated() {
        val all = listOf(portraitFile, landscapeFile, squareFile, rotatedFile)
        val filtered = OrientationHelper.filterByDisplayOrientation(all, isPortraitDisplay = true)
        println("PORTRAIT display filter: ${all.map { it.name }} -> ${filtered.map { it.name }} size=${filtered.size} (expected 3)")
        assertEquals(3, filtered.size)
        assertTrue(filtered.any { it.name == "portrait.jpg" })
        assertTrue(filtered.any { it.name == "square.jpg" })
        assertTrue(filtered.any { it.name == "rotated.jpg" })
        assertFalse(filtered.any { it.name == "landscape.jpg" })
    }

    @Test
    fun filterByDisplayOrientation_landscapeDisplay_keepLandscapeAndSquare() {
        val all = listOf(portraitFile, landscapeFile, squareFile, rotatedFile)
        val filtered = OrientationHelper.filterByDisplayOrientation(all, isPortraitDisplay = false)
        println("LANDSCAPE display filter: ${all.map { it.name }} -> ${filtered.map { it.name }} size=${filtered.size} (expected 2)")
        assertEquals(2, filtered.size)
        assertTrue(filtered.any { it.name == "landscape.jpg" })
        assertTrue(filtered.any { it.name == "square.jpg" })
        assertFalse(filtered.any { it.name == "portrait.jpg" })
        assertFalse(filtered.any { it.name == "rotated.jpg" })
    }

    @Test
    fun filterByDisplayOrientation_empty_returnsEmpty() {
        val filteredP = OrientationHelper.filterByDisplayOrientation(emptyList(), true)
        val filteredL = OrientationHelper.filterByDisplayOrientation(emptyList(), false)
        assertTrue(filteredP.isEmpty())
        assertTrue(filteredL.isEmpty())
        println("empty filter -> both empty OK")
    }
}

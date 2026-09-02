package com.carlren.photoframe

import android.content.Context
import android.graphics.Bitmap
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicBlur
import coil.size.Size
import coil.transform.Transformation
import kotlin.math.roundToInt

class BlurTransformation(
    private val context: Context,
    private val radius: Float = 18f,
    private val sampling: Float = 6f
) : Transformation {

    override val cacheKey: String = "BlurTransformation(radius=$radius,sampling=$sampling)"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        val r = radius.coerceIn(0f, 25f)
        if (r <= 0.5f) return input

        val samplingInt = sampling.coerceAtLeast(1f)
        val w = (input.width / samplingInt).roundToInt().coerceAtLeast(1)
        val h = (input.height / samplingInt).roundToInt().coerceAtLeast(1)

        val scaled = if (w != input.width || h != input.height) {
            Bitmap.createScaledBitmap(input, w, h, false)
        } else input

        return try {
            val rs = RenderScript.create(context)
            val inputAlloc = Allocation.createFromBitmap(rs, scaled)
            val output = Bitmap.createBitmap(scaled)
            val outputAlloc = Allocation.createFromBitmap(rs, output)
            val blur = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs))
            blur.setRadius(r)
            blur.setInput(inputAlloc)
            blur.forEach(outputAlloc)
            outputAlloc.copyTo(output)

            // Cleanup
            inputAlloc.destroy()
            outputAlloc.destroy()
            blur.destroy()
            rs.destroy()

            if (scaled !== input && scaled !== output) scaled.recycle()

            // Scale back up to original size for smoother fill (optional, keep small for Crop)
            if (output.width != input.width || output.height != input.height) {
                // For background Crop, we want blurred to fill screen; upscaling the small blurred bitmap is fine.
                // Let Coil handle final sizing; return the small blurred bitmap and Coil will scale via contentScale.
                output
            } else output
        } catch (e: Exception) {
            // Fallback: return scaled without blur
            scaled
        }
    }
}

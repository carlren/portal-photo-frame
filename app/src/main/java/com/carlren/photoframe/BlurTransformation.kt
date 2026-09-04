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

/** Low-resolution blur used only behind fitted photos on portrait displays. */
class BlurTransformation(
    private val context: Context,
    private val radius: Float = 20f,
    private val sampling: Float = 4f
) : Transformation {
    override val cacheKey: String = "PortraitBlur(radius=$radius,sampling=$sampling)"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        val blurRadius = radius.coerceIn(0.5f, 25f)
        val divisor = sampling.coerceAtLeast(1f)
        val width = (input.width / divisor).roundToInt().coerceAtLeast(1)
        val height = (input.height / divisor).roundToInt().coerceAtLeast(1)
        val scaled = if (width != input.width || height != input.height) {
            Bitmap.createScaledBitmap(input, width, height, false)
        } else {
            input
        }

        var renderScript: RenderScript? = null
        var inputAllocation: Allocation? = null
        var outputAllocation: Allocation? = null
        var blur: ScriptIntrinsicBlur? = null
        return try {
            renderScript = RenderScript.create(context)
            inputAllocation = Allocation.createFromBitmap(renderScript, scaled)
            val output = Bitmap.createBitmap(scaled)
            outputAllocation = Allocation.createFromBitmap(renderScript, output)
            blur = ScriptIntrinsicBlur.create(renderScript, Element.U8_4(renderScript))
            blur.setRadius(blurRadius)
            blur.setInput(inputAllocation)
            blur.forEach(outputAllocation)
            outputAllocation.copyTo(output)
            if (scaled !== input) scaled.recycle()
            output
        } catch (_: Exception) {
            scaled
        } finally {
            blur?.destroy()
            inputAllocation?.destroy()
            outputAllocation?.destroy()
            renderScript?.destroy()
        }
    }
}

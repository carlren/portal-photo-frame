package com.carlren.photoframe

import org.junit.Assert.assertEquals
import org.junit.Test

class DisplayPhotoOptimizerTest {
    @Test
    fun targetSize_capsLargeCameraPhotoAtDisplayResolution() {
        assertEquals(2560 to 1703, DisplayPhotoOptimizer.targetSize(6048, 4024))
    }

    @Test
    fun targetSize_capsFourByThreePhotoWithoutChangingAspectRatio() {
        assertEquals(2560 to 1920, DisplayPhotoOptimizer.targetSize(4000, 3000))
    }

    @Test
    fun targetSize_doesNotUpscaleSmallPhoto() {
        assertEquals(1920 to 1080, DisplayPhotoOptimizer.targetSize(1920, 1080))
    }
}

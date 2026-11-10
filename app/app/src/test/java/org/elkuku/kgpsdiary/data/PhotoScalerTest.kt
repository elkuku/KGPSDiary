package org.elkuku.kgpsdiary.data

import org.junit.Assert.assertEquals
import org.junit.Test

class PhotoScalerTest {
    @Test
    fun scalesTheLongerSideTo2048() {
        assertEquals(2048 to 1536, PhotoScaler.targetSize(4000, 3000))
        assertEquals(1536 to 2048, PhotoScaler.targetSize(3000, 4000))
    }

    @Test
    fun keepsSmallPhotos() {
        assertEquals(800 to 600, PhotoScaler.targetSize(800, 600))
    }

    @Test
    fun neverReturnsZero() {
        assertEquals(2048 to 1, PhotoScaler.targetSize(100_000, 10))
    }
}

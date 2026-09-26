package com.androplay.service

import org.junit.Assert.assertEquals
import org.junit.Test

class PictureLayoutTest {
    private fun size(mode: String, videoWidth: Int, videoHeight: Int) =
        PictureLayout.size(mode, 1920f, 1080f, videoWidth, videoHeight)

    @Test
    fun fitShowsTheWholePicture() {
        // A portrait phone screen is pillarboxed.
        assertEquals(607.5f to 1080f, size(ReceiverSettings.PICTURE_FIT, 1170, 2080))
        // A wider movie is letterboxed.
        assertEquals(1920f to 800f, size(ReceiverSettings.PICTURE_FIT, 1920, 800))
    }

    @Test
    fun fillCoversTheScreenAndCropsTheRest() {
        assertEquals(2592f to 1080f, size(ReceiverSettings.PICTURE_FILL, 1920, 800))
        val (width, height) = size(ReceiverSettings.PICTURE_FILL, 1170, 2080)
        assertEquals(1920f, width, 0.01f)
        assertEquals(3413.33f, height, 0.01f)
    }

    @Test
    fun stretchAndUnknownSizesUseTheWholeScreen() {
        assertEquals(1920f to 1080f, size(ReceiverSettings.PICTURE_STRETCH, 1920, 800))
        assertEquals(1920f to 1080f, size(ReceiverSettings.PICTURE_FIT, 0, 0))
    }
}

package com.weenas.castbay.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackdropTest {
    private fun red(c: Int) = c shr 16 and 0xFF

    @Test
    fun keepsAFlatColourFlat() {
        val color = 0xFF336699.toInt()
        val out = Backdrop.blur(IntArray(8 * 8) { color }, 8, 8, radius = 2, passes = 3)
        assertTrue(out.all { it == color })
    }

    @Test
    fun spreadsABrightPixelIntoItsNeighbours() {
        val size = 9
        val pixels = IntArray(size * size) { 0xFF000000.toInt() }
        pixels[4 * size + 4] = 0xFFFFFFFF.toInt()
        val out = Backdrop.blur(pixels, size, size, radius = 1, passes = 1)
        val centre = red(out[4 * size + 4])
        val neighbour = red(out[4 * size + 5])
        val far = red(out[0])
        // 255 spread over a 3x3 box: each of the 9 pixels gets 255 / 3 / 3.
        assertEquals(255 / 3 / 3, centre)
        assertEquals(centre, neighbour)
        assertEquals(0, far)
    }

    @Test
    fun boostsColourButKeepsGreysGrey() {
        val grey = 0xFF808080.toInt()
        assertEquals(grey, Backdrop.saturate(intArrayOf(grey), 1.5f)[0])
        // A muted purple gets further from its grey: red and blue apart from green.
        val muted = 0xFF706080.toInt()
        val boosted = Backdrop.saturate(intArrayOf(muted), 1.5f)[0]
        assertTrue((boosted and 0xFF) - (boosted shr 8 and 0xFF) > 0x80 - 0x60)
        assertEquals(0xFF, boosted ushr 24)
    }

    @Test
    fun darkensTheVeilOnlyForBrightBackdrops() {
        assertEquals(Backdrop.MIN_VEIL, Backdrop.veilFor(0.3f), 0.001f)
        assertEquals(Backdrop.MIN_VEIL, Backdrop.veilFor(0.45f), 0.001f)
        assertTrue(Backdrop.veilFor(0.7f) > Backdrop.MIN_VEIL)
        assertEquals(Backdrop.MAX_VEIL, Backdrop.veilFor(1f), 0.001f)
        assertEquals(1f, Backdrop.averageLuminance(IntArray(4) { 0xFFFFFFFF.toInt() }), 0.01f)
        assertEquals(0f, Backdrop.averageLuminance(IntArray(4) { 0xFF000000.toInt() }), 0.01f)
    }

    @Test
    fun outputIsOpaque() {
        val out = Backdrop.blur(IntArray(4 * 4) { 0x00FF0000 }, 4, 4, radius = 1, passes = 2)
        assertTrue(out.all { it ushr 24 == 0xFF })
    }
}

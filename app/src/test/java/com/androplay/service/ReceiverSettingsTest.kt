package com.androplay.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReceiverSettingsTest {
    @Test
    fun autoResolutionUsesTheDisplayUpTo1080p() {
        val auto = ReceiverSettings()
        assertEquals(1920 to 1080, auto.displaySize(1920, 1080))
        assertEquals(1280 to 720, auto.displaySize(1280, 720))
        // Portrait-reported displays are advertised in landscape.
        assertEquals(1920 to 1080, auto.displaySize(1080, 1920))
        // 4K panels and unknown sizes are capped to 1080p.
        assertEquals(1920 to 1080, auto.displaySize(3840, 2160))
        assertEquals(1920 to 1080, auto.displaySize(0, 0))
    }

    @Test
    fun explicitResolutionsIgnoreTheDisplay() {
        assertEquals(1280 to 720, ReceiverSettings(resolution = "720p").displaySize(3840, 2160))
        assertEquals(1920 to 1080, ReceiverSettings(resolution = "1080p").displaySize(1280, 720))
    }

    @Test
    fun frameRateDefaultsTo60() {
        assertEquals(60, ReceiverSettings().maxFps())
        assertEquals(30, ReceiverSettings(frameRate = "30 FPS").maxFps())
        assertEquals(60, ReceiverSettings(frameRate = "60 FPS").maxFps())
    }

    @Test
    fun onlyValidPinsAreEnforced() {
        assertTrue(ReceiverSettings.isValidPin("1234"))
        assertTrue(ReceiverSettings.isValidPin("123456"))
        assertFalse(ReceiverSettings.isValidPin("123"))
        assertFalse(ReceiverSettings.isValidPin("12a4"))
        assertEquals("", ReceiverSettings(pin = "").accessPassword())
        assertEquals("", ReceiverSettings(pin = "12").accessPassword())
        assertEquals("2468", ReceiverSettings(pin = "2468").accessPassword())
    }
}

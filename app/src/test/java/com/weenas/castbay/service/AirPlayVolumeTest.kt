package com.weenas.castbay.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AirPlayVolumeTest {
    @Test
    fun muteAndBottomOfRangeAreSilent() {
        assertEquals(0f, AirPlayVolume.toGain(AirPlayVolume.MUTE_DB), 0f)
        assertEquals(0f, AirPlayVolume.toGain(-30f), 0f)
        assertEquals(0f, AirPlayVolume.toGain(-50f), 0f)
    }

    @Test
    fun topOfRangeIsFullVolume() {
        assertEquals(1f, AirPlayVolume.toGain(0f), 0f)
        assertEquals(1f, AirPlayVolume.toGain(3f), 0f)
    }

    @Test
    fun halfSliderIsTenDecibelsDown() {
        // -15 dB is the slider's midpoint; tapering makes that -10 dB of gain.
        assertEquals(0.3162f, AirPlayVolume.toGain(-15f), 0.001f)
    }

    @Test
    fun neverQuieterThanThePlainDecibelValue() {
        var db = -29.9f
        while (db < 0f) {
            val plain = Math.pow(10.0, db / 20.0).toFloat()
            assertTrue("at $db dB", AirPlayVolume.toGain(db) >= plain - 1e-6f)
            db += 0.1f
        }
    }

    @Test
    fun gainRisesWithTheSlider() {
        var previous = 0f
        var db = -29.9f
        while (db <= 0f) {
            val gain = AirPlayVolume.toGain(db)
            assertTrue("at $db dB", gain >= previous)
            previous = gain
            db += 0.1f
        }
    }
}

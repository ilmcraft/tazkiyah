package com.inovixlabs.tazkiyah.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class MediaVolumeTest {
    @Test
    fun iconFollowsLevel() {
        assertEquals(VolumeLevel.Off, volumeLevel(0, 15))
        assertEquals(VolumeLevel.Low, volumeLevel(1, 15))
        assertEquals(VolumeLevel.Low, volumeLevel(7, 15))
        assertEquals(VolumeLevel.High, volumeLevel(8, 15))
        assertEquals(VolumeLevel.High, volumeLevel(15, 15))
    }

    @Test
    fun unmuteGoesToAboutHalf() {
        assertEquals(8, unmuteLevel(15))
        assertEquals(13, unmuteLevel(25))
        assertEquals(1, unmuteLevel(1))
        assertEquals(1, unmuteLevel(0))
    }
}

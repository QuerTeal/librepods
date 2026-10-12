package me.kavishdevar.librepods.data

import org.junit.Assert.*
import org.junit.Test

class AudioMonitorPolicyTest {
    @Test fun onlyTheSelectedOutputAndPhoneMicrophoneCanForwardAudio() {
        assertTrue(AudioMonitorPolicy.canForward(7, listOf(7), true))
        assertFalse(AudioMonitorPolicy.canForward(7, listOf(8), true))
        assertFalse(AudioMonitorPolicy.canForward(7, listOf(7, 8), true))
        assertFalse(AudioMonitorPolicy.canForward(7, emptyList(), true))
        assertFalse(AudioMonitorPolicy.canForward(7, listOf(7), false))
    }
    @Test fun gainDoesNotOverflowOrChangeUnreadBufferData() {
        val samples = shortArrayOf(32767, -32768, 1000, 999)
        AudioMonitorPolicy.applyGain(samples, 3, 0.5f)
        assertArrayEquals(shortArrayOf(16383, -16384, 500, 999), samples)
    }
    @Test(expected = IllegalArgumentException::class) fun invalidGainIsRejected() {
        AudioMonitorPolicy.applyGain(shortArrayOf(1), 1, Float.NaN)
    }
}

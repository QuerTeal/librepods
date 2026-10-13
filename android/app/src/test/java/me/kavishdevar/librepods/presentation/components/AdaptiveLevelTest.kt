package me.kavishdevar.librepods.presentation.components

import org.junit.Assert.assertEquals
import org.junit.Test

class AdaptiveLevelTest {
    // Read back from AirPods Pro 3 after a reconnect: 0 -> 0, 25/60/70 -> 50, 75/80/90 -> 100.
    @Test fun matchesTheFirmwareLevels() {
        mapOf(0f to 0, 0.24f to 0, 0.25f to 50, 0.6f to 50, 0.7f to 50, 0.75f to 100, 0.9f to 100, 1f to 100)
            .forEach { (fraction, level) -> assertEquals("fraction $fraction", level, adaptiveLevel(fraction)) }
    }

    @Test fun clampsOutOfRange() {
        assertEquals(0, adaptiveLevel(-0.3f))
        assertEquals(100, adaptiveLevel(1.4f))
    }
}

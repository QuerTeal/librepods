package me.kavishdevar.librepods.bluetooth

import org.junit.Assert.*
import org.junit.Test

class DeferredPlaybackGateTest {
    @Test fun sameSessionResumesOnlyOnce() {
        val gate = DeferredPlaybackGate({ 100L })
        gate.arm(1, "selected")
        assertTrue(gate.consume(1, "selected", true, true, false))
        assertFalse(gate.consume(1, "selected", true, true, false))
    }
    @Test fun removalCancelsLateProfileResume() {
        val gate = DeferredPlaybackGate({ 0L })
        gate.arm(1, "selected")
        gate.cancel()
        assertFalse(gate.consume(1, "selected", true, true, false))
    }
    @Test fun oldSessionCannotResumeOnNewConnection() {
        val gate = DeferredPlaybackGate({ 0L })
        gate.arm(1, "selected")
        assertFalse(gate.consume(2, "selected", true, true, false))
    }
    @Test fun otherDeviceDoesNotConsumeSelectedIntent() {
        val gate = DeferredPlaybackGate({ 0L })
        gate.arm(1, "selected")
        assertFalse(gate.consume(1, "other", true, true, false))
        assertTrue(gate.consume(1, "selected", true, true, false))
    }
    @Test fun staleIntentExpires() {
        var now = 0L
        val gate = DeferredPlaybackGate({ now }, 1000)
        gate.arm(1, "selected")
        now = 1001
        assertFalse(gate.consume(1, "selected", true, true, false))
    }
    @Test fun callOrUnknownWearBlocksResume() {
        val gate = DeferredPlaybackGate({ 0L })
        gate.arm(1, "selected")
        assertFalse(gate.consume(1, "selected", true, true, true))
        gate.arm(1, "selected")
        assertFalse(gate.consume(1, "selected", true, false, false))
    }
}

package me.kavishdevar.librepods.bluetooth

import org.junit.Assert.*
import org.junit.Test

class CallGestureGuardTest {
    @Test fun endedCallCannotConsumeLateGesture() {
        val guard = CallGestureGuard()
        guard.update(CallPhase.RINGING)
        val token = guard.arm(1)!!
        guard.update(CallPhase.IDLE)
        assertFalse(guard.consume(token, 1, true))
    }
    @Test fun newIncomingCallCannotUsePreviousCallGesture() {
        val guard = CallGestureGuard()
        guard.update(CallPhase.RINGING)
        val old = guard.arm(1)!!
        guard.update(CallPhase.IDLE)
        guard.update(CallPhase.RINGING)
        assertFalse(guard.consume(old, 1, true))
        assertTrue(guard.consume(guard.arm(1)!!, 1, true))
    }
    @Test fun disconnectInvalidatesGesture() {
        val guard = CallGestureGuard()
        guard.update(CallPhase.RINGING)
        val old = guard.arm(1)!!
        guard.invalidate()
        assertFalse(guard.consume(old, 2, true))
        assertFalse(guard.consume(guard.arm(2)!!, 2, false))
    }
    @Test fun acceptedGestureIsSingleUse() {
        val guard = CallGestureGuard()
        assertNull(guard.arm(1))
        guard.update(CallPhase.RINGING)
        val token = guard.arm(1)!!
        assertTrue(guard.consume(token, 1, true))
        assertFalse(guard.consume(token, 1, true))
    }
}

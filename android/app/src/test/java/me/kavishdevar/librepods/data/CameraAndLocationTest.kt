package me.kavishdevar.librepods.data

import org.junit.Assert.*
import org.junit.Test

class CameraAndLocationTest {
    @Test fun duplicateStemEventsCannotTakeMultiplePhotos() {
        var now = 0L
        val gate = CaptureGate { now }
        val session = gate.opened()
        assertTrue(gate.begin(session))
        assertFalse(gate.begin(session))
        gate.completed(session)
        assertFalse(gate.begin(session))
        now = 800
        assertTrue(gate.begin(session))
    }
    @Test fun oldCameraCompletionDoesNotUnlockNewCapture() {
        val gate = CaptureGate { 0L }
        val old = gate.opened()
        gate.close()
        val current = gate.opened()
        assertFalse(gate.begin(old))
        assertTrue(gate.begin(current))
        gate.completed(old)
        assertFalse(gate.begin(current))
    }
    @Test fun expiredLocationOrClockResetIsNotAConnectionLocation() {
        val fix = PhoneLocationFix(37.5, 127.0, 100f, 1000, 2000)
        assertTrue(fix.isUsable(602000))
        assertFalse(fix.isUsable(602001))
        assertFalse(fix.isUsable(1999))
    }
    @Test fun invalidCoordinatesCannotBeRecorded() {
        val fix = PhoneLocationFix(37.5, 127.0, 100f, 1000, 2000)
        assertFalse(fix.copy(latitude = Double.NaN).isUsable(2000))
        assertFalse(fix.copy(longitude = 181.0).isUsable(2000))
        assertFalse(fix.copy(accuracyMeters = -1f).isUsable(2000))
    }
}

package me.kavishdevar.librepods.bluetooth

import org.junit.Assert.*
import org.junit.Test

class LidStateTrackerTest {
    @Test fun sparseCaseBroadcastDoesNotRepeatAnOpeningAfterExpiry() {
        val tracker = LidStateTracker(2500)
        assertEquals(LidState.OPEN, tracker.observe(true, 100, 3))
        assertTrue(tracker.expire(2600))
        assertNull(tracker.observe(true, 22_000, 3))
        assertEquals(LidState.OPEN, tracker.state)
    }

    @Test fun missedCloseStillDetectsANewCaseOpeningFromItsCounter() {
        val tracker = LidStateTracker()
        assertEquals(LidState.OPEN, tracker.observe(true, 100, 3))
        assertEquals(LidState.OPEN, tracker.observe(true, 200, 4))
        assertNull(tracker.observe(true, 300, 4))
    }

    @Test fun counterWrapStillDetectsNewOpening() {
        val tracker = LidStateTracker()
        tracker.observe(true, 100, 7)
        assertEquals(LidState.OPEN, tracker.observe(true, 200, 0))
    }

    @Test fun observedCloseAllowsReopeningEvenWithSameCounter() {
        val tracker = LidStateTracker()
        tracker.observe(true, 100, 3)
        assertEquals(LidState.CLOSED, tracker.observe(false, 200, 3))
        assertEquals(LidState.OPEN, tracker.observe(true, 300, 3))
    }

    @Test fun resetClearsCaseCounterHistory() {
        val tracker = LidStateTracker()
        tracker.observe(true, 100, 3)
        tracker.reset()
        assertEquals(LidState.OPEN, tracker.observe(true, 200, 3))
    }

    @Test fun rapidOpenCloseOpenProducesAllTransitionsWithoutDuplicatePopups() {
        val tracker = LidStateTracker()
        assertEquals(LidState.OPEN, tracker.observe(true, 100))
        assertNull(tracker.observe(true, 101))
        assertEquals(LidState.CLOSED, tracker.observe(false, 102))
        assertEquals(LidState.OPEN, tracker.observe(true, 103))
    }

    @Test fun radioSilenceProducesUnknownRatherThanAConfirmedClose() {
        val tracker = LidStateTracker(2500)
        tracker.observe(true, 100)
        assertFalse(tracker.expire(2599))
        assertTrue(tracker.expire(2600))
        assertEquals(LidState.UNKNOWN, tracker.state)
        assertFalse(tracker.expire(5000))
    }

    @Test fun rotatingAdvertisingAddressDoesNotCreateAnotherOpenTransition() {
        // Both resolved addresses belong to the same selected paired identity.
        val tracker = LidStateTracker()
        tracker.observe(true, 100)
        assertNull(tracker.observe(true, 200))
        assertEquals(LidState.CLOSED, tracker.observe(false, 300))
    }

    @Test fun outOfOrderPacketCannotCloseNewerOpenStateOrExtendItsLifetime() {
        val tracker = LidStateTracker(1000)
        tracker.observe(true, 2000)
        assertNull(tracker.observe(false, 1999))
        assertEquals(LidState.OPEN, tracker.state)
        assertTrue(tracker.expire(3000))
    }

    @Test fun freshAdvertisementRestoresStateAfterExpiryOrBluetoothRestart() {
        val tracker = LidStateTracker(1000)
        tracker.observe(true, 100)
        tracker.expire(1100)
        assertEquals(LidState.OPEN, tracker.observe(true, 1200))
        tracker.reset()
        assertEquals(LidState.UNKNOWN, tracker.state)
        assertEquals(LidState.CLOSED, tracker.observe(false, 1300))
    }
}

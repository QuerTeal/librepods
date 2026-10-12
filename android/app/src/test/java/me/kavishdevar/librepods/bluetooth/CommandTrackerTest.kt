package me.kavishdevar.librepods.bluetooth

import org.junit.Assert.*
import org.junit.Test

class CommandTrackerTest {
    @Test fun confirmedSameValueIncludesProtocolPadding() {
        val tracker = CommandTracker({ 0L })
        tracker.receive(0x0D, byteArrayOf(2))
        assertTrue(tracker.isConfirmedValue(0x0D, byteArrayOf(2, 0, 0, 0)))
        assertFalse(tracker.isConfirmedValue(0x0D, byteArrayOf(2, 0, 1, 0)))
    }

    @Test fun pendingChangeMustAllowRestoringTheConfirmedBaseline() {
        val tracker = CommandTracker({ 0L })
        tracker.receive(0x0D, byteArrayOf(2))
        tracker.begin(0x0D, byteArrayOf(3))
        assertFalse(tracker.isConfirmedValue(0x0D, byteArrayOf(2)))
    }

    @Test fun uncertainChangeMustAllowRestoringTheConfirmedBaseline() {
        var time = 0L
        val tracker = CommandTracker({ time }, 1000)
        tracker.receive(0x0D, byteArrayOf(2))
        tracker.begin(0x0D, byteArrayOf(3))
        time = 1000
        tracker.expire()
        assertFalse(tracker.isConfirmedValue(0x0D, byteArrayOf(2)))
    }

    @Test fun unknownOrDifferentValuesStillNeedAWrite() {
        val tracker = CommandTracker({ 0L })
        assertFalse(tracker.isConfirmedValue(0x0D, byteArrayOf(2)))
        tracker.receive(0x0D, byteArrayOf(2))
        assertFalse(tracker.isConfirmedValue(0x0D, byteArrayOf(3)))
        assertFalse(tracker.isConfirmedValue(0x0D, byteArrayOf()))
    }
    @Test fun trimmedZeroPaddingConfirmsDefaultCallConfiguration() {
        val tracker = CommandTracker({ 0L })
        tracker.begin(0x24, byteArrayOf(0, 0))
        tracker.receive(0x24, byteArrayOf(0))
        assertEquals(SettingPhase.CONFIRMED, tracker.states.value[0x24]?.phase)
    }
    @Test fun sentValueIsPendingUntilDeviceEcho() {
        val tracker = CommandTracker({ 100L })
        tracker.receive(0x0D, byteArrayOf(2))
        tracker.begin(0x0D, byteArrayOf(3))
        assertEquals(listOf<Byte>(2), tracker.states.value[0x0D]?.confirmed)
        assertEquals(SettingPhase.PENDING, tracker.states.value[0x0D]?.phase)
        tracker.receive(0x0D, byteArrayOf(3, 0, 0, 0))
        assertEquals(SettingPhase.CONFIRMED, tracker.states.value[0x0D]?.phase)
    }

    @Test fun earlierEchoDoesNotConfirmLatestSliderChange() {
        val tracker = CommandTracker({ 0L })
        val earlier = tracker.begin(0x1F, byteArrayOf(40))
        tracker.begin(0x1F, byteArrayOf(60))
        tracker.receive(0x1F, byteArrayOf(40, 0, 0, 0))
        tracker.sendFailed(0x1F, earlier)
        assertEquals(SettingPhase.PENDING, tracker.states.value[0x1F]?.phase)
        assertEquals(listOf<Byte>(60), tracker.states.value[0x1F]?.desired)
    }

    @Test fun timeoutPreservesConfirmedValueWithoutResend() {
        var time = 0L
        val tracker = CommandTracker({ time }, 1000)
        tracker.receive(0x0D, byteArrayOf(2))
        tracker.begin(0x0D, byteArrayOf(3))
        time = 1000
        tracker.expire()
        assertEquals("confirmation_timeout", tracker.states.value[0x0D]?.failure)
        assertEquals(listOf<Byte>(2), tracker.states.value[0x0D]?.confirmed)
    }

    @Test fun failedOldSessionCannotChangeNewRequest() {
        val tracker = CommandTracker({ 0L })
        val old = tracker.begin(0x06, byteArrayOf(1))
        tracker.reset()
        tracker.begin(0x06, byteArrayOf(2))
        tracker.sendFailed(0x06, old)
        assertEquals(SettingPhase.PENDING, tracker.states.value[0x06]?.phase)
    }

    @Test fun nonzeroTrailingDataDoesNotConfirmDifferentCommand() {
        val tracker = CommandTracker({ 0L })
        tracker.begin(0x24, byteArrayOf(0, 2))
        tracker.receive(0x24, byteArrayOf(0, 2, 1, 0))
        assertEquals(SettingPhase.PENDING, tracker.states.value[0x24]?.phase)
    }
}

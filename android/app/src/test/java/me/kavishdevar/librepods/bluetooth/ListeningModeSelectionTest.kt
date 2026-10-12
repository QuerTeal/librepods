package me.kavishdevar.librepods.bluetooth

import org.junit.Assert.assertEquals
import org.junit.Test

class ListeningModeSelectionTest {
    @Test fun rapidChoicesAccumulateBeforeEitherEcho() {
        val selection = ListeningModeSelection()
        assertEquals(0x0e, selection.toggle(0x06, 0x08))
        assertEquals(0x0f, selection.toggle(0x06, 0x01))
    }

    @Test fun earlierEchoCannotReplaceLatestIntent() {
        val selection = ListeningModeSelection()
        selection.toggle(0x06, 0x08)
        selection.toggle(0x06, 0x01)
        selection.receive(0x0e)
        assertEquals(0x0d, selection.toggle(0x0e, 0x02))
    }

    @Test fun latestEchoHandsControlBackToDeviceState() {
        val selection = ListeningModeSelection()
        selection.toggle(0x06, 0x08)
        selection.receive(0x0e)
        // The other host has since changed the device to Off + Transparency.
        assertEquals(0x0d, selection.toggle(0x05, 0x08))
    }

    @Test fun minimumTwoModesUsesLatestSelection() {
        val selection = ListeningModeSelection()
        assertEquals(0x0c, selection.toggle(0x0e, 0x02))
        assertEquals(0x0c, selection.toggle(0x0e, 0x08))
    }

    @Test fun failedLatestRequestAndReconnectClearIntent() {
        val selection = ListeningModeSelection()
        selection.toggle(0x06, 0x08)
        selection.failed(0x0e)
        assertEquals(0x07, selection.toggle(0x06, 0x01))
        selection.reset()
        assertEquals(0x0e, selection.toggle(0x06, 0x08))
    }

    @Test fun failureOfEarlierRequestDoesNotDropNewerChoice() {
        val selection = ListeningModeSelection()
        selection.toggle(0x06, 0x08)
        selection.toggle(0x06, 0x01)
        selection.failed(0x0e)
        assertEquals(0x0d, selection.toggle(0x06, 0x02))
    }
}

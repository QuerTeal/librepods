package me.kavishdevar.librepods.bluetooth

import org.junit.Assert.*
import org.junit.Test

class PopupScreenGateTest {
    @Test fun openOnVisibleScreenShowsImmediately() {
        val gate = PopupScreenGate()
        assertTrue(gate.onLidOpened(interactive = true, nowMillis = 100))
        assertFalse(gate.onUserVisible(200))
    }

    @Test fun openOnDarkScreenShowsWhenUnlockedSoonAfter() {
        val gate = PopupScreenGate(10_000)
        assertFalse(gate.onLidOpened(interactive = false, nowMillis = 1_000))
        assertTrue(gate.onUserVisible(4_000))
        assertFalse(gate.onUserVisible(4_500))
    }

    @Test fun staleDarkOpenIsDropped() {
        val gate = PopupScreenGate(10_000)
        gate.onLidOpened(interactive = false, nowMillis = 1_000)
        assertFalse(gate.onUserVisible(11_001))
    }

    @Test fun closingBeforeUnlockCancelsTheDeferredPopup() {
        val gate = PopupScreenGate()
        gate.onLidOpened(interactive = false, nowMillis = 1_000)
        gate.onLidClosedOrUnknown()
        assertFalse(gate.onUserVisible(2_000))
    }
}

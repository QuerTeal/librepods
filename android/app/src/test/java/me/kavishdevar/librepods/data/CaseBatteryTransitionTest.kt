package me.kavishdevar.librepods.data

import org.junit.Assert.*
import org.junit.Test

class CaseBatteryTransitionTest {
    @Test fun reseatingTransientZeroKeepsKnownLevelAndUpdatesChargingImmediately() {
        val state = notification()
        state.updateFromCase(null, false, 91, true, 58, false, seenMillis = 1000)
        state.updateFromCase(0, true, 91, true, 58, false, seenMillis = 2000)
        assertEquals(Battery(BatteryComponent.LEFT, 72, BatteryStatus.CHARGING), state.getBattery()[0])
        state.updateFromCase(73, true, 91, true, 58, false, seenMillis = 4000)
        assertEquals(Battery(BatteryComponent.LEFT, 73, BatteryStatus.CHARGING), state.getBattery()[0])
    }

    @Test fun zeroThatRemainsStableIsAcceptedAfterTheShortConfirmationWindow() {
        val state = notification()
        state.updateFromCase(null, false, 91, true, 58, false, seenMillis = 1000)
        state.updateFromCase(0, true, 91, true, 58, false, seenMillis = 2000)
        state.updateFromCase(0, true, 91, true, 58, false, seenMillis = 4999)
        assertEquals(72, state.getBattery()[0].level)
        state.updateFromCase(0, true, 91, true, 58, false, seenMillis = 5000)
        assertEquals(0, state.getBattery()[0].level)
        assertTrue(state.getBattery()[0].hasReportedLevel())
    }

    @Test fun previouslyUnknownBatteryReportsActualZeroImmediately() {
        val state = AirPodsNotifications.BatteryNotification()
        state.updateFromCase(null, false, null, false, 58, false, seenMillis = 1000)
        state.updateFromCase(0, true, null, false, 58, false, seenMillis = 2000)
        assertEquals(Battery(BatteryComponent.LEFT, 0, BatteryStatus.CHARGING), state.getBattery()[0])
    }

    @Test fun selectingAnotherDeviceClearsPendingZeroConfirmation() {
        val state = notification()
        state.updateFromCase(null, false, 91, true, 58, false, seenMillis = 1000)
        state.updateFromCase(0, true, 91, true, 58, false, seenMillis = 2000)
        state.reset()
        state.updateFromCase(0, true, null, false, 58, false, seenMillis = 2500)
        assertEquals(Battery(BatteryComponent.LEFT, 0, BatteryStatus.CHARGING), state.getBattery()[0])
    }

    private fun notification() = AirPodsNotifications.BatteryNotification().apply {
        setBatteryDirect(72, true, 91, true, 58, false)
    }

    @Test fun takingLeftOutPreservesItsLevelAndStopsOnlyItsCharging() {
        val state = notification()
        assertTrue(state.updateFromCase(null, false, 91, true, 58, false))
        assertEquals(Battery(BatteryComponent.LEFT, 72, BatteryStatus.NOT_CHARGING), state.getBattery()[0])
        assertEquals(Battery(BatteryComponent.RIGHT, 91, BatteryStatus.CHARGING), state.getBattery()[1])
    }

    @Test fun puttingLeftBackUpdatesItsLatestCaseReading() {
        val state = notification()
        state.updateFromCase(null, false, 91, true, 58, false)
        assertTrue(state.updateFromCase(73, true, 91, true, 57, false))
        assertEquals(Battery(BatteryComponent.LEFT, 73, BatteryStatus.CHARGING), state.getBattery()[0])
        assertEquals(57, state.getBattery()[2].level)
    }

    @Test fun takingBothOutKeepsBothPercentagesWithoutChargingIcons() {
        val state = notification()
        state.updateFromCase(null, false, null, false, 58, false)
        assertEquals(listOf(72, 91), state.getBattery().take(2).map { it.level })
        assertTrue(state.getBattery().take(2).all { it.status == BatteryStatus.NOT_CHARGING })
    }

    @Test fun puttingOnlyRightBackDoesNotClearLeft() {
        val state = notification()
        state.updateFromCase(null, false, null, false, 58, false)
        state.updateFromCase(null, false, 93, true, 58, false)
        assertEquals(72, state.getBattery()[0].level)
        assertEquals(BatteryStatus.NOT_CHARGING, state.getBattery()[0].status)
        assertEquals(Battery(BatteryComponent.RIGHT, 93, BatteryStatus.CHARGING), state.getBattery()[1])
    }

    @Test fun emptyCaseAtColdStartDoesNotInventBudMeasurements() {
        val state = AirPodsNotifications.BatteryNotification()
        state.updateFromCase(null, false, null, false, 58, false)
        assertTrue(state.getBattery().take(2).none { it.hasReportedLevel() })
        assertEquals(58, state.getBattery()[2].level)
    }

    @Test fun reportedZeroRemainsRealWhenBudIsRemoved() {
        val state = notification()
        state.updateFromCase(0, true, 91, true, 58, false)
        state.updateFromCase(null, false, 91, true, 58, false)
        assertTrue(state.getBattery()[0].hasReportedLevel())
        assertEquals(0, state.getBattery()[0].level)
        assertEquals(BatteryStatus.NOT_CHARGING, state.getBattery()[0].status)
    }

    @Test fun chargingOnlyChangePublishesButIdenticalAdvertisementDoesNot() {
        val state = notification()
        assertFalse(state.updateFromCase(72, true, 91, true, 58, false))
        assertTrue(state.updateFromCase(72, false, 91, true, 58, false))
        assertFalse(state.updateFromCase(72, false, 91, true, 58, false))
    }

    @Test fun reversedAacpComponentOrderKeepsCorrectLeftAndRightIdentity() {
        val state = AirPodsNotifications.BatteryNotification()
        val packet = ByteArray(22).apply {
            this[7] = BatteryComponent.RIGHT.toByte(); this[9] = 91; this[10] = BatteryStatus.CHARGING.toByte()
            this[12] = BatteryComponent.LEFT.toByte(); this[14] = 72; this[15] = BatteryStatus.CHARGING.toByte()
            this[17] = BatteryComponent.CASE.toByte(); this[19] = 58; this[20] = BatteryStatus.NOT_CHARGING.toByte()
        }
        state.setBattery(packet)
        state.updateFromCase(null, false, 92, true, 58, false)
        assertEquals(Battery(BatteryComponent.LEFT, 72, BatteryStatus.NOT_CHARGING), state.getBattery()[0])
        assertEquals(Battery(BatteryComponent.RIGHT, 92, BatteryStatus.CHARGING), state.getBattery()[1])
    }

    @Test fun invalidReadingDoesNotPartiallyReplaceKnownMeasurements() {
        val state = notification()
        val previous = state.getBattery()
        assertFalse(state.updateFromCase(101, true, 92, true, 59, false))
        assertEquals(previous, state.getBattery())
    }

    @Test fun selectingAnotherDeviceClearsRememberedBudLevels() {
        val state = notification()
        state.reset()
        state.updateFromCase(null, false, null, false, 58, false)
        assertTrue(state.getBattery().take(2).none { it.hasReportedLevel() })
    }
}

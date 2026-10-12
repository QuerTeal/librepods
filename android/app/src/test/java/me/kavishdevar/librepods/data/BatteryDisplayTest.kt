package me.kavishdevar.librepods.data

import org.junit.Assert.*
import org.junit.Test

class BatteryDisplayTest {
    @Test fun separateCaseReadingPreservesLiveBudLevelsAndChargingStatus() {
        val notification = AirPodsNotifications.BatteryNotification()
        notification.setBatteryDirect(95, false, 100, false, null, false)
        val buds = notification.getBattery().take(2)
        assertTrue(notification.updateCaseBattery(58, false))
        assertEquals(buds, notification.getBattery().take(2))
        assertEquals(Battery(BatteryComponent.CASE, 58, BatteryStatus.NOT_CHARGING),
            notification.getBattery().last())
    }

    @Test fun missingOrInvalidCaseReadingDoesNotInventZeroOrEraseConfirmedLevel() {
        val notification = AirPodsNotifications.BatteryNotification()
        notification.setBatteryDirect(95, false, 100, false, 58, false)
        for (level in listOf(null, -1, 101, 255)) assertFalse(notification.updateCaseBattery(level, true))
        assertEquals(58, notification.getBattery().last().level)
        assertEquals(BatteryStatus.NOT_CHARGING, notification.getBattery().last().status)
    }

    @Test fun reportedEmptyCaseLevelIsRealAndDuplicateReadingsDoNotRepublish() {
        val notification = AirPodsNotifications.BatteryNotification()
        assertTrue(notification.updateCaseBattery(0, true))
        assertTrue(notification.getBattery().last().hasReportedLevel())
        assertEquals(BatteryStatus.CHARGING, notification.getBattery().last().status)
        assertFalse(notification.updateCaseBattery(0, true))
    }

    @Test fun bleMissingLevelIsUnknownWhileReportedZeroRemainsReal() {
        val notification = AirPodsNotifications.BatteryNotification()
        notification.setBatteryDirect(null, true, 0, true, null, false)
        val batteries = notification.getBattery()
        assertFalse(batteries.first { it.component == BatteryComponent.LEFT }.hasReportedLevel())
        assertTrue(batteries.first { it.component == BatteryComponent.RIGHT }.hasReportedLevel())
        assertFalse(batteries.first { it.component == BatteryComponent.CASE }.hasReportedLevel())
    }
    @Test fun emptyChargingBudRemainsTheCombinedMinimum() {
        val batteries = listOf(
            Battery(BatteryComponent.LEFT, 0, BatteryStatus.CHARGING),
            Battery(BatteryComponent.RIGHT, 80, BatteryStatus.CHARGING),
            Battery(BatteryComponent.CASE, 95, BatteryStatus.NOT_CHARGING)
        )
        assertTrue(batteries[0].hasReportedLevel())
        assertEquals(Battery(BatteryComponent.LEFT, 0, BatteryStatus.CHARGING), batteries.combinedBudsBattery())
    }

    @Test fun disconnectedAndUnknownReadingsDoNotOverrideAnAvailableBud() {
        val valid = Battery(BatteryComponent.RIGHT, 42, BatteryStatus.NOT_CHARGING)
        for (unavailable in listOf(
            Battery(BatteryComponent.LEFT, 0, BatteryStatus.DISCONNECTED),
            Battery(BatteryComponent.LEFT, -1, BatteryStatus.CHARGING),
            Battery(BatteryComponent.LEFT, 255, BatteryStatus.NOT_CHARGING)
        )) {
            assertFalse(unavailable.hasReportedLevel())
            assertEquals(42, listOf(unavailable, valid).combinedBudsBattery()?.level)
        }
    }

    @Test fun mixedChargingStatesAreNotReportedAsBothCharging() {
        val result = listOf(
            Battery(BatteryComponent.LEFT, 25, BatteryStatus.CHARGING),
            Battery(BatteryComponent.RIGHT, 33, BatteryStatus.OPTIMIZED_CHARGING)
        ).combinedBudsBattery()
        assertEquals(25, result?.level)
        assertEquals(BatteryStatus.NOT_CHARGING, result?.status)
    }

    @Test fun absentBudsDoNotBorrowTheCaseReading() {
        assertNull(emptyList<Battery>().combinedBudsBattery())
        assertNull(listOf(Battery(BatteryComponent.CASE, 100, BatteryStatus.CHARGING)).combinedBudsBattery())
    }
}

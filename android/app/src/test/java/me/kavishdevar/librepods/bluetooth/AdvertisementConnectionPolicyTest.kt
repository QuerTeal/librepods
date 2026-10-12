package me.kavishdevar.librepods.bluetooth

import org.junit.Assert.*
import org.junit.Test

class AdvertisementConnectionPolicyTest {
    @Test fun openCaseWithBothSeatedBudsCanConnectWithoutAnIrkHostState() {
        assertTrue(AdvertisementConnectionPolicy.shouldConnect("Unknown", true, false, true, true))
    }

    @Test fun closedOrEmptyCaseCannotTriggerAutomaticPaging() {
        assertFalse(AdvertisementConnectionPolicy.shouldConnect("Unknown", false, false, true, true))
        assertFalse(AdvertisementConnectionPolicy.shouldConnect("Unknown", true, false, true, false))
        assertFalse(AdvertisementConnectionPolicy.shouldConnect("Unknown", null, false, true, true))
    }

    @Test fun freeWornPodCanConnectWithoutCaseContext() {
        assertTrue(AdvertisementConnectionPolicy.shouldConnect("Disconnected", null, true, false, false))
        assertFalse(AdvertisementConnectionPolicy.shouldConnect("Disconnected", null, false, false, false))
    }

    @Test fun anotherHostsIdleMusicOrCallCannotBeTakenOver() {
        for (host in listOf("Idle", "Music", "Call", "Ringing", "Unknown")) {
            assertFalse(AdvertisementConnectionPolicy.shouldConnect(host, true, true, false, false))
        }
    }
}

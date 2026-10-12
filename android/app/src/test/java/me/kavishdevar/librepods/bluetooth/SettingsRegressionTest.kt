package me.kavishdevar.librepods.bluetooth

import org.junit.Assert.*
import org.junit.Test

class SettingsRegressionTest {
    private val enabled = AudioTakeoverOptions(true, true, true, true, true, true)

    @Test fun everyHostAndTriggerRequiresBothOptions() {
        for (host in listOf("Disconnected", "Idle", "Music", "Call", "Ringing", "Hanging Up")) {
            for (trigger in listOf("music", "call")) {
                assertTrue(AudioTakeoverPolicy.allows(trigger, host, true, enabled))
                val triggerDisabled = if (trigger == "music") enabled.copy(mediaStart = false) else enabled.copy(ringingCall = false)
                assertFalse(AudioTakeoverPolicy.allows(trigger, host, true, triggerDisabled))
                val hostDisabled = when (host) {
                    "Disconnected" -> enabled.copy(disconnected = false)
                    "Idle" -> enabled.copy(idle = false)
                    "Music" -> enabled.copy(music = false)
                    else -> enabled.copy(call = false)
                }
                assertFalse(AudioTakeoverPolicy.allows(trigger, host, true, hostDisabled))
            }
        }
    }

    @Test fun missingEvidenceAndRemovedBudsCannotTakeOver() {
        for (ear in listOf(false, null)) assertFalse(AudioTakeoverPolicy.allows("call", "Idle", ear, enabled))
        for (host in listOf(null, "Unknown")) assertFalse(AudioTakeoverPolicy.allows("music", host, true, enabled))
        assertFalse(AudioTakeoverPolicy.allows("unknown", "Idle", true, enabled))
    }

    @Test fun allDisabledBlocksAnOtherwiseReadyTakeover() {
        assertFalse(AudioTakeoverPolicy.allows("music", "Music", true, AudioTakeoverOptions(false, false, false, false, false, false)))
    }

    @Test fun newerCaseCannotReplacePodWearAndHostEvidence() {
        val pod = BLEManager.AirPodsStatus("pod", lastSeen = 100, isLeftInEar = true, connectionState = "Music")
        val case = BLEManager.AirPodsStatus("case", lastSeen = 200, isCaseAdvertisement = true)
        assertEquals(pod, BLEManager.mostRecentPodStatus(listOf(pod, case)))
        assertNull(BLEManager.mostRecentPodStatus(listOf(case)))
    }

    @Test fun unreportedDeviceBooleansRemainUnknown() {
        assertNull(ConfirmedControl.boolean(null))
        assertNull(ConfirmedControl.boolean(CommandState(desired = listOf(1))))
        assertNull(ConfirmedControl.boolean(CommandState(confirmed = listOf(0))))
        assertNull(ConfirmedControl.listeningMode(CommandState(confirmed = listOf(5))))
    }

    @Test fun pendingRequestKeepsThePreviousDeviceReport() {
        val tracker = CommandTracker({ 0 })
        tracker.receive(0x20, byteArrayOf(2))
        tracker.begin(0x20, byteArrayOf(1))
        assertEquals(false, ConfirmedControl.boolean(tracker.states.value[0x20]))
        assertEquals(SettingPhase.PENDING, tracker.states.value[0x20]?.phase)
        tracker.receive(0x20, byteArrayOf(1, 0, 0, 0))
        assertEquals(true, ConfirmedControl.boolean(tracker.states.value[0x20]))
        assertEquals(SettingPhase.CONFIRMED, tracker.states.value[0x20]?.phase)
    }

    @Test fun timeoutDoesNotSelectTheRequestedNoiseMode() {
        var now = 0L
        val tracker = CommandTracker({ now })
        tracker.receive(0x0d, byteArrayOf(2))
        tracker.begin(0x0d, byteArrayOf(3))
        now = 5000
        tracker.expire()
        assertEquals(SettingPhase.FAILED, tracker.states.value[0x0d]?.phase)
        assertEquals(2, ConfirmedControl.listeningMode(tracker.states.value[0x0d]))
        tracker.receive(0x0d, byteArrayOf(3))
        assertEquals(3, ConfirmedControl.listeningMode(tracker.states.value[0x0d]))
        assertEquals(SettingPhase.CONFIRMED, tracker.states.value[0x0d]?.phase)
    }

    @Test fun failedConversationWriteDoesNotChangeConfirmedSwitch() {
        val tracker = CommandTracker({ 0 })
        tracker.receive(0x28, byteArrayOf(1))
        val token = tracker.begin(0x28, byteArrayOf(2))
        tracker.sendFailed(0x28, token)
        assertEquals(true, ConfirmedControl.boolean(tracker.states.value[0x28]))
        assertEquals(SettingPhase.FAILED, tracker.states.value[0x28]?.phase)
    }
}

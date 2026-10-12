package me.kavishdevar.librepods.data

import me.kavishdevar.librepods.bluetooth.ATTHandles
import me.kavishdevar.librepods.bluetooth.AttSettingState
import me.kavishdevar.librepods.bluetooth.SettingPhase
import org.junit.Assert.*
import org.junit.Test

class CapabilityRegistryTest {
    private val pro3 = CapabilityContext(true, setOf(Capability.HRM, Capability.HEARING_AID,
        Capability.LISTENING_MODE, Capability.ADAPTIVE_VOLUME), true, false, "9442752")

    @Test fun stockControlDoesNotImplyHearingTransport() {
        val map = CapabilityRegistry.resolve(pro3)
        assertTrue(map.getValue(AirPodsFeature.LISTENING_MODE).canUse)
        assertEquals(FeatureSupport.PRIVILEGED, map.getValue(AirPodsFeature.HEARING_AID).support)
    }

    @Test fun hookAloneDoesNotConfirmAttAccess() {
        val map = CapabilityRegistry.resolve(pro3.copy(vendorIdHook = true))
        assertFalse(map.getValue(AirPodsFeature.HEARING_AID).canUse)
        assertEquals(FeatureReason.ATT_READ_REQUIRED, map.getValue(AirPodsFeature.HEARING_AID).reason)
    }

    @Test fun confirmedReadEnablesOnlyItsHandle() {
        val map = CapabilityRegistry.resolve(pro3.copy(vendorIdHook = true, att = mapOf(
            ATTHandles.HEARING_AID to AttSettingState(confirmed = List(104) { 0.toByte() },
                phase = SettingPhase.CONFIRMED, verifiedAtMillis = 10))))
        assertTrue(map.getValue(AirPodsFeature.HEARING_AID).canUse)
        assertFalse(map.getValue(AirPodsFeature.TRANSPARENCY).canUse)
    }

    @Test fun heartRateCapabilityIsNotStreamingEvidence() {
        assertFalse(CapabilityRegistry.resolve(pro3).getValue(AirPodsFeature.HEART_RATE).canUse)
    }

    @Test fun personalizedVolumeDoesNotImplyAdaptiveAudio() {
        val map = CapabilityRegistry.resolve(pro3)
        assertTrue(map.getValue(AirPodsFeature.PERSONALIZED_VOLUME).canUse)
        assertEquals(FeatureReason.MODEL_UNSUPPORTED, map.getValue(AirPodsFeature.ADAPTIVE_AUDIO).reason)
    }

    @Test fun unknownModelCannotInheritProSettings() {
        val map = CapabilityRegistry.resolve(pro3.copy(modelKnown = false))
        assertFalse(map.getValue(AirPodsFeature.LISTENING_MODE).canUse)
        assertEquals(FeatureReason.MODEL_UNKNOWN, map.getValue(AirPodsFeature.LISTENING_MODE).reason)
    }

    @Test fun disconnectedSessionCannotReuseConfirmedHearingAccess() {
        val map = CapabilityRegistry.resolve(pro3.copy(controlReady = false, vendorIdHook = true,
            att = mapOf(ATTHandles.HEARING_AID to AttSettingState(confirmed = listOf(1)))))
        assertFalse(map.getValue(AirPodsFeature.HEARING_AID).canUse)
    }
}

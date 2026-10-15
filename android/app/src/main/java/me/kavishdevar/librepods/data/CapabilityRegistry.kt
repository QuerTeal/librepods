package me.kavishdevar.librepods.data

import me.kavishdevar.librepods.bluetooth.ATTHandles
import me.kavishdevar.librepods.bluetooth.AttSettingState
import me.kavishdevar.librepods.bluetooth.CommandState

enum class AirPodsFeature {
    BATTERY_POPUP, AUTO_CONNECTION, EAR_DETECTION, LISTENING_MODE, ADAPTIVE_AUDIO,
    PERSONALIZED_VOLUME, CONVERSATION_AWARENESS, STEM_CONTROLS, VOLUME_SWIPE,
    CALL_CONTROLS, HEAD_GESTURES, MICROPHONE, CHIME_VOLUME, SLEEP_DETECTION,
    CHARGING, CASE_TONES, LOUD_SOUND_REDUCTION, TRANSPARENCY, HEARING_AID,
    CUSTOM_EQ, HEART_RATE, HEAD_TRACKING, SPATIAL_AUDIO, DEVICE_SWITCHING,
    ANDROID_ASSISTANT, CAMERA_REMOTE, LIVE_LISTEN, LIVE_TRANSLATION, LAST_LOCATION,
    FIND_MY, FIRMWARE_INFO, FIRMWARE_UPDATE, SIRI, HIGH_QUALITY_MICROPHONE
}

enum class FeatureSupport { AVAILABLE, WAITING, PRIVILEGED, EXPERIMENTAL, UNAVAILABLE }
enum class FeatureReason {
    IMPLEMENTED, DEVICE_VALUE_RECEIVED, CONNECTION_REQUIRED, MODEL_UNKNOWN, MODEL_UNSUPPORTED,
    ATT_ACCESS_REQUIRED, ATT_READ_REQUIRED, SETTING_STATUS_REQUIRED, PROTOCOL_UNVERIFIED, SENSOR_ONLY,
    ANDROID_RENDERER_REQUIRED, APPLE_SERVICE_REQUIRED, PER_APP_INTEGRATION_REQUIRED,
    LOCATION_OPT_IN_REQUIRED, FIRMWARE_PROTOCOL_REQUIRED, TWO_DEVICE_PRIVILEGE_REQUIRED,
    AUDIO_OUTPUT_REQUIRED, PER_APP_MONITORING, PHONE_PERMISSION_REQUIRED
}
enum class FeatureTransport { BLE, AACP, ATT, ANDROID, APPLE }

data class FeatureAvailability(
    val support: FeatureSupport,
    val reason: FeatureReason,
    val transport: FeatureTransport,
    val verifiedAtMillis: Long? = null
) {
    val canUse: Boolean get() = support == FeatureSupport.AVAILABLE
}

data class CapabilityContext(
    val modelKnown: Boolean,
    val capabilities: Set<Capability>,
    val controlReady: Boolean,
    val vendorIdHook: Boolean,
    val firmware: String,
    val locationOptIn: Boolean = false,
    val audioOutputAvailable: Boolean? = null,
    val callPermissions: Boolean = false,
    val commands: Map<Byte, CommandState> = emptyMap(),
    val att: Map<ATTHandles, AttSettingState> = emptyMap()
)

/** Model capability is a prerequisite, never evidence of a working transport or received sample. */
object CapabilityRegistry {
    fun resolve(context: CapabilityContext): Map<AirPodsFeature, FeatureAvailability> =
        AirPodsFeature.entries.associateWith { feature -> resolve(feature, context) }

    private fun resolve(feature: AirPodsFeature, c: CapabilityContext): FeatureAvailability {
        fun result(support: FeatureSupport, reason: FeatureReason, transport: FeatureTransport,
                   verified: Long? = null) = FeatureAvailability(support, reason, transport, verified)
        fun unavailable(reason: FeatureReason, transport: FeatureTransport) =
            result(FeatureSupport.UNAVAILABLE, reason, transport)
        when (feature) {
            AirPodsFeature.FIND_MY, AirPodsFeature.SIRI, AirPodsFeature.LIVE_TRANSLATION ->
                return unavailable(FeatureReason.APPLE_SERVICE_REQUIRED, FeatureTransport.APPLE)
            AirPodsFeature.FIRMWARE_UPDATE ->
                return unavailable(FeatureReason.FIRMWARE_PROTOCOL_REQUIRED, FeatureTransport.AACP)
            AirPodsFeature.SPATIAL_AUDIO ->
                return unavailable(FeatureReason.ANDROID_RENDERER_REQUIRED, FeatureTransport.ANDROID)
            AirPodsFeature.HIGH_QUALITY_MICROPHONE ->
                return unavailable(FeatureReason.PROTOCOL_UNVERIFIED, FeatureTransport.AACP)
            AirPodsFeature.LIVE_LISTEN ->
                return result(if (c.audioOutputAvailable == true) FeatureSupport.AVAILABLE else FeatureSupport.WAITING,
                    if (c.audioOutputAvailable == true) FeatureReason.PER_APP_MONITORING else FeatureReason.AUDIO_OUTPUT_REQUIRED,
                    FeatureTransport.ANDROID)
            AirPodsFeature.LAST_LOCATION ->
                return result(if (c.locationOptIn) FeatureSupport.AVAILABLE else FeatureSupport.WAITING,
                    if (c.locationOptIn) FeatureReason.IMPLEMENTED else FeatureReason.LOCATION_OPT_IN_REQUIRED,
                    FeatureTransport.ANDROID)
            else -> Unit
        }
        val required = when (feature) {
            AirPodsFeature.LISTENING_MODE -> Capability.LISTENING_MODE
            AirPodsFeature.ADAPTIVE_AUDIO -> Capability.ADAPTIVE_AUDIO
            AirPodsFeature.PERSONALIZED_VOLUME -> Capability.ADAPTIVE_VOLUME
            AirPodsFeature.CONVERSATION_AWARENESS -> Capability.CONVERSATION_AWARENESS
            AirPodsFeature.STEM_CONTROLS -> Capability.STEM_CONFIG
            AirPodsFeature.CAMERA_REMOTE -> Capability.STEM_CONFIG
            AirPodsFeature.VOLUME_SWIPE -> Capability.SWIPE_FOR_VOLUME
            AirPodsFeature.HEAD_GESTURES -> Capability.HEAD_GESTURES
            AirPodsFeature.HEART_RATE -> Capability.HRM
            AirPodsFeature.SLEEP_DETECTION -> Capability.SLEEP_DETECTION
            AirPodsFeature.LOUD_SOUND_REDUCTION -> Capability.LOUD_SOUND_REDUCTION
            AirPodsFeature.TRANSPARENCY, AirPodsFeature.HEARING_AID -> Capability.HEARING_AID
            else -> null
        }
        if (!c.modelKnown) return result(FeatureSupport.WAITING, FeatureReason.MODEL_UNKNOWN, FeatureTransport.AACP)
        if (required != null && required !in c.capabilities)
            return unavailable(FeatureReason.MODEL_UNSUPPORTED, FeatureTransport.AACP)
        if (feature == AirPodsFeature.DEVICE_SWITCHING && !c.vendorIdHook)
            return result(FeatureSupport.PRIVILEGED, FeatureReason.TWO_DEVICE_PRIVILEGE_REQUIRED, FeatureTransport.AACP)
        val handle = when (feature) {
            AirPodsFeature.LOUD_SOUND_REDUCTION -> ATTHandles.LOUD_SOUND_REDUCTION
            AirPodsFeature.TRANSPARENCY -> ATTHandles.TRANSPARENCY
            AirPodsFeature.HEARING_AID -> ATTHandles.HEARING_AID
            else -> null
        }
        if (handle != null && !c.vendorIdHook)
            return result(FeatureSupport.PRIVILEGED, FeatureReason.ATT_ACCESS_REQUIRED, FeatureTransport.ATT)
        if (feature == AirPodsFeature.BATTERY_POPUP)
            return result(FeatureSupport.AVAILABLE, FeatureReason.IMPLEMENTED, FeatureTransport.BLE)
        if (!c.controlReady) return result(FeatureSupport.WAITING, FeatureReason.CONNECTION_REQUIRED,
            if (handle == null) FeatureTransport.AACP else FeatureTransport.ATT)
        if (feature == AirPodsFeature.HEAD_GESTURES && !c.callPermissions)
            return result(FeatureSupport.WAITING, FeatureReason.PHONE_PERMISSION_REQUIRED, FeatureTransport.ANDROID)
        if (handle != null) {
            val read = c.att[handle]
            val bytes = read?.confirmed?.toByteArray()
            val valid = bytes != null && when (handle) {
                ATTHandles.HEARING_AID -> parseHearingAidSettingsResponse(bytes) != null
                ATTHandles.TRANSPARENCY -> parseTransparencySettingsResponse(bytes) != null
                ATTHandles.LOUD_SOUND_REDUCTION -> bytes.size == 1 && bytes[0] in byteArrayOf(0, 1)
            }
            return if (valid) result(FeatureSupport.AVAILABLE,
                FeatureReason.DEVICE_VALUE_RECEIVED, FeatureTransport.ATT, read?.verifiedAtMillis)
            else result(FeatureSupport.WAITING, FeatureReason.ATT_READ_REQUIRED, FeatureTransport.ATT)
        }
        if (feature == AirPodsFeature.HEAD_TRACKING)
            return result(FeatureSupport.EXPERIMENTAL, FeatureReason.SENSOR_ONLY, FeatureTransport.AACP)
        if (feature == AirPodsFeature.FIRMWARE_INFO && c.firmware.isBlank())
            return result(FeatureSupport.WAITING, FeatureReason.CONNECTION_REQUIRED, FeatureTransport.AACP)
        val identifier: Byte? = when (feature) {
            AirPodsFeature.LISTENING_MODE -> 0x0D
            AirPodsFeature.ADAPTIVE_AUDIO -> 0x2E
            AirPodsFeature.PERSONALIZED_VOLUME -> 0x26
            AirPodsFeature.CONVERSATION_AWARENESS -> 0x28
            AirPodsFeature.MICROPHONE -> 0x01
            AirPodsFeature.CHIME_VOLUME -> 0x1F
            AirPodsFeature.SLEEP_DETECTION -> 0x35
            AirPodsFeature.CHARGING -> 0x3B
            AirPodsFeature.CASE_TONES -> 0x31
            AirPodsFeature.CUSTOM_EQ -> 0x63
            else -> null
        }
        val read = identifier?.let { c.commands[it] }
        if (feature in setOf(AirPodsFeature.CASE_TONES, AirPodsFeature.CHIME_VOLUME,
                AirPodsFeature.CHARGING, AirPodsFeature.CUSTOM_EQ) && read?.confirmed == null)
            return result(FeatureSupport.WAITING, FeatureReason.SETTING_STATUS_REQUIRED, FeatureTransport.AACP)
        return result(FeatureSupport.AVAILABLE,
            if (read?.confirmed != null) FeatureReason.DEVICE_VALUE_RECEIVED else FeatureReason.IMPLEMENTED,
            FeatureTransport.AACP, read?.verifiedAtMillis)
    }
}

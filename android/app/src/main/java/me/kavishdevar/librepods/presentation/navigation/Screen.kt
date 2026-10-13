package me.kavishdevar.librepods.presentation.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
sealed interface Screen: NavKey {
    val showTopBar: Boolean
        get() = true

    @Serializable
    data object Onboarding: Screen {
        override val showTopBar: Boolean = false
    }

    @Serializable
    data object AirPodsSettings: Screen

    @Serializable
    data object Rename: Screen

    @Serializable
    data object AppSettings: Screen

    @Serializable
    data object Language: Screen

    @Serializable
    data object Troubleshooting: Screen

    @Serializable
    data object FeatureStatus: Screen

    @Serializable
    data object HeadTracking: Screen

    @Serializable
    data object Accessibility: Screen

    @Serializable
    data object TransparencyCustomization: Screen

    @Serializable
    data object HearingAid: Screen

    @Serializable
    data object HearingAidAdjustments: Screen

    @Serializable
    data object AdaptiveStrength: Screen

//    @Serializable
//    data object CameraControl: Screen

    @Serializable
    data object OpenSourceLicenses: Screen

    @Serializable
    data object UpdateHearingTest: Screen

    @Serializable
    data object VersionInfo: Screen

    @Serializable
    data object HearingProtection: Screen

    @Serializable
    data object Purchase: Screen

    @Serializable
    data object Equalizer: Screen

    @Serializable
    data class LongPress(
        val bud: String
    ): Screen

    @Serializable
    data class CallControl(
        val action: String
    ): Screen

    @Serializable
    data object MicrophoneSettings: Screen

    /** An iOS 27 AirPods sub-page in the Apple design; see [AirPodsSection]. */
    @Serializable
    data class AirPodsSection(
        val section: String
    ): Screen

    @Serializable
    data object ReleaseNotes: Screen {
        override val showTopBar: Boolean = false
    }
}

/** Sub-pages of the Apple design's AirPods page, as in iOS 27 Settings. */
object AirPodsSection {
    const val AUDIO = "audio"
    const val CONTROLS = "controls"
    const val HEARING = "hearing"
    const val BATTERY = "battery"
}

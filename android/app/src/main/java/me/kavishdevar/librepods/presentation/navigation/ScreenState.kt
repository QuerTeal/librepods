package me.kavishdevar.librepods.presentation.navigation

/** Stable, Bundle-saveable routes; arguments survive activity recreation without a compiler plugin. */
internal object ScreenState {
    private val destinations = mapOf(
        "onboarding" to Screen.Onboarding,
        "airpods" to Screen.AirPodsSettings,
        "rename" to Screen.Rename,
        "settings" to Screen.AppSettings,
        "language" to Screen.Language,
        "troubleshooting" to Screen.Troubleshooting,
        "feature_status" to Screen.FeatureStatus,
        "head_tracking" to Screen.HeadTracking,
        "accessibility" to Screen.Accessibility,
        "transparency" to Screen.TransparencyCustomization,
        "hearing_aid" to Screen.HearingAid,
        "hearing_adjustments" to Screen.HearingAidAdjustments,
        "adaptive" to Screen.AdaptiveStrength,
        "licenses" to Screen.OpenSourceLicenses,
        "hearing_test" to Screen.UpdateHearingTest,
        "version" to Screen.VersionInfo,
        "hearing_protection" to Screen.HearingProtection,
        "purchase" to Screen.Purchase,
        "equalizer" to Screen.Equalizer,
        "microphone" to Screen.MicrophoneSettings,
        "release_notes" to Screen.ReleaseNotes,
    )

    fun encode(screen: Screen): String = when (screen) {
        is Screen.LongPress -> "long_press:${screen.bud}"
        is Screen.CallControl -> "call_control:${screen.action}"
        is Screen.AirPodsSection -> "section:${screen.section}"
        else -> destinations.entries.first { it.value == screen }.key
    }

    fun decode(route: String): Screen? = when {
        route.startsWith("long_press:") -> Screen.LongPress(route.substringAfter(':'))
        route.startsWith("call_control:") -> Screen.CallControl(route.substringAfter(':'))
        route.startsWith("section:") -> Screen.AirPodsSection(route.substringAfter(':'))
        else -> destinations[route]
    }
}

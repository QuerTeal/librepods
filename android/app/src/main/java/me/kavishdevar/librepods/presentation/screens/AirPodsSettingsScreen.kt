/*
    LibrePods - AirPods liberated from Apple’s ecosystem
    Copyright (C) 2025 LibrePods contributors

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <https://www.gnu.org/licenses/>.
*/

@file:OptIn(ExperimentalEncodingApi::class)

package me.kavishdevar.librepods.presentation.screens

// import me.kavishdevar.librepods.utils.RadareOffsetFinder
import me.kavishdevar.librepods.presentation.navigation.AirPodsSection
import me.kavishdevar.librepods.presentation.theme.AppleColors
import me.kavishdevar.librepods.presentation.components.StyledList
import me.kavishdevar.librepods.presentation.components.AppleGlyphs
import me.kavishdevar.librepods.presentation.components.AppleIconTile
import me.kavishdevar.librepods.presentation.components.AppleVolumeSlider
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyItemScope
import android.annotation.SuppressLint
import android.content.Context.MODE_PRIVATE
import android.content.Intent
import android.content.SharedPreferences
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.material3.toPath
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.graphics.shapes.Morph
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.highlight.Highlight
import kotlinx.coroutines.delay
import me.kavishdevar.librepods.BuildConfig
import me.kavishdevar.librepods.R
import me.kavishdevar.librepods.presentation.components.screenTopPadding
import me.kavishdevar.librepods.bluetooth.AACPManager
import me.kavishdevar.librepods.bluetooth.ATTHandles
import me.kavishdevar.librepods.data.AirPodsPro3
import me.kavishdevar.librepods.data.Capability
import me.kavishdevar.librepods.data.AirPodsFeature
import me.kavishdevar.librepods.bluetooth.SettingPhase
import me.kavishdevar.librepods.presentation.MaterialIcons
import me.kavishdevar.librepods.presentation.components.AboutCard
import me.kavishdevar.librepods.presentation.components.AudioSettings
import me.kavishdevar.librepods.presentation.components.BatteryView
import me.kavishdevar.librepods.presentation.components.CallControlSettings
import me.kavishdevar.librepods.presentation.components.ConnectionSettings
import me.kavishdevar.librepods.presentation.components.HearingHealthSettings
import me.kavishdevar.librepods.presentation.components.MaterialButtonStyle
import me.kavishdevar.librepods.presentation.components.NoiseControlSettings
import me.kavishdevar.librepods.presentation.components.PressAndHoldSettings
import me.kavishdevar.librepods.presentation.components.StyledButton
import me.kavishdevar.librepods.presentation.components.StyledListItem
import me.kavishdevar.librepods.presentation.components.StyledToggle
import me.kavishdevar.librepods.presentation.theme.DesignSystem
import me.kavishdevar.librepods.presentation.theme.LibrePodsTheme
import me.kavishdevar.librepods.presentation.theme.LocalDesignSystem
import me.kavishdevar.librepods.presentation.viewmodel.AirPodsUiState
import me.kavishdevar.librepods.presentation.viewmodel.AirPodsViewModel
import me.kavishdevar.librepods.presentation.viewmodel.demoState
import java.util.concurrent.TimeUnit
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.math.min
import kotlin.time.Duration.Companion.seconds
import me.kavishdevar.librepods.presentation.theme.sfProFamily
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import me.kavishdevar.librepods.presentation.theme.LocalOneUI

@Composable
fun AirPodsSettingsRoute(
    viewModel: AirPodsViewModel,
    navigateToRename: () -> Unit,
    navigateToHearingProtection: () -> Unit,
    navigateToHearingAid: () -> Unit,
    navigateToLeftLongPress: () -> Unit,
    navigateToRightLongPress: () -> Unit,
    navigateToPurchase: () -> Unit,
    navigateToAdaptiveStrength: () -> Unit,
    navigateToEqualizer: () -> Unit,
    navigateToHeadTracking: () -> Unit,
    navigateToAccessibility: () -> Unit,
    navigateToVersion: () -> Unit,
    navigateToTroubleshooting: () -> Unit,
    navigateToCallControlScreen: (action: String) -> Unit,
    navigateToMicrophoneSettings: () -> Unit,
    navigateToFeatureStatus: () -> Unit = {},
    section: String? = null,
    navigateToSection: (String) -> Unit = {}
) {
    val state by viewModel.uiState.collectAsState()

    val m3eEnabled = LocalDesignSystem.current == DesignSystem.Material
    val topPadding = screenTopPadding(material = WindowInsets.statusBars.asPaddingValues().calculateTopPadding())
    val bottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 12.dp

    Box (
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainer)
    ) {
        AirPodsSettingsScreen(
            state = state,

            topPadding = topPadding,
            bottomPadding = bottomPadding,

            setControlCommandInt = viewModel::setControlCommandInt,
            setControlCommandBoolean = viewModel::setControlCommandBoolean,
//        setControlCommandValue = viewModel::setControlCommandValue,
            setControlCommandByte = viewModel::setControlCommandByte,

            setATTCharacteristicValue = viewModel::setATTCharacteristicValue,

            onAutomaticEarDetectionChanged = viewModel::setAutomaticEarDetectionEnabled,
            onAutomaticConnectionChanged = viewModel::setAutomaticConnectionEnabled,
            setDynamicEndOfCharge = viewModel::setDynamicEndOfCharge,
            setOffListeningMode = viewModel::setOffListeningMode,
            disconnect = viewModel::disconnect,

            navigateToRename = navigateToRename,
            navigateToHearingProtection = navigateToHearingProtection,
            navigateToHearingAid = navigateToHearingAid,
            navigateToLeftLongPress = navigateToLeftLongPress,
            navigateToRightLongPress = navigateToRightLongPress,
            navigateToPurchase = navigateToPurchase,
            navigateToAdaptiveStrength = navigateToAdaptiveStrength,
            navigateToEqualizer = navigateToEqualizer,
            navigateToHeadTracking = navigateToHeadTracking,
            navigateToAccessibility = navigateToAccessibility,
            navigateToVersion = navigateToVersion,
            navigateToTroubleshooting = navigateToTroubleshooting,
            navigateToCallControlScreen = navigateToCallControlScreen,
            navigateToMicrophoneSettings = navigateToMicrophoneSettings,
            navigateToFeatureStatus = navigateToFeatureStatus,
            section = section,
            navigateToSection = navigateToSection,

            activateDemoMode = viewModel::activateDemoMode,
            reconnectFromSavedMac = viewModel::reconnectFromSavedMac
        )
    }
}

    @OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("MissingPermission", "UnspecifiedRegisterReceiverFlag")
@Composable
fun AirPodsSettingsScreen(
        state: AirPodsUiState,

        topPadding: Dp = 16.dp,
        bottomPadding: Dp = 16.dp,

        setControlCommandInt: (AACPManager.Companion.ControlCommandIdentifiers, Int) -> Unit,
        setControlCommandBoolean: (AACPManager.Companion.ControlCommandIdentifiers, Boolean) -> Unit,
//        setControlCommandValue: (AACPManager.Companion.ControlCommandIdentifiers, ByteArray) -> Unit,
        setControlCommandByte: (AACPManager.Companion.ControlCommandIdentifiers, Byte) -> Unit,
        setATTCharacteristicValue: (ATTHandles, ByteArray) -> Unit,

        onAutomaticEarDetectionChanged: (Boolean) -> Unit,
        onAutomaticConnectionChanged: (Boolean) -> Unit,
        setDynamicEndOfCharge: (Boolean) -> Unit,
        setOffListeningMode: (Boolean) -> Unit,
        disconnect: () -> Unit,

        navigateToRename: () -> Unit,
        navigateToHearingProtection: () -> Unit,
        navigateToHearingAid: () -> Unit,
        navigateToLeftLongPress: () -> Unit,
        navigateToRightLongPress: () -> Unit,
        navigateToPurchase: () -> Unit,
        navigateToAdaptiveStrength: () -> Unit,
        navigateToEqualizer: () -> Unit,
        navigateToHeadTracking: () -> Unit,
        navigateToAccessibility: () -> Unit,
        navigateToVersion: () -> Unit,
        navigateToTroubleshooting: () -> Unit,
        navigateToCallControlScreen: (action: String) -> Unit,
        navigateToMicrophoneSettings: () -> Unit,

        activateDemoMode: () -> Unit,
        reconnectFromSavedMac: () -> Unit,
        navigateToFeatureStatus: () -> Unit = {},
        section: String? = null,
        navigateToSection: (String) -> Unit = {},
) {
    val sharedPreferences = LocalContext.current.getSharedPreferences("settings", MODE_PRIVATE)
    var deviceName by remember {
        mutableStateOf(
            TextFieldValue(
                sharedPreferences.getString("name", state.deviceName).toString()
            )
        )
    }

    val nameChangeListener = remember {
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "name") {
                deviceName =
                    TextFieldValue(sharedPreferences.getString("name", "AirPods Pro").toString())
            }
        }
    }

    DisposableEffect(Unit) {
        sharedPreferences.registerOnSharedPreferenceChangeListener(nameChangeListener)
        onDispose {
            sharedPreferences.unregisterOnSharedPreferenceChangeListener(nameChangeListener)
        }
    }

    if (state.isLocallyConnected) {
        val capabilities = state.capabilities
        val features = state.featureAvailability

        // iOS 27 splits the AirPods page: the main page keeps the overview and links to sub-pages
        // that reuse these same entries. The other designs keep everything on one page.
        val appleLayout = LocalDesignSystem.current == DesignSystem.Apple
        fun shows(key: String): Boolean {
            // A spacer is keyed by a shortened name of the entry it precedes.
            val k = key.removePrefix("spacer_").let { short ->
                mapOf("noise" to "noise_control", "call" to "call_control", "sleep" to "sleep_detection",
                    "disconnect" to "disconnect_button")[short] ?: short
            }
            if (k == "top_padding" || k == "bottom_padding") return true
            if (!appleLayout) return !k.startsWith("apple_")
            return k in when (section) {
                null -> setOf("handoff_status", "play_update_banner", "battery", "name", "noise_control",
                    "apple_volume", "apple_nav", "upgrade_button", "about", "disconnect_button")
                AirPodsSection.AUDIO -> setOf("audio", "connection", "microphone", "sleep_detection",
                    "case_tones", "case_tone_volume", "off_listening")
                AirPodsSection.CONTROLS -> setOf("press_hold", "call_control", "head_tracking")
                AirPodsSection.HEARING -> setOf("hearing_health")
                AirPodsSection.BATTERY -> setOf("dynamic_end_of_charge")
                else -> emptySet()
            }
        }
        fun LazyListScope.entry(key: String, content: @Composable LazyItemScope.() -> Unit) {
            if (shows(key)) item(key = key, content = content)
        }
        val sectionGap = if (appleLayout) 28.dp else 16.dp

        LazyColumn(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(horizontal = 16.dp)
        ) {
            entry("top_padding") { Spacer(modifier = Modifier.height(topPadding)) }
            entry("handoff_status") {
                if (state.automaticReconnectPaused) {
                    StyledListItem(name = stringResource(R.string.handoff_paused_title),
                        description = stringResource(R.string.handoff_paused_description),
                        onClick = reconnectFromSavedMac,
                        orientation = me.kavishdevar.librepods.presentation.components.ListItemOrientation.Vertical,
                        height = 120.dp)
                }
            }
            entry("feature_status") {
                // This firmware applies most control writes without echoing them, so a missing echo
                // is not a failure. Keep the unconfirmed tracker result; a failed socket write and
                // every ATT failure still show.
                val phases = state.commandStates.values.filterNot { result ->
                    result.phase == SettingPhase.FAILED && result.failure == "confirmation_timeout"
                }.map { it.phase } + state.attStates.values.map { it.phase }
                val message = when {
                    SettingPhase.FAILED in phases -> R.string.setting_failed_notice
                    SettingPhase.PENDING in phases -> R.string.setting_pending_notice
                    else -> R.string.feature_status_description
                }
                StyledListItem(name = stringResource(R.string.feature_status_title),
                    description = stringResource(message), onClick = navigateToFeatureStatus,
                    orientation = me.kavishdevar.librepods.presentation.components.ListItemOrientation.Vertical,
                    height = 96.dp)
                Spacer(Modifier.height(16.dp))
            }
            entry("play_update_banner") {
                if (state.timeUntilFOSSPremiumExpiry > 0L) {
                    val context = LocalContext.current
                    Box(
                        modifier = Modifier
                            .background(Color(0xFF32829B), RoundedCornerShape(28.dp))
                            .clip(RoundedCornerShape(28.dp))
                            .clickable {
                                val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
                                    data = "mailto:".toUri()
                                    putExtra(Intent.EXTRA_EMAIL, arrayOf("billing@kavish.xyz"))
                                    putExtra(Intent.EXTRA_SUBJECT, "LibrePods Play billing error")
                                    putExtra(
                                        Intent.EXTRA_TEXT,
                                        "Please enter your GitHub username to restore your premium access:\n\nGitHub username: "
                                    )
                                }
                                context.startActivity(emailIntent)
                            }) {
                        Text(
                            text = stringResource(
                                R.string.play_foss_premium_banner,
                                maxOf(
                                    1,
                                    TimeUnit.MILLISECONDS.toDays(state.timeUntilFOSSPremiumExpiry)
                                        .toInt()
                                )
                            ), modifier = Modifier.padding(16.dp), style = TextStyle(
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontFamily = sfProFamily
                            )
                        )
                    }
                }
            }

            entry("battery") {
                BatteryView(
                    batteryList = state.battery,
                    budsRes = state.instance?.model?.budsRes ?: R.drawable.airpods_pro_3_buds,
                    caseRes = state.instance?.model?.caseRes ?: R.drawable.airpods_pro_3_case,
                    leftBudsRes = state.instance?.model?.leftBudsRes ?: R.drawable.airpods_pro_3_left,
                    rightBudsRes = state.instance?.model?.rightBudsRes ?: R.drawable.airpods_pro_3_right
                )
            }
            entry("spacer_battery") {
                Spacer(modifier = Modifier.height(32.dp))
            }

            entry("name") {
                StyledListItem(
                    name = stringResource(R.string.name),
                    description = deviceName.text,
                    onClick = navigateToRename,
                )
            }

            val hasHearingAidCapability =
                features[AirPodsFeature.HEARING_AID]?.canUse == true
            val hasPPECapability =
                state.instance?.model?.capabilities?.contains(Capability.PPE) == true

            if (hasHearingAidCapability || hasPPECapability) {
                if (hasPPECapability || state.vendorIdHook) {
                    entry("spacer_hearing_health") {
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
                entry("hearing_health") {
                    HearingHealthSettings(
                        hasPPECapability = hasPPECapability,
                        hasHearingAidCapability = hasHearingAidCapability,
                        vendorIdHook = state.vendorIdHook,
                        navigateToHearingProtection = navigateToHearingProtection,
                        navigateToHearingAid = navigateToHearingAid
                    )
                }
            }

            if (capabilities.contains(Capability.LISTENING_MODE)) {
                entry("spacer_noise") {
                    Spacer(modifier = Modifier.height(sectionGap))
                }
                entry("noise_control") {
                    NoiseControlSettings(
                        showOffListeningMode = state.offListeningMode,
                        noiseControlModeValue = state.controlStates[AACPManager.Companion.ControlCommandIdentifiers.LISTENING_MODE]?.getOrNull(
                            0
                        )?.toInt() ?: 3,
                        onNoiseControlModeChanged = {
                            setControlCommandInt(
                                AACPManager.Companion.ControlCommandIdentifiers.LISTENING_MODE, it
                            )
                        },
                    )
                }
            }

            entry("apple_volume") {
                Spacer(modifier = Modifier.height(24.dp))
                AppleVolumeSlider()
            }
            entry("apple_nav") {
                val hasHearing = features[AirPodsFeature.HEARING_AID]?.canUse == true ||
                    state.instance?.model?.capabilities?.contains(Capability.PPE) == true
                Spacer(modifier = Modifier.height(36.dp))
                StyledList {
                    StyledListItem(name = stringResource(R.string.audio_and_routing),
                        onClick = { navigateToSection(AirPodsSection.AUDIO) },
                        leadingContent = { AppleIconTile(AppleGlyphs.SPEAKER, AppleColors.audioTile) })
                    if (hasHearing) StyledListItem(name = stringResource(R.string.hearing_health),
                        onClick = { navigateToSection(AirPodsSection.HEARING) },
                        leadingContent = { AppleIconTile(AppleGlyphs.EAR, AppleColors.hearingTile) })
                    StyledListItem(name = stringResource(R.string.controls_and_gestures),
                        onClick = { navigateToSection(AirPodsSection.CONTROLS) },
                        leadingContent = { AppleIconTile(AppleGlyphs.GESTURE, AppleColors.controlsTile) })
                    StyledListItem(name = stringResource(R.string.accessibility),
                        onClick = navigateToAccessibility,
                        leadingContent = { AppleIconTile(AppleGlyphs.ACCESSIBILITY, AppleColors.accessibilityTile) })
                }
                Spacer(modifier = Modifier.height(sectionGap))
                StyledList {
                    StyledListItem(name = stringResource(R.string.battery),
                        onClick = { navigateToSection(AirPodsSection.BATTERY) },
                        leadingContent = { AppleIconTile(AppleGlyphs.BATTERY, AppleColors.batteryTile) })
                    StyledListItem(name = stringResource(R.string.feature_status_title),
                        onClick = navigateToFeatureStatus,
                        leadingContent = { AppleIconTile(AppleGlyphs.STATUS, AppleColors.statusTile) })
                }
            }

            if (capabilities.contains(Capability.STEM_CONFIG)) {
                entry("spacer_press_hold") {
                    Spacer(modifier = Modifier.height(sectionGap))
                }
                entry("press_hold") {
                    PressAndHoldSettings(
                        leftAction = state.leftAction,
                        rightAction = state.rightAction,
                        navigateToLeftLongPress = navigateToLeftLongPress,
                        navigateToRightLongPress = navigateToRightLongPress
                    )
                }
            }

            entry("spacer_call") {
                Spacer(modifier = Modifier.height(sectionGap))
            }
            entry("call_control") {
                val bytes =
                    state.controlStates[AACPManager.Companion.ControlCommandIdentifiers.CALL_MANAGEMENT_CONFIG]?.take(
                        2
                    )?.toByteArray() ?: byteArrayOf(0x00, 0x00)
                val flipped = try {
                    bytes[1] == 0x02.toByte()
                } catch (_: Exception) {
                    false
                }
                CallControlSettings(
                    flipped = flipped,
                    navigateToCallControlScreen = navigateToCallControlScreen
                )
            }

//                if (capabilities.contains(Capability.STEM_CONFIG) && !BuildConfig.PLAY_BUILD) {
//                    entry("spacer_camera") { Spacer(modifier = Modifier.height(sectionGap)) }
//                    entry("camera_control") {
//                        StyledListItem(
//                            to = "camera_control",
//                            name = stringResource(R.string.camera_remote),
//                            descriptionRes = stringResource(R.string.camera_control_description),
//                            titleRes = stringResource(R.string.camera_control),
//                            navController = navController
//                        )
//                    }
//                }

            entry("upgrade_button") {
                if (!state.isPremium) {
                    Spacer(modifier = Modifier.height(28.dp))
                    StyledButton(
                        onClick = navigateToPurchase,
                        backdrop = rememberLayerBackdrop(),
                        modifier = Modifier.fillMaxWidth(),
                        maxScale = 0.05f,
                        surfaceColor = MaterialTheme.colorScheme.primary
                    ) {
                        Text(
                            stringResource(R.string.unlock_advanced_features),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            entry("spacer_audio") { Spacer(modifier = Modifier.height(sectionGap)) }
            entry("audio") {
                val adaptiveVolumeCapability =
                    features[AirPodsFeature.PERSONALIZED_VOLUME]?.canUse == true
                val conversationalAwarenessCapability =
                    features[AirPodsFeature.CONVERSATION_AWARENESS]?.canUse == true
                val loudSoundReductionCapability =
                    features[AirPodsFeature.LOUD_SOUND_REDUCTION]?.canUse == true
                val adaptiveAudioCapability =
                    features[AirPodsFeature.ADAPTIVE_AUDIO]?.canUse == true

                val adaptiveVolumeChecked =
                    state.controlStates[AACPManager.Companion.ControlCommandIdentifiers.ADAPTIVE_VOLUME_CONFIG]?.getOrNull(
                        0
                    ) == 0x01.toByte()
                val conversationalAwarenessChecked =
                    state.controlStates[AACPManager.Companion.ControlCommandIdentifiers.CONVERSATION_DETECT_CONFIG]?.getOrNull(
                        0
                    ) == 0x01.toByte()

                AudioSettings(
                    adaptiveVolumeCapability = adaptiveVolumeCapability,
                    conversationalAwarenessCapability = conversationalAwarenessCapability,
                    loudSoundReductionCapability = loudSoundReductionCapability,
                    adaptiveAudioCapability = adaptiveAudioCapability,
                    customEqCapability = features[AirPodsFeature.CUSTOM_EQ]?.canUse == true,
                    adaptiveVolumeChecked = adaptiveVolumeChecked,
                    onAdaptiveVolumeCheckedChange = { checked ->
                        setControlCommandBoolean(
                            AACPManager.Companion.ControlCommandIdentifiers.ADAPTIVE_VOLUME_CONFIG,
                            checked
                        )
                    },
                    conversationalAwarenessChecked = conversationalAwarenessChecked && state.isPremium,
                    onConversationalAwarenessCheckedChange = { checked ->
                        setControlCommandBoolean(
                            AACPManager.Companion.ControlCommandIdentifiers.CONVERSATION_DETECT_CONFIG,
                            checked
                        )
                    },
                    loudSoundReductionChecked = state.loudSoundReductionEnabled,
                    onLoudSoundReductionCheckedChange = { checked ->
                        setATTCharacteristicValue(
                            ATTHandles.LOUD_SOUND_REDUCTION,
                            byteArrayOf(if (checked) 0x01.toByte() else 0x00.toByte())
                        )
                    },
                    navigateToAdaptiveStrength = navigateToAdaptiveStrength,
                    navigateToEqualizer = navigateToEqualizer,
                    vendorIdHook = state.vendorIdHook,
                    isPremium = state.isPremium
                )
            }

            entry("spacer_connection") { Spacer(modifier = Modifier.height(sectionGap)) }
            entry("connection") {
                ConnectionSettings(
                    automaticEarDetectionEnabled = state.automaticEarDetectionEnabled,
                    onAutomaticEarDetectionChanged = onAutomaticEarDetectionChanged,
                    automaticConnectionEnabled = state.automaticConnectionEnabled,
                    automaticConnectionFailed = state.commandStates[AACPManager.Companion.ControlCommandIdentifiers.AUTOMATIC_CONNECTION_CONFIG.value]
                        ?.let { it.phase == SettingPhase.FAILED && it.failure != "confirmation_timeout" } == true,
                    onAutomaticConnectionChanged = onAutomaticConnectionChanged
                )
            }

            entry("spacer_microphone") { Spacer(modifier = Modifier.height(sectionGap)) }
            entry("microphone") {
                val id = AACPManager.Companion.ControlCommandIdentifiers.MIC_MODE

                val selectedModeText = when (state.controlStates[id]?.getOrNull(0) ?: 0x00.toByte()) {
                    0x00.toByte() -> stringResource(R.string.microphone_automatic)
                    0x01.toByte() -> stringResource(R.string.microphone_always_right)
                    0x02.toByte() -> stringResource(R.string.microphone_always_left)
                    else -> stringResource(R.string.microphone_automatic)
                }

                StyledListItem(
                    name = stringResource(R.string.microphone_mode),
                    description = selectedModeText,
                    onClick = navigateToMicrophoneSettings
                )
            }

            if (capabilities.contains(Capability.SLEEP_DETECTION)) {
                entry("spacer_sleep") { Spacer(modifier = Modifier.height(sectionGap)) }
                entry("sleep_detection") {
                    val id = AACPManager.Companion.ControlCommandIdentifiers.SLEEP_DETECTION_CONFIG
                    StyledToggle(
                        label = stringResource(R.string.sleep_detection),
                        checked = state.controlStates[id]?.getOrNull(0) == 0x01.toByte(),
                        onCheckedChange = { setControlCommandBoolean(id, it) },
                        enabled = state.isPremium
                    )
                }
            }

            if (capabilities.contains(Capability.HEAD_GESTURES)) {
                entry("spacer_head_tracking") { Spacer(modifier = Modifier.height(sectionGap)) }
                entry("head_tracking") {
                    StyledListItem(
                        name = stringResource(R.string.head_gestures),
                        description = if (sharedPreferences.getBoolean(
                                "head_gestures", false
                            )
                        ) stringResource(R.string.on) else stringResource(R.string.off),
                        onClick = navigateToHeadTracking
                    )
                }
            }

            entry("spacer_dynamic_end_of_charge") { Spacer(modifier = Modifier.height(sectionGap)) }
            entry("dynamic_end_of_charge") {
                StyledToggle(
                    label = stringResource(R.string.optimized_charging),
                    description = stringResource(R.string.optimized_charging_description),
                    checked = state.dynamicEndOfCharge,
                    onCheckedChange = setDynamicEndOfCharge,
                    enabled = features[AirPodsFeature.CHARGING]?.canUse == true
                )
            }

            if (features[AirPodsFeature.CASE_TONES]?.canUse == true) {
                entry("spacer_case_tones") { Spacer(Modifier.height(16.dp)) }
                entry("case_tones") {
                    val id = AACPManager.Companion.ControlCommandIdentifiers.IN_CASE_TONE_CONFIG
                    StyledToggle(label = stringResource(R.string.case_tones_title),
                        description = stringResource(R.string.case_tones_note),
                        checked = state.controlStates[id]?.firstOrNull() == 1.toByte(),
                        onCheckedChange = { setControlCommandBoolean(id, it) })
                }
            }

            val caseVolumeId = AACPManager.Companion.ControlCommandIdentifiers.IN_CASE_TONE_VOLUME
            val caseVolume = state.controlStates[caseVolumeId]?.firstOrNull()?.toInt()?.and(0xff)
            if (caseVolume in 0..100) {
                entry("case_tone_volume") {
                    var draft by remember(caseVolume) { androidx.compose.runtime.mutableFloatStateOf(caseVolume!!.toFloat()) }
                    me.kavishdevar.librepods.presentation.components.StyledSlider(
                        label = stringResource(R.string.case_tone_volume), value = draft,
                        onValueChange = { draft = it; setControlCommandInt(caseVolumeId, it.toInt()) },
                        valueRange = 0f..100f, independent = true)
                }
            }

            entry("spacer_accessibility") { Spacer(modifier = Modifier.height(sectionGap)) }
            entry("accessibility") {
                StyledListItem(
                    name = stringResource(R.string.accessibility), onClick = navigateToAccessibility
                )
            }

            if (capabilities.contains(Capability.LOUD_SOUND_REDUCTION)) {
                entry("spacer_off_listening") { Spacer(modifier = Modifier.height(sectionGap)) }
                entry("off_listening") {
                    val id = AACPManager.Companion.ControlCommandIdentifiers.ALLOW_OFF_OPTION
                    StyledToggle(
                        label = stringResource(R.string.off_listening_mode),
                        description = stringResource(R.string.off_listening_mode_description),
                        checked = state.controlStates[id]?.getOrNull(0) == 0x01.toByte(),
                        onCheckedChange = setOffListeningMode
                    )
                }
            }

            entry("spacer_about") { Spacer(modifier = Modifier.height(32.dp)) }
            entry("about") {
                AboutCard(
                    modelName = state.modelName,
                    actualModel = state.actualModel,
                    serialNumbers = state.serialNumbers,
                    version = state.version3,
                    navigateToVersion = navigateToVersion
                )
            }

            entry("spacer_disconnect") { Spacer(modifier = Modifier.height(28.dp)) }
            entry("disconnect_button") {
                StyledButton(
                    onClick = disconnect,
                    backdrop = rememberLayerBackdrop(),
                    isInteractive = false,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                ) {
                    Text(
                        text = stringResource(R.string.disconnect),
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        textAlign = if (LocalOneUI.current) TextAlign.Center else TextAlign.Start,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

//                entry("spacer_debug") { Spacer(modifier = Modifier.height(sectionGap)) }
//                entry("debug") { StyledListItem("debug", "Debug", navController) }

            entry("bottom_padding") { Spacer(modifier = Modifier.height(bottomPadding)) }
        }
    } else {
        val backdrop = rememberLayerBackdrop()
        val backdropSurface = MaterialTheme.colorScheme.surfaceContainer
        Box(
            modifier = Modifier
                .drawBackdrop(
                    backdrop = rememberLayerBackdrop(),
                    exportedBackdrop = backdrop,
                    // Export the surface, without capturing child glass controls recursively.
                    onDrawSurface = { drawRect(backdropSurface) },
                    shape = { RoundedCornerShape(0.dp) },
                    highlight = {
                        Highlight.Ambient.copy(alpha = 0f)
                    },
                    effects = {}
                )
                .fillMaxSize()
                .padding(start = 8.dp, end = 8.dp, bottom = bottomPadding),
            contentAlignment = Alignment.Center
        ) {
            val tapCount = remember { mutableIntStateOf(0) }
            val lastTapTime = remember { mutableLongStateOf(0L) }

            var reconnecting by remember { mutableStateOf(false) }

            LaunchedEffect(reconnecting) {
                if (reconnecting) {
                    delay(5.seconds)
                    reconnecting = false
                }
            }

            if (LocalOneUI.current) {
                OneUINotConnected(
                    connectionSuccessful = state.connectionSuccessful,
                    automaticReconnectPaused = state.automaticReconnectPaused,
                    compatibilityBypassed = sharedPreferences.getBoolean("bypass_device_check.v2", false),
                    reconnecting = reconnecting,
                    onReconnect = {
                        reconnecting = true
                        reconnectFromSavedMac()
                    },
                    onTitleTapped = {
                        val now = System.currentTimeMillis()
                        if (now - lastTapTime.longValue > 400) tapCount.intValue = 0
                        tapCount.intValue++
                        lastTapTime.longValue = now
                        if (tapCount.intValue >= 5) {
                            tapCount.intValue = 0
                            activateDemoMode()
                        }
                    },
                    onTroubleshooting = navigateToTroubleshooting
                )
            } else when (LocalDesignSystem.current) {
                DesignSystem.Material -> {
                    val polygons = remember {
                        listOf(
                            MaterialShapes.Cookie9Sided,
                            MaterialShapes.Clover4Leaf,
                            MaterialShapes.SoftBurst,
                            MaterialShapes.Sunny,
                            MaterialShapes.Pentagon,
                            MaterialShapes.Cookie4Sided,
                            MaterialShapes.Oval,
                        )
                    }

                    val morphs = remember {
                        buildList {
                            for (i in polygons.indices) {
                                add(
                                    Morph(
                                        polygons[i].normalized(),
                                        polygons[(i + 1) % polygons.size].normalized()
                                    )
                                )
                            }
                        }
                    }

                    var currentMorphIndex by remember { mutableIntStateOf(0) }

                    val morphProgress = remember { Animatable(0f) }

                    LaunchedEffect(reconnecting) {
                        if (!reconnecting) {
                            currentMorphIndex = 0
                            morphProgress.snapTo(0f)
                            return@LaunchedEffect
                        }

                        while (reconnecting) {
                            morphProgress.snapTo(0f)

                            morphProgress.animateTo(
                                targetValue = 1f,
                                animationSpec = tween(
                                    durationMillis = 650,
                                    easing = FastOutSlowInEasing
                                )
                            )

                            currentMorphIndex = (currentMorphIndex + 1) % morphs.size
                        }
                    }

                    val path = remember { Path() }
                    val scaleMatrix = remember { Matrix() }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onLongPress = {
                                        activateDemoMode()
                                    }
                                )
                            }
                    ) {
                        Column(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(horizontal = 32.dp)
                                .pointerInput(Unit) {
                                    detectTapGestures(
                                        onTap = {
                                            val now = System.currentTimeMillis()

                                            if (now - lastTapTime.longValue > 400) {
                                                tapCount.intValue = 0
                                            }

                                            tapCount.intValue++
                                            lastTapTime.longValue = now

                                            if (tapCount.intValue >= 5) {
                                                tapCount.intValue = 0
                                                activateDemoMode()
                                            }
                                        }
                                    )
                                },
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            val primaryContainerColor = MaterialTheme.colorScheme.tertiaryContainer
                            val secondaryContainerColor = MaterialTheme.colorScheme.secondaryContainer

                            val animatedShapeColor by animateColorAsState(if (reconnecting) primaryContainerColor else secondaryContainerColor)

                            if (state.connectionSuccessful) {
                                Box(
                                    modifier = Modifier
                                        .size(240.dp)
                                        .background(
                                            MaterialTheme.colorScheme.surfaceBright,
                                            CircleShape
                                        )
                                        .clickable(
                                            interactionSource = null,
                                            indication = ripple(
                                                bounded = false,
                                                radius = 120.dp
                                            ),
                                            enabled = !reconnecting,
                                            onClick = {}
                                        )
                                        .pointerInput(Unit) {
                                            detectTapGestures(
                                                onTap = {
                                                    if (!reconnecting) {
                                                        currentMorphIndex = 1
                                                        reconnecting = true
                                                        reconnectFromSavedMac()
                                                    }
                                                },
                                                onPress = {
                                                    if (!reconnecting) {
                                                        morphProgress.animateTo(
                                                            targetValue = 1f,
                                                            animationSpec = spring(
                                                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                                                stiffness = Spring.StiffnessLow
                                                            )
                                                        )
                                                        tryAwaitRelease()
                                                        morphProgress.animateTo(
                                                            targetValue = 0f,
                                                            animationSpec = spring(
                                                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                                                stiffness = Spring.StiffnessLow
                                                            )
                                                        )
                                                    }
                                                }
                                            )
                                        }
                                        .drawWithContent {
                                            val activeMorph = morphs[currentMorphIndex]

                                            val shapePath = activeMorph.toPath(
                                                progress = morphProgress.value,
                                                path = path
                                            )

                                            val bounds = shapePath.getBounds()

                                            val scale = min(
                                                size.width / bounds.width,
                                                size.height / bounds.height
                                            ) * 0.8f

                                            scaleMatrix.reset()

                                            scaleMatrix.scale(x = scale, y = scale)

                                            shapePath.transform(scaleMatrix)

                                            shapePath.translate(size.center - shapePath.getBounds().center)

                                            drawPath(
                                                path = shapePath,
                                                color = animatedShapeColor
                                            )

                                            drawContent()
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (reconnecting) MaterialIcons.bluetooth_searching else MaterialIcons.headset_off,
                                        contentDescription = null,
                                        modifier = Modifier.size(84.dp),
                                        tint = if (reconnecting) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }

                                Spacer(Modifier.height(40.dp))

                                Text(
                                    text = if (reconnecting) stringResource(R.string.reconnecting) else stringResource(R.string.tap_to_reconnect),
                                    style = MaterialTheme.typography.labelSmallEmphasized,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            } else {
                                Text(
                                    text = stringResource(R.string.airpods_not_connected),
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                if (sharedPreferences.getBoolean("bypass_device_check.v2", false)) {
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        text = stringResource(R.string.compatibility_check_bypassed),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }

                        if (!BuildConfig.PLAY_BUILD) {
                            OutlinedButton(
                                onClick = navigateToTroubleshooting,
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(24.dp)
                            ) {
                                Text(
                                    stringResource(
                                        R.string.troubleshooting
                                    ),
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                        }
                    }
                }

                DesignSystem.Apple -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .pointerInput(Unit) {
                                    detectTapGestures(
                                        onTap = {
                                            val now = System.currentTimeMillis()

                                            if (now - lastTapTime.longValue > 400) {
                                                tapCount.intValue = 0
                                            }

                                            tapCount.intValue++
                                            lastTapTime.longValue = now

                                            if (tapCount.intValue >= 5) {
                                                tapCount.intValue = 0
                                                activateDemoMode()
                                            }
                                        })
                                }) {
                            Text(
                                text = stringResource(R.string.airpods_not_connected),
                                style = MaterialTheme.typography.displaySmall,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(24.dp))
                            Text(
                                text = stringResource(if (state.automaticReconnectPaused) R.string.handoff_paused_description else R.string.airpods_not_connected_description),
                                style = MaterialTheme.typography.bodyLarge,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }

                        if (state.connectionSuccessful) {
                            StyledButton(
                                onClick = { reconnectFromSavedMac(); reconnecting = true },
                                backdrop = backdrop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                                    .widthIn(max = 200.dp),
                                enabled = !reconnecting
                            ) {
                                Text(
                                    text = stringResource(R.string.reconnect_to_last_device),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }

                    if (!BuildConfig.PLAY_BUILD) {
                        StyledButton(
                            onClick = navigateToTroubleshooting,
                            backdrop = backdrop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.BottomCenter)
                                .padding(16.dp)
                                .widthIn(max = 200.dp),
                            materialButtonStyle = MaterialButtonStyle.Outlined,
                        ) {
                            Text(
                                text = stringResource(R.string.troubleshooting),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        }
    }
}

/** One UI empty state: a soft circular icon, a short explanation and a single pill button. */
@Composable
private fun OneUINotConnected(
    connectionSuccessful: Boolean,
    automaticReconnectPaused: Boolean,
    compatibilityBypassed: Boolean,
    reconnecting: Boolean,
    onReconnect: () -> Unit,
    onTitleTapped: () -> Unit,
    onTroubleshooting: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 24.dp)
                .padding(bottom = 64.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(112.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (reconnecting) MaterialIcons.bluetooth_searching else MaterialIcons.headset_off,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(52.dp)
                )
            }
            Spacer(modifier = Modifier.height(28.dp))
            Text(
                text = stringResource(R.string.airpods_not_connected),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.pointerInput(Unit) { detectTapGestures(onTap = { onTitleTapped() }) }
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = stringResource(if (automaticReconnectPaused) R.string.handoff_paused_description else R.string.airpods_not_connected_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            if (compatibilityBypassed) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.compatibility_check_bypassed),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
            if (connectionSuccessful) {
                Spacer(modifier = Modifier.height(32.dp))
                Button(
                    onClick = onReconnect,
                    enabled = !reconnecting,
                    modifier = Modifier.heightIn(min = 52.dp)
                ) {
                    Text(
                        text = stringResource(if (reconnecting) R.string.reconnecting else R.string.reconnect_to_last_device),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                }
            }
        }
        if (!BuildConfig.PLAY_BUILD) {
            TextButton(
                onClick = onTroubleshooting,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 20.dp)
            ) {
                Text(stringResource(R.string.troubleshooting), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Preview(name = "Apple")
@Composable
fun AirPodsSettingsScreenPreviewApple() {
    LibrePodsTheme(
        m3eEnabled = false
    ) {
        Box(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surfaceContainer)
        ) {
            AirPodsSettingsScreen(
                state = demoState,

                setControlCommandInt = { _, _ -> },
                setControlCommandBoolean = { _, _ -> },
                setControlCommandByte = { _, _ -> },
                setATTCharacteristicValue = { _, _ -> },

                onAutomaticEarDetectionChanged = {},
                onAutomaticConnectionChanged = {},
                setDynamicEndOfCharge = {},
                setOffListeningMode = {},
                disconnect = {},

                navigateToRename = {},
                navigateToHearingProtection = {},
                navigateToHearingAid = {},
                navigateToLeftLongPress = {},
                navigateToRightLongPress = {},
                navigateToPurchase = {},
                navigateToAdaptiveStrength = {},
                navigateToEqualizer = {},
                navigateToHeadTracking = {},
                navigateToAccessibility = {},
                navigateToVersion = {},
                navigateToTroubleshooting = {},
                navigateToCallControlScreen = {},
                navigateToMicrophoneSettings = {},

                activateDemoMode = {},
                reconnectFromSavedMac = {}
            )
        }
    }
}


@Preview(name = "Material")
@Composable
fun AirPodsSettingsScreenPreviewMaterial() {
    LibrePodsTheme(
        m3eEnabled = true
    ) {
        Box (
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surfaceContainer)
        ) {
            AirPodsSettingsScreen(
                state = demoState,

                setControlCommandInt = { _, _ -> },
                setControlCommandBoolean = { _, _ -> },
                setControlCommandByte = { _, _ -> },
                setATTCharacteristicValue = { _, _ -> },

                onAutomaticEarDetectionChanged = {},
                onAutomaticConnectionChanged = {},
                setDynamicEndOfCharge = {},
                setOffListeningMode = {},
                disconnect = {},

                navigateToRename = {},
                navigateToHearingProtection = {},
                navigateToHearingAid = {},
                navigateToLeftLongPress = {},
                navigateToRightLongPress = {},
                navigateToPurchase = {},
                navigateToAdaptiveStrength = {},
                navigateToEqualizer = {},
                navigateToHeadTracking = {},
                navigateToAccessibility = {},
                navigateToVersion = {},
                navigateToTroubleshooting = {},
                navigateToCallControlScreen = {},
                navigateToMicrophoneSettings = {},

                activateDemoMode = {},
                reconnectFromSavedMac = {}
            )
        }
    }
}

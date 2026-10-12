package me.kavishdevar.librepods.presentation.screens

import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import me.kavishdevar.librepods.RemoteCameraActivity
import me.kavishdevar.librepods.LastConnectionLocationActivity
import me.kavishdevar.librepods.AudioMonitorActivity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import me.kavishdevar.librepods.R
import me.kavishdevar.librepods.data.AirPodsFeature
import me.kavishdevar.librepods.data.AudioDiagnostics
import me.kavishdevar.librepods.data.FeatureReason
import me.kavishdevar.librepods.data.FeatureSupport
import me.kavishdevar.librepods.presentation.components.ListItemOrientation
import me.kavishdevar.librepods.presentation.components.StyledListItem
import me.kavishdevar.librepods.presentation.components.StyledSectionHeader
import me.kavishdevar.librepods.presentation.components.StyledList
import me.kavishdevar.librepods.presentation.theme.DesignSystem
import me.kavishdevar.librepods.presentation.theme.LocalDesignSystem
import me.kavishdevar.librepods.presentation.components.screenTopPadding
import me.kavishdevar.librepods.presentation.components.screenBottomPadding
import me.kavishdevar.librepods.presentation.viewmodel.AirPodsViewModel
import me.kavishdevar.librepods.presentation.viewmodel.AirPodsUiState

@Composable
fun FeatureStatusScreen(viewModel: AirPodsViewModel) {
    val context = LocalContext.current
    val phonePermissions = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()) {
        viewModel.refreshCallPermissions()
    }
    val state by viewModel.uiState.collectAsState()
    var diagnostics by remember { mutableStateOf<AudioDiagnostics?>(null) }
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(viewModel) {
        while (true) {
            diagnostics = viewModel.readAudioDiagnostics()
            now = SystemClock.elapsedRealtime()
            delay(1000)
        }
    }
    FeatureStatusContent(state, diagnostics, now, viewModel::loadATT) { feature ->
        if (feature == AirPodsFeature.HEAD_GESTURES) {
            phonePermissions.launch(arrayOf(android.Manifest.permission.READ_PHONE_STATE,
                android.Manifest.permission.ANSWER_PHONE_CALLS))
            return@FeatureStatusContent
        }
        val destination = when (feature) {
            AirPodsFeature.CAMERA_REMOTE -> RemoteCameraActivity::class.java
            AirPodsFeature.LAST_LOCATION -> LastConnectionLocationActivity::class.java
            AirPodsFeature.LIVE_LISTEN -> AudioMonitorActivity::class.java
            else -> null
        }
        destination?.let { context.startActivity(Intent(context, it)) }
    }
}

@Composable
fun FeatureStatusContent(state: AirPodsUiState, diagnostics: AudioDiagnostics?, now: Long,
                         readSettings: () -> Unit, openFeature: (AirPodsFeature) -> Unit) {
    val available = state.featureAvailability
    val topPadding = screenTopPadding(material = 0.dp)
    val bottomPadding = screenBottomPadding(material = 0.dp)
    val apple = LocalDesignSystem.current == DesignSystem.Apple
    LazyColumn(modifier = Modifier.fillMaxSize()
        .background(MaterialTheme.colorScheme.surfaceContainer).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Spacer(Modifier.height(topPadding)) }
        if (apple) {
            item { AppleDiagnostics(diagnostics) }
        } else {
            item { StyledSectionHeader(stringResource(R.string.diagnostics_title)) }
            diagnostics?.let { audio ->
                item { DiagnosticRow(R.string.diagnostics_bt_permission, audio.bluetoothPermission) }
                item { DiagnosticRow(R.string.diagnostics_overlay, audio.overlayPermission) }
                item { DiagnosticRow(R.string.diagnostics_companion, audio.companionAssociated) }
                item { DiagnosticRow(R.string.diagnostics_music_output, audio.selectedMusicOutputAvailable) }
                item { Text(stringResource(R.string.diagnostics_music_output_note),
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp)) }
                item { DiagnosticRow(R.string.diagnostics_communication, audio.selectedCommunicationOutput) }
                item { StyledListItem(name = stringResource(R.string.diagnostics_codec), onClick = null,
                    description = audio.codec ?: stringResource(R.string.diagnostics_codec_unknown),
                    orientation = ListItemOrientation.Vertical, height = 90.dp) }
            }
        }
        item { Text(stringResource(R.string.diagnostics_audio_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp)) }
        item { StyledListItem(name = stringResource(R.string.setting_retry_read),
            onClick = readSettings, enabled = state.isLocallyConnected && state.vendorIdHook) }
        item { StyledSectionHeader(stringResource(R.string.feature_status_title)) }
        items(AirPodsFeature.entries, key = { it.name }) { feature ->
            val support = available.getValue(feature)
            val status = stringResource(when (support.support) {
                FeatureSupport.AVAILABLE -> R.string.feature_available
                FeatureSupport.WAITING -> R.string.feature_waiting
                FeatureSupport.PRIVILEGED -> R.string.feature_privileged
                FeatureSupport.EXPERIMENTAL -> R.string.feature_experimental
                FeatureSupport.UNAVAILABLE -> R.string.feature_unavailable
            })
            val reason = stringResource(featureReason(support.reason))
            val age = support.verifiedAtMillis?.let {
                stringResource(R.string.feature_settings_received, ((now - it).coerceAtLeast(0) / 1000))
            }
            StyledListItem(name = stringResource(featureTitle(feature)), onClick =
                if (feature == AirPodsFeature.CAMERA_REMOTE && support.canUse)
                    ({ openFeature(feature) })
                else if (feature == AirPodsFeature.LAST_LOCATION)
                    ({ openFeature(feature) })
                else if (feature == AirPodsFeature.LIVE_LISTEN)
                    ({ openFeature(feature) })
                else if (feature == AirPodsFeature.HEAD_GESTURES && support.reason == FeatureReason.PHONE_PERMISSION_REQUIRED)
                    ({ openFeature(feature) }) else null,
                description = listOfNotNull(status, reason, age).joinToString("\n"),
                orientation = ListItemOrientation.Vertical, height = if (age == null) 116.dp else 140.dp)
        }
        item { Spacer(Modifier.height(bottomPadding)) }
    }
}

@Composable
private fun AppleDiagnostics(diagnostics: AudioDiagnostics?) {
    StyledList(title = stringResource(R.string.diagnostics_title),
        description = stringResource(R.string.diagnostics_music_output_note)) {
        listOf(R.string.diagnostics_bt_permission to diagnostics?.bluetoothPermission,
            R.string.diagnostics_overlay to diagnostics?.overlayPermission,
            R.string.diagnostics_companion to diagnostics?.companionAssociated,
            R.string.diagnostics_music_output to diagnostics?.selectedMusicOutputAvailable,
            R.string.diagnostics_communication to diagnostics?.selectedCommunicationOutput).forEach { (title, value) ->
            StyledListItem(name = stringResource(title),
                description = stringResource(when (value) {
                    true -> R.string.diagnostics_yes
                    false -> R.string.diagnostics_no
                    null -> R.string.diagnostics_unknown
                }), orientation = ListItemOrientation.Vertical, enabled = true)
        }
        StyledListItem(name = stringResource(R.string.diagnostics_codec),
            description = diagnostics?.codec ?: stringResource(R.string.diagnostics_codec_unknown),
            orientation = ListItemOrientation.Vertical, enabled = true)
    }
}

@Composable private fun DiagnosticRow(title: Int, value: Boolean?) {
    StyledListItem(name = stringResource(title), onClick = null,
        description = stringResource(when (value) {
            true -> R.string.diagnostics_yes
            false -> R.string.diagnostics_no
            null -> R.string.diagnostics_unknown
        }))
}

internal fun featureTitle(feature: AirPodsFeature): Int = when (feature) {
    AirPodsFeature.BATTERY_POPUP -> R.string.feature_battery_popup
    AirPodsFeature.AUTO_CONNECTION -> R.string.feature_auto_connection
    AirPodsFeature.EAR_DETECTION -> R.string.feature_ear_detection
    AirPodsFeature.LISTENING_MODE -> R.string.feature_listening_mode
    AirPodsFeature.ADAPTIVE_AUDIO -> R.string.feature_adaptive_audio
    AirPodsFeature.PERSONALIZED_VOLUME -> R.string.feature_personalized_volume
    AirPodsFeature.CONVERSATION_AWARENESS -> R.string.feature_conversation_awareness
    AirPodsFeature.STEM_CONTROLS -> R.string.feature_stem_controls
    AirPodsFeature.VOLUME_SWIPE -> R.string.feature_volume_swipe
    AirPodsFeature.CALL_CONTROLS -> R.string.feature_call_controls
    AirPodsFeature.HEAD_GESTURES -> R.string.feature_head_gestures
    AirPodsFeature.MICROPHONE -> R.string.feature_microphone
    AirPodsFeature.CHIME_VOLUME -> R.string.feature_chime_volume
    AirPodsFeature.SLEEP_DETECTION -> R.string.feature_sleep_detection
    AirPodsFeature.CHARGING -> R.string.feature_charging
    AirPodsFeature.CASE_TONES -> R.string.feature_case_tones
    AirPodsFeature.LOUD_SOUND_REDUCTION -> R.string.feature_loud_sound_reduction
    AirPodsFeature.TRANSPARENCY -> R.string.feature_transparency
    AirPodsFeature.HEARING_AID -> R.string.feature_hearing_aid
    AirPodsFeature.CUSTOM_EQ -> R.string.feature_custom_eq
    AirPodsFeature.HEART_RATE -> R.string.feature_heart_rate
    AirPodsFeature.HEAD_TRACKING -> R.string.feature_head_tracking
    AirPodsFeature.SPATIAL_AUDIO -> R.string.feature_spatial_audio
    AirPodsFeature.DEVICE_SWITCHING -> R.string.feature_device_switching
    AirPodsFeature.ANDROID_ASSISTANT -> R.string.feature_android_assistant
    AirPodsFeature.CAMERA_REMOTE -> R.string.feature_camera_remote
    AirPodsFeature.LIVE_LISTEN -> R.string.feature_live_listen
    AirPodsFeature.LIVE_TRANSLATION -> R.string.feature_live_translation
    AirPodsFeature.LAST_LOCATION -> R.string.feature_last_location
    AirPodsFeature.FIND_MY -> R.string.feature_find_my
    AirPodsFeature.FIRMWARE_INFO -> R.string.feature_firmware_info
    AirPodsFeature.FIRMWARE_UPDATE -> R.string.feature_firmware_update
    AirPodsFeature.SIRI -> R.string.feature_siri
    AirPodsFeature.HIGH_QUALITY_MICROPHONE -> R.string.feature_high_quality_microphone
}
internal fun featureReason(reason: FeatureReason): Int = when (reason) {
    FeatureReason.IMPLEMENTED -> R.string.feature_reason_implemented
    FeatureReason.DEVICE_VALUE_RECEIVED -> R.string.feature_reason_device_value_received
    FeatureReason.CONNECTION_REQUIRED -> R.string.feature_reason_connection_required
    FeatureReason.MODEL_UNKNOWN -> R.string.feature_reason_model_unknown
    FeatureReason.MODEL_UNSUPPORTED -> R.string.feature_reason_model_unsupported
    FeatureReason.ATT_ACCESS_REQUIRED -> R.string.feature_reason_att_access_required
    FeatureReason.ATT_READ_REQUIRED -> R.string.feature_reason_att_read_required
    FeatureReason.SETTING_STATUS_REQUIRED -> R.string.feature_reason_setting_status_required
    FeatureReason.PROTOCOL_UNVERIFIED -> R.string.feature_reason_protocol_unverified
    FeatureReason.SENSOR_ONLY -> R.string.feature_reason_sensor_only
    FeatureReason.ANDROID_RENDERER_REQUIRED -> R.string.feature_reason_android_renderer_required
    FeatureReason.APPLE_SERVICE_REQUIRED -> R.string.feature_reason_apple_service_required
    FeatureReason.PER_APP_INTEGRATION_REQUIRED -> R.string.feature_reason_per_app_integration_required
    FeatureReason.LOCATION_OPT_IN_REQUIRED -> R.string.feature_reason_location_opt_in_required
    FeatureReason.FIRMWARE_PROTOCOL_REQUIRED -> R.string.feature_reason_firmware_protocol_required
    FeatureReason.TWO_DEVICE_PRIVILEGE_REQUIRED -> R.string.feature_reason_two_device_privilege_required
    FeatureReason.AUDIO_OUTPUT_REQUIRED -> R.string.feature_reason_audio_output_required
    FeatureReason.PER_APP_MONITORING -> R.string.feature_reason_per_app_monitoring
    FeatureReason.PHONE_PERMISSION_REQUIRED -> R.string.feature_reason_phone_permission_required
}

@Composable
fun FeatureGate(viewModel: AirPodsViewModel, feature: AirPodsFeature, content: @Composable () -> Unit) {
    val state by viewModel.uiState.collectAsState()
    val availability = state.featureAvailability.getValue(feature)
    if (availability.canUse) content()
    else {
        LazyColumn(modifier = Modifier.padding(16.dp)) {
            item { Spacer(Modifier.height(16.dp)) }
            item { StyledListItem(name = stringResource(featureTitle(feature)), onClick = null,
                description = stringResource(featureReason(availability.reason)),
                orientation = ListItemOrientation.Vertical, height = 140.dp) }
        }
    }
}


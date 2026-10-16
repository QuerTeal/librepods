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

package me.kavishdevar.librepods.presentation.screens

import kotlin.math.roundToInt
import me.kavishdevar.librepods.presentation.components.StyledSectionHeader
import me.kavishdevar.librepods.presentation.overlays.IslandLayout
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import me.kavishdevar.librepods.BuildConfig
import me.kavishdevar.librepods.R
import me.kavishdevar.librepods.presentation.components.screenBottomPadding
import me.kavishdevar.librepods.presentation.components.screenTopPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.ui.text.style.TextOverflow
import me.kavishdevar.librepods.presentation.theme.LocalOneUI
import me.kavishdevar.librepods.presentation.theme.OneUIText
import me.kavishdevar.librepods.presentation.components.AppInfoCard
import me.kavishdevar.librepods.presentation.components.DeviceInfoCard
import me.kavishdevar.librepods.presentation.components.StyledBottomSheet
import me.kavishdevar.librepods.presentation.components.OneUIBottomSheet
import me.kavishdevar.librepods.presentation.components.StyledButton
import me.kavishdevar.librepods.presentation.components.StyledIconButton
import me.kavishdevar.librepods.presentation.components.StyledInputField
import me.kavishdevar.librepods.presentation.components.StyledList
import me.kavishdevar.librepods.presentation.components.StyledListItem
import me.kavishdevar.librepods.presentation.components.StyledSlider
import me.kavishdevar.librepods.presentation.components.StyledToggle
import me.kavishdevar.librepods.presentation.theme.DesignSystem
import me.kavishdevar.librepods.presentation.theme.LocalDesignSystem
import me.kavishdevar.librepods.presentation.theme.MaterialTypography
import me.kavishdevar.librepods.presentation.viewmodel.AppSettingsViewModel
import me.kavishdevar.librepods.data.ConversationalAwarenessDefaults
import me.kavishdevar.librepods.utils.XposedState
import java.util.concurrent.TimeUnit
import me.kavishdevar.librepods.presentation.theme.sfProFamily
import android.content.Context
import android.provider.Settings
import me.kavishdevar.librepods.data.Battery
import me.kavishdevar.librepods.data.BatteryComponent
import me.kavishdevar.librepods.data.BatteryStatus
import me.kavishdevar.librepods.presentation.components.ListItemOrientation
import me.kavishdevar.librepods.presentation.overlays.PopupWindow
import me.kavishdevar.librepods.presentation.overlays.IslandWindow
import me.kavishdevar.librepods.services.ServiceManager
import me.kavishdevar.librepods.presentation.theme.AppDesign
import android.os.Handler
import android.os.Looper
import me.kavishdevar.librepods.QuickSettingsDialogActivity
import me.kavishdevar.librepods.CompanionRegistrationActivity

/** Shows the bottom connection popup with sample levels so it can be tried without the case. */
internal fun previewIslandPopup(context: Context): IslandWindow? {
    if (!Settings.canDrawOverlays(context)) {
        Toast.makeText(context, R.string.overlay_permission_required, Toast.LENGTH_LONG).show()
        return null
    }
    val island = IslandWindow(context.applicationContext, observeDeviceEvents = false)
    val name = context.getSharedPreferences("settings", Context.MODE_PRIVATE).getString("name", null) ?: "AirPods Pro 3"
    return if (island.show(name, 85, context)) island else {
        Toast.makeText(context, R.string.island_popup_failed, Toast.LENGTH_LONG).show()
        null
    }
}

private fun previewConnectionPopup(context: Context): PopupWindow? {
    if (!Settings.canDrawOverlays(context)) {
        Toast.makeText(context, R.string.overlay_permission_required, Toast.LENGTH_SHORT).show()
        return null
    }
    val service = ServiceManager.getService()
    val name = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        .getString("name", null) ?: service?.airpodsInstance?.name ?: "AirPods Pro"
    // Starts with the buds in the case, takes them out to show the split, then puts them back to charge.
    val handler = Handler(Looper.getMainLooper())
    val popup = PopupWindow(context, model = service?.airpodsInstance?.model,
        observeDeviceEvents = false, onCloseCallback = { handler.removeCallbacksAndMessages(null) })
    popup.open(
        name = name,
        batteries = listOf(
            Battery(BatteryComponent.LEFT, 85, BatteryStatus.CHARGING),
            Battery(BatteryComponent.RIGHT, 80, BatteryStatus.CHARGING),
            Battery(BatteryComponent.CASE, 100, BatteryStatus.NOT_CHARGING)
        ),
        earStatus = byteArrayOf(0x02, 0x02)
    )
    handler.postDelayed({
        popup.update(
            batteries = listOf(
                Battery(BatteryComponent.LEFT, 85, BatteryStatus.NOT_CHARGING),
                Battery(BatteryComponent.RIGHT, 80, BatteryStatus.NOT_CHARGING),
                Battery(BatteryComponent.CASE, 100, BatteryStatus.NOT_CHARGING)
            ),
            earStatus = byteArrayOf(0x01, 0x01)
        )
    }, 3_500)
    handler.postDelayed({
        popup.update(
            batteries = listOf(
                Battery(BatteryComponent.LEFT, 85, BatteryStatus.CHARGING),
                Battery(BatteryComponent.RIGHT, 80, BatteryStatus.CHARGING),
                Battery(BatteryComponent.CASE, 100, BatteryStatus.NOT_CHARGING)
            ),
            earStatus = byteArrayOf(0x02, 0x02)
        )
    }, 7_000)
    return popup
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSettingsScreen(
    viewModel: AppSettingsViewModel = viewModel(),
    navigateToPurchase: () -> Unit,
    navigateToTroubleshooting: () -> Unit,
    navigateToOpenSourceLicenses: () -> Unit,
    navigateToReleaseNotesScreen: () -> Unit,
    navigateToLanguage: () -> Unit,
    navigateToFeatureStatus: () -> Unit = {}
) {
    val context = LocalContext.current
    val previewPopup = remember { mutableStateOf<PopupWindow?>(null) }
    val previewIsland = remember { mutableStateOf<IslandWindow?>(null) }
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { previewPopup.value?.dismissImmediately(); previewIsland.value?.forceClose() }
    }
    val registered = remember { mutableStateOf(false) }
    val detectionFailed = remember { mutableStateOf(false) }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner, context) {
        fun refresh() {
            registered.value = me.kavishdevar.librepods.bluetooth.CompanionRegistration.selectedAssociation(context) != null
            detectionFailed.value = context.getSharedPreferences("settings", Context.MODE_PRIVATE).getBoolean("companion_observation_failed", false)
        }
        refresh()
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val scrollState = rememberScrollState()
    val state by viewModel.uiState.collectAsState()

    val backdrop = rememberLayerBackdrop()

    val contactBottomSheet = remember { mutableStateOf(false) }
    val subjectState = remember { TextFieldState() }
    val descriptionState = remember { TextFieldState() }
    val subjectFocusRequester = remember { FocusRequester() }
    val descriptionFocusRequester = remember { FocusRequester() }

    val m3eEnabled = LocalDesignSystem.current == DesignSystem.Material
    val topPadding = screenTopPadding(material = 16.dp)
    val bottomPadding = screenBottomPadding(material = 0.dp)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .layerBackdrop(backdrop)
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(topPadding))

        val isDarkTheme = isSystemInDarkTheme()

        if (!state.isPremium && state.connectionSuccessful) {
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
            Spacer(modifier = Modifier.height(16.dp))
        }
        if (state.timeUntilFOSSPremiumExpiry > 0L) {
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
                    }
            ) {
                Text(
                    text = stringResource(
                        R.string.play_foss_premium_banner, maxOf(1, TimeUnit.MILLISECONDS.toDays(state.timeUntilFOSSPremiumExpiry).toInt())
                    ),
                    modifier = Modifier
                        .padding(16.dp),
                    style = TextStyle(
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontFamily = sfProFamily
                    )
                )
            }
        }

        GeneralSettingsSection(
            languageSummary = AppLanguage.summary(context),
            registered = registered.value,
            detectionFailed = detectionFailed.value,
            navigateToLanguage = navigateToLanguage,
            navigateToFeatureStatus = navigateToFeatureStatus,
            manageCompanion = { context.startActivity(Intent(context, CompanionRegistrationActivity::class.java)) }
        )

        Spacer(modifier = Modifier.height(16.dp))

        StyledList(title = stringResource(R.string.appearance)) {
            AppDesign.availableDesigns.forEach { design ->
                StyledListItem(
                    name = stringResource(design.labelRes),
                    selected = state.appDesign == design,
                    onClick = { viewModel.setAppDesign(design) }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (state.connectionSuccessful) {
            StyledToggle(
                title = stringResource(R.string.widget),
                label = stringResource(R.string.show_phone_battery_in_widget),
                description = stringResource(R.string.show_phone_battery_in_widget_description),
                checked = state.showPhoneBatteryInWidget,
                onCheckedChange = viewModel::setShowPhoneBatteryInWidget,
                enabled = state.isPremium
            )

            StyledList(title = stringResource(R.string.popup_animations)) {
                StyledToggle(
                    label = stringResource(R.string.show_bottom_sheet_popup),
                    description = stringResource(R.string.show_bottom_sheet_popup_description),
                    checked = state.showBottomSheetPopup,
                    onCheckedChange = viewModel::setShowBottomSheetPopup,
                )

                StyledListItem(
                    name = stringResource(R.string.preview_popup),
                    description = stringResource(R.string.preview_popup_description),
                    orientation = ListItemOrientation.Vertical,
                    onClick = { previewPopup.value?.dismissImmediately(); previewPopup.value = previewConnectionPopup(context) }
                )

                StyledToggle(
                    label = stringResource(R.string.show_island_popup),
                    description = stringResource(R.string.show_island_popup_description),
                    checked = state.showIslandPopup,
                    onCheckedChange = viewModel::setShowIslandPopup,
                )
                StyledListItem(name = stringResource(R.string.preview_island_popup),
                    description = stringResource(R.string.preview_island_popup_description),
                    orientation = ListItemOrientation.Vertical,
                    onClick = { previewIsland.value?.forceClose(); previewIsland.value = previewIslandPopup(context) })
            }

            run {
                // Size and position of the island; an island being previewed follows the sliders.
                Spacer(modifier = Modifier.height(16.dp))
                StyledSectionHeader(stringResource(R.string.island_size_position))
                StyledSlider(
                    label = stringResource(R.string.island_width),
                    description = "${state.islandWidth} dp",
                    value = state.islandWidth.toFloat(),
                    onValueChange = { viewModel.setIslandWidth(it.roundToInt()); previewIsland.value?.refreshPlacement() },
                    valueRange = IslandLayout.WIDTH_RANGE.first.toFloat()..IslandLayout.WIDTH_RANGE.last.toFloat(),
                    snapPoints = listOf(IslandLayout.DEFAULT_WIDTH.toFloat()),
                    independent = true
                )
                Spacer(modifier = Modifier.height(12.dp))
                StyledSlider(
                    label = stringResource(R.string.island_offset),
                    description = "${state.islandOffset} dp",
                    value = state.islandOffset.toFloat(),
                    onValueChange = { viewModel.setIslandOffset(it.roundToInt()); previewIsland.value?.refreshPlacement() },
                    valueRange = IslandLayout.OFFSET_RANGE.first.toFloat()..IslandLayout.OFFSET_RANGE.last.toFloat(),
                    snapPoints = listOf(IslandLayout.DEFAULT_OFFSET.toFloat()),
                    independent = true
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            StyledList(title = stringResource(R.string.quick_settings_tile)) {
                StyledToggle(
                    label = stringResource(R.string.open_dialog_for_controlling),
                    description = stringResource(R.string.open_dialog_for_controlling_description),
                    checked = state.quickSettingsDialog,
                    onCheckedChange = viewModel::setQuickSettingsDialog,
                )

                StyledListItem(
                    name = stringResource(R.string.preview_popup),
                    orientation = ListItemOrientation.Vertical,
                    onClick = {
                        context.startActivity(
                            Intent(context, QuickSettingsDialogActivity::class.java).putExtra(QuickSettingsDialogActivity.EXTRA_PREVIEW, true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            StyledList (title = stringResource(R.string.conversational_awareness)) {
                StyledToggle(
                    label = stringResource(R.string.conversational_awareness_pause_music),
                    description = stringResource(R.string.conversational_awareness_pause_music_description),
                    checked = state.conversationalAwarenessPauseMusicEnabled,
                    onCheckedChange = viewModel::setConversationalAwarenessPauseMusicEnabled,
                    enabled = state.isPremium
                )

                StyledToggle(
                    label = stringResource(R.string.relative_conversational_awareness_volume),
                    description = stringResource(R.string.relative_conversational_awareness_volume_description),
                    checked = state.relativeConversationalAwarenessVolumeEnabled,
                    onCheckedChange = viewModel::setRelativeConversationalAwarenessVolumeEnabled,
                    enabled = state.isPremium,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            val conversationalAwarenessVolume = state.conversationalAwarenessVolume
            LaunchedEffect(conversationalAwarenessVolume) {
                viewModel.setConversationalAwarenessVolume(conversationalAwarenessVolume)
            }

            StyledSlider(
                label = stringResource(R.string.conversational_awareness_volume),
                value = conversationalAwarenessVolume,
                valueRange = 10f..85f,
                snapPoints = listOf(ConversationalAwarenessDefaults.VOLUME_PERCENT.toFloat()),
                snapThreshold = ConversationalAwarenessDefaults.SNAP_THRESHOLD,
                startLabel = "10%",
                endLabel = "85%",
                onValueChange = { newValue ->
                    viewModel.setConversationalAwarenessVolume(
                        newValue
                    )
                },
                independent = true,
                enabled = state.isPremium
            )

//            if (!BuildConfig.PLAY_BUILD) {
//                Spacer(modifier = Modifier.height(16.dp))
//
//                StyledListItem(
//                    to = "",
//                    titleRes = stringResource(R.string.camera_control),
//                    name = stringResource(R.string.set_custom_camera_package),
//                    navController = navController,
//                    onClick = {
//                        if (state.isPremium) viewModel.setShowCameraDialog(true)
//                    },
//                    independent = true,
//                    descriptionRes = stringResource(R.string.camera_control_app_description)
//                )
//            }

            Spacer(modifier = Modifier.height(16.dp))
            if (context.checkSelfPermission("android.permission.BLUETOOTH_PRIVILEGED") == PackageManager.PERMISSION_GRANTED) {
                StyledToggle(
                    title = stringResource(R.string.ear_detection),
                    label = stringResource(R.string.disconnect_when_not_wearing),
                    description = stringResource(R.string.disconnect_when_not_wearing_description),
                    checked = state.disconnectWhenNotWearing,
                    onCheckedChange = viewModel::setDisconnectWhenNotWearing,
                    enabled = state.isPremium
                )
            }

            val takeoverSupported = state.isPremium && state.vendorIdHook &&
                XposedState.isAvailable && XposedState.bluetoothScopeEnabled
            Text(stringResource(if (takeoverSupported) R.string.takeover_explanation else R.string.takeover_unavailable),
                style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(16.dp))
            StyledList(title = stringResource(R.string.takeover_airpods_state)) {
                StyledToggle(
                    label = stringResource(R.string.takeover_disconnected),
                    description = stringResource(R.string.takeover_disconnected_desc),
                    checked = state.takeoverWhenDisconnected,
                    onCheckedChange = viewModel::setTakeoverWhenDisconnected,
                    enabled = takeoverSupported
                )
                StyledToggle(
                    label = stringResource(R.string.takeover_idle),
                    description = stringResource(R.string.takeover_idle_desc),
                    checked = state.takeoverWhenIdle,
                    onCheckedChange = viewModel::setTakeoverWhenIdle,
                    enabled = takeoverSupported
                )
                StyledToggle(
                    label = stringResource(R.string.takeover_music),
                    description = stringResource(R.string.takeover_music_desc),
                    checked = state.takeoverWhenMusic,
                    onCheckedChange = viewModel::setTakeoverWhenMusic,
                    enabled = takeoverSupported
                )

                StyledToggle(
                    label = stringResource(R.string.takeover_call),
                    description = stringResource(R.string.takeover_call_desc),
                    checked = state.takeoverWhenCall,
                    onCheckedChange = viewModel::setTakeoverWhenCall,
                    enabled = takeoverSupported
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            StyledList(title = stringResource(R.string.takeover_phone_state)) {
                StyledToggle(
                    label = stringResource(R.string.takeover_ringing_call),
                    description = stringResource(R.string.takeover_ringing_call_desc),
                    checked = state.takeoverWhenRingingCall,
                    onCheckedChange = viewModel::setTakeoverWhenRingingCall,
                    enabled = takeoverSupported
                )
                StyledToggle(
                    label = stringResource(R.string.takeover_media_start),
                    description = stringResource(R.string.takeover_media_start_desc),
                    checked = state.takeoverWhenMediaStart,
                    onCheckedChange = viewModel::setTakeoverWhenMediaStart,
                    enabled = takeoverSupported
                )
            }

            StyledToggle(
                title = stringResource(R.string.advanced_options), // shouldn't be here, but okay
                label = stringResource(R.string.use_alternate_head_tracking_packets),
                description = stringResource(R.string.use_alternate_head_tracking_packets_description),
                checked = state.useAlternateHeadTrackingPackets,
                onCheckedChange = viewModel::setUseAlternateHeadTrackingPackets,
                enabled = state.isPremium
            )
            Spacer(modifier = Modifier.height(16.dp))
        } else {
            Box(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(horizontal = 16.dp)
                    .padding(top = 16.dp, bottom = 2.dp)
            ) {
                Text(
                    text = stringResource(R.string.customizations_unavailable),
                    style = MaterialTypography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                )
            }
        }

        if (XposedState.isAvailable && XposedState.bluetoothScopeEnabled) {
            val restartBluetoothText = stringResource(R.string.found_offset_restart_bluetooth)
            StyledToggle(
                label = stringResource(R.string.act_as_an_apple_device) + " (${
                    stringResource(
                        R.string.requires_xposed
                    )
                })",
                description = stringResource(R.string.act_as_an_apple_device_description),
                checked = state.vendorIdHook,
                onCheckedChange = { enabled ->
                    Toast.makeText(context, restartBluetoothText, Toast.LENGTH_SHORT).show()
                    viewModel.setVendorIdHook(enabled)
                }
            )
        }

        if (!BuildConfig.PLAY_BUILD) {
            Spacer(modifier = Modifier.height(16.dp))
            StyledList {
                StyledListItem(
                    name = stringResource(R.string.troubleshooting),
                    onClick = navigateToTroubleshooting,
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        StyledList(title = stringResource(R.string.contact)) {
            StyledListItem(
                name = stringResource(R.string.email),
                onClick = { contactBottomSheet.value = true },
            )

            StyledListItem(
                name = stringResource(R.string.discord),
                onClick = {
                    val intent =
                        Intent(Intent.ACTION_VIEW, "https://discord.gg/Ts4wupXcmc".toUri())
                    context.startActivity(intent)
                },
            )

            StyledListItem(
                name = stringResource(R.string.github_issues),
                onClick = {
                    val appVersion =
                        Uri.encode("v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                    val device = Uri.encode("${Build.MANUFACTURER} ${Build.MODEL}")
                    val androidVersion = Uri.encode("${Build.ID} (${Build.DISPLAY})")
                    val appSource = Uri.encode(
                        when {
                            BuildConfig.PLAY_BUILD -> "Play"
                            else -> "GitHub"
                        }
                    )
                    val url = "https://github.com/kavishdevar/librepods/issues/new" +
                        "?template=01-bug-report-android.yml" +
                        "&app-source=$appSource" +
                        "&app-version=$appVersion" +
                        "&device=$device" +
                        "&android-version=$androidVersion"

                    val intent = Intent(Intent.ACTION_VIEW, url.toUri())
                    context.startActivity(intent)
                },
            )
        }

        Spacer(modifier = Modifier.height(20.dp))
        DeviceInfoCard()
        Spacer(modifier = Modifier.height(16.dp))
        AppInfoCard(navigateToReleaseNotesScreen)

        Spacer(modifier = Modifier.height(16.dp))

        StyledListItem(
            name = stringResource(R.string.open_source_licenses),
            onClick = navigateToOpenSourceLicenses,
        )

        Spacer(modifier = Modifier.height(bottomPadding))

        if (state.showCameraDialog) {
            AlertDialog(onDismissRequest = { viewModel.setShowCameraDialog(false) }, title = {
                Text(
                    stringResource(R.string.set_custom_camera_package),
                    fontFamily = sfProFamily,
                    fontWeight = FontWeight.Medium
                )
            }, text = {
                Column {
                    Text(
                        stringResource(R.string.enter_custom_camera_package),
                        fontFamily = sfProFamily,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    OutlinedTextField(
                        value = state.cameraPackageValue,
                        onValueChange = {
                            viewModel.setCameraPackageValue(it)
                            viewModel.setCameraPackageError(null)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        isError = state.cameraPackageError != null,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Ascii,
                            capitalization = KeyboardCapitalization.None
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = if (isDarkTheme) Color(0xFF007AFF) else Color(
                                0xFF3C6DF5
                            ),
                            unfocusedBorderColor = if (isDarkTheme) Color.Gray else Color.LightGray
                        ),
                        supportingText = {
                            if (state.cameraPackageError != null) {
                                Text(
                                    state.cameraPackageError ?: "",
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        },
                        label = { Text(stringResource(R.string.custom_camera_package)) })
                }
            }, confirmButton = {
                val successText = stringResource(R.string.custom_camera_package_set_success)
                TextButton(
                    onClick = {
                        viewModel.saveCameraPackage()
                        Toast.makeText(context, successText, Toast.LENGTH_SHORT).show()
                    }) {
                    Text(
                        stringResource(R.string.save),
                        fontFamily = sfProFamily,
                        fontWeight = FontWeight.Medium
                    )
                }
            }, dismissButton = {
                TextButton(
                    onClick = { viewModel.setShowCameraDialog(false) }) {
                    Text(
                        stringResource(R.string.cancel),
                        fontFamily = sfProFamily,
                        fontWeight = FontWeight.Medium
                    )
                }
            })
        }
    }

    val sendContactEmail: () -> Unit = {
        contactBottomSheet.value = false
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = "mailto:".toUri()
            putExtra(Intent.EXTRA_EMAIL, arrayOf("contact@kavish.xyz"))
            putExtra(Intent.EXTRA_SUBJECT, "LibrePods: ${subjectState.text}")
            putExtra(
                Intent.EXTRA_TEXT,
                "${descriptionState.text}" +
                    "\n\n----------" +
                    "\nPhone details:" +
                    "\nMANUFACTURER: ${Build.MANUFACTURER}" +
                    "\nMODEL: ${Build.MODEL} (${Build.PRODUCT})" +
                    "\nDISPLAY_VERSION: ${Build.DISPLAY}" +
                    "\nID: ${Build.ID} (SDK ${Build.VERSION.SDK_INT_FULL})" +
                    "\nXposed enabled/active: ${XposedState.isAvailable}/${XposedState.bluetoothScopeEnabled}" +
                    "\n\nApp details:" +
                    "\nVERSION: ${BuildConfig.VERSION_NAME}" +
                    "\nVERSION_CODE: ${BuildConfig.VERSION_CODE}" +
                    "\nFLAVOR: ${BuildConfig.FLAVOR}" +
                    "\nBUILD_TYPE: ${BuildConfig.BUILD_TYPE}"
            )
        }
        context.startActivity(intent)
        subjectState.clearText()
        descriptionState.clearText()
    }

    if (LocalDesignSystem.current != DesignSystem.Apple) {
        if (contactBottomSheet.value) {
            ContactSheet(
                subjectState = subjectState,
                descriptionState = descriptionState,
                subjectFocusRequester = subjectFocusRequester,
                descriptionFocusRequester = descriptionFocusRequester,
                onDismiss = { contactBottomSheet.value = false },
                onSend = sendContactEmail
            )
        }
        return
    }

    StyledBottomSheet(
        visible = contactBottomSheet.value,
        onDismiss = { contactBottomSheet.value = false },
        backdrop = backdrop
    ) { innerBackdrop, progress ->
        val animatedPadding = lerp(16.dp, 2.dp, progress)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = animatedPadding)
                .padding(bottom = 16.dp),
        ) {
           Row(
               modifier = Modifier
                   .fillMaxWidth()
                   .padding(bottom = 16.dp),
               horizontalArrangement = Arrangement.SpaceBetween,
               verticalAlignment = Alignment.CenterVertically
           ) {
               StyledIconButton(
                   icon = "\uDBC0\uDD84",
                   contentDescription = stringResource(R.string.cancel),
                   backdrop = innerBackdrop,
                   onClick = { contactBottomSheet.value = false }
               )
               Text (
                   text = stringResource(R.string.describe_your_issue),
                   style = TextStyle(
                       fontSize = 18.sp,
                       fontFamily = sfProFamily,
                       fontWeight = FontWeight.Bold,
                       textAlign = TextAlign.Center,
                       color = if (isSystemInDarkTheme()) Color.White else Color.Black
                   )
               )
               StyledIconButton(
                   icon = "\uDBC0\uDE1F",
                   contentDescription = stringResource(R.string.email),
                   backdrop = innerBackdrop,
                   surfaceColor = if (isSystemInDarkTheme()) Color(0xFF0091FF) else Color(0xFF0088FF),
                   iconTint = if (subjectState.text.isNotEmpty() && descriptionState.text.isNotEmpty()) Color.White else Color.Gray,
                   enabled = subjectState.text.isNotEmpty() && descriptionState.text.isNotEmpty(),
                   onClick = sendContactEmail
               )
           }

           Spacer(modifier = Modifier.height(8.dp))

           StyledInputField(
               inputState = subjectState,
               focusRequester = subjectFocusRequester,
               placeholder = stringResource(R.string.subject),
               forceApple = true
           )

           Spacer(modifier = Modifier.height(12.dp))

           StyledInputField(
               inputState = descriptionState,
               focusRequester = descriptionFocusRequester,
               placeholder = stringResource(R.string.describe_your_issue),
               singleLine = false,
               forceApple = true
           )
        }
    }
}

/** The e-mail form as a standard bottom sheet, for the One UI and Material looks. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ContactSheet(
    subjectState: TextFieldState,
    descriptionState: TextFieldState,
    subjectFocusRequester: FocusRequester,
    descriptionFocusRequester: FocusRequester,
    onDismiss: () -> Unit,
    onSend: () -> Unit,
) {
    val oneUI = LocalOneUI.current
    val canSend = subjectState.text.isNotEmpty() && descriptionState.text.isNotEmpty()
    val form: @Composable () -> Unit = {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (oneUI) Modifier.statusBarsPadding().navigationBarsPadding() else Modifier.imePadding())
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cancel))
                }
                Text(
                    text = stringResource(R.string.describe_your_issue),
                    style = if (oneUI) OneUIText.toolbarTitle.copy(fontSize = 19.sp) else MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp, lineHeight = 28.sp),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (oneUI) {
                    IconButton(onClick = onSend, enabled = canSend) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.email))
                    }
                } else {
                    FilledIconButton(onClick = onSend, enabled = canSend) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.email))
                    }
                }
            }
            StyledInputField(
                inputState = subjectState,
                focusRequester = subjectFocusRequester,
                placeholder = if (oneUI) stringResource(R.string.subject) else "",
                label = stringResource(R.string.subject)
            )
            Spacer(modifier = Modifier.height(12.dp))
            StyledInputField(
                inputState = descriptionState,
                focusRequester = descriptionFocusRequester,
                placeholder = if (oneUI) stringResource(R.string.describe_your_issue) else "",
                singleLine = false,
                label = stringResource(R.string.describe_your_issue)
            )
        }
    }
    if (oneUI) {
        OneUIBottomSheet(onDismiss = onDismiss, content = form)
    } else {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = rememberBottomSheetState(SheetValue.Hidden, setOf(SheetValue.Hidden, SheetValue.Expanded)),
        ) { form() }
    }
}

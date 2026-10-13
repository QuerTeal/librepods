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

// import me.kavishdevar.librepods.utils.RadareOffsetFinder
import me.kavishdevar.librepods.presentation.components.ValueSlider
import androidx.compose.runtime.SideEffect
import androidx.compose.material3.rememberSliderState
import android.annotation.SuppressLint
import android.util.Log
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.kavishdevar.librepods.R
import me.kavishdevar.librepods.presentation.theme.AppleShape
import me.kavishdevar.librepods.presentation.components.screenBottomPadding
import me.kavishdevar.librepods.presentation.components.screenTopPadding
import androidx.compose.material3.ListItemDefaults
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.width
import kotlin.math.roundToInt
import me.kavishdevar.librepods.presentation.components.OneUIContentInset
import me.kavishdevar.librepods.presentation.components.OneUISlider
import me.kavishdevar.librepods.presentation.components.StyledSectionHeader
import me.kavishdevar.librepods.presentation.theme.LocalOneUI
import me.kavishdevar.librepods.presentation.theme.OneUIText
import me.kavishdevar.librepods.data.TransparencySettings
import me.kavishdevar.librepods.data.parseTransparencySettingsResponse
import me.kavishdevar.librepods.data.sendTransparencySettings
import me.kavishdevar.librepods.presentation.components.StyledSlider
import me.kavishdevar.librepods.presentation.components.StyledToggle
import me.kavishdevar.librepods.presentation.theme.DesignSystem
import me.kavishdevar.librepods.presentation.theme.LocalDesignSystem
import me.kavishdevar.librepods.presentation.viewmodel.AirPodsViewModel
import kotlin.io.encoding.ExperimentalEncodingApi
import me.kavishdevar.librepods.presentation.theme.sfProFamily

private const val TAG = "TransparencySettings"

@SuppressLint("DefaultLocale")
@OptIn(ExperimentalMaterial3Api::class, ExperimentalEncodingApi::class)
@Composable
fun TransparencySettingsScreen(viewModel: AirPodsViewModel) {
    val isDarkTheme = isSystemInDarkTheme()
    val textColor = if (isDarkTheme) Color.White else Color.Black
    val verticalScrollState = rememberScrollState()

    val trackColor = if (isDarkTheme) Color(0xFFB3B3B3) else Color(0xFF929491)
    val activeTrackColor = if (isDarkTheme) Color(0xFF007AFF) else Color(0xFF3C6DF5)
    val thumbColor = if (isDarkTheme) Color(0xFFFFFFFF) else Color(0xFFFFFFFF)

    val state by viewModel.uiState.collectAsState()

    val m3eEnabled = LocalDesignSystem.current == DesignSystem.Material
    val topPadding = screenTopPadding(material = 0.dp)
    val bottomPadding = screenBottomPadding(material = 0.dp)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .verticalScroll(verticalScrollState)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(modifier = Modifier.height(topPadding))
        val backgroundColor = if (isDarkTheme) Color(0xFF1C1C1E) else Color(0xFFFFFFFF)

        val enabled = rememberSaveable { mutableStateOf(false) }
        val amplificationSliderValue = rememberSaveable { mutableFloatStateOf(0.5f) }
        val balanceSliderValue = rememberSaveable { mutableFloatStateOf(0.5f) }
        val toneSliderValue = rememberSaveable { mutableFloatStateOf(0.5f) }
        val ambientNoiseReductionSliderValue = rememberSaveable { mutableFloatStateOf(0.0f) }
        val conversationBoostEnabled = rememberSaveable { mutableStateOf(false) }
        val eq = rememberSaveable(
            saver = Saver(
                save = { it.value.toList() },
                restore = { mutableStateOf(it.toFloatArray()) }
            )
        ) { mutableStateOf(FloatArray(8)) }
        val phoneMediaEQ = rememberSaveable(
            saver = Saver(
                save = { it.value.toList() },
                restore = { mutableStateOf(it.toFloatArray()) }
            )
        ) { mutableStateOf(FloatArray(8) { 0.5f }) }

        val initialized = rememberSaveable { mutableStateOf(false) }
    val userEdited = remember { mutableStateOf(false) }

        val transparencySettings = remember {
            mutableStateOf(
                TransparencySettings(
                    enabled = enabled.value,
                    leftEQ = eq.value,
                    rightEQ = eq.value,
                    leftAmplification = amplificationSliderValue.floatValue + (0.5f - balanceSliderValue.floatValue) * amplificationSliderValue.floatValue * 2,
                    rightAmplification = amplificationSliderValue.floatValue + (balanceSliderValue.floatValue - 0.5f) * amplificationSliderValue.floatValue * 2,
                    leftTone = toneSliderValue.floatValue,
                    rightTone = toneSliderValue.floatValue,
                    leftConversationBoost = conversationBoostEnabled.value,
                    rightConversationBoost = conversationBoostEnabled.value,
                    leftAmbientNoiseReduction = ambientNoiseReductionSliderValue.floatValue,
                    rightAmbientNoiseReduction = ambientNoiseReductionSliderValue.floatValue,
                    netAmplification = amplificationSliderValue.floatValue,
                    balance = balanceSliderValue.floatValue
                )
            )
        }

        LaunchedEffect(
            enabled.value,
            amplificationSliderValue.floatValue,
            balanceSliderValue.floatValue,
            toneSliderValue.floatValue,
            conversationBoostEnabled.value,
            ambientNoiseReductionSliderValue.floatValue,
            eq.value
        ) {
            if (!initialized.value || !userEdited.value) return@LaunchedEffect
            userEdited.value = false
            transparencySettings.value = TransparencySettings(
                enabled = enabled.value,
                leftEQ = eq.value,
                rightEQ = eq.value,
                leftAmplification = amplificationSliderValue.floatValue + if (balanceSliderValue.floatValue < 0) -balanceSliderValue.floatValue else 0f,
                rightAmplification = amplificationSliderValue.floatValue + if (balanceSliderValue.floatValue > 0) balanceSliderValue.floatValue else 0f,
                leftTone = toneSliderValue.floatValue,
                rightTone = toneSliderValue.floatValue,
                leftConversationBoost = conversationBoostEnabled.value,
                rightConversationBoost = conversationBoostEnabled.value,
                leftAmbientNoiseReduction = ambientNoiseReductionSliderValue.floatValue,
                rightAmbientNoiseReduction = ambientNoiseReductionSliderValue.floatValue,
                netAmplification = amplificationSliderValue.floatValue,
                balance = balanceSliderValue.floatValue
            )
            Log.d("TransparencySettings", "Updated settings: ${transparencySettings.value}")
            sendTransparencySettings(viewModel::setATTCharacteristicValue, transparencySettings.value)
        }

        LaunchedEffect(state.transparencyData) {
            val parsedSettings = parseTransparencySettingsResponse(data = state.transparencyData) ?: return@LaunchedEffect
            Log.d(TAG, "Initial transparency settings: $parsedSettings")
            enabled.value = parsedSettings.enabled
            amplificationSliderValue.floatValue = parsedSettings.netAmplification
            balanceSliderValue.floatValue = parsedSettings.balance
            toneSliderValue.floatValue = parsedSettings.leftTone
            ambientNoiseReductionSliderValue.floatValue =
                parsedSettings.leftAmbientNoiseReduction
            conversationBoostEnabled.value = parsedSettings.leftConversationBoost
            if (!eq.value.contentEquals(parsedSettings.leftEQ)) {
                eq.value = parsedSettings.leftEQ.copyOf()
            }
            initialized.value = true
        }

        if (state.vendorIdHook) {
            StyledToggle(
                label = stringResource(R.string.transparency_mode),
                checked = enabled.value,
                description = stringResource(R.string.customize_transparency_mode_description),
                onCheckedChange = { userEdited.value = true; enabled.value = it }
            )
            Spacer(modifier = Modifier.height(4.dp))
            StyledSlider(
                label = stringResource(R.string.amplification),
                valueRange = -1f..1f,
                value = amplificationSliderValue.floatValue,
                onValueChange = { userEdited.value = true;
                    amplificationSliderValue.floatValue = it
                },
                startIcon = "􀊥",
                endIcon = "􀊩",
                independent = true
            )

            StyledSlider(
                label = stringResource(R.string.balance),
                valueRange = -1f..1f,
                value = balanceSliderValue.floatValue,
                onValueChange = { userEdited.value = true;
                    balanceSliderValue.floatValue = it
                },
                snapPoints = listOf(-1f, 0f, 1f),
                startLabel = stringResource(R.string.left),
                endLabel = stringResource(R.string.right),
                independent = true,
            )

            StyledSlider(
                label = stringResource(R.string.tone),
                valueRange = -1f..1f,
                value = toneSliderValue.floatValue,
                onValueChange = { userEdited.value = true;
                    toneSliderValue.floatValue = it
                },
                startLabel = stringResource(R.string.darker),
                endLabel = stringResource(R.string.brighter),
                independent = true,
            )

            StyledSlider(
                label = stringResource(R.string.ambient_noise_reduction),
                valueRange = 0f..1f,
                value = ambientNoiseReductionSliderValue.floatValue,
                onValueChange = { userEdited.value = true;
                    ambientNoiseReductionSliderValue.floatValue = it
                },
                startLabel = stringResource(R.string.less),
                endLabel = stringResource(R.string.more),
                independent = true,
            )

            StyledToggle(
                label = stringResource(R.string.conversation_boost),
                checked = conversationBoostEnabled.value,
                description = stringResource(R.string.conversation_boost_description),
                onCheckedChange = { userEdited.value = true; conversationBoostEnabled.value = it }
            )

            if (LocalDesignSystem.current == DesignSystem.Apple) {
                Text(
                    text = stringResource(R.string.equalizer),
                    style = TextStyle(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = textColor.copy(alpha = 0.6f),
                        fontFamily = sfProFamily
                    ),
                    modifier = Modifier.padding(16.dp, bottom = 4.dp)
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(backgroundColor, AppleShape(28.dp))
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    for (i in 0 until 8) {
                        val eqValue = remember(eq.value[i]) { mutableFloatStateOf(eq.value[i]) }
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(38.dp)
                        ) {
                            Text(
                                text = String.format("%.2f", eqValue.floatValue),
                                fontSize = 12.sp,
                                color = textColor,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )

                            val sliderState = rememberSliderState(eqValue.floatValue, 0, 0f..100f)
                            SideEffect { if (sliderState.value != eqValue.floatValue) sliderState.value = eqValue.floatValue }
                            Slider(
                                state = sliderState,
                                onValueChange = { newVal -> userEdited.value = true;
                                    sliderState.value = newVal
                                    eqValue.floatValue = newVal
                                    val newEQ = eq.value.copyOf()
                                    newEQ[i] = eqValue.floatValue
                                    eq.value = newEQ
                                },
                                modifier = Modifier
                                    .fillMaxWidth(0.9f)
                                    .height(36.dp),
                                colors = SliderDefaults.colors(
                                    thumbColor = thumbColor,
                                    activeTrackColor = activeTrackColor,
                                    inactiveTrackColor = trackColor
                                ),
                                thumb = {
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .shadow(4.dp, CircleShape)
                                            .background(thumbColor, CircleShape)
                                    )
                                },
                                track = {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(12.dp),
                                        contentAlignment = Alignment.CenterStart
                                    )
                                    {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(4.dp)
                                                .background(trackColor, RoundedCornerShape(4.dp))
                                        )
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth(eqValue.floatValue / 100f)
                                                .height(4.dp)
                                                .background(
                                                    activeTrackColor,
                                                    RoundedCornerShape(4.dp)
                                                )
                                        )
                                    }
                                }
                            )

                            Text(
                                text = stringResource(R.string.band_label, i + 1),
                                fontSize = 12.sp,
                                color = textColor,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            } else {
                Column {
                    StyledSectionHeader(stringResource(R.string.equalizer))
                    EqualizerBandsCard(
                        values = eq.value,
                        onValueChange = { band, value -> userEdited.value = true;
                            val newEQ = eq.value.copyOf()
                            newEQ[band] = value
                            eq.value = newEQ
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        Spacer(modifier = Modifier.height(bottomPadding))
    }
}

/** The eight transparency EQ bands as One UI seek bars or Material sliders in one card. */
@Composable
private fun EqualizerBandsCard(values: FloatArray, onValueChange: (band: Int, value: Float) -> Unit) {
    val oneUI = LocalOneUI.current
    val container = if (oneUI) MaterialTheme.colorScheme.surface else ListItemDefaults.segmentedColors().containerColor
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(if (oneUI) 26.dp else 24.dp))
            .background(container)
            .padding(horizontal = if (oneUI) OneUIContentInset else 16.dp, vertical = 10.dp)
    ) {
        values.forEachIndexed { band, value ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val labelStyle = if (oneUI) OneUIText.rowSummary else MaterialTheme.typography.labelMedium
                Text(
                    text = stringResource(R.string.band_label, band + 1),
                    style = labelStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(56.dp)
                )
                if (oneUI) {
                    OneUISlider(
                        value = value,
                        onValueChange = { onValueChange(band, it) },
                        valueRange = 0f..100f,
                        modifier = Modifier.weight(1f),
                        label = stringResource(R.string.band_label, band + 1)
                    )
                } else {
                    ValueSlider(
                        value = value,
                        onValueChange = { onValueChange(band, it) },
                        valueRange = 0f..100f,
                        modifier = Modifier.weight(1f)
                    )
                }
                Text(
                    text = value.roundToInt().toString(),
                    style = labelStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(36.dp)
                )
            }
        }
    }
}

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

package me.kavishdevar.librepods.presentation.components

import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.border
import me.kavishdevar.librepods.presentation.theme.AppleText
import me.kavishdevar.librepods.presentation.theme.AppleMetrics
import me.kavishdevar.librepods.presentation.theme.AppleColors
import android.annotation.SuppressLint
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import me.kavishdevar.librepods.R
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.core.spring
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import me.kavishdevar.librepods.presentation.theme.AppleShape
import me.kavishdevar.librepods.data.NoiseControlMode
import me.kavishdevar.librepods.presentation.theme.DesignSystem
import me.kavishdevar.librepods.presentation.theme.LibrePodsTheme
import me.kavishdevar.librepods.presentation.theme.LocalDesignSystem
import me.kavishdevar.librepods.presentation.theme.sectionHeader
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.math.roundToInt
import me.kavishdevar.librepods.presentation.theme.sfProFamily
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import me.kavishdevar.librepods.presentation.theme.LocalOneUI
import androidx.compose.foundation.layout.Spacer

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@SuppressLint("UnspecifiedRegisterReceiverFlag", "UnusedBoxWithConstraintsScope")
@Composable
fun NoiseControlSettings(
    showOffListeningMode: Boolean,
    noiseControlModeValue: Int,
    onNoiseControlModeChanged: (Int) -> Unit,
    /** AUTO_ANC_STRENGTH (0..100); with [onAdaptiveStrengthChange] the Apple control slides it. */
    adaptiveStrength: Int? = null,
    onAdaptiveStrengthChange: ((Int) -> Unit)? = null,
    /** A message when a mode needs both buds in the ears and they aren't; null when it may be picked. */
    wearGuidance: (Int) -> String? = { null },
) {
    val haptics = LocalHapticFeedback.current
    val onModeChangedWithTick: (Int) -> Unit = { mode ->
        if (mode != noiseControlModeValue) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
        onNoiseControlModeChanged(mode)
    }
    if (LocalOneUI.current) {
        OneUINoiseControl(showOffListeningMode, noiseControlModeValue, onModeChangedWithTick)
        return
    }
    when (LocalDesignSystem.current) {
        DesignSystem.Material -> {
            val options = buildList {
                if (showOffListeningMode) {
                    add(
                        Triple(
                            NoiseControlMode.OFF,
                            R.string.off,
                            R.drawable.noise_cancellation
                        )
                    )
                }

                add(
                    Triple(
                        NoiseControlMode.TRANSPARENCY,
                        R.string.transparency,
                        R.drawable.transparency
                    )
                )
                add(
                    Triple(
                        NoiseControlMode.ADAPTIVE,
                        R.string.adaptive,
                        R.drawable.adaptive
                    )
                )
                add(
                    Triple(
                        NoiseControlMode.NOISE_CANCELLATION,
                        R.string.noise_cancellation,
                        R.drawable.noise_cancellation
                    )
                )
            }

            val selectedMode = NoiseControlMode.entries[(noiseControlModeValue - 1).coerceIn(0, NoiseControlMode.entries.lastIndex)]

            Column {
                Box(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .padding(top = 4.dp, bottom = 12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.noise_control),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelSmallEmphasized
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
                ) {
                    options.forEachIndexed { index, (mode, labelRes, iconRes) ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            ToggleButton(
                                checked = selectedMode == mode,
                                onCheckedChange = {
                                    if (it) {
                                        onNoiseControlModeChanged(mode.ordinal + 1)
                                    }
                                },
                                shapes = when (index) {
                                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                    options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                                },
                                colors = ToggleButtonDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    bitmap = ImageBitmap.imageResource(iconRes),
                                    contentDescription = null,
                                    modifier = Modifier.size(42.dp)
                                )
                            }

                            Text(
                                text = stringResource(labelRes),
                                style = MaterialTheme.typography.labelSmall,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }

        DesignSystem.Apple -> {
            AppleListeningModeControl(
                showOff = showOffListeningMode,
                mode = NoiseControlMode.entries[(noiseControlModeValue - 1).coerceIn(0, NoiseControlMode.entries.lastIndex)],
                onModeChange = { onNoiseControlModeChanged(it.ordinal + 1) },
                adaptiveStrength = adaptiveStrength,
                onAdaptiveStrengthChange = onAdaptiveStrengthChange,
                wearGuidance = { wearGuidance(it.ordinal + 1) },
            )
        }
    }
}


/** Galaxy Wearable-style picker: round buttons in a card, the active mode filled with the accent color. */
@Composable
private fun OneUINoiseControl(
    showOffListeningMode: Boolean,
    noiseControlModeValue: Int,
    onNoiseControlModeChanged: (Int) -> Unit
) {
    val options = buildList {
        if (showOffListeningMode) add(Triple(NoiseControlMode.OFF, R.string.off, R.drawable.noise_cancellation))
        add(Triple(NoiseControlMode.TRANSPARENCY, R.string.transparency, R.drawable.transparency))
        add(Triple(NoiseControlMode.ADAPTIVE, R.string.adaptive, R.drawable.adaptive))
        add(Triple(NoiseControlMode.NOISE_CANCELLATION, R.string.noise_cancellation, R.drawable.noise_cancellation))
    }
    val selectedMode = NoiseControlMode.entries[(noiseControlModeValue - 1).coerceIn(0, NoiseControlMode.entries.lastIndex)]

    Column {
        OneUICategoryHeader(stringResource(R.string.noise_control))
        OneUICard {
            OneUIChoiceGrid(
                count = options.size,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 18.dp),
            ) { index, choiceModifier ->
                    val (mode, labelRes, iconRes) = options[index]
                    val selected = selectedMode == mode
                    val container by animateColorAsState(
                        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                        label = "modeContainer"
                    )
                    val content by animateColorAsState(
                        if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                        label = "modeContent"
                    )
                    val interaction = remember { MutableInteractionSource() }
                    val pressed by interaction.collectIsPressedAsState()
                    val scale by animateFloatAsState(
                        when {
                            pressed -> 0.86f
                            selected -> 1f
                            else -> 0.92f
                        },
                        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium),
                        label = "modeScale"
                    )
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = choiceModifier
                            .clip(RoundedCornerShape(16.dp))
                            .selectable(
                                selected = selected,
                                interactionSource = interaction,
                                indication = LocalIndication.current,
                                role = Role.RadioButton,
                                onClick = { if (!selected) onNoiseControlModeChanged(mode.ordinal + 1) }
                            )
                            .padding(vertical = 4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(58.dp)
                                .scale(scale)
                                .clip(CircleShape)
                                .background(container),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                bitmap = ImageBitmap.imageResource(iconRes),
                                contentDescription = null,
                                tint = content,
                                modifier = Modifier.size(30.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(labelRes),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
            }
        }
    }
}

@Preview
@Composable
fun NoiseControlSettingsPreview() {
    LibrePodsTheme(
        m3eEnabled = true
    ) {
        Box(
            modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainer)
        ) {
            NoiseControlSettings(
                showOffListeningMode = false,
                noiseControlModeValue = 2,
                onNoiseControlModeChanged = { }
            )
        }
    }
}

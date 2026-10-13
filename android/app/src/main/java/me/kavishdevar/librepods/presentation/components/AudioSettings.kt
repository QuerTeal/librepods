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

import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import me.kavishdevar.librepods.presentation.theme.LocalDesignSystem
import me.kavishdevar.librepods.presentation.theme.DesignSystem
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import me.kavishdevar.librepods.R
import kotlin.io.encoding.ExperimentalEncodingApi

@Composable
fun AudioSettings(
    adaptiveVolumeCapability: Boolean,
    conversationalAwarenessCapability: Boolean,
    loudSoundReductionCapability: Boolean,
    adaptiveAudioCapability: Boolean,
    customEqCapability: Boolean,

    adaptiveVolumeChecked: Boolean,
    onAdaptiveVolumeCheckedChange: (Boolean) -> Unit,

    conversationalAwarenessChecked: Boolean,
    onConversationalAwarenessCheckedChange: (Boolean) -> Unit,

    loudSoundReductionChecked: Boolean,
    onLoudSoundReductionCheckedChange: (Boolean) -> Unit,

    navigateToAdaptiveStrength: () -> Unit,
    navigateToEqualizer: () -> Unit,

    vendorIdHook: Boolean,
    isPremium: Boolean
) {
    if (LocalDesignSystem.current == DesignSystem.Apple) {
        // iOS 27 Audio & Routing: each switch is its own card with its description as a footer.
        if (adaptiveVolumeCapability) StyledToggle(label = stringResource(R.string.personalized_volume),
            description = stringResource(R.string.personalized_volume_description),
            checked = adaptiveVolumeChecked, onCheckedChange = onAdaptiveVolumeCheckedChange, enabled = isPremium)
        if (conversationalAwarenessCapability) StyledToggle(label = stringResource(R.string.conversational_awareness),
            description = stringResource(R.string.conversational_awareness_description),
            checked = conversationalAwarenessChecked, onCheckedChange = onConversationalAwarenessCheckedChange, enabled = isPremium)
        if (loudSoundReductionCapability && vendorIdHook) StyledToggle(label = stringResource(R.string.loud_sound_reduction),
            description = stringResource(R.string.loud_sound_reduction_description),
            checked = loudSoundReductionChecked, onCheckedChange = onLoudSoundReductionCheckedChange, enabled = isPremium)
        // Adaptive Audio strength is set in the listening mode control, as on iOS 27.
        if (customEqCapability) {
            Spacer(modifier = Modifier.height(12.dp))
            StyledList {
                StyledListItem(name = stringResource(R.string.equalizer), onClick = navigateToEqualizer)
            }
        }
        return
    }
    if (adaptiveVolumeCapability || conversationalAwarenessCapability || loudSoundReductionCapability || adaptiveAudioCapability) {
        StyledList(title = stringResource(R.string.audio)) {
            if (adaptiveVolumeCapability) {
                StyledToggle(
                    label = stringResource(R.string.personalized_volume),
                    description = stringResource(R.string.personalized_volume_description),
                    checked = adaptiveVolumeChecked,
                    onCheckedChange = onAdaptiveVolumeCheckedChange,
                    enabled = isPremium,
                )
            }

            if (conversationalAwarenessCapability) {
                StyledToggle(
                    label = stringResource(R.string.conversational_awareness),
                    description = stringResource(R.string.conversational_awareness_description),
                    checked = conversationalAwarenessChecked,
                    onCheckedChange = onConversationalAwarenessCheckedChange,
                    enabled = isPremium,
                )
            }

            if (loudSoundReductionCapability && vendorIdHook) {
                StyledToggle(
                    label = stringResource(R.string.loud_sound_reduction),
                    description = stringResource(R.string.loud_sound_reduction_description),
                    checked = loudSoundReductionChecked,
                    onCheckedChange = onLoudSoundReductionCheckedChange,
                    enabled = isPremium,
                )
            }

            if (adaptiveAudioCapability) {
                StyledListItem(
                    name = stringResource(R.string.adaptive_audio),
                    onClick = navigateToAdaptiveStrength,
                )
            }

            if (customEqCapability) {
                StyledListItem(
                    name = stringResource(R.string.equalizer),
                    onClick = navigateToEqualizer,
                )
            }
        }
    }
}

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

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import me.kavishdevar.librepods.R
import kotlin.io.encoding.ExperimentalEncodingApi

@Composable
fun ConnectionSettings(
    automaticEarDetectionEnabled: Boolean,
    onAutomaticEarDetectionChanged: (Boolean) -> Unit,
    automaticConnectionEnabled: Boolean?,
    automaticConnectionPhase: me.kavishdevar.librepods.bluetooth.SettingPhase? = null,
    onAutomaticConnectionChanged: (Boolean) -> Unit,
) {
    StyledList {
        StyledToggle(
            label = stringResource(R.string.ear_detection),
            description = stringResource(R.string.local_ear_detection_description),
            checked = automaticEarDetectionEnabled,
            onCheckedChange = onAutomaticEarDetectionChanged
        )

        StyledToggle(
            label = stringResource(R.string.automatically_connect),
            description = stringResource(when (automaticConnectionPhase) {
                me.kavishdevar.librepods.bluetooth.SettingPhase.PENDING -> R.string.setting_pending_notice
                me.kavishdevar.librepods.bluetooth.SettingPhase.FAILED -> R.string.setting_failed_notice
                else -> if (automaticConnectionEnabled == null) R.string.setting_unknown_notice else R.string.automatically_connect_description
            }),
            checked = automaticConnectionEnabled == true,
            enabled = automaticConnectionEnabled != null && automaticConnectionPhase != me.kavishdevar.librepods.bluetooth.SettingPhase.PENDING,
            onCheckedChange = onAutomaticConnectionChanged
        )
    }
}

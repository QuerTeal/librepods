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

package me.kavishdevar.librepods.presentation.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Slider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.kavishdevar.librepods.R
import me.kavishdevar.librepods.data.Battery
import me.kavishdevar.librepods.data.BatteryComponent
import me.kavishdevar.librepods.data.BatteryStatus
import me.kavishdevar.librepods.data.hasReportedLevel
import me.kavishdevar.librepods.data.NoiseControlMode
import me.kavishdevar.librepods.presentation.theme.sfProFamily
import kotlin.math.roundToInt

private val TileShape = RoundedCornerShape(30.dp)
// Near-opaque so the panel stays legible where the system doesn't blur what's behind it.
private val TileColor = Color(0xFF1E1E21).copy(alpha = 0.96f)
private val InactiveCircle = Color.White.copy(alpha = 0.14f)
private val SecondaryText = Color.White.copy(alpha = 0.72f)
private val OnWhite = Color(0xFF1B1B1D)

/** SF Symbols, drawn with SF Pro. */
private const val SPEAKER_GLYPH = "􀊧"
private const val BOLT_GLYPH = "􀋦"

/**
 * AirPods controls in the style of the One UI 9 quick panel: translucent rounded tiles over the
 * blurred screen, white for whatever is active, and a large pill slider for volume.
 */
@Composable
fun OneUIControlPanel(
    deviceName: String,
    batteries: List<Battery>,
    volumeFraction: Float,
    onVolumeFractionChange: (Float) -> Unit,
    onVolumeDragStateChange: (Boolean) -> Unit,
    availableModes: List<NoiseControlMode>,
    currentMode: NoiseControlMode?,
    onModeSelected: (NoiseControlMode) -> Unit,
    conversationalAwareness: Boolean?,
    onConversationalAwarenessChange: (Boolean) -> Unit,
    volumeSteps: Int = 0,
) {
    Column(
        modifier = Modifier
            .widthIn(max = 600.dp)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        DeviceTile(deviceName, batteries)
        ListeningModeTile(availableModes, currentMode, onModeSelected)
        VolumePill(volumeFraction, onVolumeFractionChange, onVolumeDragStateChange, volumeSteps)
        ConversationalAwarenessTile(conversationalAwareness, onConversationalAwarenessChange)
    }
}

@Composable
private fun DeviceTile(deviceName: String, batteries: List<Battery>) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(TileShape)
            .background(TileColor)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(Color.White, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(R.drawable.airpods),
                contentDescription = null,
                tint = OnWhite,
                modifier = Modifier.size(26.dp)
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = deviceName,
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            batterySummary(batteries)?.let {
                val spoken = batterySpokenSummary(batteries)
                Spacer(modifier = Modifier.height(2.dp))
                Text(text = it, color = SecondaryText, fontSize = 13.sp, maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clearAndSetSemantics { text = AnnotatedString(spoken) })
            }
        }
    }
}

@Composable
private fun batterySpokenSummary(batteries: List<Battery>): String = listOf(
    BatteryComponent.LEFT to stringResource(R.string.left),
    BatteryComponent.RIGHT to stringResource(R.string.right),
    BatteryComponent.CASE to stringResource(R.string.case_alt),
).mapNotNull { (component, label) ->
    batteries.find { it.component == component && it.hasReportedLevel() }?.let {
        stringResource(if (it.status == BatteryStatus.CHARGING) R.string.popup_battery_charging
            else R.string.popup_battery_level, label, it.level)
    }
}.joinToString("; ")

@Composable
private fun batterySummary(batteries: List<Battery>): AnnotatedString? {
    val parts = listOf(
        BatteryComponent.LEFT to stringResource(R.string.left),
        BatteryComponent.RIGHT to stringResource(R.string.right),
        BatteryComponent.CASE to stringResource(R.string.case_alt),
    ).mapNotNull { (component, label) ->
        batteries.find { it.component == component }
            ?.takeIf { it.hasReportedLevel() }
            ?.let { (it.status == BatteryStatus.CHARGING) to "$label ${it.level}%" }
    }
    if (parts.isEmpty()) return null
    return buildAnnotatedString {
        parts.forEachIndexed { index, (charging, text) ->
            if (index > 0) append("  ·  ")
            append(text)
            if (charging) withStyle(SpanStyle(fontFamily = sfProFamily)) { append(" $BOLT_GLYPH") }
        }
    }
}

@Composable
private fun ListeningModeTile(
    availableModes: List<NoiseControlMode>,
    currentMode: NoiseControlMode?,
    onModeSelected: (NoiseControlMode) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(TileShape)
            .background(TileColor)
            .padding(horizontal = 8.dp, vertical = 16.dp)
    ) {
        Text(
            text = stringResource(R.string.noise_control),
            color = SecondaryText,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 12.dp, bottom = 12.dp)
        )
        OneUIChoiceGrid(availableModes.size) { index, choiceModifier ->
                val mode = availableModes[index]
                val selected = mode == currentMode
                val circle by animateColorAsState(if (selected) Color.White else InactiveCircle, label = "modeCircle")
                val iconTint by animateColorAsState(if (selected) OnWhite else Color.White, label = "modeIcon")
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = choiceModifier
                        .clip(RoundedCornerShape(16.dp))
                        .selectable(selected = selected, role = Role.RadioButton, onClick = { if (!selected) onModeSelected(mode) })
                        .padding(vertical = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(circle),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            bitmap = ImageBitmap.imageResource(modeIcon(mode)),
                            contentDescription = null,
                            tint = iconTint,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(modeLabel(mode)),
                        color = if (selected) Color.White else SecondaryText,
                        fontSize = 12.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        textAlign = TextAlign.Center,
                        maxLines = 2
                    )
                }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VolumePill(
    fraction: Float,
    onFractionChange: (Float) -> Unit,
    onDragStateChange: (Boolean) -> Unit,
    steps: Int,
) {
    val value = fraction.coerceIn(0f, 1f)
    val volumeLabel = stringResource(R.string.media_volume)
    val interaction = remember { MutableInteractionSource() }
    val dragging by interaction.collectIsDraggedAsState()
    val dragCallback by rememberUpdatedState(onDragStateChange)
    DisposableEffect(dragging) {
        dragCallback(dragging)
        onDispose { if (dragging) dragCallback(false) }
    }
    Slider(
        value = value,
        onValueChange = onFractionChange,
        valueRange = 0f..1f,
        steps = steps.coerceAtLeast(0),
        interactionSource = interaction,
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .semantics {
                contentDescription = volumeLabel
                stateDescription = "${(value * 100).roundToInt()}%"
            },
        thumb = {},
        track = { state ->
            val shown = state.value.coerceIn(0f, 1f)
            Box(
                modifier = Modifier.fillMaxWidth().height(64.dp)
                    .clip(TileShape).background(Color(0xFF3A3A3E))
                    .clearAndSetSemantics {}
            ) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(shown).background(Color.White))
                Text(
                    text = SPEAKER_GLYPH, fontFamily = sfProFamily, fontSize = 18.sp,
                    color = if (shown > 0.12f) OnWhite else Color.White,
                    modifier = Modifier.align(Alignment.CenterStart).padding(start = 20.dp)
                )
                Text(
                    text = "${(shown * 100).roundToInt()}%",
                    color = if (shown > 0.9f) OnWhite else Color.White,
                    fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.align(Alignment.CenterEnd).padding(end = 20.dp)
                )
            }
        }
    )
}

@Composable
private fun ConversationalAwarenessTile(enabled: Boolean?, onChange: (Boolean) -> Unit) {
    val circle by animateColorAsState(if (enabled == true) Color.White else InactiveCircle, label = "caCircle")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(TileShape)
            .background(TileColor)
            .toggleable(value = enabled == true, enabled = enabled != null, role = Role.Switch, onValueChange = onChange)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(circle, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(R.drawable.conversational_awareness),
                contentDescription = null,
                tint = if (enabled == true) OnWhite else Color.White,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column {
            Text(stringResource(R.string.conversational_awareness), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(stringResource(if (enabled == null) R.string.setting_unknown_notice else if (enabled) R.string.on else R.string.off), color = SecondaryText, fontSize = 13.sp)
        }
    }
}

private fun modeIcon(mode: NoiseControlMode) = when (mode) {
    NoiseControlMode.OFF -> R.drawable.noise_cancellation
    NoiseControlMode.TRANSPARENCY -> R.drawable.transparency
    NoiseControlMode.ADAPTIVE -> R.drawable.adaptive
    NoiseControlMode.NOISE_CANCELLATION -> R.drawable.noise_cancellation
}

private fun modeLabel(mode: NoiseControlMode) = when (mode) {
    NoiseControlMode.OFF -> R.string.off
    NoiseControlMode.TRANSPARENCY -> R.string.transparency
    NoiseControlMode.ADAPTIVE -> R.string.adaptive
    NoiseControlMode.NOISE_CANCELLATION -> R.string.noise_cancellation
}

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

import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import kotlinx.coroutines.Job
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.setValue
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlinx.coroutines.launch
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Switch
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.kavishdevar.librepods.presentation.theme.OneUIText
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import me.kavishdevar.librepods.presentation.theme.LocalOneUIColors

/** Rounded card that groups One UI rows. */
internal val OneUICardShape = RoundedCornerShape(26.dp)

/** Horizontal inset of row content inside a card; headers and footers line up with it. */
internal val OneUIContentInset = 18.dp

/** True inside a One UI card, where a plain button needs a fill that stands out from the card. */
internal val LocalOneUIOnCard = staticCompositionLocalOf { false }

@Composable
internal fun OneUICard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(OneUICardShape)
            .background(MaterialTheme.colorScheme.surface)
    ) {
        CompositionLocalProvider(LocalOneUIOnCard provides true) { content() }
    }
}

@Composable
internal fun OneUICategoryHeader(text: String) {
    Text(
        text = text,
        style = OneUIText.category,
        color = LocalOneUIColors.current.category,
        modifier = Modifier.padding(start = OneUIContentInset, end = OneUIContentInset, top = 10.dp, bottom = 8.dp)
    )
}

@Composable
internal fun OneUIFooter(text: String) {
    Text(
        text = text,
        style = OneUIText.footer,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = OneUIContentInset, end = OneUIContentInset, top = 8.dp, bottom = 4.dp)
    )
}

/**
 * A One UI list row. [valueSummary] shows the summary in the accent color, which One UI uses for
 * a setting's current value; otherwise it's secondary text. [selected] adds a leading radio button
 * for a single choice, [checked] a leading check circle for a multiple choice.
 */
@Composable
internal fun OneUIRow(
    title: String,
    summary: String? = null,
    valueSummary: Boolean = false,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    selected: Boolean? = null,
    checked: Boolean? = null,
    role: Role? = null,
    showDivider: Boolean = false,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val haptics = LocalHapticFeedback.current
    val activate: () -> Unit = {
        if (selected == false || checked != null) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
        onClick?.invoke()
    }
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    when {
                        onClick == null -> Modifier
                        selected != null -> Modifier.selectable(selected, enabled = enabled, role = Role.RadioButton, onClick = activate)
                        checked != null -> Modifier.toggleable(checked, enabled = enabled, role = Role.Checkbox, onValueChange = { activate() })
                        else -> Modifier.clickable(enabled = enabled, role = role ?: Role.Button, onClick = activate)
                    }
                )
                .heightIn(min = 54.dp)
                .padding(horizontal = OneUIContentInset, vertical = 14.dp)
                .alpha(if (enabled) 1f else 0.4f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selected != null) {
                OneUIRadio(selected = selected)
                Spacer(modifier = Modifier.width(18.dp))
            }
            if (checked != null) {
                OneUICheckCircle(checked = checked)
                Spacer(modifier = Modifier.width(18.dp))
            }
            if (leading != null) {
                leading()
                Spacer(modifier = Modifier.width(16.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = OneUIText.rowTitle, color = MaterialTheme.colorScheme.onSurface)
                if (summary != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = summary,
                        style = OneUIText.rowSummary,
                        color = if (valueSummary) LocalOneUIColors.current.value else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (trailing != null) {
                Spacer(modifier = Modifier.width(16.dp))
                trailing()
            }
        }
        if (showDivider) OneUIDivider()
    }
}

@Composable
internal fun OneUIDivider() {
    HorizontalDivider(
        thickness = 0.6.dp,
        color = MaterialTheme.colorScheme.outlineVariant,
        modifier = Modifier.padding(horizontal = OneUIContentInset)
    )
}

/** A row whose whole surface toggles the switch, as in One UI settings. */
@Composable
internal fun OneUISwitchRow(
    title: String,
    summary: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    showDivider: Boolean = false,
) {
    val haptics = LocalHapticFeedback.current
    // Haptics only for the user's own changes, not for state the AirPods report.
    val toggle: (Boolean) -> Unit = { value ->
        haptics.performHapticFeedback(if (value) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
        onCheckedChange(value)
    }
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = toggle)
                .heightIn(min = 54.dp)
                .padding(horizontal = OneUIContentInset, vertical = 14.dp)
                .alpha(if (enabled) 1f else 0.4f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = OneUIText.rowTitle, color = MaterialTheme.colorScheme.onSurface)
                if (summary != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(text = summary, style = OneUIText.rowSummary, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            OneUISwitch(checked = checked, onCheckedChange = toggle, enabled = enabled, modifier = Modifier.clearAndSetSemantics {})
        }
        if (showDivider) OneUIDivider()
    }
}

/** Android framework switch; the setting row owns its single accessible toggle target. */
@Composable
internal fun OneUISwitch(
    checked: Boolean,
    modifier: Modifier = Modifier,
    onCheckedChange: ((Boolean) -> Unit)? = null,
    enabled: Boolean = true,
) {
    NativeOneUISwitch(checked, enabled, modifier, onCheckedChange)
}

@Composable
internal fun OneUIRadio(selected: Boolean) {
    SeslChoiceIndicator(selected, radio = true)
}

/** One UI seek bar: thin rounded track with a round thumb. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OneUISlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    label: String? = null,
    snapPoints: List<Float> = emptyList(),
    snapThreshold: Float = 0.05f,
) {
    NativeOneUISeekBar(value, valueRange, enabled, label, modifier, onValueChange, snapPoints, snapThreshold)
}

/** One UI 9's thin back chevron (9x18dp, measured from Settings). */
internal val OneUIBackChevron: ImageVector = ImageVector.Builder(
    name = "OneUIBack", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f
).apply {
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 1.7f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(16.5f, 3f)
        lineTo(7.5f, 12f)
        lineTo(16.5f, 21f)
    }
}.build()

/**
 * One UI 9 multiple-choice check: a 20dp circle with a gray ring, filled with the accent and a
 * white tick when checked (as in the Clock app's selection mode).
 */
@Composable
internal fun OneUICheckCircle(checked: Boolean, modifier: Modifier = Modifier) {
    SeslChoiceIndicator(checked, radio = false, modifier = modifier)
}

/**
 * One UI 9 button: a 52dp pill. [filled] is the accent-colored main action with a white bold
 * label; otherwise the pill takes the card color (gray on a card) with a dark bold label.
 */
@Composable
internal fun OneUIButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    filled: Boolean = false,
    containerColor: Color = Color.Unspecified,
    content: @Composable RowScope.() -> Unit,
) {
    val container = when {
        filled -> containerColor.takeOrElse { MaterialTheme.colorScheme.primary }
        LocalOneUIOnCard.current -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.surface
    }
    val contentColor = if (filled) Color.White else MaterialTheme.colorScheme.onSurface
    SeslButton(onClick, modifier, enabled, container, contentColor, content)
}

/**
 * One UI 9 text field: 18sp text over an underline that darkens and thickens while focused, with
 * a clear button while a single-line field has text.
 */
@Composable
internal fun OneUITextField(
    state: TextFieldState,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    singleLine: Boolean = true,
    focusRequester: FocusRequester? = null,
    label: String? = null,
) {
    SeslTextField(state, modifier, placeholder, singleLine, focusRequester, label)
}

/** A button of [OneUIDialog]; [color] tints a destructive action. */
internal class OneUIDialogButton(
    val text: String,
    val onClick: () -> Unit,
    val color: Color = Color.Unspecified,
    val enabled: Boolean = true,
)

/**
 * One UI 9 dialog, measured from the system's "Phone name" dialog: a 26dp-round card docked to the
 * bottom of the screen over a light scrim, with bold text buttons split by a short divider.
 */
@Composable
internal fun OneUIDialog(
    onDismissRequest: () -> Unit,
    title: String? = null,
    message: String? = null,
    buttons: List<OneUIDialogButton>,
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    SeslDialog(onDismissRequest, title, message, buttons, content)
}

/**
 * One UI's press feedback, from a recording of Settings: the pressed surface darkens evenly (no
 * spreading ripple) and fades back on release. A quick tap still shows the full darkening once.
 */
internal class OneUIPressIndication(private val color: Color) : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = OneUIPressNode(interactionSource, color)
    override fun equals(other: Any?): Boolean = other is OneUIPressIndication && other.color == color
    override fun hashCode(): Int = color.hashCode()
}

private class OneUIPressNode(
    private val interactionSource: InteractionSource,
    private val color: Color,
) : Modifier.Node(), DrawModifierNode {
    private val level = Animatable(0f)

    override fun onAttach() {
        coroutineScope.launch {
            var job: Job? = null
            interactionSource.interactions.collect { interaction ->
                when (interaction) {
                    is PressInteraction.Press -> {
                        job?.cancel()
                        job = launch { level.animateTo(1f, tween(90)) }
                    }
                    is PressInteraction.Release, is PressInteraction.Cancel -> {
                        val pressing = job
                        job = launch {
                            pressing?.join()
                            level.animateTo(0f, tween(260))
                        }
                    }
                }
            }
        }
    }

    override fun ContentDrawScope.draw() {
        drawContent()
        if (level.value > 0f) drawRect(color = color, alpha = 0.09f * level.value)
    }
}

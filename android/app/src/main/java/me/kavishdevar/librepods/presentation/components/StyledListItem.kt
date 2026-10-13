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
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import me.kavishdevar.librepods.R
import me.kavishdevar.librepods.presentation.theme.AppleUnevenShape
import me.kavishdevar.librepods.presentation.theme.AppleShape
import me.kavishdevar.librepods.presentation.theme.AppleColors
import me.kavishdevar.librepods.presentation.theme.AppleMetrics
import me.kavishdevar.librepods.presentation.theme.DesignSystem
import me.kavishdevar.librepods.presentation.theme.LocalDesignSystem
import me.kavishdevar.librepods.presentation.theme.sectionHeader
import me.kavishdevar.librepods.presentation.theme.sfProFamily
import me.kavishdevar.librepods.presentation.theme.LocalOneUI

@Composable
fun StyledListItem(
    modifier: Modifier = Modifier,
    title: String? = null,
    name: String,
    onClick: (() -> Unit)?,
    description: String? = null,
    height: Dp = 58.dp,
    enabled: Boolean = true,
    orientation: ListItemOrientation = ListItemOrientation.Horizontal,
    leadingContent: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null
) {
    if (LocalOneUI.current) {
        Column(modifier = modifier) {
            title?.let { OneUICategoryHeader(it) }
            OneUICard {
                OneUIListItem(name, onClick, description, enabled, orientation, null, leadingContent, trailingContent, showDivider = false)
            }
        }
        return
    }
    val m3eEnabled = LocalDesignSystem.current == DesignSystem.Material
    Column {
        title?.let {
            if (!m3eEnabled) {
                AppleSectionHeader(it)
                return@let
            }
            Box(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .padding(top = 4.dp, bottom = 8.dp)
            ) {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelSmallEmphasized
                )
            }
        }
        Column(
            modifier = modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .background(
                    if (m3eEnabled) Color.Transparent else MaterialTheme.colorScheme.surface,
                    if (m3eEnabled) RoundedCornerShape(16.dp) else AppleShape(AppleMetrics.CardRadius)
                )
                .clip(if (m3eEnabled) RoundedCornerShape(16.dp) else AppleShape(AppleMetrics.CardRadius))
        ) {
            StyledListItemContent(
                name = name,
                onClick = onClick,
                description = description,
                height = height,
                enabled = enabled,
                index = 0,
                count = 1,
                orientation = orientation,
                leadingContent = leadingContent,
                trailingContent = trailingContent
            )
        }
    }
}

@Composable
fun StyledListScope.StyledListItem(
    modifier: Modifier = Modifier,
    name: String,
    onClick: (() -> Unit)? = null,
    description: String? = null,
    enabled: Boolean = onClick != null,
    orientation: ListItemOrientation = ListItemOrientation.Horizontal,
    selected: Boolean? = null,
    /** Marks an option of a multiple choice; [selected] is for a single choice. */
    checked: Boolean? = null,
    leadingContent: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null
) {
    item { index, count ->
        StyledListItemContent(
            name = name,
            onClick = onClick,
            description = description,
            enabled = enabled,
            index = index,
            count = count,
            orientation = orientation,
            modifier = modifier,
            selected = selected,
            checked = checked,
            leadingContent = leadingContent,
            trailingContent = trailingContent
        )
    }
}

/**
 * One UI shows a setting's current value as an accent-colored summary under the title (the
 * horizontal orientation's trailing value elsewhere), and explanatory text as a gray summary.
 */
@Composable
private fun OneUIListItem(
    name: String,
    onClick: (() -> Unit)?,
    description: String?,
    enabled: Boolean,
    orientation: ListItemOrientation,
    selected: Boolean?,
    leadingContent: (@Composable () -> Unit)?,
    trailingContent: (@Composable () -> Unit)?,
    showDivider: Boolean,
    checked: Boolean? = null,
) {
    OneUIRow(
        title = name,
        summary = description,
        valueSummary = orientation == ListItemOrientation.Horizontal && onClick != null && selected == null && checked == null,
        onClick = onClick,
        enabled = onClick == null || enabled,
        selected = selected,
        checked = checked,
        showDivider = showDivider,
        leading = leadingContent,
        trailing = trailingContent
    )
}

enum class ListItemOrientation{
    Horizontal,
    Vertical
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun StyledListItemContent(
    modifier: Modifier = Modifier,
    name: String,
    onClick: (() -> Unit)?,
    description: String? = null,
    height: Dp = 58.dp,
    enabled: Boolean = true,
    index: Int,
    count: Int,
    orientation: ListItemOrientation = ListItemOrientation.Horizontal,
    selected: Boolean? = null,
    checked: Boolean? = null,
    leadingContent: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null
) {
    if (LocalOneUI.current) {
        OneUIListItem(name, onClick, description, enabled, orientation, selected, leadingContent, trailingContent, showDivider = index + 1 != count, checked = checked)
        return
    }
    val isDarkTheme = isSystemInDarkTheme()
    val surfaceColor = MaterialTheme.colorScheme.surface
    val surfaceDimColor = MaterialTheme.colorScheme.surfaceDim
    var backgroundColor by remember { mutableStateOf(surfaceColor) }
    val animatedBackgroundColor by animateColorAsState(targetValue = backgroundColor, animationSpec = tween(durationMillis = 500))
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    when (LocalDesignSystem.current) {
        DesignSystem.Apple -> {
            // iOS 27 rows are 52 pt; screens that pass their own height keep it.
            val rowHeight = if (height == 58.dp) AppleMetrics.RowHeight else height
            val trailingContentDefault: @Composable () -> Unit = {
                if (trailingContent == null) {
                    if (onClick != null) {
                        val ticked = selected ?: checked
                        if (ticked != null) {
                            val floatAnimateState by animateFloatAsState(
                                targetValue = if (ticked) 1f else 0f,
                                animationSpec = tween(durationMillis = 300)
                            )

                            Text(
                                text = "􀆅",
                                style = TextStyle(
                                    fontSize = 20.sp,
                                    fontFamily = sfProFamily,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = floatAnimateState),
                                ),
                                modifier = Modifier.padding(end = 4.dp)
                            )
                        } else {
                            Text(
                                text = "􀯻",
                                style = MaterialTheme.typography.bodyMedium,
                                color = AppleColors.secondaryLabel(),
                                modifier = Modifier
                                    .padding(start = if (description != null) 6.dp else 0.dp)
                            )
                        }
                    }
                } else {
                    trailingContent()
                }
            }
            Column (
                modifier = Modifier
                    .background(
                        animatedBackgroundColor,
                        when {
                            (index == 0 && count == 1) -> {
                                AppleShape(AppleMetrics.CardRadius)
                            }

                            (index == 0) -> {
                                AppleUnevenShape(topStart = AppleMetrics.CardRadius,
                                    topEnd = AppleMetrics.CardRadius,
                                    bottomStart = 0.dp,
                                    bottomEnd = 0.dp
                                )
                            }

                            (index + 1 == count) -> {
                                AppleUnevenShape(topStart = 0.dp,
                                    topEnd = 0.dp,
                                    bottomStart = AppleMetrics.CardRadius,
                                    bottomEnd = AppleMetrics.CardRadius
                                )
                            }

                            else -> {
                                RectangleShape
                            }
                        }
                    )
                    .semantics(mergeDescendants = true) {
                        if (selected != null) {
                            role = Role.RadioButton
                            toggleableState = ToggleableState(selected)
                        } else if (checked != null) {
                            role = Role.Checkbox
                            toggleableState = ToggleableState(checked)
                        }
                        if (!enabled) disabled()
                        if (onClick != null) onClick {
                            if (enabled) {
                                haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                                onClick.invoke()
                                true
                            } else false
                        }
                    }
                    .pointerInput(enabled, onClick) {
                        detectTapGestures(
                            onPress = {
                                if (enabled) {
                                    backgroundColor = surfaceDimColor
                                    tryAwaitRelease()
                                    backgroundColor = surfaceColor
                                }
                            },
                            onTap = {
                                if (enabled) {
                                    scope.launch {
                                        haptics.performHapticFeedback(
                                            HapticFeedbackType.ContextClick
                                        )
                                    }
                                    onClick?.invoke()
                                }
                            }
                        )
                    }
                    .heightIn(min = rowHeight)
                    .padding(start = if (leadingContent != null) AppleMetrics.IconRowInset else AppleMetrics.ContentInset, end = AppleMetrics.ContentInset)
            ) {
                Row(
                    modifier = Modifier
                        .heightIn(min = rowHeight)
                        .padding(vertical = if (orientation == ListItemOrientation.Vertical) 12.dp else 0.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (leadingContent != null) {
                        leadingContent()
                        Spacer(modifier = Modifier.width(AppleMetrics.IconTextGap))
                    }
                    Column (
                        modifier = if (orientation == ListItemOrientation.Vertical) Modifier.weight(1f) else Modifier,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        if (description != null && orientation == ListItemOrientation.Vertical) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(if (isDarkTheme) 0.6f else 0.8f), // TODO: move to color scheme
                            )
                        }
                    }

                    if (orientation == ListItemOrientation.Horizontal) Spacer(modifier = Modifier.weight(1f))

                    if (orientation == ListItemOrientation.Horizontal && description != null) {
                        Text(
                            text = description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = AppleColors.secondaryLabel()
                        )
                    }

                    trailingContentDefault()
                }
                if (index+1 != count) {
                    // iOS separators are a hairline that starts at the row text.
                    HorizontalDivider(
                        thickness = Dp.Hairline,
                        color = AppleColors.separator(),
                        modifier = Modifier
                            .padding(start = if (leadingContent != null) AppleMetrics.IconTile + AppleMetrics.IconTextGap else 0.dp)
                    )
                }
            }
        }

        DesignSystem.Material -> {
            val defaultShape = when {
                count == 1 -> RoundedCornerShape(24.dp)

                index == 0 -> RoundedCornerShape(
                    topStart = 24.dp,
                    topEnd = 24.dp,
                    bottomStart = 8.dp,
                    bottomEnd = 8.dp
                )

                index == count - 1 -> RoundedCornerShape(
                    topStart = 8.dp,
                    topEnd = 8.dp,
                    bottomStart = 24.dp,
                    bottomEnd = 24.dp
                )

                else -> RoundedCornerShape(8.dp)
            }
            Column {
                SegmentedListItem(
                    modifier = modifier.heightIn(min = 64.dp),
                    shapes = ListItemDefaults.shapes().copy(
                        shape = defaultShape,
                        pressedShape = RoundedCornerShape(24.dp),
                        selectedShape = RoundedCornerShape(24.dp),
                        hoveredShape = RoundedCornerShape(24.dp),
                    ),
                    onClick = onClick ?: {},
                    leadingContent = leadingContent,
                    trailingContent = {
                        if (trailingContent == null) {
                            if (onClick != null) {
                                if (checked != null) {
                                    Checkbox(checked = checked, onCheckedChange = null)
                                } else if (selected == true) {
                                    Icon(Icons.Default.Check, contentDescription = null)
                                } else if (selected == null) {
                                    Icon(
                                        Icons.AutoMirrored.Default.KeyboardArrowRight,
                                        contentDescription = null
                                    )
                                }
                            }
                        } else {
                            trailingContent()
                        }
                    },
                    supportingContent = {
                        if (description != null) Text(
                            description,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    },
                    content = {
                        Text(
                            text = name,
                            style = MaterialTheme.typography.labelMediumEmphasized,
                            modifier = Modifier.padding(
                                top = 4.dp,
                                bottom = if (description != null) 0.dp else 4.dp
                            )
                        )
                    },
                    verticalAlignment = Alignment.CenterVertically,
                    colors = if (onClick == null) {
                            ListItemDefaults.segmentedColors().run {
                                copy(
                                    disabledContentColor = contentColor,
                                    disabledSupportingContentColor = supportingContentColor,
                                    disabledTrailingContentColor = trailingContentColor
                                )
                            }
                        } else ListItemDefaults.segmentedColors(),
                    enabled = onClick != null && enabled,
                    selected = selected ?: false
                )
                if (index+1 != count) {
                    Spacer(modifier = Modifier.height(2.dp))
                }
            }
        }
    }
}

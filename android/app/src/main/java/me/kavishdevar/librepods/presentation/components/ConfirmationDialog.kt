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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Dialog
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import me.kavishdevar.librepods.R
import me.kavishdevar.librepods.presentation.theme.AppleShape
import me.kavishdevar.librepods.presentation.theme.DesignSystem
import me.kavishdevar.librepods.presentation.theme.LocalDesignSystem
import me.kavishdevar.librepods.presentation.theme.LocalOneUI

@OptIn(ExperimentalMaterial3Api::class)
@ExperimentalHazeMaterialsApi
@Composable
fun ConfirmationDialog(
    showDialog: MutableState<Boolean>,
    title: String,
    message: String,
    confirmText: String = "Enable",
    dismissText: String = "Cancel",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit = { showDialog.value = false },
    backdrop: LayerBackdrop,
) {
    if (LocalOneUI.current) {
        if (showDialog.value) {
            OneUIDialog(
                onDismissRequest = onDismiss,
                title = title,
                message = message,
                buttons = listOf(OneUIDialogButton(dismissText, onDismiss), OneUIDialogButton(confirmText, onConfirm))
            )
        }
        return
    }
    if (LocalDesignSystem.current == DesignSystem.Material) {
        if (showDialog.value) {
            AlertDialog(
                onDismissRequest = onDismiss,
                properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = false),
                title = { Text(text = title) },
                text = { Text(text = message) },
                confirmButton = { TextButton(onClick = onConfirm) { Text(text = confirmText) } },
                dismissButton = { TextButton(onClick = onDismiss) { Text(text = dismissText) } }
            )
        }
        return
    }
    if (!showDialog.value) return
    val dialogSurface = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
    val dialogDensity = LocalDensity.current
    Dialog(onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnBackPress = true,
            dismissOnClickOutside = false, usePlatformDefaultWidth = false)) {
        CompositionLocalProvider(LocalDensity provides dialogDensity) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                val innerBackdrop = rememberLayerBackdrop()
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.2f))
                        .clickable(enabled = false, onClick = {}),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .requiredWidthIn(min = 200.dp, max = 360.dp)
                            .clip(AppleShape(48.dp))
                            .drawBackdrop(
                                backdrop = backdrop,
                                exportedBackdrop = innerBackdrop,
                                shape = { AppleShape(48.dp) },
                                effects = {
                                    vibrancy()
                                    blur(4f.dp.toPx())
                                    lens(12f.dp.toPx(), 48f.dp.toPx(), true)
                                },
                                onDrawSurface = {
                                    drawRect(dialogSurface)
                                })
                    ) {
                        Column(Modifier.verticalScroll(rememberScrollState()),
                            horizontalAlignment = Alignment.CenterHorizontally) {
                            Spacer(modifier = Modifier.height(24.dp))
                            Text(
                                title,
                                color = MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.bodyLarge,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                message,
                                color = MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(0.9f),
                                horizontalArrangement = Arrangement.spacedBy(24.dp)
                            ) {
                                StyledButton(
                                    onClick = onDismiss,
                                    backdrop = innerBackdrop,
                                    modifier = Modifier.weight(1f),
                                    materialButtonStyle = MaterialButtonStyle.Outlined,
                                ) {
                                    Text(
                                        text = dismissText,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                                StyledButton(
                                    onClick = onConfirm,
                                    backdrop = innerBackdrop,
                                    modifier = Modifier.weight(1f),
                                    materialButtonStyle = MaterialButtonStyle.Filled,
                                    surfaceColor = MaterialTheme.colorScheme.primary
                                ) {
                                    Text(
                                        text = confirmText,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(24.dp))
                        }
                    }
                }
            }
        }
    }
}

/**
 * A confirm/cancel alert in the active look: One UI's bottom dialog, or Material's alert dialog.
 * [destructive] tints the confirm action with the error color.
 */
@Composable
fun StyledAlertDialog(
    onDismissRequest: () -> Unit,
    title: String,
    text: String,
    confirmText: String,
    onConfirm: () -> Unit,
    dismissText: String,
    destructive: Boolean = false,
) {
    val confirmColor = if (destructive) MaterialTheme.colorScheme.error else Color.Unspecified
    if (LocalOneUI.current) {
        OneUIDialog(
            onDismissRequest = onDismissRequest,
            title = title,
            message = text,
            buttons = listOf(
                OneUIDialogButton(dismissText, onDismissRequest),
                OneUIDialogButton(confirmText, onConfirm, color = confirmColor)
            )
        )
    } else {
        AlertDialog(
            onDismissRequest = onDismissRequest,
            title = { Text(text = title) },
            text = { Text(text = text) },
            confirmButton = { TextButton(onClick = onConfirm) { Text(text = confirmText, color = confirmColor) } },
            dismissButton = { TextButton(onClick = onDismissRequest) { Text(text = dismissText) } }
        )
    }
}

// SPDX-License-Identifier: GPL-3.0-or-later
package me.kavishdevar.librepods.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * iOS 27 Settings metrics, measured from the user's iPadOS 27 recordings (2x, so 2 px = 1 pt = 1 dp).
 * The AirPods page there is the reference for the Apple design.
 */
object AppleMetrics {
    val RowHeight = 52.dp
    val CardRadius = 26.dp
    /** Row text, section headers and footers all start 20 pt inside the card edge. */
    val ContentInset = 20.dp
    val IconRowInset = 16.dp
    val IconTile = 29.dp
    val IconTileRadius = 7.dp
    val IconTextGap = 15.dp
    val SegmentHeight = 56.dp
    val SegmentInset = 2.dp
    val VolumeHeight = 56.dp
    val HeaderGap = 10.dp
    val FooterGap = 10.dp
}

object AppleText {
    val sectionHeader = TextStyle(fontFamily = sfProFamily, fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold)
    val footer = TextStyle(fontFamily = sfProFamily, fontSize = 13.sp, lineHeight = 18.sp)
    val segmentLabel = TextStyle(fontFamily = sfProFamily, fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium)
}

object AppleColors {
    /** Grouped-list separator: rgba(60,60,67,0.29) light, rgba(84,84,88,0.6) dark. */
    @Composable @ReadOnlyComposable
    fun separator() = if (isSystemInDarkTheme()) Color(0x99545458) else Color(0x4A3C3C43)

    /** Header, footer, row value and chevron gray (#848387 measured in light mode). */
    @Composable @ReadOnlyComposable
    fun secondaryLabel() = MaterialTheme.colorScheme.onSurfaceVariant

    @Composable @ReadOnlyComposable
    fun segmentTrack() = if (isSystemInDarkTheme()) Color(0xFF2C2C2E) else Color(0xFFE5E5EA)

    @Composable @ReadOnlyComposable
    fun segmentSelected() = if (isSystemInDarkTheme()) Color(0xFF000000) else Color(0xFFFFFFFF)

    @Composable @ReadOnlyComposable
    fun volumeTrack() = if (isSystemInDarkTheme()) Color(0xFF0E0E0F) else Color(0xFFE8E7ED)

    /** Measured in dark mode only; the light recording never showed a filled volume track. */
    @Composable @ReadOnlyComposable
    fun volumeFill() = if (isSystemInDarkTheme()) Color(0xFF878787) else Color(0xFFFFFFFF)

    val audioTile = Color(0xFFFF2E57)
    val hearingTile = Color(0xFF0A84FF)
    val controlsTile = Color(0xFF8E8E93)
    val accessibilityTile = Color(0xFF0A84FF)
    val batteryTile = Color(0xFF34C759)
    val statusTile = Color(0xFF5856D6)
}

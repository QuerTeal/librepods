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

package me.kavishdevar.librepods.presentation.theme

import android.content.SharedPreferences
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import me.kavishdevar.librepods.R

/** The look the user picked in settings. */
enum class AppDesign(val prefValue: String, @StringRes val labelRes: Int) {
    OneUI("oneui", R.string.design_one_ui),
    Apple("apple", R.string.design_apple),
    Material("material", R.string.design_material);

    companion object {
        const val PREF_KEY = "app_design"
        val availableDesigns = listOf(OneUI, Apple)
        fun fromPreferenceValue(value: String?): AppDesign =
            availableDesigns.firstOrNull { it.prefValue == value } ?: OneUI

        fun fromPreferences(preferences: SharedPreferences): AppDesign =
            fromPreferenceValue(preferences.getString(PREF_KEY, null))
    }
}

/**
 * True when the One UI look is active. One UI reuses the Material design system's structure
 * (scaffold, spacing), so components check this on top of [LocalDesignSystem] to restyle.
 */
val LocalOneUI = staticCompositionLocalOf { false }

// One UI 9 values, measured from the system Settings app (light theme); dark mirrors its structure.
internal val OneUIBlue = Color(0xFF387AFF)

internal val OneUILightColorScheme = lightColorScheme(
    primary = OneUIBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDE8FF),
    onPrimaryContainer = Color(0xFF123F99),
    secondaryContainer = Color(0xFFE4E4E7),
    onSecondaryContainer = Color(0xFF010102),
    tertiaryContainer = Color(0xFFDDE8FF),
    onTertiaryContainer = Color(0xFF123F99),
    background = Color(0xFFF3F3F5),
    onBackground = Color(0xFF010102),
    surface = Color(0xFFFCFCFF),
    onSurface = Color(0xFF010102),
    onSurfaceVariant = Color(0xFF6A6A6D),
    surfaceContainer = Color(0xFFF3F3F5),
    surfaceContainerLow = Color(0xFFFCFCFF),
    surfaceContainerHigh = Color(0xFFFCFCFF),
    surfaceContainerHighest = Color(0xFFE4E4E7),
    surfaceBright = Color(0xFFFCFCFF),
    surfaceDim = Color(0xFFE4E4E7),
    outline = Color(0xFFA3A3A7),
    outlineVariant = Color(0xFFE4E4E7),
    error = Color(0xFFE5392E),
)

internal val OneUIDarkColorScheme = darkColorScheme(
    primary = Color(0xFF4A87FF),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF173E80),
    onPrimaryContainer = Color(0xFFDDE8FF),
    secondaryContainer = Color(0xFF2C2C2F),
    onSecondaryContainer = Color(0xFFFAFAFC),
    tertiaryContainer = Color(0xFF173E80),
    onTertiaryContainer = Color(0xFFDDE8FF),
    background = Color(0xFF000000),
    onBackground = Color(0xFFFAFAFC),
    surface = Color(0xFF1A1A1C),
    onSurface = Color(0xFFFAFAFC),
    onSurfaceVariant = Color(0xFF9D9DA3),
    surfaceContainer = Color(0xFF000000),
    surfaceContainerLow = Color(0xFF1A1A1C),
    surfaceContainerHigh = Color(0xFF1A1A1C),
    surfaceContainerHighest = Color(0xFF2C2C2F),
    surfaceBright = Color(0xFF2C2C2F),
    surfaceDim = Color(0xFF0E0E0F),
    outline = Color(0xFF5E5E63),
    outlineVariant = Color(0xFF2C2C2F),
    error = Color(0xFFFF5A4F),
)

/** One UI colors that have no Material color role. */
@Immutable
internal data class OneUIColors(
    /** A setting's current value, shown under its title. */
    val value: Color,
    val category: Color,
    val switchOffTrack: Color,
    val dialog: Color,
    val dialogDivider: Color,
    /** The underline of a focused text field. */
    val fieldLine: Color,
    /** The round background toolbar buttons gain while the content is scrolled. */
    val floatingButton: Color,
)

internal val OneUILightColors = OneUIColors(
    value = Color(0xFF2E65D4),
    category = Color(0xFF848487),
    switchOffTrack = Color(0xFFA3A3A7),
    dialog = Color(0xFFEEEEEF),
    dialogDivider = Color(0xFFD6D6D8),
    fieldLine = Color(0xFF252528),
    floatingButton = Color(0xFFF8F8F8),
)
internal val OneUIDarkColors = OneUIColors(
    value = Color(0xFF7BA3FF),
    category = Color(0xFF8E8E93),
    switchOffTrack = Color(0xFF5E5E63),
    dialog = Color(0xFF262629),
    dialogDivider = Color(0xFF454549),
    fieldLine = Color(0xFFE4E4E7),
    floatingButton = Color(0xFF2A2A2D),
)

internal val LocalOneUIColors = staticCompositionLocalOf { OneUILightColors }

/** Text styles for One UI list rows and headers; the system font is One UI's own on Galaxy devices. */
internal object OneUIText {
    val rowTitle = TextStyle(fontFamily = FontFamily.Default, fontSize = 17.sp, lineHeight = 22.sp)
    val rowSummary = TextStyle(fontFamily = FontFamily.Default, fontSize = 14.sp, lineHeight = 19.sp)
    val category = TextStyle(fontFamily = FontFamily.Default, fontSize = 13.sp, lineHeight = 17.sp, fontWeight = FontWeight.Medium)
    val footer = TextStyle(fontFamily = FontFamily.Default, fontSize = 13.sp, lineHeight = 18.sp)
    val toolbarTitle = TextStyle(fontFamily = FontFamily.Default, fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold)
    val button = TextStyle(fontFamily = FontFamily.Default, fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.Bold)
    val field = TextStyle(fontFamily = FontFamily.Default, fontSize = 18.sp, lineHeight = 24.sp)
    // Measured from the system's "Phone name" dialog.
    val dialogTitle = TextStyle(fontFamily = FontFamily.Default, fontSize = 17.sp, lineHeight = 23.sp, fontWeight = FontWeight.Bold)
    val dialogBody = TextStyle(fontFamily = FontFamily.Default, fontSize = 17.sp, lineHeight = 27.sp)
    val dialogButton = TextStyle(fontFamily = FontFamily.Default, fontSize = 17.sp, lineHeight = 23.sp, fontWeight = FontWeight.Bold)
}

internal val OneUITypography = Typography().run {
    fun TextStyle.system() = copy(fontFamily = FontFamily.Default)
    copy(
        displayLarge = displayLarge.system(),
        displayMedium = displayMedium.system(),
        displaySmall = displaySmall.system().copy(fontSize = 32.sp, lineHeight = 38.sp),
        headlineLarge = headlineLarge.system(),
        // LargeTopAppBar's expanded title.
        headlineMedium = headlineMedium.system().copy(fontSize = 32.sp, lineHeight = 38.sp, fontWeight = FontWeight.SemiBold),
        headlineSmall = headlineSmall.system().copy(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
        // Toolbar title.
        titleLarge = titleLarge.system().copy(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold),
        titleMedium = titleMedium.system().copy(fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
        titleSmall = titleSmall.system().copy(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
        bodyLarge = bodyLarge.system().copy(fontSize = 17.sp, lineHeight = 22.sp),
        bodyMedium = bodyMedium.system().copy(fontSize = 16.sp, lineHeight = 21.sp),
        bodySmall = bodySmall.system().copy(fontSize = 14.sp, lineHeight = 19.sp),
        labelLarge = labelLarge.system().copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
        labelMedium = labelMedium.system().copy(fontSize = 16.sp),
        labelSmall = labelSmall.system().copy(fontSize = 13.sp),
        displayLargeEmphasized = displayLargeEmphasized.system(),
        displayMediumEmphasized = displayMediumEmphasized.system(),
        displaySmallEmphasized = displaySmallEmphasized.system(),
        headlineLargeEmphasized = headlineLargeEmphasized.system(),
        headlineMediumEmphasized = headlineMediumEmphasized.system(),
        headlineSmallEmphasized = headlineSmallEmphasized.system(),
        titleLargeEmphasized = titleLargeEmphasized.system(),
        titleMediumEmphasized = titleMediumEmphasized.system(),
        titleSmallEmphasized = titleSmallEmphasized.system(),
        bodyLargeEmphasized = bodyLargeEmphasized.system(),
        bodyMediumEmphasized = bodyMediumEmphasized.system().copy(fontSize = 16.sp),
        bodySmallEmphasized = bodySmallEmphasized.system().copy(fontSize = 14.sp),
        labelLargeEmphasized = labelLargeEmphasized.system(),
        labelMediumEmphasized = labelMediumEmphasized.system().copy(fontSize = 17.sp, fontWeight = FontWeight.Normal),
        labelSmallEmphasized = labelSmallEmphasized.system().copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
    )
}

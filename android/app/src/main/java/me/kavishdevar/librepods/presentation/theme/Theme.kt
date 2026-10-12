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

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import me.kavishdevar.librepods.presentation.components.OneUIPressIndication
import androidx.compose.runtime.remember
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.LocalIndication
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

val ColorScheme.sectionHeader: Color
    get() = onBackground.copy(alpha = 0.6f)

private val AppleDarkColorScheme = darkColorScheme(
    onSurfaceVariant = Color(0xFF98989D),
    outlineVariant = Color(0xFF38383A),
    surfaceContainer = Color(0xFF000000), // for some reason background is not used as the background in gmail and settings app, but surfacecontainer, so using that
    onBackground = Color(0xFFFFFFFF),
    surface = Color(0xFF1C1C1E),
    onSurface = Color(0xFFFFFFFF),
    surfaceDim = Color(0x40888888),
    primary = Color(0xFF0091FF),
    secondaryContainer = Color(0xFF366AA8),
    onSecondaryContainer = Color(0xFF0091FF),
    onPrimary = Color(0xFFFFFFFF)
)

private val AppleLightColorScheme = lightColorScheme(
    onSurfaceVariant = Color(0xFF8E8E93),
    outlineVariant = Color(0xFFC6C6C8),
    surfaceContainer = Color(0xFFF2F2F7),
    onBackground = Color(0xFF000000),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF000000),
    surfaceDim = Color(0x40D9D9D9),
    secondaryContainer = Color(0xFF6BC0FF),
    onSecondaryContainer = Color(0xFF0088FF),
    primary = Color(0xFF0088FF),
    onPrimary = Color(0xFFFFFFFF)
)

@Composable
fun LibrePodsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    m3eEnabled: Boolean = false,
    design: AppDesign = if (m3eEnabled) AppDesign.OneUI else AppDesign.Apple,
    content: @Composable () -> Unit
) {
    // Legacy callers and saved Material choices also resolve to the available native design.
    val activeDesign = AppDesign.fromPreferenceValue(design.prefValue)
    val colorScheme = when (activeDesign) {
        AppDesign.Material -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        AppDesign.OneUI -> if (darkTheme) OneUIDarkColorScheme else OneUILightColorScheme
        AppDesign.Apple -> if (darkTheme) AppleDarkColorScheme else AppleLightColorScheme
    }

    CompositionLocalProvider(
        // One UI is built on the Material structure and restyled where LocalOneUI is set.
        LocalDesignSystem provides if (activeDesign == AppDesign.Apple) DesignSystem.Apple else DesignSystem.Material,
        LocalOneUI provides (activeDesign == AppDesign.OneUI),
        LocalOneUIColors provides if (darkTheme) OneUIDarkColors else OneUILightColors
    ) {
        MaterialExpressiveTheme(
            colorScheme = colorScheme,
            motionScheme = MotionScheme.standard(),
            typography = when (activeDesign) {
                AppDesign.Material -> MaterialTypography
                AppDesign.OneUI -> OneUITypography
                AppDesign.Apple -> AppleTypography
            }
        ) {
            // Keep the app at the same composition position when the design changes.
            val pressColor = MaterialTheme.colorScheme.onSurface
            val oneUIIndication = remember(pressColor) { OneUIPressIndication(pressColor) }
            CompositionLocalProvider(
                LocalIndication provides oneUIIndication,
                content = content
            )
        }
    }
}

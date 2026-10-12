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

import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedCornerStyle
import com.kyant.shapes.RoundedRectangle
import com.kyant.shapes.UnevenRoundedRectangle

// iOS rounds its cards, buttons and screens with continuous curvature (the "squircle"): the curve
// eases into the straight edge instead of meeting it with a circular arc, so the same radius looks
// softer. The Apple look uses these instead of RoundedCornerShape.

/** A rounded rectangle with iOS's continuous corners. */
fun AppleShape(radius: Dp): Shape = RoundedRectangle(radius, RoundedCornerStyle.Continuous)

/** [AppleShape] with a radius per corner, for the first and last rows of a grouped list. */
fun AppleUnevenShape(topStart: Dp = 0.dp, topEnd: Dp = 0.dp, bottomEnd: Dp = 0.dp, bottomStart: Dp = 0.dp): Shape =
    UnevenRoundedRectangle(topStart, topEnd, bottomEnd, bottomStart, RoundedCornerStyle.Continuous)

/** A pill with iOS's continuous ends, for buttons and switches. */
val AppleCapsule: Shape = Capsule(RoundedCornerStyle.Continuous)

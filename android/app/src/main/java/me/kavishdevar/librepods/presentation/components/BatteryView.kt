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

import android.content.res.Configuration
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import me.kavishdevar.librepods.R
import me.kavishdevar.librepods.data.Battery
import me.kavishdevar.librepods.data.BatteryComponent
import me.kavishdevar.librepods.data.BatteryStatus
import me.kavishdevar.librepods.data.hasReportedLevel
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.math.roundToInt

@Composable
fun BatteryView(
    batteryList: List<Battery>,
    budsRes: Int,
    caseRes: Int,
    leftBudsRes: Int = R.drawable.airpods_pro_3_left,
    rightBudsRes: Int = R.drawable.airpods_pro_3_right
) {
    val left = batteryList.find { it.component == BatteryComponent.LEFT }?.takeIf { it.hasReportedLevel() }
    val right = batteryList.find { it.component == BatteryComponent.RIGHT }?.takeIf { it.hasReportedLevel() }
    val case = batteryList.find { it.component == BatteryComponent.CASE }?.takeIf { it.hasReportedLevel() }
    val singleDisplayed = left != null && right != null && left.status == right.status &&
        (left.level - right.level) in -3..3

    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        BoxWithConstraints(Modifier.widthIn(max = 500.dp).fillMaxWidth()) {
            val imageBandHeight = minOf(148.dp, maxWidth * 0.35f)
            val budImageHeight = imageBandHeight * 0.63f
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                if (singleDisplayed || (left == null && right == null)) {
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        BatteryProductImage(budsRes, stringResource(R.string.buds),
                            imageBandHeight, budImageHeight)
                        Spacer(Modifier.height(12.dp))
                        if (singleDisplayed) {
                            BatteryIndicator(left!!.level.coerceAtMost(right!!.level), left.status)
                        } else {
                            UnavailableBattery()
                        }
                    }
                } else {
                    // A bud and its indicator share a column, including when only one bud reports.
                    Row(Modifier.weight(1f)) {
                        for (battery in listOfNotNull(left, right)) {
                            val isLeft = battery.component == BatteryComponent.LEFT
                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                BatteryProductImage(
                                    if (isLeft) leftBudsRes else rightBudsRes,
                                    stringResource(if (isLeft) R.string.left else R.string.right) +
                                        " " + stringResource(R.string.buds),
                                    imageBandHeight, budImageHeight)
                                Spacer(Modifier.height(12.dp))
                                BatteryIndicator(battery.level, battery.status,
                                    if (isLeft) "\uDBC6\uDCE5" else "\uDBC6\uDCE8")
                            }
                        }
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    BatteryProductImage(caseRes, stringResource(R.string.case_alt),
                        imageBandHeight, imageBandHeight)
                    Spacer(Modifier.height(12.dp))

                    if (case != null) {
                        BatteryIndicator(
                            case.level,
                            case.status,
                            prefix = if (!singleDisplayed) "\uDBC3\uDE6C" else ""
                        )
                    } else {
                        UnavailableBattery()
                    }
                }
            }
        }
    }
}

@Composable
private fun BatteryProductImage(resource: Int, description: String, bandHeight: Dp, imageHeight: Dp) {
    val bitmap = ImageBitmap.imageResource(resource)
    val painter = remember(bitmap, resource) {
        // Visible bounds plus a two-pixel margin; trim only the known asset padding.
        val crop = when (resource) {
            R.drawable.airpods_pro_3_buds -> intArrayOf(72, 95, 178, 130)
            R.drawable.airpods_pro_3_case -> intArrayOf(13, 57, 292, 204)
            R.drawable.airpods_pro_3_left -> intArrayOf(23, 15, 85, 130)
            R.drawable.airpods_pro_3_right -> intArrayOf(21, 15, 85, 130)
            R.drawable.airpods_pro_2_buds -> intArrayOf(28, 287, 1977, 1314)
            R.drawable.airpods_pro_2_case -> intArrayOf(9, 108, 785, 590)
            else -> intArrayOf(0, 0, bitmap.width, bitmap.height)
        }
        // Legacy drawable resources can be scaled by Android's resource density.
        val scale = when (resource) {
            R.drawable.airpods_pro_2_buds -> bitmap.width / 2048f
            R.drawable.airpods_pro_2_case -> bitmap.width / 805f
            else -> 1f
        }
        BitmapPainter(bitmap,
            srcOffset = IntOffset((crop[0] * scale).roundToInt(), (crop[1] * scale).roundToInt()),
            srcSize = IntSize((crop[2] * scale).roundToInt(), (crop[3] * scale).roundToInt()))
    }
    Box(Modifier.fillMaxWidth().height(bandHeight), contentAlignment = Alignment.Center) {
        Image(painter = painter, contentDescription = description,
            modifier = Modifier.size(
                width = imageHeight * (painter.intrinsicSize.width / painter.intrinsicSize.height),
                height = imageHeight))
    }
}

@Composable
private fun UnavailableBattery() {
    Text(stringResource(R.string.battery_unavailable),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodySmall)
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun BatteryViewPreview() {
    val fakeBattery = listOf(
        Battery(BatteryComponent.LEFT, 85, BatteryStatus.CHARGING),
        Battery(BatteryComponent.RIGHT, 40, BatteryStatus.OPTIMIZED_CHARGING),
        Battery(BatteryComponent.CASE, 60, BatteryStatus.NOT_CHARGING)
    )

    val bg = if (isSystemInDarkTheme()) Color.Black else Color(0xFFF2F2F7)

    Box(
        modifier = Modifier
            .background(bg)
            .padding(16.dp)
    ) {
        BatteryView(
            batteryList = fakeBattery,
            budsRes = R.drawable.airpods_pro_3_buds,
            caseRes = R.drawable.airpods_pro_3_case
        )
    }
}
